"""Generated-ABI client only. Registry contracts remain authoritative."""
import base64
import json
from pathlib import Path
from urllib.parse import urlsplit
from web3 import Web3
from web3.exceptions import ContractLogicError, TransactionNotFound
from web3.logs import DISCARD
from .errors import ApiError


def hex_value(value):
    return Web3.to_hex(value)


def load_abi(directory, name):
    return json.loads((Path(directory) / (name + ".json")).read_text())


class RegistryClient:
    def __init__(self, rpc_url, address, sender, abi_dir, deployment_block):
        url = urlsplit(rpc_url)
        if url.scheme != "http" or url.hostname not in ("127.0.0.1", "localhost", "::1") or url.username or url.password:
            raise ValueError("P0 requires a loopback HTTP RPC without credentials")
        self.w3 = Web3(Web3.HTTPProvider(rpc_url, request_kwargs={"timeout": 5}))
        if self.w3.eth.chain_id != 31337:
            raise ValueError("P0 supports local chain 31337 only")
        self.sender = Web3.to_checksum_address(sender)
        self.address = Web3.to_checksum_address(address)
        self.deployment_block = deployment_block
        if deployment_block < 0 or deployment_block > self.w3.eth.block_number:
            raise ValueError("Invalid deployment block")
        self.abi = load_abi(abi_dir, "ThreatRegistry")
        self.contract = self.w3.eth.contract(address=self.address, abi=self.abi)
        if not self.w3.eth.get_code(self.address):
            raise ValueError("Registry address has no deployed code")
        adapter_address = self.contract.functions.VERIFIER_ADAPTER().call()
        adapter = self.w3.eth.contract(address=adapter_address, abi=load_abi(abi_dir, "VerifierAdapter"))
        target = adapter.functions.VERIFIER().call()
        mock = self.w3.eth.contract(address=target, abi=load_abi(abi_dir, "MockVerifier"))
        if mock.functions.LOCAL_CHAIN_ID().call() != 31337:
            raise ValueError("Expected configured local Mock Verifier")
        # Check configured artifacts, never call setFixture from the API.
        mock.functions.FIXTURE_ADMIN().call()
        self.meta = {"chainId": "31337", "registryAddress": self.address,
                     "transactionMode": "LOCAL_TRANSACTION", "proofMode": "MOCK_PROOF"}
        self.domain = {**self.meta, "sender": self.sender,
                       "deploymentBlock": deployment_block,
                       "deploymentBlockHash": hex_value(self.w3.eth.get_block(deployment_block)["hash"]),
                       "codeHash": hex_value(Web3.keccak(self.w3.eth.get_code(self.address)))}

    def ensure_chain(self):
        if self.w3.eth.chain_id != 31337 or hex_value(self.w3.eth.get_block(self.deployment_block)["hash"]) != self.domain["deploymentBlockHash"]:
            raise ApiError(503, "CHAIN_CHANGED")

    def call_for(self, payload):
        return self.contract.functions.registerThreat(
            bytes.fromhex(payload["voiceprintHash"][2:]), payload["riskScore"], int(payload["nonce"]),
            bytes.fromhex(payload["proof"][2:]), [int(value) for value in payload["publicInputs"]])

    def preflight(self, payload):
        try:
            return hex_value(self.call_for(payload).call({"from": self.sender}))
        except ContractLogicError as exc:
            code = "CONTRACT_REJECTED"
            data = getattr(exc, "data", None)
            if isinstance(data, str):
                for entry in self.abi:
                    if entry["type"] == "error":
                        signature = entry["name"] + "(" + ",".join(v["type"] for v in entry["inputs"]) + ")"
                        if data.startswith(hex_value(Web3.keccak(text=signature)[:4])):
                            code = entry["name"]
                            break
            raise ApiError(422, code) from exc

    def send(self, payload):
        return hex_value(self.call_for(payload).transact({"from": self.sender}))

    def record(self, threat_id, block="latest"):
        raw = bytes.fromhex(threat_id[2:])
        if not self.contract.functions.exists(raw).call(block_identifier=block):
            raise ApiError(404, "THREAT_NOT_FOUND")
        values = self.contract.functions.getThreat(raw).call(block_identifier=block)
        return {"threatId": hex_value(values[0]), "voiceprintHash": hex_value(values[1]),
                "riskScore": values[2], "registeredAt": str(values[3]), "zkProof": hex_value(values[4])}

    def receipt_status(self, tx_hash, threat_id, payload):
        try:
            receipt = self.w3.eth.get_transaction_receipt(tx_hash)
        except TransactionNotFound:
            return "SUBMITTED", None
        block = self.w3.eth.get_block(receipt["blockNumber"])
        if block["hash"] != receipt["blockHash"]:
            return "SUBMITTED", "REORG_PENDING"
        if receipt["status"] != 1:
            return "FAILED", "TRANSACTION_REVERTED"
        events = self.contract.events.ThreatRegistered().process_receipt(receipt, errors=DISCARD)
        matches = [event for event in events if event["address"].lower() == self.address.lower()
                   and hex_value(event["args"]["threatId"]) == threat_id
                   and hex_value(event["args"]["voiceprintHash"]) == payload["voiceprintHash"]
                   and event["args"]["riskScore"] == payload["riskScore"]]
        if len(matches) != 1:
            return "FAILED", "REGISTRATION_EVENT_MISMATCH"
        record = self.record(threat_id, receipt["blockNumber"])
        if record["voiceprintHash"] != payload["voiceprintHash"] or record["riskScore"] != payload["riskScore"] or record["zkProof"] != payload["proof"]:
            return "FAILED", "REGISTRATION_RECORD_MISMATCH"
        return "REGISTERED", None

    def events(self, cursor=None):
        head = self.w3.eth.block_number
        start = self.deployment_block
        if cursor:
            try:
                if len(cursor) > 2048:
                    raise ValueError()
                c = json.loads(base64.urlsafe_b64decode(cursor.encode()))
                if set(c) != {"v", "chainId", "registry", "nextBlock", "previousHash"} or c["v"] != 1:
                    raise ValueError()
                start = c["nextBlock"]
                if type(start) is not int or start < self.deployment_block:
                    raise ValueError()
                if c["chainId"] != 31337 or c["registry"] != self.address:
                    raise ValueError()
            except (ValueError, TypeError, KeyError, UnicodeError):
                raise ApiError(400, "INVALID_CURSOR")
            if start > head + 1:
                raise ApiError(409, "CURSOR_REORG", recovery="Chain head moved back; restart without cursor")
            if start > 0 and c["previousHash"] != hex_value(self.w3.eth.get_block(start - 1)["hash"]):
                raise ApiError(409, "CURSOR_REORG", recovery="Discard cached chain events and restart without cursor")
        end = min(head, start + 99)  # Whole-block pages cannot split or skip same-block events.
        anchor = hex_value(self.w3.eth.get_block(end)["hash"])
        result = []
        if start <= end:
            for event_class in (self.contract.events.ThreatRegistered, self.contract.events.BlacklistPromoted):
                for event in event_class().get_logs(from_block=start, to_block=end):
                    args = {key: hex_value(value) if isinstance(value, bytes) else str(value) if key == "registeredAt" else value
                            for key, value in event["args"].items()}
                    result.append({"chainId": "31337", "blockNumber": str(event["blockNumber"]),
                                   "blockHash": hex_value(event["blockHash"]), "txHash": hex_value(event["transactionHash"]),
                                   "logIndex": str(event["logIndex"]), "event": event["event"], "args": args})
        if anchor != hex_value(self.w3.eth.get_block(end)["hash"]):
            raise ApiError(503, "CHAIN_CHANGED_DURING_QUERY")
        result.sort(key=lambda event: (int(event["blockNumber"]), int(event["logIndex"])))
        next_cursor = base64.urlsafe_b64encode(json.dumps({"v": 1, "chainId": 31337, "registry": self.address,
                                                         "nextBlock": end + 1, "previousHash": anchor}).encode()).decode()
        return {"events": result, "nextCursor": next_cursor, "throughBlock": str(end), **self.meta}
