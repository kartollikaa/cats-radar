#!/usr/bin/env bash
set -euo pipefail

repo_root=$(git rev-parse --show-toplevel)
suffix=skillcheck

if CI=true "$repo_root/gradlew" \
    -I "$repo_root/.claude/skills/device-check/scripts/suffixed-debug.init.gradle" \
    -PdeviceCheckSuffix=Bad-Suffix :app:help --no-configuration-cache --console=plain; then
    exit 1
fi

CI=true "$repo_root/gradlew" \
    -I "$repo_root/.claude/skills/device-check/scripts/suffixed-debug.init.gradle" \
    -PdeviceCheckSuffix="$suffix" :app:assembleDebug \
    --no-configuration-cache --no-build-cache --rerun-tasks --console=plain

sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}
aapt2="$sdk_root/build-tools/$(find "$sdk_root/build-tools" -mindepth 1 -maxdepth 1 -type d \
    -exec basename {} \; | sort -V | tail -1)/aapt2"
badging=$("$aapt2" dump badging "$repo_root/app/build/outputs/apk/debug/app-debug.apk")
package_line=${badging%%$'\n'*}
[[ $package_line == *"package: name='com.kartollika.catsradar.$suffix'"* ]]
printf '%s\n' "$package_line"
