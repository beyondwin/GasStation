# Splash Signal Pulse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify GasStation startup on AndroidX SplashScreen, add a one-shot 300ms Signal Pulse on API 31+, and fade cleanly into the first Compose frame without delaying startup.

**Architecture:** Keep Android launch chrome entirely inside `app`. A version-catalogued AndroidX SplashScreen dependency and starting theme provide API 24–30 static fallback and API 31+ AVD selection, while a small app-internal `SplashExitAnimator` owns the 180ms exit and reduced-motion behavior. Existing startup hooks, permission/location readiness, Compose navigation, launcher icon, and feature/domain/data modules remain unchanged.

**Tech Stack:** Kotlin 2.4.10, AndroidX Core SplashScreen 1.2.0, Android resource qualifiers, AnimatedVectorDrawable, Robolectric 4.16.1, JUnit 4, Android Macrobenchmark

## Global Constraints

- `app` owns all starting themes, splash drawables, motion wiring, and tests.
- `androidx.core:core-splashscreen` is pinned to stable version `1.2.0` through `gradle/libs.versions.toml`.
- API 24–30 uses the existing static black droplet on launcher yellow.
- API 31+ uses one 300ms AVD: drop alpha/scale at 0–300ms and one low-alpha ring at 80–260ms.
- Exit motion is exactly 180ms: splash alpha `1 → 0`, icon scale `1.0 → 1.06`.
- The exit view is removed on animation end, animation cancel, and immediately when system animations are disabled.
- Do not call `setKeepOnScreenCondition`, sleep, delay, or wait for AVD completion.
- Do not add a Splash Activity, Compose splash route, text, branding image, flavor branch, or dark splash.
- Keep the same yellow/black splash in day and night modes because the app remains light-first.
- Do not modify `core:designsystem`, any `feature:*`, `domain:*`, `data:*`, demo seed, launcher adaptive icon, or external-map flow.
- Capture the same-device startup baseline before the first production edit.
- API 30 and latest-API runtime evidence are both required before completion.

## File Map

- `gradle/libs.versions.toml`
  - Adds the stable `coreSplashscreen` version and `androidx-core-splashscreen` alias.
- `app/build.gradle.kts`
  - Adds the AndroidX SplashScreen runtime dependency.
- `app/src/main/res/values/themes.xml`
  - Defines the AndroidX starting theme, static icon, launcher yellow, and post-splash theme.
- `app/src/main/res/values-v31/themes.xml`
  - Keeps the API 31+ framework splash attributes and post-splash theme explicit.
- `app/src/main/res/drawable/ic_splash_foreground.xml`
  - Remains the API 24–30 static inset droplet.
- `app/src/main/res/drawable-v31/ic_splash_foreground.xml`
  - Selects the Signal Pulse AVD on API 31+ under the same resource id.
- `app/src/main/res/drawable-v31/ic_splash_signal_pulse_vector.xml`
  - Owns named drop/ring groups and paths inside the splash safe area.
- `app/src/main/res/animator-v31/splash_drop_scale.xml`
  - Scales the drop from `0.82 → 1.04 → 1.0` over 300ms.
- `app/src/main/res/animator-v31/splash_drop_alpha.xml`
  - Fades the drop from `0 → 1` over the first 100ms.
- `app/src/main/res/animator-v31/splash_ring_scale.xml`
  - Expands the signal ring once from `0.75 → 1.35` during 80–260ms.
- `app/src/main/res/animator-v31/splash_ring_alpha.xml`
  - Fades the ring `0 → 0.35 → 0` during 80–260ms.
- `app/src/main/java/com/gasstation/MainActivity.kt`
  - Installs AndroidX SplashScreen before `super.onCreate()` and connects the exit animator.
- `app/src/main/java/com/gasstation/SplashExitAnimator.kt`
  - Owns reduced-motion detection, exit animator creation, and one-shot removal.
- `app/src/testDemo/java/com/gasstation/SplashThemeResourceTest.kt`
  - Protects API 30/31, day/night, static/animated resource, and post-theme contracts.
- `app/src/testDemo/java/com/gasstation/SplashExitAnimatorTest.kt`
  - Protects exact exit timing, alpha/scale endpoints, animations-off behavior, and one-shot cleanup.
- `docs/architecture.md`
  - Documents the AndroidX launch sequence before the existing station-list runtime flow.
- `docs/test-strategy.md`
  - Documents the host/resource versus device evidence split.
- `docs/verification-matrix.md`
  - Adds focused splash commands and the API 30/latest runtime matrix.
- `CHANGELOG.md`
  - Records the user-visible launch polish under `Unreleased`.

---

### Task 1: Unify the starting theme on AndroidX SplashScreen

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values-v31/themes.xml`
- Modify: `app/src/main/java/com/gasstation/MainActivity.kt:23-32`
- Modify: `app/src/testDemo/java/com/gasstation/SplashThemeResourceTest.kt`

**Interfaces:**
- Consumes: manifest activity theme `@style/Theme.GasStation.Launcher`, existing `R.color.ic_launcher_background`, existing `R.drawable.ic_splash_foreground`, and `Theme.GasStation`.
- Produces: AndroidX `Theme.SplashScreen` starting theme with `postSplashScreenTheme = Theme.GasStation`; `MainActivity` calls `installSplashScreen()` immediately before `super.onCreate()`.
- Preserves: package names, activity entry point, demo/prod startup hooks, system-bar policy, first-content reporting, and launcher icon resources.

- [ ] **Step 1: Capture the before-change startup baseline on the existing API 37 AVD**

Resolve the SDK path and start the existing `Pixel_8` AVD in a persistent terminal if no emulator is connected:

```bash
GASSTATION_SDK_ROOT="$(sed -n 's/^sdk.dir=//p' local.properties)"
test -x "$GASSTATION_SDK_ROOT/platform-tools/adb"
if ! "$GASSTATION_SDK_ROOT/platform-tools/adb" devices | awk 'NR > 1 && $2 == "device" { found = 1 } END { exit !found }'; then
  "$GASSTATION_SDK_ROOT/emulator/emulator" \
    @Pixel_8 \
    -no-snapshot-save \
    -no-boot-anim \
    -gpu swiftshader_indirect
fi
```

After `sys.boot_completed` becomes `1`, capture only the startup macrobenchmark and copy its JSON to an explicit temporary baseline:

```bash
GASSTATION_SDK_ROOT="$(sed -n 's/^sdk.dir=//p' local.properties)"
GASSTATION_LATEST_SERIAL=""
for GASSTATION_CANDIDATE_SERIAL in $(
  "$GASSTATION_SDK_ROOT/platform-tools/adb" devices |
    awk 'NR > 1 && $2 == "device" { print $1 }'
); do
  GASSTATION_AVD_NAME="$(
    "$GASSTATION_SDK_ROOT/platform-tools/adb" \
      -s "$GASSTATION_CANDIDATE_SERIAL" emu avd name 2>/dev/null |
      tr -d '\r' |
      sed -n '1p'
  )"
  if [ "$GASSTATION_AVD_NAME" = "Pixel_8" ]; then
    GASSTATION_LATEST_SERIAL="$GASSTATION_CANDIDATE_SERIAL"
    break
  fi
done
test -n "$GASSTATION_LATEST_SERIAL"
ANDROID_SERIAL="$GASSTATION_LATEST_SERIAL" ./gradlew \
  :app:installDemoBenchmark \
  :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.gasstation.benchmark.StationListBenchmark#startupToFirstContent \
  --warning-mode fail
GASSTATION_BEFORE_JSON="$(
  find benchmark/build/outputs/connected_android_test_additional_output \
    -name '*benchmarkData.json' -type f -exec stat -f '%m %N' {} + |
    sort -nr |
    sed -n '1s/^[0-9]* //p'
)"
test -n "$GASSTATION_BEFORE_JSON"
cp "$GASSTATION_BEFORE_JSON" /tmp/gasstation-splash-signal-pulse-before.json
```

Expected: 10 cold startup iterations complete and `/tmp/gasstation-splash-signal-pulse-before.json` exists. Do not compare this emulator result with the historical Samsung Galaxy S20+ numbers.

- [ ] **Step 2: Replace the class-wide API 31 test with failing version-specific starting-theme tests**

Rewrite `SplashThemeResourceTest` to use per-test `@Config` and add these helpers/tests before changing production resources:

```kotlin
package com.gasstation

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.core.splashscreen.R as SplashScreenR
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class SplashThemeResourceTest {
    @Test
    @Config(sdk = [30], application = android.app.Application::class)
    fun `pre android 12 launcher resolves branded compat splash and post theme`() {
        val themedContext = launcherThemedContext()

        assertThemeResource(
            themedContext,
            SplashScreenR.attr.windowSplashScreenBackground,
            R.color.ic_launcher_background,
        )
        assertThemeResource(
            themedContext,
            SplashScreenR.attr.windowSplashScreenAnimatedIcon,
            R.drawable.ic_splash_foreground,
        )
        assertThemeResource(
            themedContext,
            SplashScreenR.attr.postSplashScreenTheme,
            R.style.Theme_GasStation,
        )
    }

    @Test
    @Config(
        sdk = [30],
        qualifiers = "night",
        application = android.app.Application::class,
    )
    fun `pre android 12 night mode keeps brand constant splash colors`() {
        val themedContext = launcherThemedContext()

        assertThemeResource(
            themedContext,
            SplashScreenR.attr.windowSplashScreenBackground,
            R.color.ic_launcher_background,
        )
        assertThemeResource(
            themedContext,
            SplashScreenR.attr.windowSplashScreenAnimatedIcon,
            R.drawable.ic_splash_foreground,
        )
        assertThemeResource(
            themedContext,
            SplashScreenR.attr.postSplashScreenTheme,
            R.style.Theme_GasStation,
        )
    }

    @Test
    @Config(sdk = [31], application = android.app.Application::class)
    fun `android 12 launcher resolves framework splash and post theme`() {
        val themedContext = launcherThemedContext()

        assertThemeResource(
            themedContext,
            android.R.attr.windowSplashScreenBackground,
            R.color.ic_launcher_background,
        )
        assertThemeResource(
            themedContext,
            android.R.attr.windowSplashScreenAnimatedIcon,
            R.drawable.ic_splash_foreground,
        )
        assertThemeResource(
            themedContext,
            SplashScreenR.attr.postSplashScreenTheme,
            R.style.Theme_GasStation,
        )
    }

    @Test
    @Config(
        sdk = [31],
        qualifiers = "night",
        application = android.app.Application::class,
    )
    fun `android 12 night mode keeps brand constant splash resources`() {
        val themedContext = launcherThemedContext()

        assertThemeResource(
            themedContext,
            android.R.attr.windowSplashScreenBackground,
            R.color.ic_launcher_background,
        )
        assertThemeResource(
            themedContext,
            android.R.attr.windowSplashScreenAnimatedIcon,
            R.drawable.ic_splash_foreground,
        )
        assertThemeResource(
            themedContext,
            SplashScreenR.attr.postSplashScreenTheme,
            R.style.Theme_GasStation,
        )
    }

    @Test
    @Config(sdk = [30], application = android.app.Application::class)
    fun `fallback splash background uses an inset drawable for the centered icon`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val drawable = context.getDrawable(R.drawable.splash_screen_background)

        assertNotNull(drawable)
        assertTrue(drawable is LayerDrawable)
        val layerDrawable = drawable as LayerDrawable
        assertEquals(2, layerDrawable.numberOfLayers)
        assertTrue(layerDrawable.getDrawable(1) is InsetDrawable)
    }

    private fun launcherThemedContext(): ContextThemeWrapper {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activityInfo = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getActivityInfo(
                ComponentName(context, MainActivity::class.java),
                PackageManager.ComponentInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getActivityInfo(
                ComponentName(context, MainActivity::class.java),
                0,
            )
        }
        return ContextThemeWrapper(context, activityInfo.themeResource)
    }

    private fun assertThemeResource(
        themedContext: ContextThemeWrapper,
        attribute: Int,
        expectedResource: Int,
    ) {
        val value = TypedValue()
        assertTrue(themedContext.theme.resolveAttribute(attribute, value, true))
        assertEquals(expectedResource, value.resourceId)
    }
}
```

The night-mode test must retain all three background, icon, and post-theme assertions.

- [ ] **Step 3: Run the focused test and verify RED**

Run:

```bash
./gradlew :app:testDemoDebugUnitTest \
  --tests 'com.gasstation.SplashThemeResourceTest' \
  --warning-mode fail
```

Expected: FAIL at test compilation because `androidx.core.splashscreen.R` is not available, or fail to resolve the AndroidX splash attributes because the current launcher theme does not inherit `Theme.SplashScreen`.

- [ ] **Step 4: Add AndroidX SplashScreen 1.2.0 through the version catalog**

Add to `[versions]` in `gradle/libs.versions.toml`:

```toml
coreSplashscreen = "1.2.0"
```

Add to `[libraries]`:

```toml
androidx-core-splashscreen = { module = "androidx.core:core-splashscreen", version.ref = "coreSplashscreen" }
```

Add to `app/build.gradle.kts` beside the other AndroidX runtime dependencies:

```kotlin
implementation(libs.androidx.core.splashscreen)
```

- [ ] **Step 5: Replace the base launcher theme with the AndroidX starting theme**

Replace `Theme.GasStation.Launcher` in `app/src/main/res/values/themes.xml`:

```xml
<style name="Theme.GasStation.Launcher" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">@color/ic_launcher_background</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/ic_splash_foreground</item>
    <item name="postSplashScreenTheme">@style/Theme.GasStation</item>
</style>
```

Replace the API 31 style in `app/src/main/res/values-v31/themes.xml`:

```xml
<style name="Theme.GasStation.Launcher" parent="Theme.SplashScreen">
    <item name="android:windowSplashScreenBackground">@color/ic_launcher_background</item>
    <item name="android:windowSplashScreenAnimatedIcon">@drawable/ic_splash_foreground</item>
    <item name="postSplashScreenTheme">@style/Theme.GasStation</item>
</style>
```

Do not add `windowSplashScreenAnimationDuration` or `setKeepOnScreenCondition`.

- [ ] **Step 6: Install SplashScreen before Activity creation**

Add:

```kotlin
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
```

Replace the start of `MainActivity.onCreate()` with:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    applySystemBars()
    super.onCreate(savedInstanceState)
    setContent {
        GasStationTheme {
            GasStationNavHost(
                externalMapLauncher = externalMapLauncher,
                onStationListFirstContentDrawn = startupDrawReporter::reportFirstContentDrawn,
            )
        }
    }
}
```

Remove `setTheme(R.style.Theme_GasStation)`. Do not move startup readiness into this method.

- [ ] **Step 7: Run focused tests and verify GREEN**

Run:

```bash
./gradlew \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :app:processDemoDebugResources \
  :app:processProdDebugResources \
  --warning-mode fail
```

Expected: PASS. API 30 resolves the AndroidX attrs and static resource, API 31 resolves framework attrs, both resolve `Theme.GasStation` as the post theme, and app startup code compiles for demo/prod.

- [ ] **Step 8: Commit the AndroidX starting-theme migration**

```bash
git add \
  gradle/libs.versions.toml \
  app/build.gradle.kts \
  app/src/main/res/values/themes.xml \
  app/src/main/res/values-v31/themes.xml \
  app/src/main/java/com/gasstation/MainActivity.kt \
  app/src/testDemo/java/com/gasstation/SplashThemeResourceTest.kt
git commit -m "refactor: unify splash startup contract"
```

---

### Task 2: Add the API 31+ Signal Pulse AVD

**Files:**
- Create: `app/src/main/res/drawable-v31/ic_splash_foreground.xml`
- Create: `app/src/main/res/drawable-v31/ic_splash_signal_pulse_vector.xml`
- Create: `app/src/main/res/animator-v31/splash_drop_scale.xml`
- Create: `app/src/main/res/animator-v31/splash_drop_alpha.xml`
- Create: `app/src/main/res/animator-v31/splash_ring_scale.xml`
- Create: `app/src/main/res/animator-v31/splash_ring_alpha.xml`
- Modify: `app/src/testDemo/java/com/gasstation/SplashThemeResourceTest.kt`
- Verify unchanged: `app/src/main/res/drawable/ic_splash_foreground.xml`
- Verify unchanged: `app/src/main/res/drawable/ic_brand_drop.xml`
- Verify unchanged: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`

**Interfaces:**
- Consumes: `R.drawable.ic_splash_foreground` selected by both launcher themes and the existing droplet path data `M50,7 C43,17 24,42 24,59 C24,80 35.65,95 50,95 C64.35,95 76,80 76,59 C76,42 57,17 50,7 Z`.
- Produces: the same resource id resolves to `InsetDrawable` on API 30 and `AnimatedVectorDrawable` on API 31+; the final AVD frame matches the existing black droplet.
- Preserves: adaptive launcher foreground/monochrome resources and pre-31 static splash.

- [ ] **Step 1: Add the failing API 31 animated-resource test**

Add the import:

```kotlin
import android.graphics.drawable.AnimatedVectorDrawable
```

Add:

```kotlin
@Test
@Config(sdk = [31], application = android.app.Application::class)
fun `android 12 splash foreground is an animated vector drawable`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    assertTrue(
        context.getDrawable(R.drawable.ic_splash_foreground) is AnimatedVectorDrawable,
    )
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew :app:testDemoDebugUnitTest \
  --tests 'com.gasstation.SplashThemeResourceTest.android 12 splash foreground is an animated vector drawable' \
  --warning-mode fail
```

Expected: FAIL because API 31 currently resolves the base inset drawable, not an `AnimatedVectorDrawable`.

- [ ] **Step 3: Create the named Signal Pulse vector**

Create `app/src/main/res/drawable-v31/ic_splash_signal_pulse_vector.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="100dp"
    android:height="100dp"
    android:viewportWidth="100"
    android:viewportHeight="100">
    <group
        android:name="artwork_group"
        android:pivotX="50"
        android:pivotY="50"
        android:scaleX="0.70"
        android:scaleY="0.70">
        <group
            android:name="ring_group"
            android:pivotX="50"
            android:pivotY="50"
            android:scaleX="0.75"
            android:scaleY="0.75">
            <path
                android:name="ring_path"
                android:fillColor="@android:color/transparent"
                android:pathData="M50,16 A34,34 0,1 1,49.99,16 Z"
                android:strokeAlpha="0"
                android:strokeColor="@color/ic_launcher_foreground_fill"
                android:strokeLineCap="round"
                android:strokeWidth="2.5" />
        </group>
        <group
            android:name="drop_group"
            android:pivotX="50"
            android:pivotY="50"
            android:scaleX="0.82"
            android:scaleY="0.82">
            <path
                android:name="drop_path"
                android:fillAlpha="0"
                android:fillColor="@color/ic_launcher_foreground_fill"
                android:pathData="M50,7 C43,17 24,42 24,59 C24,80 35.65,95 50,95 C64.35,95 76,80 76,59 C76,42 57,17 50,7 Z" />
        </group>
    </group>
</vector>
```

The outer `artwork_group` keeps the ring at its maximum `1.35` scale and the droplet inside the system splash safe circle. Do not alter `ic_brand_drop.xml`.

- [ ] **Step 4: Create the four exact animator resources**

Create `app/src/main/res/animator-v31/splash_drop_scale.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<objectAnimator xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="300">
    <propertyValuesHolder android:propertyName="scaleX">
        <keyframe android:fraction="0" android:value="0.82" />
        <keyframe android:fraction="0.3333" android:value="1.04" />
        <keyframe android:fraction="1" android:value="1" />
    </propertyValuesHolder>
    <propertyValuesHolder android:propertyName="scaleY">
        <keyframe android:fraction="0" android:value="0.82" />
        <keyframe android:fraction="0.3333" android:value="1.04" />
        <keyframe android:fraction="1" android:value="1" />
    </propertyValuesHolder>
</objectAnimator>
```

Create `app/src/main/res/animator-v31/splash_drop_alpha.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<objectAnimator xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="100"
    android:propertyName="fillAlpha"
    android:valueFrom="0"
    android:valueTo="1"
    android:valueType="floatType" />
```

Create `app/src/main/res/animator-v31/splash_ring_scale.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<objectAnimator xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="180"
    android:startOffset="80">
    <propertyValuesHolder
        android:propertyName="scaleX"
        android:valueFrom="0.75"
        android:valueTo="1.35"
        android:valueType="floatType" />
    <propertyValuesHolder
        android:propertyName="scaleY"
        android:valueFrom="0.75"
        android:valueTo="1.35"
        android:valueType="floatType" />
</objectAnimator>
```

Create `app/src/main/res/animator-v31/splash_ring_alpha.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<objectAnimator xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="180"
    android:startOffset="80">
    <propertyValuesHolder android:propertyName="strokeAlpha">
        <keyframe android:fraction="0" android:value="0" />
        <keyframe android:fraction="0.45" android:value="0.35" />
        <keyframe android:fraction="1" android:value="0" />
    </propertyValuesHolder>
</objectAnimator>
```

- [ ] **Step 5: Connect the AVD under the existing splash resource id**

Create `app/src/main/res/drawable-v31/ic_splash_foreground.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:drawable="@drawable/ic_splash_signal_pulse_vector">
    <target
        android:name="drop_group"
        android:animation="@animator/splash_drop_scale" />
    <target
        android:name="drop_path"
        android:animation="@animator/splash_drop_alpha" />
    <target
        android:name="ring_group"
        android:animation="@animator/splash_ring_scale" />
    <target
        android:name="ring_path"
        android:animation="@animator/splash_ring_alpha" />
</animated-vector>
```

- [ ] **Step 6: Process resources and verify GREEN**

Run:

```bash
./gradlew \
  :app:processDemoDebugResources \
  :app:processProdDebugResources \
  :app:testDemoDebugUnitTest \
  --tests 'com.gasstation.SplashThemeResourceTest' \
  --warning-mode fail
```

Expected: PASS. API 30 still resolves an inset/static drawable; API 31 resolves `AnimatedVectorDrawable`; AAPT accepts all named targets and animator properties.

- [ ] **Step 7: Commit the Signal Pulse resources**

```bash
git add \
  app/src/main/res/drawable-v31/ic_splash_foreground.xml \
  app/src/main/res/drawable-v31/ic_splash_signal_pulse_vector.xml \
  app/src/main/res/animator-v31/splash_drop_scale.xml \
  app/src/main/res/animator-v31/splash_drop_alpha.xml \
  app/src/main/res/animator-v31/splash_ring_scale.xml \
  app/src/main/res/animator-v31/splash_ring_alpha.xml \
  app/src/testDemo/java/com/gasstation/SplashThemeResourceTest.kt
git commit -m "feat: animate splash signal pulse"
```

---

### Task 3: Add the reduced-motion-safe exit transition

**Files:**
- Create: `app/src/main/java/com/gasstation/SplashExitAnimator.kt`
- Create: `app/src/testDemo/java/com/gasstation/SplashExitAnimatorTest.kt`
- Modify: `app/src/main/java/com/gasstation/MainActivity.kt:23-33`

**Interfaces:**
- Consumes: `androidx.core.splashscreen.SplashScreen`, `SplashScreenViewProvider.view`, `SplashScreenViewProvider.iconView`, and `Settings.Global.ANIMATOR_DURATION_SCALE`.
- Produces: `SplashExitAnimator.install(splashScreen: SplashScreen, context: Context): Unit` and internal test seam `animate(splashView: View, iconView: View, animationsEnabled: Boolean, onRemove: () -> Unit): AnimatorSet?`.
- Preserves: no splash hold condition and no dependency on startup hooks, feature state, permissions, location, preferences, or network.

- [ ] **Step 1: Write failing exit animator tests**

Create `app/src/testDemo/java/com/gasstation/SplashExitAnimatorTest.kt`:

```kotlin
package com.gasstation

import android.content.Context
import android.provider.Settings
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = android.app.Application::class)
class SplashExitAnimatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun restoreAnimatorScale() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }

    @Test
    fun `animations disabled removes splash immediately`() {
        var removals = 0

        val result = SplashExitAnimator().animate(
            splashView = View(context),
            iconView = View(context),
            animationsEnabled = false,
            onRemove = { removals += 1 },
        )

        assertNull(result)
        assertEquals(1, removals)
    }

    @Test
    fun `enabled exit uses exact duration and end values`() {
        val splashView = View(context)
        val iconView = View(context)
        var removals = 0

        val animator = SplashExitAnimator().animate(
            splashView = splashView,
            iconView = iconView,
            animationsEnabled = true,
            onRemove = { removals += 1 },
        )

        assertNotNull(animator)
        assertEquals(180L, animator?.duration)
        animator?.end()
        assertEquals(0f, splashView.alpha)
        assertEquals(1.06f, iconView.scaleX)
        assertEquals(1.06f, iconView.scaleY)
        assertEquals(1, removals)
    }

    @Test
    fun `cancel and end remove provider only once`() {
        var removals = 0
        val animator = requireNotNull(
            SplashExitAnimator().animate(
                splashView = View(context),
                iconView = View(context),
                animationsEnabled = true,
                onRemove = { removals += 1 },
            ),
        )

        animator.cancel()
        animator.end()

        assertEquals(1, removals)
    }

    @Test
    fun `system animator scale controls reduced motion`() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
        assertFalse(context.areSystemAnimationsEnabled())

        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        assertTrue(context.areSystemAnimationsEnabled())
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew :app:testDemoDebugUnitTest \
  --tests 'com.gasstation.SplashExitAnimatorTest' \
  --warning-mode fail
```

Expected: FAIL at compilation because `SplashExitAnimator` and `areSystemAnimationsEnabled` do not exist.

- [ ] **Step 3: Implement the exact exit animator and one-shot cleanup**

Create `app/src/main/java/com/gasstation/SplashExitAnimator.kt`:

```kotlin
package com.gasstation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.splashscreen.SplashScreen

private const val SPLASH_EXIT_DURATION_MILLIS = 180L
private const val SPLASH_EXIT_ICON_SCALE = 1.06f

internal class SplashExitAnimator(
    private val animationsEnabled: (Context) -> Boolean = Context::areSystemAnimationsEnabled,
) {
    fun install(splashScreen: SplashScreen, context: Context) {
        splashScreen.setOnExitAnimationListener { provider ->
            animate(
                splashView = provider.view,
                iconView = provider.iconView,
                animationsEnabled = animationsEnabled(context),
                onRemove = provider::remove,
            )
        }
    }

    internal fun animate(
        splashView: View,
        iconView: View,
        animationsEnabled: Boolean,
        onRemove: () -> Unit,
    ): AnimatorSet? {
        val cleanup = OneShotSplashRemoval(onRemove)
        if (!animationsEnabled) {
            cleanup.removeNow()
            return null
        }

        return AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, SPLASH_EXIT_ICON_SCALE),
                ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, SPLASH_EXIT_ICON_SCALE),
            )
            duration = SPLASH_EXIT_DURATION_MILLIS
            interpolator = DecelerateInterpolator()
            addListener(cleanup)
            start()
        }
    }
}

internal fun Context.areSystemAnimationsEnabled(): Boolean =
    Settings.Global.getFloat(
        contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) > 0f

private class OneShotSplashRemoval(private val onRemove: () -> Unit) : AnimatorListenerAdapter() {
    private var removed = false

    override fun onAnimationEnd(animation: Animator) {
        removeNow()
    }

    override fun onAnimationCancel(animation: Animator) {
        removeNow()
    }

    fun removeNow() {
        if (removed) return
        removed = true
        onRemove()
    }
}
```

- [ ] **Step 4: Connect the exit animator without adding a hold condition**

Change `MainActivity.onCreate()`:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen = installSplashScreen()
    applySystemBars()
    super.onCreate(savedInstanceState)
    SplashExitAnimator().install(splashScreen, this)
    setContent {
        GasStationTheme {
            GasStationNavHost(
                externalMapLauncher = externalMapLauncher,
                onStationListFirstContentDrawn = startupDrawReporter::reportFirstContentDrawn,
            )
        }
    }
}
```

Do not call `setKeepOnScreenCondition`.

- [ ] **Step 5: Run focused and app regression tests**

Run:

```bash
./gradlew :app:testDemoDebugUnitTest \
  --tests 'com.gasstation.SplashExitAnimatorTest' \
  --tests 'com.gasstation.SplashThemeResourceTest' \
  --warning-mode fail
./gradlew :app:testProdDebugUnitTest --warning-mode fail
```

Expected: both commands PASS. Demo tests prove the exact motion/resource contracts; the full prod unit-test task compiles and exercises the same production Activity path without applying demo-only test filters.

- [ ] **Step 6: Commit the exit transition**

```bash
git add \
  app/src/main/java/com/gasstation/SplashExitAnimator.kt \
  app/src/main/java/com/gasstation/MainActivity.kt \
  app/src/testDemo/java/com/gasstation/SplashExitAnimatorTest.kt
git commit -m "feat: smooth splash exit transition"
```

---

### Task 4: Close runtime, performance, documentation, and release evidence

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/test-strategy.md`
- Modify: `docs/verification-matrix.md`
- Modify: `CHANGELOG.md`
- Verify: `docs/performance.md`
- Verify: `README.md`

**Interfaces:**
- Consumes: Task 1 AndroidX starting theme, Task 2 AVD, Task 3 `SplashExitAnimator`, existing `StationListBenchmark.startupToFirstContent`.
- Produces: live documentation and device evidence proving API 30 static fallback, latest-API Signal Pulse, reduced motion, no blank frame, and no repeated 10% startup median regression.
- Preserves: historical Samsung Galaxy S20+ numbers in `docs/performance.md` and README unless a new physical-device evidence run is explicitly performed.

- [ ] **Step 1: Add exact live documentation for the new launch contract**

Add a `Launch splash` paragraph immediately before `## 런타임 흐름` in `docs/architecture.md`:

```markdown
## Launch splash

`MainActivity`는 `super.onCreate()` 직전에 AndroidX `installSplashScreen()`을 호출합니다. API 24–30은 launcher yellow와 정적 검정 물방울을, API 31 이상은 같은 final symbol의 300ms `Signal Pulse` AVD를 사용합니다. 첫 Activity frame이 준비되면 app-owned `SplashExitAnimator`가 180ms fade/scale exit를 적용하며, system animator scale이 0이면 즉시 제거합니다. Splash는 permission, location, demo seed, preferences, network readiness를 기다리지 않습니다.
```

In the `app` row of `docs/test-strategy.md`, extend the splash evidence description with:

```markdown
`SplashThemeResourceTest`는 API 30/31, day/night, static/AVD, post-theme resource contract를 보호하고, `SplashExitAnimatorTest`는 180ms exit와 animations-off 즉시 제거·one-shot cleanup을 보호합니다. 실제 clipping과 blank-frame 여부는 API 30/latest emulator cold-launch evidence가 소유합니다.
```

Add a `Splash Signal Pulse` subsection to the focused verification area of `docs/verification-matrix.md`:

````markdown
### Splash Signal Pulse

```bash
./gradlew \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :app:processDemoDebugResources \
  :app:processProdDebugResources \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  :app:assembleDemoRelease \
  :app:assembleProdRelease \
  --warning-mode fail
```

API 30과 최신 API emulator에서 cold launch를 녹화하고 기본 animator scale과 0배를 각각 확인합니다. API 30은 정적 물방울, API 31 이상은 one-shot 300ms AVD를 사용하며 두 경로 모두 180ms exit 또는 animations-off 즉시 제거를 사용합니다. 두 API runtime evidence가 없으면 splash version-parity 완료를 주장하지 않습니다.
````

Add under `CHANGELOG.md` → `Unreleased` → `사용자 영향`:

```markdown
- Android 버전별 스플래시를 AndroidX 계약으로 통일하고, Android 12 이상에서 시작을 지연하지 않는 짧은 Signal Pulse와 reduced-motion-safe 종료 전환을 추가했습니다.
```

Do not replace the existing performance numbers in README or `docs/performance.md`.

- [ ] **Step 2: Run the full host/build gate**

Run:

```bash
scripts/agent/check-contracts.sh
./gradlew \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :app:processDemoDebugResources \
  :app:processProdDebugResources \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  :app:assembleDemoRelease \
  :app:assembleProdRelease \
  --warning-mode fail
scripts/agent/verify.sh docs
git diff --check
```

Expected: every command exits 0. Release builds retain splash resources under R8; resource shrinking remains disabled.

- [ ] **Step 3: Provision the API 30 verification target if it is absent**

Check:

```bash
GASSTATION_SDK_ROOT="$(sed -n 's/^sdk.dir=//p' local.properties)"
"$GASSTATION_SDK_ROOT/emulator/emulator" -list-avds
find "$GASSTATION_SDK_ROOT/system-images/android-30" -maxdepth 3 -type d 2>/dev/null
```

If `GasStation_API_30` and the API 30 ARM64 Google APIs image are absent, install Android 11 (R), API 30, Google APIs ARM64 system image through Android Studio SDK Manager, accepting the Android SDK license in the user-owned SDK Manager UI. Then create an AVD named exactly `GasStation_API_30` with Pixel 4 hardware. Do not silently accept a new SDK license from an unattended shell.

Re-run:

```bash
GASSTATION_SDK_ROOT="$(sed -n 's/^sdk.dir=//p' local.properties)"
"$GASSTATION_SDK_ROOT/emulator/emulator" -list-avds | rg '^GasStation_API_30$'
```

Expected: exactly one matching AVD. If the license or image cannot be obtained, stop and report API 30 runtime evidence as the blocking requirement; do not mark the plan complete.

- [ ] **Step 4: Record API 30 static-fallback runtime evidence**

Start `GasStation_API_30` in a persistent terminal:

```bash
GASSTATION_SDK_ROOT="$(sed -n 's/^sdk.dir=//p' local.properties)"
"$GASSTATION_SDK_ROOT/emulator/emulator" \
  @GasStation_API_30 \
  -no-snapshot-save \
  -no-boot-anim
```

After boot:

```bash
GASSTATION_SDK_ROOT="$(sed -n 's/^sdk.dir=//p' local.properties)"
GASSTATION_API30_SERIAL=""
for GASSTATION_CANDIDATE_SERIAL in $(
  "$GASSTATION_SDK_ROOT/platform-tools/adb" devices |
    awk 'NR > 1 && $2 == "device" { print $1 }'
); do
  GASSTATION_AVD_NAME="$(
    "$GASSTATION_SDK_ROOT/platform-tools/adb" \
      -s "$GASSTATION_CANDIDATE_SERIAL" emu avd name 2>/dev/null |
      tr -d '\r' |
      sed -n '1p'
  )"
  if [ "$GASSTATION_AVD_NAME" = "GasStation_API_30" ]; then
    GASSTATION_API30_SERIAL="$GASSTATION_CANDIDATE_SERIAL"
    break
  fi
done
test -n "$GASSTATION_API30_SERIAL"
ANDROID_SERIAL="$GASSTATION_API30_SERIAL" ./gradlew :app:installDemoDebug --warning-mode fail
mkdir -p app/build/reports
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_API30_SERIAL" shell am force-stop com.gasstation.demo
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_API30_SERIAL" shell screenrecord \
  --size 720x1280 \
  --time-limit 5 \
  /sdcard/gasstation-api30-splash.mp4 &
GASSTATION_RECORD_PID=$!
sleep 0.5
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_API30_SERIAL" shell am start \
  -S -W -n com.gasstation.demo/com.gasstation.MainActivity
wait "$GASSTATION_RECORD_PID"
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_API30_SERIAL" pull \
  /sdcard/gasstation-api30-splash.mp4 \
  app/build/reports/gasstation-api30-splash.mp4
```

Repeat after setting animator scale to zero:

```bash
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_API30_SERIAL" shell settings put global animator_duration_scale 0
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_API30_SERIAL" shell am force-stop com.gasstation.demo
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_API30_SERIAL" shell screenrecord \
  --size 720x1280 \
  --time-limit 3 \
  /sdcard/gasstation-api30-reduced-motion.mp4 &
GASSTATION_RECORD_PID=$!
sleep 0.5
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_API30_SERIAL" shell am start \
  -S -W -n com.gasstation.demo/com.gasstation.MainActivity
wait "$GASSTATION_RECORD_PID"
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_API30_SERIAL" pull \
  /sdcard/gasstation-api30-reduced-motion.mp4 \
  app/build/reports/gasstation-api30-reduced-motion.mp4
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_API30_SERIAL" shell settings put global animator_duration_scale 1
```

Inspect the recording and confirm: static black droplet, launcher yellow, no blank frame, no clipping, no residual splash, and immediate exit at animator scale 0.

- [ ] **Step 5: Record latest-API AVD and reduced-motion runtime evidence**

Start the existing `Pixel_8` API 37 AVD in a persistent terminal:

```bash
GASSTATION_SDK_ROOT="$(sed -n 's/^sdk.dir=//p' local.properties)"
"$GASSTATION_SDK_ROOT/emulator/emulator" \
  @Pixel_8 \
  -no-snapshot-save \
  -no-boot-anim \
  -gpu swiftshader_indirect
```

After boot, resolve that AVD by name, install demo debug, and record the default-motion launch:

```bash
GASSTATION_SDK_ROOT="$(sed -n 's/^sdk.dir=//p' local.properties)"
GASSTATION_LATEST_SERIAL=""
for GASSTATION_CANDIDATE_SERIAL in $(
  "$GASSTATION_SDK_ROOT/platform-tools/adb" devices |
    awk 'NR > 1 && $2 == "device" { print $1 }'
); do
  GASSTATION_AVD_NAME="$(
    "$GASSTATION_SDK_ROOT/platform-tools/adb" \
      -s "$GASSTATION_CANDIDATE_SERIAL" emu avd name 2>/dev/null |
      tr -d '\r' |
      sed -n '1p'
  )"
  if [ "$GASSTATION_AVD_NAME" = "Pixel_8" ]; then
    GASSTATION_LATEST_SERIAL="$GASSTATION_CANDIDATE_SERIAL"
    break
  fi
done
test -n "$GASSTATION_LATEST_SERIAL"
ANDROID_SERIAL="$GASSTATION_LATEST_SERIAL" ./gradlew :app:installDemoDebug --warning-mode fail
mkdir -p app/build/reports
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_LATEST_SERIAL" shell settings put global animator_duration_scale 1
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_LATEST_SERIAL" shell am force-stop com.gasstation.demo
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_LATEST_SERIAL" shell screenrecord \
  --size 720x1280 \
  --time-limit 5 \
  /sdcard/gasstation-api37-splash.mp4 &
GASSTATION_RECORD_PID=$!
sleep 0.5
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_LATEST_SERIAL" shell am start \
  -S -W -n com.gasstation.demo/com.gasstation.MainActivity
wait "$GASSTATION_RECORD_PID"
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_LATEST_SERIAL" pull \
  /sdcard/gasstation-api37-splash.mp4 \
  app/build/reports/gasstation-api37-splash.mp4
```

Record the same launch with system animations disabled, then restore the setting:

```bash
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_LATEST_SERIAL" shell settings put global animator_duration_scale 0
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_LATEST_SERIAL" shell am force-stop com.gasstation.demo
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_LATEST_SERIAL" shell screenrecord \
  --size 720x1280 \
  --time-limit 3 \
  /sdcard/gasstation-api37-reduced-motion.mp4 &
GASSTATION_RECORD_PID=$!
sleep 0.5
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_LATEST_SERIAL" shell am start \
  -S -W -n com.gasstation.demo/com.gasstation.MainActivity
wait "$GASSTATION_RECORD_PID"
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_LATEST_SERIAL" pull \
  /sdcard/gasstation-api37-reduced-motion.mp4 \
  app/build/reports/gasstation-api37-reduced-motion.mp4
"$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_LATEST_SERIAL" shell settings put global animator_duration_scale 1
```

Confirm: one Signal Pulse, no loop, final black droplet, no blank frame, no clipping, no residual splash, and immediate exit at animator scale 0. Generated videos remain build evidence and are not committed.

- [ ] **Step 6: Capture the after-change benchmark and compare medians**

Run the same startup-only benchmark used in Task 1:

```bash
GASSTATION_SDK_ROOT="$(sed -n 's/^sdk.dir=//p' local.properties)"
GASSTATION_LATEST_SERIAL=""
for GASSTATION_CANDIDATE_SERIAL in $(
  "$GASSTATION_SDK_ROOT/platform-tools/adb" devices |
    awk 'NR > 1 && $2 == "device" { print $1 }'
); do
  GASSTATION_AVD_NAME="$(
    "$GASSTATION_SDK_ROOT/platform-tools/adb" \
      -s "$GASSTATION_CANDIDATE_SERIAL" emu avd name 2>/dev/null |
      tr -d '\r' |
      sed -n '1p'
  )"
  if [ "$GASSTATION_AVD_NAME" = "Pixel_8" ]; then
    GASSTATION_LATEST_SERIAL="$GASSTATION_CANDIDATE_SERIAL"
    break
  fi
done
test -n "$GASSTATION_LATEST_SERIAL"
ANDROID_SERIAL="$GASSTATION_LATEST_SERIAL" ./gradlew \
  :app:installDemoBenchmark \
  :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.gasstation.benchmark.StationListBenchmark#startupToFirstContent \
  --warning-mode fail
GASSTATION_AFTER_JSON="$(
  find benchmark/build/outputs/connected_android_test_additional_output \
    -name '*benchmarkData.json' -type f -exec stat -f '%m %N' {} + |
    sort -nr |
    sed -n '1s/^[0-9]* //p'
)"
test -n "$GASSTATION_AFTER_JSON"
cp "$GASSTATION_AFTER_JSON" /tmp/gasstation-splash-signal-pulse-after.json
```

Read both JSON files, calculate both median regressions, and fail the step if either exceeds 10%:

```bash
jq -e -s '
  def startup:
    .benchmarks[] | select(.name == "startupToFirstContent");
  def medians:
    {
      initial: .metrics.timeToInitialDisplayMs.median,
      full: .metrics.timeToFullDisplayMs.median
    };
  {
    before: (.[0] | startup | medians),
    after: (.[1] | startup | medians)
  } as $result |
  $result + {
    regressionPercent: {
      initial: ((($result.after.initial / $result.before.initial) - 1) * 100),
      full: ((($result.after.full / $result.before.full) - 1) * 100)
    }
  } |
  if (
    .regressionPercent.initial > 10 or
    .regressionPercent.full > 10
  ) then
    error("startup median regression exceeded 10 percent")
  else
    .
  end
' \
  /tmp/gasstation-splash-signal-pulse-before.json \
  /tmp/gasstation-splash-signal-pulse-after.json
```

Expected: neither median repeatedly regresses by more than 10% under the same emulator/build/compilation conditions. If either exceeds 10%, rerun once after cooling/idle; if the regression repeats, shorten or remove the custom exit before continuing.

- [ ] **Step 7: Run the canonical final gate**

Run:

```bash
scripts/agent/verify.sh auto
scripts/agent/check-contracts.sh
git diff --check
git status --short
```

Expected: all gates pass. Only intended production, test, resource, live-doc, and changelog files are modified.

- [ ] **Step 8: Commit documentation and evidence contract**

Generated videos and benchmark JSON remain untracked under build output or `/tmp`. Commit only authored live documents:

```bash
git add \
  docs/architecture.md \
  docs/test-strategy.md \
  docs/verification-matrix.md \
  CHANGELOG.md
git commit -m "docs: document splash signal pulse verification"
```

- [ ] **Step 9: Final review**

Review the complete diff and commit series:

```bash
git log --oneline --decorate -6
git diff 9f85f47..HEAD --stat
git diff 9f85f47..HEAD --check
rg -n \
  'setKeepOnScreenCondition|Thread\\.sleep|delay\\(|windowSplashScreenAnimationDuration' \
  app/src/main app/build.gradle.kts
```

Expected:

- four narrow implementation commits;
- no splash hold or deprecated duration attribute;
- no changes under feature/domain/data/designsystem;
- API 30 and latest runtime recordings inspected;
- before/after startup medians within the approved threshold;
- working tree clean.
