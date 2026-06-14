## ClusterTune v0.5.1

ClusterTune is an Android utility for tuning CPU cluster frequency limits on
handhelds — cap how fast each cluster may run to cut heat, fan noise, and power
draw, and free up thermal/power headroom for the GPU.

This fork extends upstream (v0.3.1) to work on rooted handhelds that **don't**
ship the vendor PServer service, and adds device support for the AYANEO Pocket
FIT (Snapdragon G3 Gen 3 / SG8350P). This is a cumulative release — it includes
everything from the fork so far.

### New in v0.5.1

**Marketing names for AYN / Retroid SoCs in the device info card**
The device info card now shows a friendly chipset name on the handhelds this fork
was originally built for, not just the SG8350P. Added: Snapdragon 8 Gen 2 (AYN
Odin 2 / 2 Mini / 2 Portal), Snapdragon 845 (AYN Odin), Snapdragon 865 (Retroid
Pocket 5 / Mini / Flip 2), Dimensity 900 (Retroid Pocket 4 / AYN Odin Lite),
Dimensity 1100 (Retroid Pocket 4 Pro / 3+), and Unisoc Tiger T618 (Retroid Pocket
3). Any device whose SoC isn't in the list still shows its raw model string, so
nothing regresses.

### Everything in this fork

**Device info card on the main screen**
A tappable header card under the title shows your device at a glance — name, SoC
model, and core count — and expands into full details: chipset marketing name,
SoC model number and vendor, CPU core/cluster layout, Android version, codename,
build, and a per-cluster cores + max-frequency breakdown. Everything derives from
the device's `Build` info and the detected CPU policies.

**Magisk `su` fallback — runs on devices without PServer**
Upstream only obtained privileged access through a vendor system service
(`PServerBinder`) that ships on AYN/Retroid handhelds. Devices without it — like
the Pocket FIT — were wrongly reported as *"not compatible."* ClusterTune now
falls back to running its commands through a Magisk-style `su` binary when that
service is absent, and reports itself available if **either** backend works. The
`su` capability is probed once per launch and cached. On these devices the first
privileged action triggers a Magisk Superuser prompt for `com.aure.clustertune`.

**SG8350P (AYANEO Pocket FIT) bundled profiles**
Adds a Performance profile plus Small / Medium / Large underclock tiers tuned for
the four clusters: efficiency (cpu0–1), two performance clusters (cpu2–4, cpu5–6),
and the prime core (cpu7). Frequencies are snapped to each cluster's real,
kernel-reported steps.

**Performance profile now reaches true stock (turbo-bin unlock)**
The prime cluster's top 3.30 GHz "boost" bin (3,302,400 kHz) is reported as the
hardware maximum but is hidden from the list of individually selectable
frequencies (which tops out at 3,052,800 kHz). Bundled profiles previously could
not target it, so Performance sat ~250 MHz below stock on that core. ClusterTune
now accepts a profile frequency that lands in the hardware turbo band
(`selectable max` < f ≤ `observed hardware max`), and the SG8350P Performance
profile targets the real 3,302,400 kHz — matching an untouched device. The value
shows as **"3.30 GHz (boosted)"** in the UI. (The manual slider still maxes at the
selectable 3.05 GHz step; the turbo bin is reachable via the Performance and
Stock profiles.)

**Documentation**
New "Device compatibility" section in the README explaining the PServer vs.
Magisk `su` paths, the first-action superuser prompt, and how to add a profile
for a new SoC.

**Build & release**
- CI builds a debug APK and runs unit tests on every push/PR.
- The release workflow builds and publishes without signing secrets (this fork
  has none), shipping the debug-keystore APK.
- Version bumped to 0.5.1 (versionCode 501).

### Compatibility

- Android 12+ (`minSdk 31`).
- Either a handheld with the built-in PServer service (no Magisk needed), **or**
  a Magisk-rooted device that can grant `su`.

### Install note

This is a **debug-keystore** build. Each release is signed with a fresh debug
key, so if you have a previous ClusterTune debug build installed, uninstall it
first (otherwise installation fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`).

> ⚠️ ClusterTune changes CPU frequency limits, which can affect stability,
> thermals, battery life, and performance. Use it only if you understand what
> CPU frequency limits do and accept the risk.
