# 📥 InputBooster — Installation Guide

This guide walks you through everything you need to get InputBooster running. Follow the steps for your setup and you'll be up in under two minutes.

---

## Requirements

Before installing, make sure your system meets the following:

| Dependency    | Required Version | Download |
|---------------|------------------|----------|
| Minecraft Java Edition | 1.21.x  | [minecraft.net](https://minecraft.net) |
| Fabric Loader | 0.16.0 or higher | [fabricmc.net](https://fabricmc.net/use/) |
| Fabric API    | Latest           | [Modrinth](https://modrinth.com/mod/fabric-api) |
| Java          | 21 or higher     | [adoptium.net](https://adoptium.net) |

> **Not sure which Java version you have?** Open a terminal and run `java -version`. If the number is below 21, download a newer version from the link above.

---

## Step-by-Step Installation

### Step 1 — Install Fabric Loader

If you haven't already, download and run the Fabric Loader installer from [fabricmc.net](https://fabricmc.net/use/). Select Minecraft version **1.21.x** and follow the on-screen instructions. This will create a new Fabric profile in your Minecraft Launcher.

### Step 2 — Install Fabric API

Fabric API is a required dependency. Download it from [Modrinth](https://modrinth.com/mod/fabric-api) and place the `.jar` file into your `.minecraft/mods/` folder.

> **Can't find the mods folder?**
> - **Windows:** Press `Win + R`, type `%appdata%\.minecraft\mods` and hit Enter
> - **macOS:** Open Finder → Go → Go to Folder → `~/Library/Application Support/minecraft/mods`
> - **Linux:** Navigate to `~/.minecraft/mods`
>
> If the `mods` folder doesn't exist yet, create it manually.

### Step 3 — Download InputBooster

Download the latest `inputbooster-2.0.0.jar` from the [GitHub Releases](https://github.com/ahaduzzamankhan/inputbooster/releases) page.

> **Modrinth:** The mod is not yet listed on Modrinth. Download from the Releases page above until the listing goes live.

### Step 4 — Place the File

Move the downloaded `.jar` file into your `.minecraft/mods/` folder alongside Fabric API.

Your mods folder should look something like this:

```
.minecraft/
└── mods/
    ├── fabric-api-x.x.x.jar
    └── inputbooster-2.0.0.jar
```

### Step 5 — Launch with Fabric

Open the Minecraft Launcher, select the **Fabric** profile from the profile dropdown, and press **Play**.

InputBooster activates automatically on launch. No keybind, no toggle, no extra steps.

---

## Verifying the Installation

Once in-game, press **F3** to open the debug screen. You should see an InputBooster status block:

```
[InputBooster 2.0.0] ACTIVE
Poll Rate   : 200 Hz
Client FPS  : 60
Recovered   : 0 inputs
CPS         : 0
Sprint Fix  : ON  | Auto-Sprint : ON
W-Tap       : ON  | Auto-Strafe : ON
Anti-Idle   : ON
by Ahaduzzaman Khan
```

If you see this block, the mod is installed and running correctly.

---

## Compatibility

### Works with:
- Sodium
- Iris
- Lithium
- OptiFabric
- Most other performance-oriented Fabric mods

### May conflict with:
- Other input-modifying mods
- Macro or autoclicker mods

If you experience unexpected behavior, try removing other input-related mods first to isolate the conflict.

---

## Configuration (Optional)

InputBooster works out of the box with sensible defaults. If you want to customize it, a config file is generated automatically at:

```
.minecraft/config/inputbooster.properties
```

Open it with any text editor. Key settings:

```properties
# Polling rate in Hz — Range: 100–500 | Default: 200
poll_rate_hz=200

# Sprint fix, auto-sprint, W-tap, anti-idle, auto-strafe
sprint_fix=true
auto_sprint=true
wtap_assist=true
anti_idle=true
auto_strafe=true

# Show stats in F3 debug screen
show_f3_info=true
```

**Quick recommendations:**
- Running below 30 FPS? Set `poll_rate_hz=500`
- Prefer vanilla sprint behavior? Set `auto_sprint=false`

> All changes take effect after a full Minecraft restart.

---

## Uninstalling

To remove InputBooster, simply delete `inputbooster-2.0.0.jar` from your `.minecraft/mods/` folder and restart Minecraft. No leftover files will affect your game — the config file at `.minecraft/config/inputbooster.properties` can also be deleted if you want a clean removal.

---

## Troubleshooting

**The mod doesn't appear in F3**
- Confirm you launched with the Fabric profile, not the vanilla profile
- Check that both Fabric API and InputBooster `.jar` files are in the mods folder
- Make sure your Fabric Loader version is 0.16.0 or higher

**Game crashes on launch**
- Verify your Java version is 21 or higher (`java -version` in terminal)
- Check if another input-modifying mod is conflicting — try launching with only InputBooster and Fabric API

**Sprint or inputs still feel inconsistent**
- Open the config file and set `poll_rate_hz=500`
- Confirm `sprint_fix=true` is set
- Check F3 to see the "Recovered" input count — a rising number confirms the mod is actively catching missed inputs

**Still having issues?**
Open a report on [GitHub Issues](https://github.com/ahaduzzamankhan/inputbooster/issues) with your Fabric log and a description of the problem.

---

## Need Help?

| Channel     | Link |
|-------------|------|
| Bug Reports | [GitHub Issues](https://github.com/ahaduzzamankhan/inputbooster/issues) |
| Discussions | [GitHub Discussions](https://github.com/ahaduzzamankhan/inputbooster/discussions) |
| Download    | [Releases Page](https://github.com/ahaduzzamankhan/inputbooster/releases) |

---

*InputBooster v2.0.0 · Made by [Ahaduzzaman Khan](https://github.com/ahaduzzamankhan) · MIT License*
