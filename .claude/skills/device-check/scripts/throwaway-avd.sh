#!/usr/bin/env bash
set -euo pipefail

usage() {
    printf 'usage: bash %s <create|boot|delete> <name> <even-port> [source-avd]\n' "$0" >&2
    exit 2
}

[[ $# -ge 3 && $# -le 4 ]] || usage

action=$1
avd_name=$2
emulator_port=$3
source_avd=${4:-Release_Walk}

[[ $avd_name =~ ^[A-Za-z][A-Za-z0-9_-]*$ ]] || usage
[[ $source_avd =~ ^[A-Za-z][A-Za-z0-9_-]*$ ]] || usage
[[ $emulator_port =~ ^[0-9]+$ ]] || usage
(( emulator_port % 2 == 0 )) || usage

sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}
adb_bin="$sdk_root/platform-tools/adb"
emulator_bin="$sdk_root/emulator/emulator"
avd_root=${ANDROID_AVD_HOME:-$HOME/.android/avd}
serial="emulator-$emulator_port"
source_dir="$avd_root/$source_avd.avd"
source_ini="$avd_root/$source_avd.ini"
target_dir="$avd_root/$avd_name.avd"
target_ini="$avd_root/$avd_name.ini"
target_log="$avd_root/$avd_name.log"

port_is_free() {
    ! lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null | grep -q .
}

case $action in
    create)
        [[ $avd_name != "$source_avd" ]] || usage
        [[ -f "$source_dir/config.ini" && -f "$source_ini" ]]
        grep -q 'android-37\.1' "$source_dir/config.ini"
        [[ ! -e "$target_dir" && ! -e "$target_ini" ]]
        ! "$adb_bin" devices | awk 'NR > 1 {print $1}' | grep -qx "$serial"
        mkdir -p "$target_dir"
        sed "s/$source_avd/$avd_name/g" "$source_dir/config.ini" > "$target_dir/config.ini"
        sed "s/$source_avd/$avd_name/g" "$source_ini" > "$target_ini"
        ;;
    boot)
        [[ -f "$target_dir/config.ini" && -f "$target_ini" ]]
        grep -q 'android-37\.1' "$target_dir/config.ini"
        ! "$adb_bin" devices | awk 'NR > 1 {print $1}' | grep -qx "$serial"
        command -v lsof >/dev/null
        port_is_free "$emulator_port"
        port_is_free "$((emulator_port + 1))"
        nohup "$emulator_bin" -avd "$avd_name" -port "$emulator_port" -no-window \
            -no-snapshot -no-audio -no-boot-anim -wipe-data -gpu host \
            -dns-server 8.8.8.8,1.1.1.1 > "$target_log" 2>&1 &
        emulator_pid=$!
        ready=false
        for _ in $(seq 1 180); do
            kill -0 "$emulator_pid" 2>/dev/null || exit 1
            if "$adb_bin" devices | awk 'NR > 1 && $2 == "device" {print $1}' | grep -qx "$serial"; then
                "$adb_bin" -s "$serial" wait-for-device
                actual_name=$("$adb_bin" -s "$serial" emu avd name | tr -d '\r')
                boot_complete=$("$adb_bin" -s "$serial" shell getprop sys.boot_completed | tr -d '\r')
                if [[ $actual_name == "$avd_name" && $boot_complete == 1 ]]; then
                    ready=true
                    break
                fi
            fi
            sleep 1
        done
        [[ $ready == true ]]
        printf 'ready %s as %s\n' "$avd_name" "$serial"
        ;;
    delete)
        if "$adb_bin" devices | awk 'NR > 1 {print $1}' | grep -qx "$serial"; then
            actual_name=$("$adb_bin" -s "$serial" emu avd name | tr -d '\r')
            [[ $actual_name == "$avd_name" ]]
            "$adb_bin" -s "$serial" emu kill
            for _ in $(seq 1 60); do
                "$adb_bin" devices | awk 'NR > 1 {print $1}' | grep -qx "$serial" || break
                sleep 1
            done
            if "$adb_bin" devices | awk 'NR > 1 {print $1}' | grep -qx "$serial"; then
                exit 1
            fi
        fi
        rm -rf -- "$target_dir"
        rm -f -- "$target_ini" "$target_log"
        ;;
    *)
        usage
        ;;
esac
