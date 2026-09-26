#!/usr/bin/env bash
set -euo pipefail

repo_root=$(git rev-parse --show-toplevel)
test_root=$(mktemp -d)
trap 'rm -rf "$test_root"' EXIT

mkdir -p "$test_root/sdk/platform-tools" "$test_root/avd/Source37.avd"
printf '#!/usr/bin/env bash\nprintf "List of devices attached\\n\\n"\n' > "$test_root/sdk/platform-tools/adb"
chmod +x "$test_root/sdk/platform-tools/adb"
printf 'AvdId=Source37\nimage.sysdir.1=system-images/android-37.1/google_apis/arm64-v8a/\n' \
    > "$test_root/avd/Source37.avd/config.ini"
printf 'path=%s/avd/Source37.avd\npath.rel=avd/Source37.avd\n' "$test_root" \
    > "$test_root/avd/Source37.ini"

ANDROID_SDK_ROOT="$test_root/sdk" ANDROID_AVD_HOME="$test_root/avd" \
    bash "$repo_root/.claude/skills/device-check/scripts/throwaway-avd.sh" \
    create DeviceCheck 5612 Source37

test -f "$test_root/avd/DeviceCheck.avd/config.ini"
test -f "$test_root/avd/DeviceCheck.ini"
test "$(find "$test_root/avd/DeviceCheck.avd" -type f | wc -l | tr -d ' ')" = 1
grep -q 'AvdId=DeviceCheck' "$test_root/avd/DeviceCheck.avd/config.ini"
grep -q 'path.rel=avd/DeviceCheck.avd' "$test_root/avd/DeviceCheck.ini"

ANDROID_SDK_ROOT="$test_root/sdk" ANDROID_AVD_HOME="$test_root/avd" \
    bash "$repo_root/.claude/skills/device-check/scripts/throwaway-avd.sh" \
    delete DeviceCheck 5612

test ! -e "$test_root/avd/DeviceCheck.avd"
test ! -e "$test_root/avd/DeviceCheck.ini"

sed -i.bak 's/android-37\.1/android-36/' "$test_root/avd/Source37.avd/config.ini"
if ANDROID_SDK_ROOT="$test_root/sdk" ANDROID_AVD_HOME="$test_root/avd" \
    bash "$repo_root/.claude/skills/device-check/scripts/throwaway-avd.sh" \
    create WrongImage 5612 Source37; then
    exit 1
fi
