#!/usr/bin/env python3
"""SAMPLE/MOCK_PROOF: build, deploy and verify on an owned ephemeral loopback Anvil."""
import hashlib
import json
import shutil
import socket
import subprocess
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

from export_abi import ROOT, export


def tool(name):
    local = ROOT / ".tools" / name
    found = str(local) if local.is_file() else shutil.which(name)
    if not found:
        raise SystemExit("Missing Foundry tool: " + name)
    return found


def run(*args):
    result = subprocess.run(args, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError(result.stdout)
    return result.stdout.strip()


def main():
    forge, anvil, cast = tool("forge"), tool("anvil"), tool("cast")
    run(forge, "build")
    export()
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    rpc_url = "http://127.0.0.1:" + str(port)
    # Never connect this script to an external network or expose unlocked accounts.
    node = subprocess.Popen(
        [anvil, "--host", "127.0.0.1", "--port", str(port), "--chain-id", "31337"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )

    def rpc(method, params):
        data = json.dumps({"jsonrpc": "2.0", "id": 1, "method": method, "params": params}).encode()
        request = urllib.request.Request(rpc_url, data, {"Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=5) as response:
            payload = json.load(response)
        if "error" in payload:
            raise RuntimeError(str(payload["error"]))
        return payload["result"]

    def script(name, signature, *args):
        run(forge, "script", "contracts/script/" + name + ".s.sol:" + name,
            "--sig", signature, *args, "--rpc-url", rpc_url,
            "--sender", operator, "--unlocked", "--broadcast", "--slow", "--non-interactive")
        return json.loads((ROOT / "broadcast" / (name + ".s.sol") / "31337" / "run-latest.json").read_text())

    def checked_receipt(tx):
        actual = rpc("eth_getTransactionByHash", [tx["hash"]])
        if actual["nonce"] != tx["transaction"]["nonce"]:
            raise RuntimeError("Broadcast transaction hash/nonce mismatch")
        return rpc("eth_getTransactionReceipt", [tx["hash"]])

    try:
        for _ in range(100):
            if node.poll() is not None:
                raise RuntimeError("Owned Anvil exited before readiness; retry with a new port")
            try:
                if rpc("eth_chainId", []) == "0x7a69":
                    break
            except (OSError, urllib.error.URLError):
                pass
            time.sleep(0.1)
        else:
            raise RuntimeError("Anvil readiness timeout")
        if "anvil" not in rpc("web3_clientVersion", []).lower():
            raise RuntimeError("Expected owned local Anvil")
        operator = rpc("eth_accounts", [])[0]
        deployed = script("DeployLocal", "run(address)", operator)
        addresses = {}
        manifest_contracts = {}
        for tx in deployed["transactions"]:
            name = tx.get("contractName")
            if name not in ("MockVerifier", "VerifierAdapter", "ThreatRegistry"):
                continue
            address = tx["contractAddress"]
            receipt = checked_receipt(tx)
            if receipt["contractAddress"].lower() != address.lower():
                raise RuntimeError("Deployment address/receipt mismatch")
            if int(receipt["status"], 16) != 1:
                raise RuntimeError("Deployment receipt failed: " + name)
            code = rpc("eth_getCode", [address, "latest"])
            if code == "0x":
                raise RuntimeError("Missing deployed code: " + name)
            addresses[name] = address
            manifest_contracts[name] = {
                "address": address, "transactionHash": tx["hash"],
                "deploymentBlock": int(receipt["blockNumber"], 16),
                "gasUsed": int(receipt["gasUsed"], 16),
                "runtimeBytecodeSha256": hashlib.sha256(bytes.fromhex(code[2:])).hexdigest(),
                "abi": "../abi/" + name + ".json",
            }
        if len(addresses) != 3:
            raise RuntimeError("Expected three contract deployments")
        smoke = script("SmokeLocal", "run(address,address,address)",
                       addresses["MockVerifier"], addresses["ThreatRegistry"], operator)
        events = []
        for tx in smoke["transactions"]:
            receipt = checked_receipt(tx)
            if int(receipt["status"], 16) != 1:
                raise RuntimeError("Smoke transaction failed")
            for log in receipt["logs"]:
                if log["address"].lower() == addresses["ThreatRegistry"].lower():
                    events.append({"function": tx["function"], "transactionHash": tx["hash"],
                                   "blockNumber": int(receipt["blockNumber"], 16),
                                   "gasUsed": int(receipt["gasUsed"], 16),
                                   "topics": log["topics"], "data": log["data"]})
        if len(events) != 2 or events[0]["topics"][1] != events[1]["topics"][1]:
            raise RuntimeError("Registration and promotion events must share one threatId")
        signatures = ("ThreatRegistered(bytes32,bytes32,uint16,uint64)", "BlacklistPromoted(bytes32,bytes32)")
        for event, signature in zip(events, signatures):
            if event["topics"][0] != run(cast, "keccak", signature):
                raise RuntimeError("Unexpected event signature or order")
        threat_id = events[0]["topics"][1]
        # Check live chain reads after receipt confirmation, independently of script simulation.
        record = run(cast, "call", addresses["ThreatRegistry"],
                     "getThreat(bytes32)((bytes32,bytes32,uint16,uint64,bytes))", threat_id, "--rpc-url", rpc_url)
        phone_key = run(cast, "call", addresses["ThreatRegistry"], "TEST_PHONE_KEY()(bytes32)", "--rpc-url", rpc_url)
        if run(cast, "call", addresses["ThreatRegistry"], "isBlacklisted(bytes32)(bool)", phone_key,
               "--rpc-url", rpc_url) != "true":
            raise RuntimeError("Live blacklist read failed")
        if threat_id.lower() not in record.lower():
            raise RuntimeError("Live record read failed")
        manifest = {
            "status": "SAMPLE / MOCK_PROOF; local mechanics verified, NO zkML verification",
            "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
            "chainId": 31337, "network": "owned ephemeral Anvil; stopped after this run",
            "rpc": rpc_url, "explorer": None, "operator": operator,
            "toolchain": {"forge": run(forge, "--version").splitlines()[0], "solidity": "0.8.24"},
            "contracts": manifest_contracts, "threatId": threat_id,
            "sourceSha256": {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
                             for p in sorted((ROOT / "contracts" / "src").rglob("*.sol"))},
            "fixture": {"circuitId": 1, "minRiskScore": 7000, "riskScore": 9000,
                        "proof": "MOCK_PROOF fixture v1",
                        "voiceprintSource": "keccak256 of SAMPLE text; NOT model output",
                        "syntheticNumber": "+12025550123", "phoneKey": phone_key,
                        "numberEvidence": "SAMPLE local number fixture v1"},
            "events": events, "liveRecordRead": record,
        }
        manifest["fixture"]["proofBytes"] = len(manifest["fixture"]["proof"].encode())
        target = ROOT / "contracts" / "deployments" / "local.json"
        target.write_text(json.dumps(manifest, indent=2) + "\n")
        print("Local deployment, registration, promotion and live reads passed")
        print("SAMPLE threatId: " + threat_id)
        print("Manifest: " + str(target))
    finally:
        node.terminate()
        try:
            node.wait(timeout=5)
        except subprocess.TimeoutExpired:
            node.kill()
            node.wait()


if __name__ == "__main__":
    main()
