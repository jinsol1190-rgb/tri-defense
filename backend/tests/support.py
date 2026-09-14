"""SAMPLE/MOCK_PROOF test setup using existing contract artifacts, never API behavior."""
import io
import json
import shutil
import socket
import subprocess
import time
from pathlib import Path
from web3 import Web3
from tridefense_backend.registry import load_abi

ROOT = Path(__file__).resolve().parents[2]


class LocalChain:
    def __init__(self):
        self.node = None

    def start(self):
        forge = str(ROOT / ".tools/forge") if (ROOT / ".tools/forge").exists() else shutil.which("forge")
        anvil = str(ROOT / ".tools/anvil") if (ROOT / ".tools/anvil").exists() else shutil.which("anvil")
        if not forge or not anvil:
            raise RuntimeError("Foundry forge/anvil required for real local RPC tests")
        subprocess.run([forge, "build", "--no-lint"], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
        self.url = "http://127.0.0.1:" + str(port)
        self.node = subprocess.Popen([anvil, "--host", "127.0.0.1", "--port", str(port), "--chain-id", "31337"],
                                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            self.w3 = Web3(Web3.HTTPProvider(self.url, request_kwargs={"timeout": 2}))
            for _ in range(100):
                if self.node.poll() is not None:
                    raise RuntimeError("Owned Anvil exited")
                if self.w3.is_connected():
                    break
                time.sleep(0.1)
            else:
                raise RuntimeError("Anvil readiness timeout")
            self.sender = self.w3.eth.accounts[0]
            self.mock, _ = self.deploy("MockVerifier", self.sender)
            self.adapter, _ = self.deploy("VerifierAdapter", self.mock.address, 1)
            self.registry, self.block = self.deploy("ThreatRegistry", self.adapter.address, 7000, self.sender,
                                                  Web3.keccak(text="+12025550123"),
                                                  Web3.keccak(text="SAMPLE local number fixture v1"))
            return self
        except BaseException:
            self.stop()
            raise

    def deploy(self, name, *args):
        artifact = json.loads((ROOT / "out" / (name + ".sol") / (name + ".json")).read_text())
        contract = self.w3.eth.contract(abi=load_abi(ROOT / "contracts/abi", name), bytecode=artifact["bytecode"]["object"])
        receipt = self.w3.eth.wait_for_transaction_receipt(contract.constructor(*args).transact({"from": self.sender}))
        if receipt["status"] != 1:
            raise RuntimeError("Fixture deployment failed")
        return self.w3.eth.contract(address=receipt["contractAddress"], abi=contract.abi), receipt["blockNumber"]

    def payload(self, nonce=1, accepted=True):
        # These are explicitly synthetic transport fixtures, not model or proof outputs.
        voiceprint = Web3.keccak(text="SAMPLE backend transport fixture; no AI")
        proof = b"MOCK_PROOF backend fixture v1"
        value = int.from_bytes(voiceprint, "big")
        inputs = [1, 9000, value >> 128, value & (2**128 - 1), 31337,
                  int(self.registry.address, 16), int(self.sender, 16), nonce]
        if accepted:
            tx = self.mock.functions.setFixture(proof, inputs, True).transact({"from": self.sender})
            self.w3.eth.wait_for_transaction_receipt(tx)
        return {"idempotencyKey": "sample-" + str(nonce), "nonce": str(nonce),
                "voiceprintHash": Web3.to_hex(voiceprint), "riskScore": 9000,
                "proof": Web3.to_hex(proof), "publicInputs": [str(v) for v in inputs]}

    def rpc(self, method, params=None):
        response = self.w3.provider.make_request(method, params or [])
        if "error" in response:
            raise RuntimeError(str(response["error"]))
        return response["result"]

    def stop(self):
        if self.node:
            self.node.terminate()
            try:
                self.node.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.node.kill()
                self.node.wait()


def request(app, method, path, body=None, raw=None, content_type="application/json"):
    data = raw if raw is not None else json.dumps(body).encode() if body is not None else b""
    route, _, query = path.partition("?")
    environ = {"REQUEST_METHOD": method, "PATH_INFO": route, "QUERY_STRING": query,
               "CONTENT_TYPE": content_type, "CONTENT_LENGTH": str(len(data)), "wsgi.input": io.BytesIO(data)}
    status = []
    response = b"".join(app(environ, lambda line, headers: status.append(int(line.split()[0]))))
    return status[0], json.loads(response)
