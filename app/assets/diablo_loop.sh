#!/system/bin/sh
# Diablo Mode - loop (DuduSync). Sai quando o flag some.
# modo: turbo = trava min=max no maximo | auto = remove caps, schedutil escala

FLAG="__FLAG__"
INTF="__INTF__"
MODEF="__MODE__"

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

# para o daemon que re-capa (o loop segura; o watchdog religa se esquentar).
# O HAL termico (thermal-default) NAO pode parar: o ThermalService do Android
# chama getThermalHeadroom() e trava a main thread do jogo se o HAL sumir -> ANR.
setprop ctl.stop thermal-engine 2>/dev/null
# FDE.AI tambem capa CPU/GPU; desliga enquanto o modo esta ativo
pm disable-user --user 0 com.android.system.optimizer 2>/dev/null
pm disable-user --user 0 com.feravolt.fdeai.donate 2>/dev/null
# perfmond (Samsung) e quem re-capa CPU/GPU por tras: pausa enquanto o modo esta ligado
for p in $(pidof perfmond 2>/dev/null); do kill -STOP "$p" 2>/dev/null; done

while [ -f "$FLAG" ]; do
  mode=$(cat "$MODEF" 2>/dev/null)
  [ "$mode" != "auto" ] && mode="turbo"
  # se o perfmond reiniciou, pausa de novo
  for p in $(pidof perfmond 2>/dev/null); do kill -STOP "$p" 2>/dev/null; done

  # ---- sempre: remove os caps (BCL fica ligado: e protecao de corrente) ----
  echo 0 > /sys/class/kgsl/kgsl-3d0/thermal_pwrlevel 2>/dev/null
  echo 0 > /sys/class/kgsl/kgsl-3d0/max_pwrlevel 2>/dev/null
  echo 1200000000 > /sys/class/kgsl/kgsl-3d0/devfreq/max_freq 2>/dev/null
  echo 4473600 > /sys/devices/system/cpu/cpufreq_limit/cpufreq_max_limit 2>/dev/null

  if [ "$mode" = "turbo" ]; then
    # TRAVA no maximo
    echo performance > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor 2>/dev/null
    echo performance > /sys/devices/system/cpu/cpufreq/policy6/scaling_governor 2>/dev/null
    # max antes do min: se o sistema re-capou o max, o min seria rejeitado
    echo 3532800 > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq 2>/dev/null
    echo 3532800 > /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq 2>/dev/null
    echo 4473600 > /sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq 2>/dev/null
    echo 4473600 > /sys/devices/system/cpu/cpufreq/policy6/scaling_min_freq 2>/dev/null
    echo 0 > /sys/class/kgsl/kgsl-3d0/min_pwrlevel 2>/dev/null
    echo 1200000000 > /sys/class/kgsl/kgsl-3d0/devfreq/min_freq 2>/dev/null
  else
    # AUTO: escala sob demanda, caps removidos (max disponivel)
    echo schedutil > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor 2>/dev/null
    echo schedutil > /sys/devices/system/cpu/cpufreq/policy6/scaling_governor 2>/dev/null
    echo 556800 > /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq 2>/dev/null
    echo 3532800 > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq 2>/dev/null
    echo 1017600 > /sys/devices/system/cpu/cpufreq/policy6/scaling_min_freq 2>/dev/null
    echo 4473600 > /sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq 2>/dev/null
    echo 389000000 > /sys/class/kgsl/kgsl-3d0/devfreq/min_freq 2>/dev/null
  fi

  # ---- watchdog (GPU 88C | CPU 92C | batt 46C) ----
  gt=$(cat /sys/class/thermal/$GPUZ/temp 2>/dev/null); gt=${gt:-0}
  tm=$(cat /sys/class/thermal/$ZMID/temp 2>/dev/null); tm=${tm:-0}
  tp=$(cat /sys/class/thermal/$ZPRIME/temp 2>/dev/null); tp=${tp:-0}
  bt=$(cat /sys/class/power_supply/battery/temp 2>/dev/null); bt=${bt:-0}
  if [ "$gt" -gt 84000 ] || [ "$tm" -gt 88000 ] || [ "$tp" -gt 88000 ] || [ "$bt" -gt 450 ]; then
    # devolve o controle pra Samsung durante o resfriamento
    for p in $(pidof perfmond 2>/dev/null); do kill -CONT "$p" 2>/dev/null; done
    setprop ctl.start thermal-engine 2>/dev/null
    sleep 12
    setprop ctl.stop thermal-engine 2>/dev/null
  fi

  iv=$(cat "$INTF" 2>/dev/null)
  case "$iv" in
    0.5|1|2|3|4|5|6) ;;
    *) iv=2 ;;
  esac
  sleep "$iv"
done

# saida: devolve o perfmond (se o app nao fizer isso pelo turnOff)
for p in $(pidof perfmond 2>/dev/null); do kill -CONT "$p" 2>/dev/null; done
