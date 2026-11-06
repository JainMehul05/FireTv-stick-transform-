# Recovery Guide - Premium Cinematic Fire TV Launcher

## Overview
This guide covers recovery procedures for the Premium Cinematic Fire TV Launcher (com.gathin.tvhome) on Amazon Fire TV Stick Lite (AFTSS).

**Critical**: The original Amazon launcher (com.amazon.tv.launcher) is NEVER disabled or uninstalled. This launcher coexists with the system launcher.

---

## Quick Recovery Commands

### 1. Force Stop & Restart
```powershell
# Force stop the launcher
adb shell am force-stop com.gathin.tvhome

# Restart the launcher
adb shell am start -n com.gathin.tvhome/.MainActivity
```

### 2. Clear App Data (Reset to Defaults)
```powershell
# Clears all preferences (dock pins, wallpaper, settings)
adb shell pm clear com.gathin.tvhome
```

### 3. Uninstall Launcher (Complete Removal)
```powershell
# Uninstall only this launcher
adb uninstall com.gathin.tvhome

# Verify Amazon launcher still works
adb shell am start -n com.amazon.tv.launcher/com.amazon.tv.launcher.HomeActivity
```

---

## Home Redirect Recovery

### If Home Redirect Causes Issues (Boot Loop, Wrong Launcher)

#### Option A: Disable via ADB (Recommended)
```powershell
# Disable home redirect accessibility service
adb shell settings put secure enabled_accessibility_services ""
adb shell settings put secure accessibility_enabled 0

# Verify Amazon launcher is default
adb shell cmd package set-home-activity com.amazon.tv.launcher/com.amazon.tv.launcher.HomeActivity
```

#### Option B: Disable via Settings Menu (if accessible)
1. Navigate to Settings → Accessibility
2. Find "Home screen" or "Premium Cinematic Launcher"
3. Toggle OFF
3. If Settings inaccessible, use Option A

#### Option C: Safe Mode Boot
1. Power off Fire TV
2. Hold **Back + Right** on remote while powering on
3. Keep held until "Safe Mode" appears
4. In Safe Mode, only system apps run - uninstall or disable our launcher
5. Reboot normally

#### Option D: Factory Reset (Last Resort)
**Warning**: Erases all data
- Settings → My Fire TV → Reset to Factory Defaults
- Or: Hold **Back + Right** for 10 seconds during boot

---

## Amazon Launcher Restoration

### If Amazon Launcher Seems Missing
```powershell
# Re-enable Amazon launcher
adb shell pm enable com.amazon.tv.launcher

# Set as default home
adb shell cmd package set-home-activity com.amazon.tv.launcher/com.amazon.tv.launcher.HomeActivity

# Launch to verify
adb shell am start -n com.amazon.tv.launcher/com.amazon.tv.launcher.HomeActivity
```

### If Home Button Opens Wrong App
```powershell
# Reset default home activity
adb shell cmd package set-home-activity com.amazon.tv.launcher/com.amazon.tv.launcher.HomeActivity

# Clear our home intent (if needed)
adb shell pm disable com.gathin.tvhome/com.gathin.tvhome.MainActivity
# Then re-enable if you want to keep our launcher:
adb shell pm enable com.gathin.tvhome/com.gathin.tvhome.MainActivity
```

---

## Common Issues & Fixes

### Issue: "App not installed" or Installation Fails
```powershell
# Check if package exists
adb shell pm list packages | findstr com.gathin.tvhome

# If exists, uninstall first
adb uninstall com.gathin.tvhome

# Then reinstall
adb install -r app-debug.apk
```

### Issue: Launcher Crashes on Startup
```powershell
# Check logs
adb logcat -d | findstr com.gathin.tvhome

# Clear data and restart
adb shell pm clear com.gathin.tvhome
adb shell am start -n com.gathin.tvhome/.MainActivity
```

### Issue: Home Button Doesn't Work / Opens Amazon Launcher
```powershell
# Home redirect not enabled - this is DEFAULT behavior
# To enable (requires ADB permission once):
adb shell pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS

# Then enable via Settings menu (long press Settings button)
# Or via ADB:
adb shell settings put secure enabled_accessibility_services com.gathin.tvhome/com.gathin.tvhome.HomeRedirectService
adb shell settings put secure accessibility_enabled 1
```

### Issue: Settings Shortcuts Don't Work
- Some Fire OS versions have different settings activity names
- Toast message "Not available on this TV" is expected fallback
- Manually navigate: Settings → [desired section]

### Issue: WiFi/Network Status Not Updating
- Requires ACCESS_NETWORK_STATE & ACCESS_WIFI_STATE (granted in manifest)
- Check: `adb shell dumpsys connectivity`
- Minute tick updates every 60s; NetworkCallback provides real-time updates

### Issue: Dock Apps Missing After Reboot
- Dock pins saved in SharedPreferences (`tvhome` / `dock`)
- Check: `adb shell run-as com.gathin.tvhome cat /data/data/com.gathin.tvhome/shared_prefs/tvhome.xml`
- Default dock apps defined in `res/values/config.xml` → `dock_apps` array

### Issue: Background Images Not Loading
- Time-of-day backgrounds: `bg_morning`, `bg_afternoon`, `bg_evening`, `bg_night` (drawable-nodpi)
- Screensaver images: `saver_01` through `saver_12` (drawable-nodpi)
- Verify: `adb shell run-as com.gathin.tvhome ls /data/data/com.gathin.tvhome/files/` (should be empty - resources in APK)

### Issue: Screensaver Not Appearing
```powershell
# Check current screensaver
adb shell settings get secure screensaver_components

# Set our screensaver
adb shell settings put secure screensaver_components com.gathin.tvhome/com.gathin.tvhome.PhotoDream

# Activate screensaver (for testing)
adb shell am start -n com.gathin.tvhome/.PhotoDream
```

---

## ADB Connection Troubleshooting

### USB ADB
```powershell
# Enable USB debugging on Fire TV
# Settings → My Fire TV → Developer Options → ADB Debugging = ON
# Settings → My Fire TV → Developer Options → USB Debugging = ON

# Connect USB cable
adb devices
```

### Network ADB (Wireless)
```powershell
# On Fire TV: Settings → My Fire TV → About → Network → Note IP address
# On Fire TV: Settings → My Fire TV → Developer Options → ADB Debugging = ON

# On PC:
adb connect <IP_ADDRESS>:5555
adb devices

# If connection fails:
adb kill-server
adb start-server
adb connect <IP_ADDRESS>:5555
```

### ADB Authorization Issues
```powershell
# On Fire TV: Allow USB debugging prompt should appear
# If not, revoke and re-authorize:
adb kill-server
adb usb
# Unplug/replug USB or toggle ADB Debugging off/on
```

---

## Backup & Restore

### Backup Preferences
```powershell
# Backup SharedPreferences
adb shell run-as com.gathin.tvhome cat /data/data/com.gathin.tvhome/shared_prefs/tvhome.xml > tvhome_backup.xml

# Backup all app data (requires root for full backup)
adb backup -f launcher_backup.ab -apk com.gathin.tvhome

# Restore
adb restore launcher_backup.ab
```

---

## Verification Checklist After Recovery

After any recovery procedure, verify:

- [ ] Amazon launcher launches: `adb shell am start -n com.amazon.tv.launcher/com.amazon.tv.launcher.HomeActivity`
- [ ] Home button returns to Amazon launcher
- [ ] Settings accessible: `adb shell am start -n com.amazon.tv.settings/.SettingsActivity`
- [ ] Our launcher installs: `adb install -r app-debug.apk`
- [ ] Our launcher launches: `adb shell am start -n com.gathin.tvhome/.MainActivity`
- [ ] Home redirect can be toggled (after ADB grant)
- [ ] No boot loops or crashes

---

## Emergency Contacts & Resources

- **Amazon Fire TV Support**: https://www.amazon.com/firetv-support
- **ADB Documentation**: https://developer.android.com/studio/command-line/adb
- **Fire TV Developer**: https://developer.amazon.com/firetv
- **Project Repository**: https://github.com/itsmeterrylin/fire-tv-cleanup

---

## Version Information

- **Launcher Package**: com.gathin.tvhome
- **Version Code**: 2
- **Version Name**: 2.0
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 29 (Android 10)
- **Compile SDK**: 34
- **Build**: Debug (debuggable=true)

**Last Updated**: 2026-11-05  
**Compatible**: Fire OS 7.x (Android 9), Fire TV Stick Lite (AFTSS/sheldon)