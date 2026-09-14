#!/usr/bin/env python3
"""SAMPLE / MOCK_PROOF demo of Backend P0 against owned ephemeral Anvil."""
import argparse
import json
import sys
import tempfile
import threading
import time
import urllib.request
from pathlib import Path
from wsgiref.simple_server import make_server

# Repository-only development fixture helper; not imported by the API runtime.
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tests"))
from support import LocalChain, ROOT
from tridefense_backend.api import create_app
from tridefense_backend.registry import RegistryClient
from tridefense_backend.service import SubmissionService


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serve", action="store_true", help="Keep the local API and chain running until Ctrl-C")
    parser.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()
    chain = LocalChain().start()
    try:
        with tempfile.TemporaryDirectory() as directory:
            client = RegistryClient(chain.url, chain.registry.address, chain.sender, ROOT / "contracts/abi", chain.block)
            service = SubmissionService(client, str(Path(directory) / "demo.sqlite3"))
            try:
                with make_server("127.0.0.1", args.port if args.serve else 0, create_app(service)) as server:
                    payload = chain.payload()
                    base = "http://127.0.0.1:%d" % server.server_port
                    print("SAMPLE / MOCK_PROOF local API: " + base, flush=True)
                    if args.serve:
                        print("POST /v1/threat-submissions with this explicitly approved test fixture:", flush=True)
                        print(json.dumps(payload, indent=2), flush=True)
                        server.serve_forever()
                    else:
                        thread = threading.Thread(target=server.serve_forever, daemon=True)
                        thread.start()
                        try:
                            req = urllib.request.Request(base + "/v1/threat-submissions", json.dumps(payload).encode(),
                                                         {"Content-Type": "application/json"})
                            with urllib.request.urlopen(req, timeout=10) as response:
                                assert response.status == 202
                                submitted = json.load(response)
                            def get(path):
                                with urllib.request.urlopen(base + path, timeout=10) as response:
                                    return json.load(response)
                            for _ in range(50):
                                status = get("/v1/threat-submissions/" + submitted["submissionId"])
                                if status["status"] != "SUBMITTED":
                                    break
                                time.sleep(0.1)
                            record = get("/v1/registry/threats/" + status["threatId"])
                            events = get("/v1/registry/events")
                            assert status["status"] == "REGISTERED"
                            assert record["threatId"] == events["events"][0]["args"]["threatId"] == status["threatId"]
                            print(json.dumps({"submission": status, "record": record, "events": events}, indent=2))
                            print("PASS: HTTP submission -> receipt-backed status -> Registry -> event identity")
                        finally:
                            server.shutdown()
                            thread.join(timeout=5)
            finally:
                service.close()
    except KeyboardInterrupt:
        print("Stopped local demo")
    finally:
        chain.stop()


if __name__ == "__main__":
    main()
