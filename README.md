# Diablo Mode (Samsung) — by DuduSync

An Android app that does what RedMagic's **"Diablo Mode"** does in hardware — pin the CPU and GPU
at their maximum clocks — on a **Samsung Snapdragon** phone, using **root only**. No kernel, no
custom ROM, no Magisk module required.

> **Tested on Galaxy S25 Ultra (SM-S938B, Snapdragon 8 Elite / Adreno 830, One UI 8.5 / Android 17).**
> Snapdragon-only. Exynos is not supported (different nodes).

---

## ⚠️ Warning

This **defeats the phone's thermal management**. Long sessions without a cooler can overheat the SoC
and wear the battery faster. The app warns with a 5-second countdown before enabling, and a watchdog
restores thermal protection if the GPU passes 88 °C, a CPU cluster 92 °C, or the battery 46 °C.
Use at your own risk.

---

## What it does

Samsung (and Qualcomm) ship several daemons that keep the CPU around **1.4 GHz** and the GPU around
**443 MHz**, even when the chip is cool. The app wins against them with a small **root shell loop**
that re-applies the unlock every 0.5–6 s (configurable in the app):

| Cap | Interface | Unlocked to |
|---|---|---|
| Battery Current Limit (GPU) | `/sys/class/kgsl/kgsl-3d0/bcl` | `0` |
| GPU thermal power level | `.../thermal_pwrlevel` | `0` (max) |
| GPU ceiling | `.../devfreq/max_freq` | `1200000000` (1.2 GHz) |
| Kernel CPU cap | `/sys/devices/system/cpu/cpufreq_limit/cpufreq_max_limit` | `4473600` (4.47 GHz) |
| CPU governor / ceiling | `policy0`, `policy6` | `performance` or `schedutil`, max freq |

The daemons (`thermal-engine`, Samsung thermal HAL, Qualcomm perf HAL, and the third-party **FDE.AI**
if installed) respawn when killed, so killing them once does not stick — the **loop** is what makes it
hold.

### Two modes

- **TURBO** — pins `min = max = top clock` (CPU 3.53/4.47 GHz, GPU 1.2 GHz). Maximum, runs hot even idle.
- **AUTO** — removes the caps and uses `schedutil`, so it reaches max under load and idles cool.

### Off = stock

Turning it off kills the loop, sets the governor back to Samsung's `walt`, restores the stock
frequency range, restarts `thermal-engine`, and re-enables FDE.AI — back to stock behaviour.

---

## Features

- Modern dark UI: one card per cluster with **min / chip max / current cap / current**, a live bar
  and a temperature coloured green→orange→red.
- Battery card (percent, temperature, charging).
- **TURBO / AUTO** switch.
- **Re-apply interval slider**: 0.5, 1, 2, 3, 4, 5, 6 s.
- **5-second warning countdown** before enabling.
- **Loading screen + disk cache** (thermal-zone indices and last values) so the app opens in ~280 ms
  and already shows the clocks before root answers.

## Requirements

- Root (KernelSU, KernelSU-Next, Magisk, APatch — temporary late-load root works too).
- A Samsung **Snapdragon** phone, Android 13+.
- A cooler is strongly recommended.

## Install

1. Download `DiabloMode.apk` from **Releases**.
2. Install it, open it, and grant **root** when KernelSU/Magisk asks.
3. Tap **ATIVAR MODO DIABLO**, wait for the 5 s countdown.

## Build it yourself

The app is plain Kotlin with **no Android Studio project** — it builds with the Android SDK build
tools directly (aapt2, kotlinc, d8, apksigner). See [`build.sh`](build.sh). You need:

- `aapt2`, `d8`, `apksigner`, `zipalign` (Android build-tools)
- `kotlinc` (Kotlin compiler) and `kotlin-stdlib.jar`
- `android.jar` from an SDK platform (API 26+)
- JDK 17+

Run:

```sh
./build.sh path/to/android.jar path/to/kotlin-stdlib.jar
# produces DiabloMode.apk
```

## How the root part works

The app ships two shell scripts as assets and writes them to its private files dir on first launch:

- **`diablo_loop.sh`** — the unlock loop (runs detached via `su` + `setsid`, survives backgrounding;
  exits when the app's `diablo.on` flag file disappears). Reads the mode (`turbo`/`auto`) and the
  interval from files the UI writes, so toggling in the app applies live.
- **`status.sh`** — reads clocks/temps in one root call and prints `key=value`; caches its thermal
  zone indices so it does not rescan all zones every refresh.

Nothing here touches the kernel, the boot image, or any partition. A reboot restores everything.

## Tested

| Device | SoC | Android | Result |
|---|---|---|---|
| Galaxy S25 Ultra (SM-S938B) | Snapdragon 8 Elite | 17 | CPU 4.47/3.53 GHz, GPU 1.2 GHz pinned; 60 fps in *Ocarina of Time* (Banana Native) |

## License

MIT — see `LICENSE`. No warranty.
