# InputBooster Changelog v2.1.0

## Version 2.1.0 - Major Update & Complete Fix (2026-04-09)

### 🚀 New Features
- **Tabbed Settings UI**: Poll Rate | Features | Advanced tabs for organized settings
- **Enhanced Keybindings**: Added P key to toggle mod ON/OFF
- **Advanced Settings Tab**: FPS check interval (1-100), debug mode, action bar messages
- **CPS Limiter Feature**: New toggleable click-per-second limiting
- **Improved Poll Rate Algorithm**: Better FPS scaling with 5 tiers
- **6 Preset Buttons**: Quick select (100, 150, 200, 350, 500, 750 Hz)
- **Professional Logging**: Visual banners and detailed initialization logs
- **Better F3 Integration**: Comprehensive debug overlay with feature status

### 🔧 Critical Fixes
- **Gradle Updated**: 8.8 → 9.4.1
- **MC Version**: Properly configured for 1.21.0-1.21.11 range
- **Java 21**: Enforced as minimum requirement
- **Thread Safety**: Added AtomicBoolean for initialization, proper synchronization
- **Error Handling**: Try-catch blocks in all critical sections
- **Configuration**: 14 settings (was 8) with proper validation
- **F3 Overlay**: Complete rewrite using HudRenderCallback

### ⚡ Improvements
- **Build System**: Better repository setup, compiler options
- **Code Quality**: Comprehensive Javadoc, consistent formatting
- **Error Messages**: Better context and helpful debugging info
- **Performance**: Optimized FPS checks and polling logic
- **Documentation**: 500+ lines of installation and setup guides

### 📊 Configuration Changes
**New Settings**:
- `poll_rate_auto` - Toggle AUTO/MANUAL mode
- `show_action_bar` - Toggle action bar messages
- `debug_mode` - Enable verbose logging
- `fps_check_interval` - Ticks between FPS checks (1-100)

**Enhanced Settings**:
- `cps_limiter` - New feature toggle
- Better documentation for all settings

### 🎮 UI/UX Improvements
- Tabbed settings screen with better organization
- Real-time FPS and Hz display in settings
- Unsaved changes indicator
- Better button styling with ✓/✗ symbols
- Custom slider classes for better control
- Professional formatting throughout

### 📈 Compatibility
- Tested with MC 1.21.4
- Compatible with 1.21.0 - 1.21.11
- Java 21+ required
- Fabric Loader 0.16.0+
- Works with Sodium, Iris, Lithium, FerriteCore

### 🐛 Bug Fixes
- Fixed uninitialized state tracking
- Proper shutdown sequence
- Better exception handling in tick events
- Config validation on load/save
- Thread safety improvements
- Memory leak prevention

### 📚 Documentation
- `INSTALLATION_GUIDE.md` - Complete setup instructions
- `README_FIXES.txt` - Detailed fix summary
- Inline Javadoc comments throughout code
- Better config file documentation

### 💻 Technical Details
- **Source**: Java 21+ required
- **Build**: Gradle 9.4.1
- **Loader**: Fabric 0.16.0+
- **API**: Fabric API 0.112.0+1.21.4
- **Loom**: fabric-loom 1.7-SNAPSHOT

### 🙏 Acknowledgments
- Fabric Team for excellent modding framework
- Community feedback and testing
- MC 1.21.4 compatibility updates

---

## Previous Versions

See CHANGELOG.md for earlier version history (v1.x, v2.0.x)

---

## Migration from v2.0.4

### Automatic Migration
- Config file automatically migrated with new settings
- Old settings preserved with sensible defaults
- No breaking changes

### Recommended Actions
1. Update mod JAR to v2.1.0
2. Check new settings via [O] key
3. Review F3 overlay improvements
4. Enable debug mode if needed

### Known Changes
- Poll rate algorithm adjusted (better FPS scaling)
- F3 display format changed (improved readability)
- New keybind P for toggle (customizable)

---

**Questions?** Check INSTALLATION_GUIDE.md or open a GitHub issue!

