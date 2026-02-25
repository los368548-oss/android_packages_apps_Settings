# Material You Dynamic Glassmorphic Colors Implementation

## Overview

The glassmorphic effect now supports **Material You (Monet) dynamic colors**, which means the UI colors will automatically change based on the user's wallpaper selection.

## How It Works

### Material You Color System

Android 12+ introduced Material You (codenamed Monet), which extracts colors from the user's wallpaper and generates a complete color palette. The system creates:

- **Primary colors** - Main accent colors derived from wallpaper
- **Secondary colors** - Complementary accent colors
- **Tertiary colors** - Additional accent variants
- **Surface colors** - Background colors with appropriate contrast
- **Container colors** - Subtle tinted backgrounds

### Implementation

The dynamic glassmorphic system uses private Android color resources:

```xml
@androidprv:color/materialColorPrimary
@androidprv:color/materialColorSecondary
@androidprv:color/materialColorTertiary
@androidprv:color/materialColorSurface
@androidprv:color/materialColorSurfaceContainer
@androidprv:color/materialColorPrimaryContainer
@androidprv:color/materialColorOutlineVariant
```

These colors are automatically updated by the system when the wallpaper changes.

## Files Created

### Dynamic Color Definitions
- `setting/res/values/colors_glassmorphic_dynamic.xml` - Color definitions using Material You references

### Dynamic Drawables
- `setting/res/drawable/glassmorphic_selector_dynamic.xml` - Light mode dynamic selector
- `setting/res/drawable-night/glassmorphic_selector_dynamic.xml` - Dark mode dynamic selector
- `setting/res/drawable/glassmorphic_bg_dynamic.xml` - Light mode background
- `setting/res/drawable-night/glassmorphic_bg_dynamic.xml` - Dark mode background

## Usage

### Option 1: Use Dynamic Background (Recommended)

To use the wallpaper-adaptive glassmorphic effect:

```xml
<LinearLayout
    android:background="@drawable/glassmorphic_bg_dynamic"
    ... >
```

Or reference via drawable resource:

```xml
<LinearLayout
    android:background="@drawable/glassmorphic_preference_background_dynamic"
    ... >
```

### Option 2: Use Static Background (Original)

To use the fixed-color glassmorphic effect:

```xml
<LinearLayout
    android:background="@drawable/glassmorphic_bg"
    ... >
```

## Visual Comparison

### Static Glassmorphic (Original)
```
Light Mode: Cyan tint (#42E8F4FD)
Dark Mode: Purple tint (#301A1F2E)
Accent: Fixed coral (#FF8A65)
```

### Dynamic Glassmorphic (Material You)
```
Light Mode: Wallpaper-derived surface color
Dark Mode: Wallpaper-derived surface color (darker)
Accent: Wallpaper-derived primary color
```

## Color Mapping

| Element | Static Color | Dynamic Color |
|---------|-------------|---------------|
| Surface | `#42E8F4FD` | `materialColorSurfaceContainer` |
| Border | `#50FFFFFF` | `materialColorOutlineVariant` |
| Glow | `#30C8E6FF` | `materialColorPrimaryContainer` |
| Ripple | `#38FFFFFF` | `materialColorPrimary` |
| Accent | `#FF8A65` | `materialColorPrimary` |

## Category Accent Colors

For category-specific pages (Network, Display, etc.), the accent colors now use:

| Category | Static | Dynamic |
|----------|--------|---------|
| Network | `#42A5F5` | `materialColorPrimary` |
| Devices | `#26A69A` | `materialColorSecondary` |
| Apps | `#AB47BC` | `materialColorTertiary` |
| Notifications | `#FF8A65` | `materialColorPrimary` |
| Battery | `#66BB6A` | `materialColorSecondary` |
| Display | `#7E57C2` | `materialColorTertiary` |
| Sound | `#EC407A` | `materialColorPrimary` |
| Storage | `#FFA726` | `materialColorSecondary` |
| Security | `#EF5350` | `materialColorTertiary` |

## Requirements

- Android 12 (API 31) or higher for full Material You support
- Device must support Monet color extraction
- Works on AOSP-based ROMs with Material You support

## Fallback Behavior

On devices without Material You support:
- The system falls back to default Material 3 colors
- The glassmorphic effect still works with system accent colors
- No visual breakage occurs

## Testing

To test dynamic colors:
1. Change device wallpaper
2. Observe glassmorphic colors updating
3. Check both light and dark modes
4. Verify accent colors match wallpaper palette

## Notes

- The About Phone section continues to use its dedicated glassmorphic implementation
- Static colors remain available for consistency if needed
- Both implementations can coexist in the same app
