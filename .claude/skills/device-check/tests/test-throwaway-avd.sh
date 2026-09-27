#!/usr/bin/env bash
set -euo pipefail

repo_root=$(git rev-parse --show-toplevel)
helper_path=${DEVICE_CHECK_HELPER:-$repo_root/.claude/skills/device-check/scripts/throwaway-avd.sh}
test_root=$(mktemp -d)
trap 'rm -rf "$test_root"' EXIT

mkdir -p "$test_root/sdk/platform-tools" "$test_root/sdk/emulator" "$test_root/bin" \
    "$test_root/avd/Source37.avd"
cat > "$test_root/sdk/platform-tools/adb" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ ${1:-} == devices ]]; then
    printf 'List of devices attached\n'
    fake_state=${FAKE_ADB_STATE:-}
    [[ -n $fake_state && -f $fake_state ]] && printf '%s\tdevice\n' "$(cat "$fake_state")"
    exit 0
fi
[[ ${1:-} == -s && ${2:-} == "$FAKE_SERIAL" ]]
shift 2
case "$*" in
    "wait-for-device") exit 0 ;;
    "emu avd name") printf '%s\n' "$FAKE_AVD_NAME" ;;
    "shell getprop sys.boot_completed")
        if [[ -z ${FAKE_BOOT_COUNT:-} ]]; then
            printf '1\n'
            exit 0
        fi
        boot_count=0
        [[ -f $FAKE_BOOT_COUNT ]] && boot_count=$(cat "$FAKE_BOOT_COUNT")
        boot_count=$((boot_count + 1))
        printf '%s' "$boot_count" > "$FAKE_BOOT_COUNT"
        if [[ ${FAKE_BOOT_NEVER:-false} == true || $boot_count -lt ${FAKE_BOOT_AFTER:-1} ]]; then
            printf '0\n'
        else
            printf '1\n'
        fi
        ;;
    "emu kill")
        kill "$(cat "$FAKE_EMULATOR_PID")" 2>/dev/null || true
        rm -f "$FAKE_ADB_STATE"
        ;;
    *) exit 1 ;;
esac
EOF
chmod +x "$test_root/sdk/platform-tools/adb"
cat > "$test_root/sdk/emulator/emulator" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s' "$FAKE_SERIAL" > "$FAKE_ADB_STATE"
printf '%s' "$$" > "$FAKE_EMULATOR_PID"
trap 'rm -f "$FAKE_ADB_STATE"' EXIT
while :; do sleep 60; done
EOF
chmod +x "$test_root/sdk/emulator/emulator"
cat > "$test_root/bin/lsof" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ -n ${FAKE_BUSY_PORT:-} && " $* " == *":$FAKE_BUSY_PORT "* ]]; then
    printf '123\n'
    exit 0
fi
exit 1
EOF
chmod +x "$test_root/bin/lsof"
printf 'AvdId=Source37\nimage.sysdir.1=system-images/android-37.1/google_apis/arm64-v8a/\n' \
    > "$test_root/avd/Source37.avd/config.ini"
printf 'path=%s/avd/Source37.avd\npath.rel=avd/Source37.avd\n' "$test_root" \
    > "$test_root/avd/Source37.ini"

ANDROID_SDK_ROOT="$test_root/sdk" ANDROID_AVD_HOME="$test_root/avd" \
    bash "$helper_path" \
    create DeviceCheck 5612 Source37

test -f "$test_root/avd/DeviceCheck.avd/config.ini"
test -f "$test_root/avd/DeviceCheck.ini"
test "$(find "$test_root/avd/DeviceCheck.avd" -type f | wc -l | tr -d ' ')" = 1
grep -q 'AvdId=DeviceCheck' "$test_root/avd/DeviceCheck.avd/config.ini"
grep -q 'path.rel=avd/DeviceCheck.avd' "$test_root/avd/DeviceCheck.ini"

if PATH="$test_root/bin:$PATH" FAKE_BUSY_PORT=5612 \
    FAKE_ADB_STATE="$test_root/adb-state" FAKE_EMULATOR_PID="$test_root/emulator-pid" \
    FAKE_AVD_NAME=DeviceCheck FAKE_SERIAL=emulator-5612 \
    ANDROID_SDK_ROOT="$test_root/sdk" ANDROID_AVD_HOME="$test_root/avd" \
    bash "$helper_path" \
    boot DeviceCheck 5612; then
    exit 1
fi

if PATH="$test_root/bin:$PATH" FAKE_BUSY_PORT=5613 \
    FAKE_ADB_STATE="$test_root/adb-state" FAKE_EMULATOR_PID="$test_root/emulator-pid" \
    FAKE_AVD_NAME=DeviceCheck FAKE_SERIAL=emulator-5612 \
    ANDROID_SDK_ROOT="$test_root/sdk" ANDROID_AVD_HOME="$test_root/avd" \
    bash "$helper_path" \
    boot DeviceCheck 5612; then
    exit 1
fi

if PATH="$test_root/bin:$PATH" FAKE_BOOT_NEVER=true \
    FAKE_BOOT_COUNT="$test_root/boot-count" FAKE_ADB_STATE="$test_root/adb-state" \
    FAKE_EMULATOR_PID="$test_root/emulator-pid" FAKE_AVD_NAME=DeviceCheck \
    FAKE_SERIAL=emulator-5612 DEVICE_CHECK_BOOT_ATTEMPTS=3 DEVICE_CHECK_BOOT_SLEEP_SECONDS=1 \
    ANDROID_SDK_ROOT="$test_root/sdk" ANDROID_AVD_HOME="$test_root/avd" \
    bash "$helper_path" \
    boot DeviceCheck 5612; then
    exit 1
fi
test "$(cat "$test_root/boot-count")" -ge 2
test ! -e "$test_root/adb-state"
rm -f "$test_root/boot-count"

PATH="$test_root/bin:$PATH" \
    FAKE_BOOT_AFTER=3 FAKE_BOOT_COUNT="$test_root/boot-count" \
    FAKE_ADB_STATE="$test_root/adb-state" FAKE_EMULATOR_PID="$test_root/emulator-pid" \
    FAKE_AVD_NAME=DeviceCheck FAKE_SERIAL=emulator-5612 \
    DEVICE_CHECK_BOOT_SLEEP_SECONDS=1 \
    ANDROID_SDK_ROOT="$test_root/sdk" ANDROID_AVD_HOME="$test_root/avd" \
    bash "$helper_path" \
    boot DeviceCheck 5612 | grep -q 'ready DeviceCheck as emulator-5612'
test "$(cat "$test_root/boot-count")" = 3

PATH="$test_root/bin:$PATH" \
    FAKE_ADB_STATE="$test_root/adb-state" FAKE_EMULATOR_PID="$test_root/emulator-pid" \
    FAKE_AVD_NAME=DeviceCheck FAKE_SERIAL=emulator-5612 \
    ANDROID_SDK_ROOT="$test_root/sdk" ANDROID_AVD_HOME="$test_root/avd" \
    bash "$helper_path" \
    delete DeviceCheck 5612

test ! -e "$test_root/avd/DeviceCheck.avd"
test ! -e "$test_root/avd/DeviceCheck.ini"

sed -i.bak 's/android-37\.1/android-36/' "$test_root/avd/Source37.avd/config.ini"
if ANDROID_SDK_ROOT="$test_root/sdk" ANDROID_AVD_HOME="$test_root/avd" \
    bash "$helper_path" \
    create WrongImage 5612 Source37; then
    exit 1
fi
