#!/usr/bin/env python3
"""Capture the actual offline Compose demo on an explicitly selected Android emulator."""
import argparse
from pathlib import Path
import shutil
import subprocess
import time

ANDROID = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True, help='adb serial of a disposable emulator')
args = parser.parse_args()
adb_path = ANDROID / '.tools/sdk/platform-tools/adb'
if not adb_path.exists():
    adb_path = shutil.which('adb')
if not adb_path:
    raise SystemExit('Install adb and put it on PATH, or use android/.tools/sdk.')
adb = [str(adb_path), '-s', args.serial]

def run(*command, **kwargs):
    return subprocess.run(adb + list(command), check=True, **kwargs)

for apk in ['debug/app-debug.apk', 'androidTest/debug/app-debug-androidTest.apk']:
    run('install', '-r', str(ANDROID / 'app/build/outputs/apk' / apk))
out = ANDROID / 'docs/demo'
out.mkdir(parents=True, exist_ok=True)
remote_video = '/sdcard/tri-defense-ui-demo.mp4'
existing = subprocess.run(adb + ['shell', 'pidof', 'screenrecord'], capture_output=True, text=True)
if existing.stdout.strip():
    raise SystemExit('An emulator recording is already running; finish it before capturing this demo.')
record = subprocess.Popen(adb + ['shell', 'screenrecord', '--bit-rate', '3000000', '--size', '720x1600', '--time-limit', '120', remote_video],
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
try:
    remote_captures = '/sdcard/Android/data/org.tridefense.android/files/demo-captures'
    subprocess.run(adb + ['shell', 'rm', '-f', remote_captures + '/capture-request.txt'], check=False)
    screenshots = out / 'screenshots'
    screenshots.mkdir(exist_ok=True)
    expected = {'01-incoming', '02-in-call', '03-warning', '04-after-call'}
    captured = set()
    with (out / 'capture-test.txt').open('w') as log:
        test = subprocess.Popen(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
            'org.tridefense.android.ui.demo.DemoUiTest#continuousCallDemo',
            '-e', 'captureDemo', 'true', 'org.tridefense.android.test/androidx.test.runner.AndroidJUnitRunner'],
            stdout=log, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 110
        try:
            while test.poll() is None:
                if time.monotonic() > deadline:
                    raise RuntimeError('Capture test timed out')
                response = subprocess.run(adb + ['shell', 'cat', remote_captures + '/capture-request.txt'], capture_output=True, text=True)
                name = response.stdout.strip()
                if name in expected and name not in captured:
                    time.sleep(1.2) # Dwell for the video; test waits for this screenshot acknowledgement.
                    with (screenshots / (name + '.png')).open('wb') as image:
                        run('exec-out', 'screencap', '-p', stdout=image)
                    run('shell', 'touch', remote_captures + '/' + name + '.done')
                    captured.add(name)
                    print('Captured ' + name, flush=True)
                else:
                    time.sleep(.1)
        finally:
            if test.poll() is None:
                run('shell', 'am', 'force-stop', 'org.tridefense.android')
                test.wait(timeout=10)
    result = (out / 'capture-test.txt').read_text()
    if 'OK (1 test)' not in result or captured != expected:
        raise RuntimeError(result)

finally:
    # Stop only this emulator's recording; SIGINT finalizes the MP4 container.
    pid = subprocess.run(adb + ['shell', 'pidof', 'screenrecord'], capture_output=True, text=True)
    for value in pid.stdout.split():
        if value.isdigit():
            subprocess.run(adb + ['shell', 'kill', '-2', value], check=False)
    record.wait(timeout=15)
if not all((screenshots / (name + '.png')).exists() for name in expected):
    raise RuntimeError('Expected four actual screen captures')
for old in screenshots.glob('*.png'):
    if old.stem not in expected:
        old.unlink()
video = subprocess.run(adb + ['pull', remote_video, str(out / 'tri-defense-ui-demo.mp4')], capture_output=True, text=True)
print('PASS: four real Android screenshots and UI flow test')
if video.returncode == 0:
    print('Video: ' + str(out / 'tri-defense-ui-demo.mp4'))
else:
    print('Video unavailable: ' + (record.stdout.read() if record.stdout else '') + video.stderr)
run('shell', 'am', 'start', '-n', 'org.tridefense.android/.ui.demo.DemoActivity')
