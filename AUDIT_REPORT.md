# COMPREHENSIVE REPOSITORY AUDIT — FIRE TV LAUNCHER

## Project Overview
- **Package**: `com.gathin.tvhome`
- **Language**: Java (Android API 28+)
- **Target**: Amazon Fire TV Stick Lite (AFTSS, Android 9, API 28, 1GB RAM, MediaTek MT8695)
- **Resolution**: 1920×1080

---

## 1. EXISTING FUNCTIONAL BUGS

### 1.1 Clock Positioning & Clipping (CRITICAL)
**Location**: `item_hero_section.xml` lines 49-69
- Clock uses `TextClock` with `format12Hour="h:mm"` and `format24Hour="HH:mm"`
- AM/PM and Date are in a horizontal LinearLayout with `baselineAligned="true"`
- **Issue**: RelativeLayout with `layout_alignBottom="@id/leftBlock"` for top buttons can cause misalignment when text sizes vary
- **Issue**: No safe-area insets handling for overscan on TV displays
- **Issue**: Clock text size is 64sp (text_size_9xl) — may clip on smaller screens or with overscan

### 1.2 Three Top-Right Utility Buttons Non-Functional (CRITICAL)
**Location**: `item_hero_section.xml` lines 107-125, `MainActivity.java`
- **btnInputs** (ic_input): No OnClickListener registered
- **btnWifi** (ic_wifi): Only updates label via `updateWifiLabel()`, no click action
- **btnSettings** (ic_settings): No OnClickListener registered
- **Root Cause**: Buttons defined in layout but never wired up in `MainContentAdapter.HeroHolder` or `MainActivity`
- **Impact**: User cannot access Inputs, Wi-Fi settings, or Settings from home screen

### 1.3 App Dock Icons Too Small (HIGH)
**Location**: `dimens.xml` line 98 (`icon_dock=56dp`), `item_dock.xml` line 26
- 56dp icons at 10-foot viewing distance on 1080p are barely recognizable
- Apple TV uses ~80-90dp equivalent; Google TV uses ~72dp
- **Recommendation**: Increase to 72-80dp with proportional label scaling

### 1.4 Hero Title/Metadata Poor Positioning (HIGH)
**Location**: `item_hero_section.xml` lines 129-178
- Hero content area fixed at `@dimen/hero_height_compact` (400dp)
- Title max-width 900dp but no responsive behavior
- Content pinned to bottom-left with fixed padding — doesn't adapt to artwork aspect ratios
- No parallax or cinematic depth effect

### 1.5 Weak Visual Hierarchy (MEDIUM)
- Top bar: Greeting (12sp), Clock (64sp), Date (14sp) — good hierarchy but cramped
- Dock: No clear visual separation from content rows
- Content rows: Headers use accent color but weak contrast against background
- No consistent elevation/depth system

### 1.6 Laggy/Unresponsive Feel (HIGH)
**Root Causes Identified**:
1. **vividColor() called during bind** (`DockAdapter.java:226-231`, `GridAdapter.java:157-162`, `ContentAdapter`): Creates 12×12 bitmap, draws, scans pixels — ON MAIN THREAD during RecyclerView binding
2. **Spring animations on every focus change**: GlassView spring (260 stiffness) + dock magnification + ambient glow all trigger simultaneously
3. **No view recycling optimization**: `setHasFixedSize(true)` but `notifyDataSetChanged()` used frequently
4. **Bitmap decoding without downsampling**: `ImageLoader` exists but not used for dock/grid icons (uses `ri.loadIcon(pm)` directly)
5. **Ambient glow gradient recalculation** on every focus change (`MainActivity.java:177-182`)

---

## 2. INCORRECT SCREEN POSITIONING / CLIPPED ELEMENTS

| Element | Issue | File |
|---------|-------|------|
| Clock/Date | Potential clipping on overscan | `item_hero_section.xml:49-92` |
| Dock labels | `heroName` positioned programmatically, may go off-screen | `MainActivity.java:681-705` |
| Dock magnification | `card.setPivotY(card.getHeight())` — pivot at bottom causes upward growth | `MainActivity.java:656-658` |
| GlassView | `rect.set(inset, inset, ...)` with 6dp inset — may clip at small sizes | `GlassView.java:52-53` |
| Category tabs | Horizontal RecyclerView no padding — first/last items clip at edges | `activity_app_library.xml:84-96` |

---

## 3. UNRESPONSIVE BUTTONS / BROKEN CLICK LISTENERS

| Button | Expected Action | Current State |
|--------|----------------|---------------|
| btnInputs | Show HDMI/input selector popup | **NO LISTENER** |
| btnWifi | Open Wi-Fi settings | Updates label only |
| btnSettings | Open SettingsActivity | **NO LISTENER** |
| btnLibraryBack | Finish AppLibraryActivity | Works (set in Activity) |
| btnLibrarySearch | Search apps | **NO LISTENER** (icon only) |
| btnSettingsBack | Finish SettingsActivity | Works (set in Activity) |
| btnWallpaperBack | Finish WallpaperPickerActivity | Works |
| Apply Wallpaper | Save preference | Works |
| Dock item click | Launch app | Works (in adapter) |
| Dock item long-press | Show menu (pin/unpin/move/info) | Works |
| Content card click | No action defined | Stub only |
| Grid item click | Launch app | Works |
| Apps row click | Open AppLibraryActivity | Works |

---

## 4. BROKEN REMOTE NAVIGATION

### 4.1 Focus Management Issues
- **Dock → Content Row DOWN**: Works via `focusFirstContentRow()` but relies on ViewHolder lookup
- **Content Row UP at position 0**: Goes to dock last item — correct
- **Content Row LEFT at position 0**: Should go to previous row/dock — partially implemented
- **Grid UP from top row**: Returns to main content — works
- **Apps Row UP**: Should go to last content row — implemented in `AppsRowHolder`
- **Focus restoration on resume**: Only focuses dock, not previous position

### 4.2 Navigation Loops / Traps
- **Hero section**: Not focusable — cannot reach top bar buttons via D-pad
- **Top bar buttons (Inputs, WiFi, Settings)**: Not in focus order — unreachable by remote
- **Category tabs in App Library**: Focusable but no clear visual indication when focused vs selected

### 4.3 Missing Focus States
- Top bar buttons: Have `state_focused` drawable but never receive focus
- Apps row: Has focus listener but no visual focus animation
- Settings items: Use `bg_glass_button` but no scale/lift animation

---

## 5. PERFORMANCE ISSUES

### 5.1 Main Thread Bitmap Operations (CRITICAL)
```java
// DockAdapter.java:233-272 — called during onBindViewHolder via ambientColorAt()
private static int vividColor(Drawable live) {
    Bitmap bmp = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888); // ALLOCATION
    Canvas c = new Canvas(bmp);
    d.setBounds(0, 0, 12, 12);
    d.draw(c); // DRAW
    // ... pixel scanning loop
    bmp.recycle();
}
```
- Called for every dock item on bind → 4-7 allocations per frame during scroll
- Same in `GridAdapter.java:164-203` and potentially ContentAdapter

### 5.2 Inefficient Image Loading
- `PackageManager.loadIcon()` returns full-resolution drawables (often 512×512+)
- No downsampling for 56dp/48dp display sizes
- No caching of loaded icons across Activity recreation

### 5.3 Excessive Animation Overhead
- SpringAnimation for glass (2 springs × 60fps)
- Dock magnification animates ALL items on every focus change (not just neighbors)
- Ambient glow crossfade (600ms + 500ms) on every focus change
- Hero background crossfade (600ms + 900ms) on time-of-day change

### 5.4 Memory Pressure
- 12 saver_XX images in drawable-nodpi (likely 1920×1080 each = ~3-5MB each)
- All loaded as Drawables simultaneously in content adapters
- No LRU cache for content artwork (unlike ImageLoader for wallpapers)
- `Bitmap.Config.ARGB_8888` used in vividColor instead of RGB_565

---

## 6. CODE STRUCTURE ISSUES

### 6.1 Duplicated Code
- `vividColor()` duplicated in `DockAdapter`, `GridAdapter` (identical)
- Background loading logic duplicated in `MainActivity`, `AppLibraryActivity`, `SettingsActivity`, `WallpaperPickerActivity`
- Focus animation logic duplicated in `DockAdapter.Holder`, `GridAdapter.Holder`, `ContentAdapter.Holder`, `LibraryGridAdapter.Holder`, `WallpaperAdapter.Holder`, `CategoryAdapter.Holder`

### 6.2 Tight Coupling
- `MainActivity` directly manipulates `GlassView`, `ambientGlow`, `heroName`, `dockDot` — UI logic in Activity
- Adapters hold references to Activity listeners — circular dependencies
- `MainContentAdapter` creates layout managers in `onBindViewHolder` — should be in `onCreateViewHolder`

### 6.3 Missing Abstractions
- No common `BaseAdapter` or `FocusableViewHolder` for shared focus/animation logic
- No `ImageLoader` integration for app icons
- No centralized navigation/focus manager

---

## 7. MISSING FEATURES (PER DESIGN SPEC)

| Feature | Status |
|---------|--------|
| Cinematic hero with artwork + gradient overlay | Partial (static background only) |
| Hero Play/Open + More Info actions | Missing |
| Hero fallback when artwork unavailable | Missing |
| Floating dock with premium focus scaling | Partial (magnification works but icons small) |
| App library with categories + search | Partial (categories work, search missing) |
| Settings with organized sections | Partial (flat list, no sections UI) |
| Wallpaper picker with preview | Works |
| Screensaver (PhotoDream) | Works (Ken Burns) |
| Home redirect service | Works (accessibility-based) |

---

## 8. COMPATIBILITY VERIFICATION

| Requirement | Status |
|-------------|--------|
| Package ID `com.gathin.tvhome` | ✅ Preserved |
| API 28 compatibility | ✅ minSdk 26, targetSdk 29 |
| Gradle build system | ✅ Standard |
| Amazon launcher preserved | ✅ HomeRedirectService optional |
| No system app removal | ✅ |
| Recoverable/launchable independently | ✅ |
| No unnecessary dependencies | ✅ (only RecyclerView + DynamicAnimation) |

---

## 9. PRIORITY FIX LIST

### P0 — Critical (Blockers)
1. **Wire up 3 utility buttons** (Inputs, WiFi, Settings) with click listeners and focus support
2. **Fix clock clipping** with safe-area padding and responsive layout
3. **Move vividColor() off main thread** — cache ambient colors during app load
4. **Make top bar buttons reachable via D-pad** — add to focus order

### P1 — High (Major UX)
5. **Increase dock icon size** to 72-80dp with proportional scaling
6. **Redesign hero section** with cinematic artwork, gradient overlays, proper text hierarchy
7. **Implement hero fallback** for missing artwork
8. **Optimize dock magnification** — animate only focused + neighbors
9. **Fix focus navigation loops** — ensure all sections reachable, no traps
10. **Add focus animations to Settings, Apps row, Category tabs**

### P2 — Medium (Polish)
11. **Extract common focus animation logic** to base classes
12. **Integrate ImageLoader for app icons** with downsampling
13. **Add search to App Library**
14. **Organize Settings into visual sections** with headers
15. **Add section headers to Settings RecyclerView**

### P3 — Low (Nice to Have)
16. **Hero parallax/depth effect** on focus
17. **Content row "See All" navigation**
18. **Refined spring physics** (stiffness/damping per component)
19. **Hero transition animations** on background change

---

## 10. FILES REQUIRING MODIFICATION

### Core Activity & Navigation
- `MainActivity.java` — Fix utility buttons, focus navigation, performance
- `MainContentAdapter.java` — HeroHolder wiring, focus management

### Layouts
- `item_hero_section.xml` — Clock safety, hero redesign, button focusability
- `item_dock.xml` — Larger icons, better focus visual
- `item_dock_section.xml` — Dock container improvements
- `activity_main.xml` — Safe area handling

### Adapters
- `DockAdapter.java` — Cache ambient colors, optimize magnification
- `GridAdapter.java` — Cache ambient colors, ImageLoader integration
- `ContentAdapter.java` — Focus animations, click handling

### New/Enhanced Components
- `FocusManager.java` (new) — Centralized navigation logic
- `BaseFocusableAdapter.java` (new) — Shared focus/animation logic
- `IconLoader.java` (new) — Cached, downsampled app icons

### Settings & App Library
- `SettingsActivity.java` — Section headers, search
- `AppLibraryActivity.java` — Search implementation
- `activity_settings.xml` — Sectioned layout
- `activity_app_library.xml` — Search bar

### Drawables (New/Updated)
- Hero gradient overlays
- Updated dock/item backgrounds for larger sizes
- Focus ring improvements

---

*Audit completed. Proceeding with implementation.*