# Premium Cinematic Fire TV Launcher - Device Testing Checklist

## Target Device
- **Device**: Amazon Fire TV Stick Lite (AFTSS, codename: sheldon)
- **OS**: Fire OS 7.7.1.7 (Android 9, API 28)
- **RAM**: ~1 GB
- **Resolution**: 1920×1080
- **Architecture**: arm64-v8a (MediaTek MT8695)

---

## Pre-Installation Requirements

- [ ] Fire TV Stick Lite connected via ADB (USB or network)
- [ ] ADB debugging enabled on device (Settings → My Fire TV → Developer Options → ADB Debugging)
- [ ] Apps from Unknown Sources enabled (Settings → My Fire TV → Developer Options → Apps from Unknown Sources)
- [ ] Original Amazon launcher accessible for recovery

---

## Installation Commands

```powershell
# 1. Connect to device (replace IP if using network ADB)
adb connect <DEVICE_IP>:5555

# 2. Verify connection
adb devices

# 3. Install the launcher
adb install -r "D:\android studio\fire-tv-cleanup\fire-tv-cleanup\launcher\app\build\outputs\apk\debug\app-debug.apk"

# 4. Verify installation
adb shell pm list packages | findstr com.gathin.tvhome

# 5. Launch the app
adb shell am start -n com.gathin.tvhome/.MainActivity
```

---

## Functional Testing Checklist

### ✅ Home Screen & Navigation
- [ ] **App launches without crash** - Cold start < 2s
- [ ] **Hero section displays** - Time-of-day background, greeting, clock, date
- [ ] **Top bar buttons work** - Inputs, WiFi, Settings (focus + click)
- [ ] **Floating dock visible** - 4+ pinned apps, centered, floating appearance
- [ ] **D-pad navigation** - Left/Right on dock, Up to top bar, Down to content rows
- [ ] **Focus indicators** - Dock magnification (1.25x), lift (12dp), hero tooltip, dot
- [ ] **Ambient glow** - Color matches focused app, smooth transitions
- [ ] **Glass highlight** - Springs between focused items in content rows/grid

### ✅ Content Rows
- [ ] **Movies row** - 6 items, horizontal scroll, focus scale (1.12x), lift (8dp)
- [ ] **TV Shows row** - 6 items, same behavior
- [ ] **Continue Watching row** - 4 items, same behavior
- [ ] **Focus badge** - Appears on focused content card
- [ ] **Vertical navigation** - Up from row 0 → dock, Down from row 2 → Apps row
- [ ] **Horizontal navigation** - Left/Right within rows, edge handling

### ✅ Apps Row & App Library
- [ ] **Apps row clickable** - Opens AppLibraryActivity
- [ ] **App Library opens** - Category tabs (7 categories), vertical grid
- [ ] **Category tabs** - Horizontal scroll, focus + click, filter grid
- [ ] **App grid** - 4 columns, focus scale (1.08x), name label on focus
- [ ] **Category filtering** - Tap category → filters apps instantly
- [ ] **Back navigation** - Back from grid → category tabs → main screen
- [ ] **App launch** - Click app → launches correctly

### ✅ Dock Interactions
- [ ] **Pin to dock** - Long press/Menu on grid item → "Pin to Dock"
- [ ] **Unpin from dock** - Long press/Menu on dock item → "Unpin from Dock"
- [ ] **Move left/right** - Menu on dock item → reorder
- [ ] **App info** - Menu → "App Info" → opens system settings
- [ ] **Dock full handling** - Toast when trying to pin with 4 items

### ✅ System Integration
- [ ] **Inputs menu** - Top bar Inputs button → HDMI/AV/Live TV list
- [ ] **WiFi button** - Shows status, click → WiFi settings
- [ ] **Settings button** - Click → Fire TV settings, Long press → Home redirect toggle
- [ ] **Home redirect** - Toggle via Settings menu (requires WRITE_SECURE_SETTINGS via ADB)
- [ ] **Time/date updates** - Greeting, clock, date update every minute
- [ ] **Background rotation** - Morning/Afternoon/Evening/Night backgrounds crossfade

### ✅ App Library Activity
- [ ] **Category tabs** - 7 tabs (All, Streaming, Entertainment, Music, Games, Utilities, System)
- [ ] **Tab navigation** - D-pad Left/Right, focus indication
- [ ] **Grid navigation** - 4 columns, Up/Down/Left/Right
- [ ] **UP from first row** → focuses category tabs
- [ ] **DOWN from category tabs** → enters grid
- [ ] **App launch** - All installed leanback apps launch correctly
- [ ] **Empty state** - Shows when category has no apps
- [ ] **Back button** - Grid → Category tabs → MainActivity (finish)

### ✅ Settings Activity
- [ ] **Launcher section** - Wallpaper, Screensaver, Home redirect
- [ ] **System shortcuts** - WiFi, Display, Bluetooth, Apps, Storage, Date/Time, Accessibility
- [ ] **About section** - Version, Licenses, Privacy Policy
- [ ] **Safe intent handling** - Toast for unavailable settings
- [ ] **Back navigation** - Returns to MainActivity

### ✅ Wallpaper Picker
- [ ] **16 wallpapers** - 4 time-of-day + 12 saver photos
- [ ] **Grid layout** - 4 columns, focus scale, selection overlay
- [ ] **Live preview** - Background updates on focus
- [ ] **Apply button** - Visible when selection changes, saves preference
- [ ] **Back navigation** - Returns to MainActivity

### ✅ Screensaver (PhotoDream)
- [ ] **Activation** - Selected via system screensaver settings
- [ ] **12 photos** - Crossfade (3s), Ken Burns zoom (1.12x over 25s)
- [ ] **Clock/date** - Bottom-right, Outfit font, shadow
- [ ] **Vignette** - Subtle radial darkening
- [ ] **Dismissal** - Any remote input exits

### ✅ Home Redirect (Optional - requires ADB permission)
```powershell
# Grant permission
adb shell pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS

# Enable via Settings menu (long press Settings button)
# Or via ADB:
adb shell settings put secure enabled_accessibility_services com.gathin.tvhome/com.gathin.tvhome.HomeRedirectService
adb shell settings put secure accessibility_enabled 1
```
- [ ] **Home button** - Returns to this launcher from Amazon home
- [ ] **Settings protection** - Doesn't redirect when entering Settings
- [ ] **Disable** - Settings menu toggle works

---

## Performance Validation (Fire TV Stick Lite - 1GB RAM)

- [ ] **Cold start** < 2 seconds
- [ ] **Memory usage** < 150MB (check via `adb shell dumpsys meminfo com.gathin.tvhome`)
- [ ] **Frame rate** - 60fps during navigation (no visible jank)
- [ ] **Bitmap cache** < 48MB (ImageLoader cache)
- [ ] **No memory leaks** - Rotate/navigate repeatedly, check `dumpsys meminfo`
- [ ] **Handler cleanup** - No leaked handlers (check `dumpsys activity`)
- [ ] **Spring animations** - Smooth, no stutter on 1GB device

---

## Recovery Procedures

### If Launcher Crashes or Freezes
1. **ADB reboot**: `adb reboot`
2. **Force stop**: `adb shell am force-stop com.gathin.tvhome`
3. **Clear data**: `adb shell pm clear com.gathin.tvhome`
4. **Uninstall**: `adb uninstall com.gathin.tvhome`

### If Home Redirect Causes Boot Loop
1. **Disable via ADB** (from recovery mode or fastboot):
   ```powershell
   adb shell settings put secure enabled_accessibility_services ""
   adb shell settings put secure accessibility_enabled 0
   ```
2. **Or boot to safe mode** - Hold Back + Right on remote during boot
3. **Factory reset** - Last resort (Settings → My Fire TV → Reset to Factory Defaults)

### Restore Amazon Launcher
```powershell
# Re-enable Amazon launcher as default home
adb shell cmd package set-home-activity com.amazon.tv.launcher/com.amazon.tv.launcher.HomeActivity

# Or disable our home intent filter
adb shell pm disable com.gathin.tvhome/com.gathin.tvhome.MainActivity
```

---

## Known Limitations

| Limitation | Impact | Workaround |
|------------|--------|------------|
| Home redirect requires WRITE_SECURE_SETTINGS (ADB) | Can't enable from TV alone | Grant via ADB once |
| Custom wallpapers not supported | Only 16 built-in | Use built-in cinematic wallpapers |
| No real content metadata | Placeholder artwork | Integrate content provider later |
| Fire OS settings intents may vary | Some shortcuts may not work | Toast fallback, manual navigation |
| Home redirect disabled by default | Amazon launcher remains default | Enable via Settings menu after ADB grant |
| No voice search integration | Alexa not connected | Use Fire TV remote microphone button |

---

## Sign-off

| Test | Status | Notes |
|------|--------|-------|
| Installation | ☐ Pass / ☐ Fail | |
| Home Screen | ☐ Pass / ☐ Fail | |
| Dock Navigation | ☐ Pass / ☐ Fail | |
| Content Rows | ☐ Pass / ☐ Fail | |
| App Library | ☐ Pass / ☐ Fail | |
| Settings | ☐ Pass / ☐ Fail | |
| Wallpaper Picker | ☐ Pass / ☐ Fail | |
| Screensaver | ☐ Pass / ☐ Fail | |
| Home Redirect | ☐ Pass / ☐ Fail | |
| Performance | ☐ Pass / ☐ Fail | |
| Recovery | ☐ Pass / ☐ Fail | |

**Tested by**: _______________  
**Date**: _______________  
**Device**: Amazon Fire TV Stick Lite (AFTSS)  
**OS**: Fire OS 7.7.1.7 / Android 9  
**APK Version**: 1.0 (debug)