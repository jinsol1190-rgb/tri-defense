#!/usr/bin/env python3
"""SAMPLE / MOCK_PROOF only. Android is the submission client; no host POST substitute."""
import argparse
import json
import shlex
import subprocess
import sys
import tempfile
import threading
from pathlib import Path
from urllib.request import urlopen
from wsgiref.simple_server import make_server

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend/tests'))
from support import LocalChain
from tridefense_backend.api import create_app
from tridefense_backend.registry import RegistryClient
from tridefense_backend.service import SubmissionService


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device', required=True, help='Explicit adb serial; installs debug APKs and clears only their app data')
    parser.add_argument('--serve', action='store_true', help='Keep API/chain available for Dashboard inspection after device test')
    args = parser.parse_args()
    adb = [str(ROOT / 'android/.tools/sdk/platform-tools/adb'), '-s', args.device]
    def device(*command):
        return subprocess.check_output(adb + list(command), text=True)
    chain = LocalChain().start()
    try:
        with tempfile.TemporaryDirectory() as directory:
            client = RegistryClient(chain.url, chain.registry.address, chain.sender, ROOT / 'contracts/abi', chain.block)
            service = SubmissionService(client, str(Path(directory) / 'integration.sqlite3'))
            try:
                with make_server('127.0.0.1', 8000, create_app(service)) as server:
                    fixture = dict(proofMode='MOCK_PROOF', chainId='31337', backendUrl='http://127.0.0.1:8000',
                                   registryAddress=chain.registry.address, submission=chain.payload())
                    out = ROOT / 'integration/out'
                    out.mkdir(exist_ok=True)
                    (out / 'fixture.json').write_text(json.dumps(fixture, indent=2))
                    thread = threading.Thread(target=server.serve_forever, daemon=True)
                    thread.start()
                    try:
                        for apk in ['app-debug.apk', '../androidTest/debug/app-debug-androidTest.apk']:
                            device('install', '-r', str(ROOT / 'android/app/build/outputs/apk/debug' / apk))
                        device('shell', 'pm clear org.tridefense.android')
                        device('reverse', 'tcp:8000', 'tcp:8000')
                        command = 'am instrument -w -r -e class org.tridefense.android.MockIntegrationDeviceTest -e fixture ' + shlex.quote(json.dumps(fixture)) + ' org.tridefense.android.test/androidx.test.runner.AndroidJUnitRunner'
                        result = device('shell', command)
                        (out / 'instrumentation.txt').write_text(result)
                        if 'OK (1 test)' not in result or 'FAILURES' in result:
                            raise RuntimeError(result)
                        evidence = json.loads(device('exec-out', 'run-as', 'org.tridefense.android', 'cat', 'files/integration-evidence.json'))
                        def get(path):
                            with urlopen('http://127.0.0.1:8000' + path, timeout=10) as response:
                                return json.load(response)
                        status = get('/v1/threat-submissions/' + evidence['submissionId'])
                        record = get('/v1/registry/threats/' + evidence['threatId'])
                        page = get('/v1/registry/events')
                        assert status['status'] == evidence['status'] == 'REGISTERED'
                        assert len(page['events']) == 1
                        event = page['events'][0]
                        assert record['threatId'] == event['args']['threatId'] == evidence['roomThreatId']
                        assert status['txHash'] == event['txHash'] == evidence['txHash']
                        assert record['zkProof'] == fixture['submission']['proof'] == evidence['roomProof']
                        assert record['voiceprintHash'] == evidence['roomVoiceprintHash']
                        assert evidence['roomThreatCount'] == evidence['roomEventCount'] == 1
                        receipt = chain.w3.eth.get_transaction_receipt(status['txHash'])
                        assert receipt['status'] == 1
                        report = dict(android=evidence, submission=status, record=record, events=page)
                        (out / 'result.json').write_text(json.dumps(report, indent=2))
                        print('PASS: Android UI -> HTTP -> VerifierAdapter -> MockVerifier -> Registry event -> Android Room', flush=True)
                        print('Dashboard: http://127.0.0.1:5173 · Backend API mode · submission ' + evidence['submissionId'], flush=True)
                        if args.serve:
                            print('READY: keep this process running during Dashboard verification. Ctrl-C stops owned services.', flush=True)
                            threading.Event().wait()
                    finally:
                        server.shutdown()
                        thread.join(timeout=5)
                        # The disposable emulator may already have been stopped by the operator.
                        subprocess.run(adb + ['reverse', '--remove', 'tcp:8000'],
                                       check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            finally:
                service.close()
    except KeyboardInterrupt:
        print('Stopped local integration services')
    finally:
        chain.stop()


if __name__ == '__main__':
    main()
