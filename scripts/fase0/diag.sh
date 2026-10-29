#!/usr/bin/env bash
# Recoge información del dispositivo y del motor para la fase 0.
# Uso: scripts/fase0/diag.sh [directorio_salida]
# La salida puede contener datos personales (cuentas, IDs). No la subas al repo:
# revísala antes de compartirla.
set -u

HOST_PKG="${HOST_PKG:-top.niunaijun.blackbox}"
OUT="${1:-$HOME/fase0-logs/$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$OUT"

adb wait-for-device

{
  echo "== build =="
  for p in ro.product.manufacturer ro.product.model ro.build.version.release \
           ro.build.version.sdk ro.build.version.security_patch ro.build.display.id \
           ro.product.cpu.abilist persist.sys.locale ro.build.version.incremental; do
    printf '%s=%s\n' "$p" "$(adb shell getprop "$p" | tr -d '\r')"
  done
  echo "page_size=$(adb shell getconf PAGE_SIZE | tr -d '\r')"
  echo "kernel=$(adb shell uname -r | tr -d '\r')"
  echo
  echo "== GMS en el sistema real =="
  adb shell pm list packages | grep -E 'com\.google\.android\.(gms|gsf)|com\.android\.vending|org\.microg|com\.huawei\.(health|hwid)' || echo "(ninguno)"
  echo
  echo "== motor =="
  adb shell dumpsys package "$HOST_PKG" | grep -E 'versionName|targetSdk|pageSizeCompat|flags=' | head -n 10
  adb shell ps -A -o PID,USER,NAME | grep "$HOST_PKG" || echo "(sin procesos)"
} > "$OUT/device.txt" 2>&1

adb shell dumpsys notification --noredact > "$OUT/notification.txt" 2>&1
adb shell dumpsys activity processes "$HOST_PKG" > "$OUT/am-processes.txt" 2>&1
adb shell dumpsys activity services "$HOST_PKG" > "$OUT/am-services.txt" 2>&1
adb shell dumpsys deviceidle whitelist > "$OUT/deviceidle.txt" 2>&1
adb shell cmd appops get "$HOST_PKG" > "$OUT/appops.txt" 2>&1
adb logcat -d -b main,system,crash,events -v threadtime > "$OUT/logcat.txt" 2>&1
adb shell ls /data/tombstones > "$OUT/tombstones-list.txt" 2>&1

echo "Diagnóstico guardado en $OUT"
