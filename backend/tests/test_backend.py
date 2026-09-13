import json
import tempfile
import threading
import unittest
import urllib.request
from pathlib import Path
from unittest.mock import patch
from wsgiref.simple_server import make_server
from web3 import Web3
from tridefense_backend.api import create_app
from tridefense_backend.registry import RegistryClient
from tridefense_backend.service import SubmissionService
from support import LocalChain, ROOT, request


class BackendTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.chain = LocalChain().start()

    @classmethod
    def tearDownClass(cls):
        cls.chain.stop()

    def setUp(self):
        self.snapshot = self.chain.rpc("evm_snapshot")
        self.tmp = tempfile.TemporaryDirectory()
        self.db = str(Path(self.tmp.name) / "submissions.sqlite3")
        self.client = RegistryClient(self.chain.url, self.chain.registry.address, self.chain.sender,
                                     ROOT / "contracts/abi", self.chain.block)
        self.service = SubmissionService(self.client, self.db)
        self.app = create_app(self.service)

    def tearDown(self):
        self.service.close()
        self.tmp.cleanup()
        self.chain.rpc("evm_revert", [self.snapshot])

    def submit(self, nonce=1):
        payload = self.chain.payload(nonce)
        code, response = request(self.app, "POST", "/v1/threat-submissions", payload)
        self.assertEqual(code, 202, response)
        self.assertEqual(response["status"], "SUBMITTED")
        self.chain.w3.eth.wait_for_transaction_receipt(response["txHash"])
        return payload, response

    def status(self, response):
        return request(self.app, "GET", "/v1/threat-submissions/" + response["submissionId"])

    def test_submit_status_registry_and_event_share_real_chain_identity(self):
        payload, submitted = self.submit()
        code, status = self.status(submitted)
        self.assertEqual((code, status["status"]), (200, "REGISTERED"))
        self.assertEqual(status["proofMode"], "MOCK_PROOF")
        code, record = request(self.app, "GET", "/v1/registry/threats/" + status["threatId"])
        self.assertEqual(code, 200)
        self.assertEqual(record["voiceprintHash"], payload["voiceprintHash"])
        self.assertEqual(record["zkProof"], payload["proof"])
        self.assertEqual(record["riskScore"], 9000)
        code, page = request(self.app, "GET", "/v1/registry/events")
        self.assertEqual(code, 200)
        self.assertEqual(len(page["events"]), 1)
        event = page["events"][0]
        self.assertEqual(event["args"]["threatId"], status["threatId"])
        self.assertEqual(event["txHash"], status["txHash"])
        self.assertEqual(record["registeredAt"], event["args"]["registeredAt"])
        self.assertNotIn("phoneKey", event["args"])
        self.assertIn("blockHash", event)
        self.assertIn("logIndex", event)
        code, empty = request(self.app, "GET", "/v1/registry/events?cursor=" + page["nextCursor"])
        self.assertEqual((code, empty["events"]), (200, []))

    def test_same_payload_retry_is_durable_and_does_not_broadcast_again(self):
        payload, response = self.submit()
        nonce = self.chain.w3.eth.get_transaction_count(self.chain.sender)
        self.service.close()
        self.service = SubmissionService(self.client, self.db)
        self.app = create_app(self.service)
        code, retry = request(self.app, "POST", "/v1/threat-submissions", payload)
        self.assertEqual((code, retry["submissionId"], retry["status"]), (202, response["submissionId"], "REGISTERED"))
        self.assertEqual(self.chain.w3.eth.get_transaction_count(self.chain.sender), nonce)

    def test_idempotency_and_nonce_conflicts(self):
        payload, _ = self.submit()
        changed = {**payload, "riskScore": 8999}
        self.assertEqual(request(self.app, "POST", "/v1/threat-submissions", changed)[0], 409)
        changed = {**payload, "idempotencyKey": "different-key"}
        code, error = request(self.app, "POST", "/v1/threat-submissions", changed)
        self.assertEqual((code, error["errorCode"]), (409, "NONCE_CONFLICT"))

    def test_unapproved_mock_proof_rejected_no_events_no_submission(self):
        payload = self.chain.payload(accepted=False)
        code, error = request(self.app, "POST", "/v1/threat-submissions", payload)
        self.assertEqual((code, error["errorCode"]), (422, "InvalidProof"))
        self.assertEqual(self.service.db.execute("SELECT count(*) FROM submissions").fetchone()[0], 0)
        self.assertEqual(request(self.app, "GET", "/v1/registry/events")[1]["events"], [])

    def test_tampered_proof_and_fields_are_rejected_by_contract(self):
        payload = self.chain.payload()
        for changed in ({**payload, "proof": "0x1234"}, {**payload, "riskScore": 8000},
                        {**payload, "voiceprintHash": "0x" + "11" * 32}):
            with self.subTest(changed=changed):
                self.assertEqual(request(self.app, "POST", "/v1/threat-submissions", changed)[0], 422)

    def test_invalid_wire_values_and_phone_fields_rejected(self):
        payload = self.chain.payload(accepted=False)
        cases = [{**payload, "riskScore": value} for value in (True, -1, 10001, "9000", 90.5)]
        cases += [{**payload, "nonce": value} for value in (1, "-1", "01", str(2**256))]
        cases += [{**payload, "phoneKey": "0x" + "11" * 32}, {**payload, "publicInputs": ["1"]},
                  {**payload, "proof": "0x"}, {**payload, "voiceprintHash": "0x" + "00" * 32},
                  {**payload, "proof": "0x1"}, {**payload, "publicInputs": [1] * 8}]
        for body in cases:
            with self.subTest(body=body):
                self.assertEqual(request(self.app, "POST", "/v1/threat-submissions", body)[0], 400)

    def test_json_transport_validation(self):
        for raw in (b"{", b'{"x":1,"x":2}', b'"not an object"', b'\xff'):
            self.assertEqual(request(self.app, "POST", "/v1/threat-submissions", raw=raw)[0], 400)
        self.assertEqual(request(self.app, "POST", "/v1/threat-submissions", raw=b"x" * 150001)[0], 413)
        self.assertEqual(request(self.app, "POST", "/v1/threat-submissions", {}, content_type="text/plain")[0], 415)

    def test_pending_receipt_does_not_mean_registered(self):
        payload = self.chain.payload()
        self.chain.rpc("evm_setAutomine", [False])
        try:
            code, response = request(self.app, "POST", "/v1/threat-submissions", payload)
            self.assertEqual(code, 202)
            self.assertEqual(self.status(response)[1]["status"], "SUBMITTED")
            self.chain.rpc("evm_mine")
            self.assertEqual(self.status(response)[1]["status"], "REGISTERED")
        finally:
            self.chain.rpc("evm_setAutomine", [True])

    def test_broadcast_uncertainty_retains_identity_without_automatic_resend(self):
        payload = self.chain.payload()
        with patch.object(self.client, "send", side_effect=TimeoutError("sensitive RPC detail")) as send:
            code, response = request(self.app, "POST", "/v1/threat-submissions", payload)
            self.assertEqual((code, response["errorCode"]), (503, "BROADCAST_UNCERTAIN"))
            self.assertNotIn("sensitive", json.dumps(response))
            code, retry = request(self.app, "POST", "/v1/threat-submissions", payload)
            self.assertEqual(retry["submissionId"], response["submissionId"])
            self.assertEqual(retry["status"], "SUBMITTED")
            self.assertEqual(retry["errorCode"], "BROADCAST_UNCERTAIN")
            self.assertEqual(send.call_count, 1)

    def test_mined_revert_is_failed_not_registered(self):
        payload = self.chain.payload()
        def send_reverting(value):
            proof = bytes.fromhex(value["proof"][2:])
            inputs = [int(v) for v in value["publicInputs"]]
            revoke = self.chain.mock.functions.setFixture(proof, inputs, False).transact({"from": self.chain.sender})
            self.chain.w3.eth.wait_for_transaction_receipt(revoke)
            tx = self.client.call_for(value).transact({"from": self.chain.sender, "gas": 500000})
            return Web3.to_hex(tx)
        with patch.object(self.client, "send", side_effect=send_reverting):
            code, response = request(self.app, "POST", "/v1/threat-submissions", payload)
        self.assertEqual(code, 202)
        self.chain.w3.eth.wait_for_transaction_receipt(response["txHash"])
        code, status = self.status(response)
        self.assertEqual((code, status["status"], status["errorCode"]), (200, "FAILED", "TRANSACTION_REVERTED"))
        self.assertEqual(request(self.app, "GET", "/v1/registry/events")[1]["events"], [])

    def test_successful_unrelated_receipt_is_not_registration(self):
        payload = self.chain.payload()
        unrelated = self.chain.w3.eth.send_transaction({"from": self.chain.sender, "to": self.chain.sender, "value": 0})
        self.chain.w3.eth.wait_for_transaction_receipt(unrelated)
        with patch.object(self.client, "send", return_value=Web3.to_hex(unrelated)):
            _, response = request(self.app, "POST", "/v1/threat-submissions", payload)
        status = self.status(response)[1]
        self.assertEqual((status["status"], status["errorCode"]), ("FAILED", "REGISTRATION_EVENT_MISMATCH"))

    def test_event_pages_do_not_skip_or_duplicate_block_ranges(self):
        _, first = self.submit(1)
        self.chain.rpc("anvil_mine", ["0x64"])
        _, second = self.submit(2)
        code, page = request(self.app, "GET", "/v1/registry/events")
        self.assertEqual(code, 200)
        self.assertEqual([e["args"]["threatId"] for e in page["events"]], [first["threatId"]])
        code, next_page = request(self.app, "GET", "/v1/registry/events?cursor=" + page["nextCursor"])
        self.assertEqual(code, 200)
        self.assertEqual([e["args"]["threatId"] for e in next_page["events"]], [second["threatId"]])

    def test_rpc_outage_is_503_not_empty_success(self):
        with patch.object(self.client, "ensure_chain", side_effect=ConnectionError("private RPC")):
            for path in ("/v1/registry/events", "/v1/registry/threats/0x" + "11" * 32, "/v1/threat-submissions/id"):
                code, response = request(self.app, "GET", path)
                self.assertEqual((code, response["errorCode"]), (503, "DEPENDENCY_UNAVAILABLE"))
                self.assertNotIn("private", json.dumps(response))

    def test_missing_records_and_unknown_routes(self):
        self.assertEqual(request(self.app, "GET", "/v1/registry/threats/0x" + "11" * 32)[0], 404)
        self.assertEqual(request(self.app, "GET", "/v1/threat-submissions/missing")[0], 404)
        self.assertEqual(request(self.app, "POST", "/v1/blacklist-promotions", {})[0], 404)

    def test_event_api_includes_external_promotion_without_implementing_it(self):
        _, response = self.submit()
        phone = self.chain.registry.functions.TEST_PHONE_KEY().call()
        tx = self.chain.registry.functions.promoteToBlacklist(bytes.fromhex(response["threatId"][2:]), phone).transact({"from": self.chain.sender})
        self.chain.w3.eth.wait_for_transaction_receipt(tx)
        code, page = request(self.app, "GET", "/v1/registry/events")
        self.assertEqual(code, 200)
        self.assertEqual([e["event"] for e in page["events"]], ["ThreatRegistered", "BlacklistPromoted"])

    def test_invalid_cursor_and_namespace(self):
        for cursor in ("garbage", "W10=", "e30="):
            self.assertEqual(request(self.app, "GET", "/v1/registry/events?cursor=" + cursor)[0], 400)
        self.assertEqual(request(self.app, "GET", "/v1/registry/events?cursor=a&cursor=b")[0], 400)

    def test_reorganization_invalidates_cursor_and_registered_status(self):
        checkpoint = self.chain.rpc("evm_snapshot")
        _, response = self.submit()
        self.assertEqual(self.status(response)[1]["status"], "REGISTERED")
        _, page = request(self.app, "GET", "/v1/registry/events")
        self.chain.rpc("evm_revert", [checkpoint])
        self.chain.rpc("evm_increaseTime", [10])
        self.chain.rpc("anvil_mine", ["0x3"])
        code, error = request(self.app, "GET", "/v1/registry/events?cursor=" + page["nextCursor"])
        self.assertEqual((code, error["errorCode"]), (409, "CURSOR_REORG"))
        self.assertEqual(self.status(response)[1]["status"], "SUBMITTED")

    def test_cursor_detects_chain_height_rollback(self):
        checkpoint = self.chain.rpc("evm_snapshot")
        self.submit()
        _, page = request(self.app, "GET", "/v1/registry/events")
        self.chain.rpc("evm_revert", [checkpoint])
        code, error = request(self.app, "GET", "/v1/registry/events?cursor=" + page["nextCursor"])
        self.assertEqual((code, error["errorCode"]), (409, "CURSOR_REORG"))

    def test_database_rejects_different_sender_configuration(self):
        other = RegistryClient(self.chain.url, self.chain.registry.address, self.chain.w3.eth.accounts[1],
                               ROOT / "contracts/abi", self.chain.block)
        with self.assertRaises(ValueError):
            SubmissionService(other, self.db)

    def test_rpc_configuration_rejects_public_endpoint(self):
        with self.assertRaises(ValueError):
            RegistryClient("https://example.com", self.chain.registry.address, self.chain.sender,
                           ROOT / "contracts/abi", self.chain.block)

    def test_live_http_listener(self):
        payload = self.chain.payload()
        with make_server("127.0.0.1", 0, self.app) as server:
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            try:
                url = "http://127.0.0.1:%d/v1/threat-submissions" % server.server_port
                req = urllib.request.Request(url, json.dumps(payload).encode(), {"Content-Type": "application/json"})
                with urllib.request.urlopen(req, timeout=5) as response:
                    self.assertEqual(response.status, 202)
                    submitted = json.load(response)
                self.chain.w3.eth.wait_for_transaction_receipt(submitted["txHash"])
                with urllib.request.urlopen(url + "/" + submitted["submissionId"], timeout=5) as response:
                    self.assertEqual(json.load(response)["status"], "REGISTERED")
            finally:
                server.shutdown()
                thread.join(timeout=5)


if __name__ == "__main__":
    unittest.main()
