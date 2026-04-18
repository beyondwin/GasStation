# Splash And Icon Refresh Design

## Goal

- Show a branded launch experience with a yellow background and centered icon on app startup.
- Keep the existing droplet symbol, but remove the low-resolution look by replacing bitmap-scaled assets with vector-based drawables where Android uses them most.

## Approaches Considered

### 1. Yellow splash with centered icon only

- Pros: clean, immediate, matches Android splash expectations, avoids cramped launch text.
- Cons: relies entirely on the symbol carrying the brand.

### 2. Yellow splash with centered icon and app name

- Pros: more explicit branding on first launch.
- Cons: easier to look crowded, short splash duration makes the text feel incidental, requires more layout tuning across screen sizes.

### 3. Keep current icon but only upscale PNG files

- Pros: smallest implementation delta.
- Cons: does not actually remove the bitmap-scaling problem on modern launchers.

## Chosen Direction

Use approach 1. Keep the droplet symbol, render the splash as a yellow field with a centered icon only, and replace the adaptive icon foreground/monochrome resources with XML drawables backed by vector paths.

## Implementation Notes

- Apply a dedicated launcher theme to `MainActivity` for startup only.
- Use `android:windowBackground` for the pre-Android-12 fallback splash.
- Use `android:windowSplashScreenBackground` and `android:windowSplashScreenAnimatedIcon` for Android 12+.
- Switch back to the normal app theme at the start of `MainActivity.onCreate()`.
- Preserve the adaptive icon manifest setup and continue exposing `@mipmap/ic_launcher`.

## Verification

- Add Robolectric coverage for splash theme attributes and non-bitmap adaptive foreground resources.
- Verify Android resource processing for the app module succeeds after the changes.
- Note any unrelated pre-existing compile failures separately instead of rolling them into this task.
