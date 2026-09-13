#!/usr/bin/env python3
"""Verify the running Dashboard against evidence produced by the Android device test."""
import json
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'integration/out'
result = json.loads((OUT / 'result.json').read_text())
status = result['submission']
command = ['pnpm', '--dir', str(ROOT / 'dashboard'), 'exec', 'agent-browser', '--session', 'integration-check']


def browser(*args):
    return subprocess.check_output(command + list(args), text=True)


try:
    browser('open', 'http://127.0.0.1:5173')
    deadline = time.monotonic() + 20
    button = 'Inspect threat ' + status['threatId']
    while button not in browser('snapshot', '-i'):
        if time.monotonic() > deadline:
            raise AssertionError('Android registration did not appear in Registry view')
        time.sleep(0.5)
    browser('find', 'role', 'button', 'click', '--name', button)
    browser('find', 'label', 'Submission ID', 'fill', status['submissionId'])
    browser('find', 'role', 'button', 'click', '--name', 'Track')
    required = ['ThreatRegistered', 'REGISTERED', 'REAL backend connection', 'MOCK_PROOF',
                status['threatId'], status['txHash'], result['record']['voiceprintHash']]
    deadline = time.monotonic() + 20
    while True:
        body = browser('get', 'text', 'body')
        if all(value in body for value in required):
            break
        if time.monotonic() > deadline:
            raise AssertionError('Dashboard did not display matching Android/Registry evidence')
        time.sleep(0.5)
    assert browser('eval', 'document.querySelector("vite-error-overlay, .vite-error-overlay") ? "ERROR" : "OK"').strip() == '"OK"'
    errors = browser('errors').strip()
    assert not errors, errors
    browser('screenshot', '--full', str(OUT / 'dashboard-verified.png'))
    (OUT / 'dashboard.txt').write_text(body)
    (OUT / 'dashboard-result.json').write_text(json.dumps(dict(status='PASS', checks=required, consoleErrors=[]), indent=2))
    print('PASS: Dashboard displays the Android submission, Registry detail and emitted event with REAL / MOCK_PROOF indicators')
finally:
    browser('close')
