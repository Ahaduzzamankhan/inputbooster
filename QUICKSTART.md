# InputBooster v2.1.0 - Quick Start Guide

## ⚡ 30-Second Setup

### 1. Prerequisites
- Minecraft 1.21 (any version 1.21.0 - 1.21.11)
- Java 21 or higher
- Fabric Loader installed

### 2. Install
1. Download `inputbooster-2.1.0.jar`
2. Place in `.minecraft/mods/` folder
3. Launch Minecraft with Fabric

### 3. First Launch
- Check logs for "✓ InputBooster Ready!" message
- Config file created: `.minecraft/config/inputbooster.properties`

---

## 🎮 How to Use

### In-Game Controls
| Key | Action |
|-----|--------|
| **O** | Open settings |
| **P** | Toggle mod on/off |
| **F3** | Show debug overlay |

### Settings Screen (Press O)
1. **Poll Rate Tab**: Change Hz and mode (AUTO/MANUAL)
2. **Features Tab**: Toggle features on/off
3. **Advanced Tab**: Advanced options
4. Click "Save & Close" to save changes

---

## ⚙️ Recommended Settings

### For Low FPS (<30 FPS)
- Mode: **AUTO** (FPS-adaptive)
- Features: All **ON**
- F3: **ON** (to monitor)

### For High FPS (>60 FPS)
- Mode: **MANUAL**
- Hz: **150-200**
- Features: As needed

### For Balanced Performance
- Mode: **AUTO**
- Features: All **ON** except CPS Limiter
- F3: **OFF** (saves resources)

---

## 🐛 Troubleshooting

### Mod Not Loading?
1. Check Java version: `java -version`
2. Ensure Java 21+
3. Check logs in `.minecraft/logs/latest.log`

### Settings Not Saving?
1. Close Minecraft properly
2. Check write permissions for `.minecraft/config/`
3. Don't edit config while game is running

### Poor Performance?
1. Disable unused features
2. Lower poll rate in MANUAL mode
3. Disable F3 overlay if not needed

---

## 📖 More Information

- **Full Guide**: See `INSTALLATION_GUIDE.md`
- **What's New**: See `CHANGELOG_v2.1.0.md`
- **All Fixes**: See `README_FIXES.txt`

---

## 🎯 Features Overview

✓ **Ultra-fast polling** (60-1000 Hz)
✓ **AUTO mode** - FPS-adaptive scaling
✓ **MANUAL mode** - Fixed polling rate
✓ **In-game settings** - No config file editing needed
✓ **F3 integration** - Real-time stats display
✓ **Sprint fix** - Better sprint handling
✓ **W-Tap assist** - Combat helper
✓ **Anti-Idle** - Prevent AFK kicks
✓ **Auto-Strafe** - Movement assistant
✓ **CPS Limiter** - Click rate limiting

---

## 💡 Tips & Tricks

1. **For PvP**: Use AUTO mode with all features enabled
2. **For Testing**: Enable Debug Mode in Advanced tab
3. **For Monitoring**: Keep F3 overlay enabled
4. **For Stability**: Use moderate Hz (200-350)
5. **For Low FPS**: Use higher Hz (400-500)

---

## ❓ Still Have Questions?

Check the full `INSTALLATION_GUIDE.md` for detailed documentation!

**Happy low-FPS PvPing!** ⚡🎮

