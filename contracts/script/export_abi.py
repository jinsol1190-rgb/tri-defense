#!/usr/bin/env python3
"""Export deterministic ABI JSON from Foundry artifacts; no third-party packages."""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTRACTS = ("ThreatRegistry", "VerifierAdapter", "IProofVerifier", "MockVerifier")


def export(check=False):
    for name in CONTRACTS:
        source = ROOT / "out" / (name + ".sol") / (name + ".json")
        target = ROOT / "contracts" / "abi" / (name + ".json")
        content = json.dumps(json.loads(source.read_text())["abi"], indent=2) + "\n"
        if check:
            if not target.exists() or target.read_text() != content:
                raise SystemExit("Stale or missing ABI: " + str(target))
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content)
    print("ABI check passed" if check else "Exported four contract ABIs")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    export(parser.parse_args().check)
