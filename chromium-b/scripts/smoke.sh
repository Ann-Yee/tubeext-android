#!/usr/bin/env bash
# Minimal device smoke test. Refuses to call an APK "working" until Android launches it.
set -u
mkdir -p test-output
echo "ABI list:" | tee test-output/status.txt
adb shell getprop ro.product.cpu.abilist | tee -a test-output/status.txt
adb shell getprop ro.dalvik.vm.native.bridge | tee -a test-output/status.txt
adb logcat -c
APK="$(find dist -name '*.apk' | head -n1)"
test -n "$APK" || exit 11
echo "Installing $APK" | tee -a test-output/status.txt
if ! adb install -r "$APK" > test-output/install.log 2>&1; then
    cat test-output/install.log
    if grep -q "NO_MATCHING_ABIS" test-output/install.log; then
        echo "Emulator cannot execute ARM64 native library; needs real ARM64 device" | tee -a test-output/status.txt
        exit 12
    fi
    exit 13
fi
adb shell am force-stop app.tubeext.chromium || true
adb shell monkey -p app.tubeext.chromium -c android.intent.category.LAUNCHER 1 >test-output/launch.log 2>&1 || true
sleep 25
adb shell pidof app.tubeext.chromium >test-output/pid.txt 2>&1 || true
adb logcat -d -b crash -v time >test-output/crash.log || true
adb logcat -d -v time >test-output/logcat.log || true
adb shell dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity' >test-output/activity.txt || true
adb exec-out screencap -p >test-output/screenshot.png || true
echo "PID: $(cat test-output/pid.txt)" | tee -a test-output/status.txt
if grep -E -i 'FATAL EXCEPTION|UnsatisfiedLinkError|NoClassDefFoundError|ClassNotFoundException' test-output/crash.log; then
    echo "App failed with Android exception" | tee -a test-output/status.txt
    exit 14
fi
if [[ ! -s test-output/pid.txt ]]; then
    echo "App process did not survive startup" | tee -a test-output/status.txt
    exit 15
fi
echo "Android launch smoke-test PASS (browser page functionality not yet verified)" | tee -a test-output/status.txt
