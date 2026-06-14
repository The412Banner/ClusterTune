<h1>
  <img src="docs/app-icon.svg" alt="" width="48" align="left">
  ClusterTune
</h1>

![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/AurelioB/cluster-tune/total)
![GitHub Release](https://img.shields.io/github/v/release/AurelioB/cluster-tune)

<a href='https://ko-fi.com/J3J518XVKR' target='_blank'><img height='36' style='border:0px;height:36px;' src='https://storage.ko-fi.com/cdn/kofi6.png?v=6' border='0' alt='Buy Me a Coffee at ko-fi.com' /></a>

ClusterTune is an Android utility for tuning CPU frequency limits on supported handheld devices. It lets you adjust CPU clusters, save performance profiles, and access quick controls from an Android Quick Settings tile.

The app has been tested with the AYN Odin 3, but should be compatible with other AYN and Retroid devices.

Upstream, ClusterTune relies solely on the device's built-in PServer service and does not require Magisk or user-granted root. This fork adds a **Magisk `su` fallback** so it also works on rooted handhelds that lack the PServer service (see [Device compatibility](#device-compatibility)).

> [!WARNING]
> ClusterTune changes CPU frequency limits. This may affect device stability, thermals, battery life, and performance, and I cannot guarantee that it is safe for your hardware or beneficial for your use case. Use it only if you understand what CPU frequency limits do and are comfortable accepting the risk.

## Why use this?

ClusterTune underclocks by setting lower maximum CPU frequencies. It does not undervolt the CPU, and it is not an adaptive governor; it simply caps how fast each CPU cluster is allowed to run.

Lower CPU frequency caps can reduce power draw, which may help lower temperatures, quiet the fan, and extend battery life. In some games, reducing CPU power and heat may also leave more thermal or power headroom for the GPU, especially when the game is GPU-bound.

## Screenshots

| Main app | Profile editor |
| --- | --- |
| <img src="docs/screenshots/main-app.png" alt="ClusterTune main app view" width="420"> | <img src="docs/screenshots/profile-editor.png" alt="ClusterTune profile editor dialog" width="420"> |

| Quick Settings tile dialog | Settings |
| --- | --- |
| <img src="docs/screenshots/quick-settings-dialog.png" alt="ClusterTune Quick Settings tile dialog" width="420"> | <img src="docs/screenshots/settings.png" alt="ClusterTune settings view" width="420"> |

## Features

- Tune CPU clusters with per-cluster frequency sliders.
- View the currently applied frequency cap for each cluster.
- Save, edit, delete, reorder, import, and export profiles.
- Use bundled profiles for supported devices.
- Reapply the last profile on boot.
- Quick Settings tile for fast access.
- Customizable quick tile behavior

## Requirements

- Android 12+ (`minSdk 31`).
- Privileged access through **either** of:
  - A compatible handheld with the built-in PServer service, such as supported AYN and Retroid devices (no Magisk needed); **or**
  - A Magisk-rooted device that can grant `su` to apps (see [Device compatibility](#device-compatibility)).

## Device compatibility

ClusterTune needs privileged (root) access to read and write the protected CPU
frequency controls under `/sys/devices/system/cpu/.../cpufreq/`. There are two
ways it can obtain that access:

1. **PServer service (no Magisk).** AYN and Retroid handhelds ship a vendor
   system service called `PServerBinder` that runs privileged commands on the
   app's behalf. When it is present, ClusterTune uses it automatically and never
   prompts for root.

2. **Magisk `su` fallback (this fork).** Devices without the `PServerBinder`
   service — for example the AYANEO Pocket FIT and other non-AYN/Retroid rooted
   handhelds — were previously reported as *"not compatible,"* because the app
   only knew how to talk to PServer. This fork adds a fallback that runs the same
   commands through a Magisk-style `su` binary when the PServer service is
   missing.

   On these devices, the **first** privileged action triggers a Magisk
   Superuser prompt for `com.aure.clustertune` — grant it and ClusterTune works
   normally. The `su` capability is detected once per app launch and cached.

If neither backend is available, the app reports that the device is not
compatible.

### Adding your device

If your SoC is not yet bundled, ClusterTune can still tune clusters with the
per-cluster sliders. To ship a tuned profile, add a JSON file named after the
detected SoC model under `app/src/main/assets/bundled_profiles/` — see
[Bundled Profiles](#bundled-profiles). The SG8350P (Snapdragon G3 Gen 3 /
AYANEO Pocket FIT) is included as an example of a Magisk-only device.

## Build

```bash
./gradlew assembleDebug
```

Run unit tests:

```bash
./gradlew testDebugUnitTest
```

Run both:

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is produced under:

```text
app/build/outputs/apk/debug/
```

## Project Structure

```text
app/src/main/java/com/aure/clustertune/
  data/       detection, storage, bundled profiles, repository
  model/      app state and profile models
  root/       PServer access and command execution
  tile/       Quick Settings tile and add-tile prompt
  ui/         Compose screens, dialogs, settings, theme
  boot/       boot completed receiver

app/src/main/assets/bundled_profiles/
  <SoC model>.json
```

## Bundled Profiles

Bundled profiles are stored as SoC-specific JSON files:

```text
app/src/main/assets/bundled_profiles/CQ8725S.json
```

The filename should match the detected SoC model, such as `ro.soc.model`.

Example:

```json
{
  "schemaVersion": 1,
  "socModel": "CQ8725S",
  "profiles": [
    {
      "id": "bundled_cq8725s_small",
      "name": "Small Underclock",
      "maxFrequencies": {
        "0": 2745600,
        "6": 3072000
      }
    }
  ]
}
```

Exported profiles follow the same schema.

## Notes For Contributors

- UI refers to cpufreq policies as CPU clusters, but internal code keeps `policy` naming because it matches Linux/sysfs terminology.
- ClusterTune uses the device's PServer service to read and write protected CPU frequency controls without asking for root. When PServer is absent, `RootExec` falls back to running the same commands via `su -c` (the `su` probe result is cached process-wide), which does prompt for Magisk Superuser access. `RootExec.isRootAvailable` is true if either backend works.

## License and Attribution

ClusterTune is distributed under the terms of the GNU General Public License v2.0.

The RootExec / PServer command execution code in this project is based on code from
[O2P Tweaks](https://github.com/FeralAI/o2ptweaks.app) by FeralAI, which is also
licensed under the GNU General Public License v2.0.

ClusterTune was inspired by the Odin 3 underclocking scripts published in
[TheOldTaylor/Odin3-CPU-Underclock](https://github.com/TheOldTaylor/Odin3-CPU-Underclock).
The original underclocking idea was shared by Reddit users u/twoohfive205 and
u/JoaozaoS in
[this r/OdinHandheld comment](https://www.reddit.com/r/OdinHandheld/comments/1snp9xd/comment/ogphgmb/).

## AI Assistance Disclosure

I used AI assistance while building this project. Software development is my day job, and I reviewed the code throughout the process. I understand how the app works and what it does.
