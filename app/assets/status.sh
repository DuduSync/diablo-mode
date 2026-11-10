#!/system/bin/sh
# status v2 - usa cache de zonas (GPUZ/ZMID/ZPRIME) pra ser rapido.
# Uso: status.sh <arquivo_cache_zonas>

CACHE="$1"
GPUZ=""; ZMID=""; ZPRIME=""
if [ -n "$CACHE" ] && [ -f "$CACHE" ]; then
  . "$CACHE" 2>/dev/null
fi
if [ -z "$GPUZ" ] || [ -z "$ZMID" ] || [ -z "$ZPRIME" ]; then
  GPUZ=""; ZMID=""; ZPRIME=""
  for z in $(seq 0 70); do
    ty=$(cat /sys/class/thermal/thermal_zone$z/type 2>/dev/null)
    case "$ty" in
      gpuss-0)   GPUZ="thermal_zone$z" ;;
      cpu-0-5-1) ZMID="thermal_zone$z" ;;
      cpu-1-1-1) ZPRIME="thermal_zone$z" ;;
    esac
  done
  [ -z "$GPUZ" ] && GPUZ="thermal_zone23"
  [ -z "$ZMID" ] && ZMID="$GPUZ"
  [ -z "$ZPRIME" ] && ZPRIME="$GPUZ"
  if [ -n "$CACHE" ]; then
    { echo "GPUZ=$GPUZ"; echo "ZMID=$ZMID"; echo "ZPRIME=$ZPRIME"; } > "$CACHE" 2>/dev/null
  fi
fi

K=/sys/class/kgsl/kgsl-3d0
P0=/sys/devices/system/cpu/cpufreq/policy0
P6=/sys/devices/system/cpu/cpufreq/policy6
CL=/sys/devices/system/cpu/cpufreq_limit

echo "gm=$(cat $K/devfreq/min_freq 2>/dev/null)"
echo "gc=$(cat $K/clock_mhz 2>/dev/null)"
echo "gx=$(cat $K/devfreq/max_freq 2>/dev/null)"
echo "gt=$(cat /sys/class/thermal/$GPUZ/temp 2>/dev/null)"
echo "ghw=$(cat $K/devfreq/available_frequencies 2>/dev/null | tr ' ' '\n' | sort -n | tail -1)"

echo "c0m=$(cat $P0/scaling_min_freq 2>/dev/null)"
echo "c0c=$(cat $P0/scaling_cur_freq 2>/dev/null)"
echo "c0x=$(cat $P0/scaling_max_freq 2>/dev/null)"
echo "c0hw=$(cat $P0/cpuinfo_max_freq 2>/dev/null)"

echo "c6m=$(cat $P6/scaling_min_freq 2>/dev/null)"
echo "c6c=$(cat $P6/scaling_cur_freq 2>/dev/null)"
echo "c6x=$(cat $P6/scaling_max_freq 2>/dev/null)"
echo "c6hw=$(cat $P6/cpuinfo_max_freq 2>/dev/null)"

echo "gov=$(cat $P0/scaling_governor 2>/dev/null)"
echo "kcap=$(cat $CL/cpufreq_max_limit 2>/dev/null)"
echo "bt=$(cat /sys/class/power_supply/battery/temp 2>/dev/null)"
echo "bc=$(cat /sys/class/power_supply/battery/capacity 2>/dev/null)"
echo "bs=$(cat /sys/class/power_supply/battery/status 2>/dev/null)"

echo "tmid=$(cat /sys/class/thermal/$ZMID/temp 2>/dev/null)"
echo "tprime=$(cat /sys/class/thermal/$ZPRIME/temp 2>/dev/null)"
