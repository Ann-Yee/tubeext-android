#!/usr/bin/env bash
set -u
mkdir -p test-output
status=0
probe() {
    label="$1"; pkg="$2"; apk="$3"
    echo "::group::SMOKE $label"
    echo "=== $label $(date -u) ===" | tee "test-output/$label.status"
    adb logcat -c
    adb shell am force-stop "$pkg" || true
    if ! adb install -r "$apk" > "test-output/$label.install" 2>&1; then
        echo "INSTALL_FAIL" | tee -a "test-output/$label.status"
        cat "test-output/$label.install"
        status=1
        echo "::endgroup::"
        return
    fi
    adb shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >"test-output/$label.launch" 2>&1 || true
    sleep 16
    adb shell pidof "$pkg" >"test-output/$label.pid" || true
    adb logcat -d -b crash -v time >"test-output/$label.crash" || true
    adb logcat -d -v time >"test-output/$label.logcat" || true
    adb exec-out screencap -p >"test-output/$label.png" || true
    if [[ -s "test-output/$label.pid" ]]; then
       echo "RUNNING pid=$(cat test-output/$label.pid)" | tee -a "test-output/$label.status"
    else
       echo "EXITED_OR_CRASHED" | tee -a "test-output/$label.status"
       status=1
    fi
    if [[ -s "test-output/$label.crash" ]]; then
       sed -n '1,35p' "test-output/$label.crash" || true
    fi
    echo "::endgroup::"
}
adb shell getprop ro.product.cpu.abilist | tee test-output/abi.txt
adb shell getprop ro.dalvik.vm.native.bridge | tee -a test-output/abi.txt
probe upstream org.barebrowser "dist/Bare-2.0.7-arm64-v8a.apk"
probe repacked app.tubeext.chromium "dist/TubeExt-Chromium-B-experimental-arm64.apk"
echo "A/B status: $status" | tee test-output/result.txt
exit "$status"
