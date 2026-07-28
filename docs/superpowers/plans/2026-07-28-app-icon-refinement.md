# Refined App Icon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the rough splash and launcher droplet with a GPT Image-derived, vector-normalized refined droplet that stays crisp across Android splash, adaptive, monochrome, and legacy launcher surfaces.

**Architecture:** `app` continues to own all launch chrome. A reviewed GPT Image source is normalized into one SVG master; Android static splash, API 31+ AVD, adaptive foreground, monochrome icon, and legacy mipmaps derive from that geometry while existing resource IDs, AndroidX SplashScreen lifecycle, and exit animation contracts remain stable.

**Tech Stack:** GPT Image 2.0, Android vector drawables and AnimatedVectorDrawable, AndroidX SplashScreen 1.2.0, Robolectric/JUnit 4, Gradle 9.6.1, Android API 30 and API 37 emulators, Macrobenchmark, Roborazzi, Playwright, `sips`, ADB.

## Global Constraints

- Work from approved spec `docs/superpowers/specs/2026-07-28-app-icon-refinement-design.md` at or after baseline commit `5532693`.
- Run `git status --short` and `scripts/agent/preflight.sh` before implementation; preserve unrelated user changes.
- Use the `imagegen` skill before invoking the image generation tool.
- Visual direction is `A. 정제된 물방울`; do not reopen price-pin or Urban Signal ring concepts.
- Palette is exact yellow `#FFDC00`, black `#222222`, and optional white `#FFFFFF`.
- Do not add text, currency symbols, maps, pumps, gradients, shadows, 3D, photo texture, or a device/mockup frame.
- Keep resource IDs `@mipmap/ic_launcher` and `@drawable/ic_splash_foreground`.
- API 24–30 uses a static final frame; API 31+ uses one alpha/scale settle animation of at most 300ms.
- Remove the existing signal ring; do not add loops, translation, rotation, bounce, splash hold conditions, or startup delay.
- Preserve the 180ms `SplashExitAnimator` fade/scale, animations-off immediate removal, demo/prod parity, manifest entry point, and startup reporting.
- `app` owns runtime icon resources; do not change `core:designsystem`, `feature:*`, `domain:*`, or `data:*` unless the icon audit produces reproducible failure evidence.
- Do not AI-regenerate real station-brand trademarks.
- Modern splash/adaptive resources must be XML/vector based; only API 24–25 legacy launcher resources remain density PNGs.
- A startup median regression above 10% must be investigated and may not be waived after one idle/cool rerun.
- Do not push, open a PR, tag, release, or deploy unless separately requested.

---

## File Structure

### New files

- `docs/design-assets/app-icon/refined-droplet-source.png` — selected high-resolution GPT Image output.
- `docs/design-assets/app-icon/refined-droplet-master.svg` — one-path, flat-color vector normalization of the selected output.
- `docs/design-assets/app-icon/README.md` — exact prompt, generation provenance, selection criteria, palette, safe-zone, and derivation notes.
- `docs/design-assets/app-icon/icon-audit.md` — evidence-backed decision for launcher/splash, station-brand, Material, and custom Canvas icons.
- `app/src/testDemo/java/com/gasstation/AppIconSourceContractTest.kt` — source-level palette, shared-geometry, no-ring, and legacy mipmap contracts.

### Modified files

- `app/src/main/res/values/colors.xml` — exact Urban Signal launcher colors.
- `app/src/main/res/drawable/ic_brand_drop.xml` — normalized shared drop group/path.
- `app/src/main/res/drawable/ic_brand_drop_monochrome.xml` — same silhouette as a tintable monochrome mask.
- `app/src/main/res/drawable/ic_splash_foreground.xml` — smaller static inset wrapper.
- `app/src/main/res/drawable-v31/ic_splash_foreground.xml` — drop-only AVD targeting the shared vector.
- `app/src/main/res/animator-v31/splash_drop_scale.xml` — restrained settle keyframes.
- `app/src/main/res/animator-v31/splash_drop_alpha.xml` — drop reveal timing.
- `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` — legacy launcher render at 48/72/96/144/192px.
- `app/src/testDemo/java/com/gasstation/AppIconResourceTest.kt` — modern adaptive/monochrome non-bitmap smoke coverage.
- `docs/architecture.md` — refined-drop AVD ownership and shared geometry.
- `docs/test-strategy.md` — source contract plus API 30/API 37 evidence role.
- `docs/verification-matrix.md` — focused commands and runtime evidence procedure.
- `CHANGELOG.md` — user-visible icon refinement.

### Deleted files

- `app/src/main/res/drawable-v31/ic_splash_signal_pulse_vector.xml` — duplicated ring/drop vector superseded by shared `ic_brand_drop`.
- `app/src/main/res/animator-v31/splash_ring_alpha.xml` — ring-only animator.
- `app/src/main/res/animator-v31/splash_ring_scale.xml` — ring-only animator.

---

### Task 1: Generate, select, and normalize the refined-droplet master

**Files:**
- Create: `docs/design-assets/app-icon/refined-droplet-source.png`
- Create: `docs/design-assets/app-icon/refined-droplet-master.svg`
- Create: `docs/design-assets/app-icon/README.md`

**Interfaces:**
- Consumes: approved visual direction, exact palette, and Android circle/squircle safe-zone requirements from the spec.
- Produces: a selected square PNG and an SVG with `viewBox="0 0 100 100"`, exactly one closed silhouette path named `refined-drop`, no transforms, and path bounds contained within `x=28..72`, `y=14..86`.

- [ ] **Step 1: Confirm the execution checkout and asset directory**

Run:

```bash
git status --short
scripts/agent/preflight.sh
test "$(git rev-parse --abbrev-ref HEAD)" != "main" || echo "Create the execution worktree before production changes"
mkdir -p docs/design-assets/app-icon
```

Expected: preflight passes, the intended execution branch/worktree is identified, and no unrelated dirty path is present.

- [ ] **Step 2: Generate the high-resolution source with GPT Image**

Invoke the `imagegen` skill and use this exact generation prompt:

```text
Create a square, high-resolution Android app icon source for a Korean fuel-price comparison app named GasStation. Use a flat geometric vector-like style: one refined black fuel droplet centered on a solid Urban Signal yellow field. The droplet must have balanced bezier curves, a softly resolved top point, a stable rounded lower bowl, generous negative space, and a professional transportation-utility identity. Use only exact-looking yellow #FFDC00 and black #222222. No text, letters, won symbol, map pin, gas pump, signal ring, gradient, shadow, glow, 3D, photographic texture, border, rounded-square mask, device frame, or mockup. Keep the droplet comfortably inside Android adaptive icon circle and squircle safe zones and readable at 48 pixels.
```

Generate one result first. If the result violates any negative constraint, edit that result rather than broadening the concept. Save the accepted output as `docs/design-assets/app-icon/refined-droplet-source.png`.

- [ ] **Step 3: Inspect the source before tracing**

Run:

```bash
file docs/design-assets/app-icon/refined-droplet-source.png
sips -g pixelWidth -g pixelHeight docs/design-assets/app-icon/refined-droplet-source.png
```

Expected: a square PNG of at least 1024×1024 with one centered black droplet, flat yellow field, no text, no ring, and no baked launcher mask.

- [ ] **Step 4: Normalize the selected silhouette into one SVG path**

Trace the selected silhouette with these locked rules:

- SVG `viewBox` is `0 0 100 100`.
- The black silhouette is one closed `M/C/Z` path with no stroke and `fill="#222222"`.
- The path is named `refined-drop`.
- Its visible bounds remain within `x=28..72`, `y=14..86`.
- Remove small AI edge noise and omit the optional highlight if it is not readable at 48px.
- Do not include the yellow background in the SVG; Android owns the background as a color resource.

Save the result as `docs/design-assets/app-icon/refined-droplet-master.svg`. This file is the sole geometry interface for Tasks 2 and 3: Android `android:pathData` is copied verbatim from its `d` attribute.

- [ ] **Step 5: Write provenance and derivation notes**

Create `docs/design-assets/app-icon/README.md` with this structure:

```markdown
# Refined Droplet App Icon Source

## Direction

Approved direction: A, refined droplet.

## Generation

- Tool: GPT Image 2.0 through the Codex image generation tool
- Generated at: 2026-07-28, Asia/Seoul
- Source: `refined-droplet-source.png`
- Normalized vector: `refined-droplet-master.svg`

## Prompt

Create a square, high-resolution Android app icon source for a Korean fuel-price comparison app named GasStation. Use a flat geometric vector-like style: one refined black fuel droplet centered on a solid Urban Signal yellow field. The droplet must have balanced bezier curves, a softly resolved top point, a stable rounded lower bowl, generous negative space, and a professional transportation-utility identity. Use only exact-looking yellow #FFDC00 and black #222222. No text, letters, won symbol, map pin, gas pump, signal ring, gradient, shadow, glow, 3D, photographic texture, border, rounded-square mask, device frame, or mockup. Keep the droplet comfortably inside Android adaptive icon circle and squircle safe zones and readable at 48 pixels.

## Selection and normalization

- One centered droplet; no text, pump, map pin, price mark, ring, gradient, or shadow.
- Runtime palette is fixed by Android resources to `#FFDC00` and `#222222`.
- SVG contains one closed silhouette path in a `0 0 100 100` viewBox.
- Path bounds stay inside x 28–72 and y 14–86 for splash/adaptive safe area.
- Runtime resources derive from the SVG; the generated PNG is provenance, not a runtime dependency.
```

- [ ] **Step 6: Review the master at target sizes**

Inspect the PNG at full size and render/zoom the SVG at 48, 96, 192, and splash-preview sizes. Reject the master if the top point collapses, the lower bowl becomes asymmetric, or the symbol touches a circle/squircle crop.

Expected: the same silhouette remains recognizable and centered at every size.

- [ ] **Step 7: Commit the reviewed source**

```bash
git add docs/design-assets/app-icon
git diff --cached --check
git commit -m "art: add refined droplet icon source"
```

Expected: only the PNG, SVG, and provenance README are committed.

---

### Task 2: Replace splash geometry and motion under source-contract tests

**Files:**
- Create: `app/src/testDemo/java/com/gasstation/AppIconSourceContractTest.kt`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/drawable/ic_brand_drop.xml`
- Modify: `app/src/main/res/drawable/ic_brand_drop_monochrome.xml`
- Modify: `app/src/main/res/drawable/ic_splash_foreground.xml`
- Modify: `app/src/main/res/drawable-v31/ic_splash_foreground.xml`
- Modify: `app/src/main/res/animator-v31/splash_drop_scale.xml`
- Modify: `app/src/main/res/animator-v31/splash_drop_alpha.xml`
- Delete: `app/src/main/res/drawable-v31/ic_splash_signal_pulse_vector.xml`
- Delete: `app/src/main/res/animator-v31/splash_ring_alpha.xml`
- Delete: `app/src/main/res/animator-v31/splash_ring_scale.xml`
- Test: `app/src/testDemo/java/com/gasstation/SplashThemeResourceTest.kt`
- Test: `app/src/testDemo/java/com/gasstation/SplashExitAnimatorTest.kt`

**Interfaces:**
- Consumes: `refined-droplet-master.svg` with one path named `refined-drop`.
- Produces: `@drawable/ic_brand_drop` containing named `drop_group` and `drop_path`; API 31+ AVD targets only those names; static splash and launcher wrappers reuse that drawable.
- Preserves: `R.drawable.ic_splash_foreground`, `R.color.ic_launcher_background`, `SplashExitAnimator.animate(...)`, and all manifest/theme IDs.

- [ ] **Step 1: Capture the before-change API 37 startup baseline**

Resolve the existing `Pixel_8` AVD and run only the 10-iteration cold-start benchmark:

```bash
GASSTATION_SDK_ROOT="$(sed -n 's/^sdk.dir=//p' local.properties)"
GASSTATION_API37_SERIAL=""
for GASSTATION_CANDIDATE_SERIAL in $(
  "$GASSTATION_SDK_ROOT/platform-tools/adb" devices |
    awk 'NR > 1 && $2 == "device" { print $1 }'
); do
  if [ "$("$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_CANDIDATE_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')" = "37" ]; then
    GASSTATION_API37_SERIAL="$GASSTATION_CANDIDATE_SERIAL"
    break
  fi
done
test -n "$GASSTATION_API37_SERIAL"
ANDROID_SERIAL="$GASSTATION_API37_SERIAL" ./gradlew \
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
cp "$GASSTATION_BEFORE_JSON" /tmp/gasstation-app-icon-before.json
```

Expected: 10 cold starts complete and `/tmp/gasstation-app-icon-before.json` exists. If no API 37 device is connected, start the existing `Pixel_8` AVD before continuing.

- [ ] **Step 2: Write the failing source contract**

Create `AppIconSourceContractTest.kt`:

```kotlin
package com.gasstation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppIconSourceContractTest {
    @Test
    fun `launcher palette uses exact urban signal tokens`() {
        val colors = projectFile("app/src/main/res/values/colors.xml").readText()

        assertTrue(colors.contains("""<color name="ic_launcher_background">#FFDC00</color>"""))
        assertTrue(colors.contains("""<color name="ic_launcher_foreground_fill">#FF222222</color>"""))
        assertFalse(colors.contains("#FFFED70A"))
        assertFalse(colors.contains("#FF111111"))
    }

    @Test
    fun `static animated and monochrome icons share one refined silhouette`() {
        val colorVector = projectFile("app/src/main/res/drawable/ic_brand_drop.xml").readText()
        val monochromeVector = projectFile("app/src/main/res/drawable/ic_brand_drop_monochrome.xml").readText()
        val avd = projectFile("app/src/main/res/drawable-v31/ic_splash_foreground.xml").readText()

        assertTrue(colorVector.contains("""android:name="drop_group""""))
        assertTrue(colorVector.contains("""android:name="drop_path""""))
        assertEquals(pathData(colorVector), pathData(monochromeVector))
        assertTrue(avd.contains("""android:drawable="@drawable/ic_brand_drop""""))
        assertFalse(avd.contains("ring"))
        assertFalse(projectFileExists("app/src/main/res/drawable-v31/ic_splash_signal_pulse_vector.xml"))
        assertFalse(projectFileExists("app/src/main/res/animator-v31/splash_ring_alpha.xml"))
        assertFalse(projectFileExists("app/src/main/res/animator-v31/splash_ring_scale.xml"))
    }

    private fun pathData(xml: String): String = requireNotNull(
        Regex("""android:pathData="([^"]+)"""").find(xml),
    ).groupValues[1]

    private fun projectFile(path: String): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(workingDirectory) { it.parentFile }
            .map { File(it, path) }
            .firstOrNull(File::exists)
            ?: error("Could not find project file: $path from $workingDirectory")
    }

    private fun projectFileExists(path: String): Boolean {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(workingDirectory) { it.parentFile }
            .map { File(it, path) }
            .any(File::exists)
    }
}
```

- [ ] **Step 3: Run the contract and verify RED**

```bash
./gradlew \
  :app:testDemoDebugUnitTest \
  --tests com.gasstation.AppIconSourceContractTest \
  --warning-mode fail
```

Expected: FAIL because the old colors and ring resources still exist and the AVD does not target `@drawable/ic_brand_drop`.

- [ ] **Step 4: Implement the exact palette and shared vector**

Update `colors.xml` to:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#FFDC00</color>
    <color name="ic_launcher_foreground_fill">#FF222222</color>
</resources>
```

Copy the single `d` value from `refined-droplet-master.svg` verbatim into both `ic_brand_drop.xml` and `ic_brand_drop_monochrome.xml`. The color vector must contain `drop_group` and `drop_path`; monochrome uses the same path with `@android:color/white`. Do not approximate a second path by hand.

Set the static `ic_splash_foreground.xml` wrapper to 28dp inset on all four sides so the shared geometry is smaller than the current splash.

- [ ] **Step 5: Replace the ring AVD with a drop-only settle**

Make `drawable-v31/ic_splash_foreground.xml` target `@drawable/ic_brand_drop`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:drawable="@drawable/ic_brand_drop">
    <target
        android:name="drop_group"
        android:animation="@animator/splash_drop_scale" />
    <target
        android:name="drop_path"
        android:animation="@animator/splash_drop_alpha" />
</animated-vector>
```

Use these exact settle keyframes in `splash_drop_scale.xml`:

```xml
<keyframe android:fraction="0" android:value="0.94" />
<keyframe android:fraction="0.3333" android:value="1.02" />
<keyframe android:fraction="1" android:value="1" />
```

Keep the total scale duration at 300ms and the alpha duration at 100ms. Delete the ring vector and both ring animator files.

- [ ] **Step 6: Run focused GREEN tests and resource processing**

```bash
./gradlew \
  :app:testDemoDebugUnitTest \
  --tests com.gasstation.AppIconSourceContractTest \
  --tests com.gasstation.SplashThemeResourceTest \
  --tests com.gasstation.SplashExitAnimatorTest \
  :app:processDemoDebugResources \
  :app:processProdDebugResources \
  --warning-mode fail
```

Expected: all source, theme, exit, and resource-processing checks pass; no missing ring reference remains.

- [ ] **Step 7: Commit the shared splash resource**

```bash
git add \
  app/src/main/res/values/colors.xml \
  app/src/main/res/drawable \
  app/src/main/res/drawable-v31 \
  app/src/main/res/animator-v31 \
  app/src/testDemo/java/com/gasstation/AppIconSourceContractTest.kt
git diff --cached --check
git commit -m "feat: refine splash droplet icon"
```

---

### Task 3: Regenerate adaptive, monochrome, and legacy launcher variants

**Files:**
- Modify: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Modify: `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modify: `app/src/main/res/mipmap-mdpi/ic_launcher.png`
- Modify: `app/src/main/res/mipmap-hdpi/ic_launcher.png`
- Modify: `app/src/main/res/mipmap-xhdpi/ic_launcher.png`
- Modify: `app/src/main/res/mipmap-xxhdpi/ic_launcher.png`
- Modify: `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`
- Modify: `app/src/testDemo/java/com/gasstation/AppIconSourceContractTest.kt`
- Modify: `app/src/testDemo/java/com/gasstation/AppIconResourceTest.kt`

**Interfaces:**
- Consumes: shared color and monochrome vectors from Task 2 and the selected 1024px-or-larger source PNG from Task 1.
- Produces: XML modern launcher foregrounds and legacy PNGs at exactly 48, 72, 96, 144, and 192px.

- [ ] **Step 1: Add failing legacy replacement and monochrome smoke tests**

Add these imports and members to `AppIconSourceContractTest`:

```kotlin
import org.junit.Assert.assertNotNull
import java.security.MessageDigest
import javax.imageio.ImageIO

@Test
fun `legacy launcher pngs are regenerated at canonical densities`() {
    val oldHashes = setOf(
        "0a50c3a058b382319ca5c2bcd3d72e682dc41f3eb80db2c29a2d43267e95faa7",
        "97ae665b121e5c0ef66ffccee8809fb48c4c34e9db171aab8b9be78f647a0db2",
        "82abb7f6f27e575830b6c4c04b415edc6e388fff172bf63dae4104e393a00dc5",
        "b790283d9fcc5ba770dc9e4b496ab12c9bef010a10434ad18bc0b358333a392a",
        "5a1660bc3a8967bb60d85de229a3e8e603a5eaed53520044d0b5f59131831197",
    )
    val expectedSizes = mapOf(
        "mdpi" to 48,
        "hdpi" to 72,
        "xhdpi" to 96,
        "xxhdpi" to 144,
        "xxxhdpi" to 192,
    )

    expectedSizes.forEach { (density, expectedSize) ->
        val file = projectFile("app/src/main/res/mipmap-$density/ic_launcher.png")
        val image = ImageIO.read(file)
        assertNotNull("$density launcher must decode", image)
        assertEquals("$density width", expectedSize, image.width)
        assertEquals("$density height", expectedSize, image.height)
        assertFalse("$density launcher still uses the old asset", sha256(file) in oldHashes)
    }
}

private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes())
    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
```

Extend `AppIconResourceTest` with:

```kotlin
@Test
@Config(sdk = [33])
fun `themed launcher monochrome is vector backed`() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val drawable = context.getDrawable(R.drawable.ic_launcher_monochrome)

    assertNotNull(drawable)
    assertFalse(
        "ic_launcher_monochrome should remain vector or xml based",
        drawable is BitmapDrawable,
    )
}
```

- [ ] **Step 2: Run the tests and verify RED**

```bash
./gradlew \
  :app:testDemoDebugUnitTest \
  --tests com.gasstation.AppIconSourceContractTest \
  --tests com.gasstation.AppIconResourceTest \
  --warning-mode fail
```

Expected: FAIL because the five legacy PNG hashes still match the old assets.

- [ ] **Step 3: Keep modern wrappers vector-backed**

Keep `ic_launcher_foreground.xml` pointing to `@drawable/ic_brand_drop` and `ic_launcher_monochrome.xml` pointing to `@drawable/ic_brand_drop_monochrome`. Start with symmetric 18dp insets; change only the wrapper inset if circle/squircle preview clips or appears optically off-center. Do not duplicate path data in either wrapper.

- [ ] **Step 4: Produce the exact-color 1024px legacy render**

Read the `playwright` skill, then use `node_repl` to render the committed SVG over an exact yellow background:

```javascript
var iconFs = await import("node:fs/promises");
var iconPath = await import("node:path");
var iconPlaywright = await import("playwright");
var iconProjectDir = nodeRepl.cwd;
var iconSvgPath = iconPath.join(iconProjectDir, "docs/design-assets/app-icon/refined-droplet-master.svg");
var iconOutputDir = iconPath.join(iconProjectDir, "app/build/reports/app-icon-refinement");
var iconMasterSvg = await iconFs.readFile(iconSvgPath, "utf8");
var iconLegacySvg = iconMasterSvg.replace(
  /(<svg[^>]*>)/,
  '$1<rect x="0" y="0" width="100" height="100" fill="#FFDC00"/>'
);
await iconFs.mkdir(iconOutputDir, { recursive: true });
var iconBrowser = await iconPlaywright.chromium.launch({ headless: true });
var iconPage = await iconBrowser.newPage({
  viewport: { width: 1024, height: 1024 },
  deviceScaleFactor: 1
});
await iconPage.setContent(
  `<body style="margin:0;width:1024px;height:1024px;overflow:hidden">${iconLegacySvg}</body>`
);
await iconPage.locator("svg").evaluate((node) => {
  node.setAttribute("width", "1024");
  node.setAttribute("height", "1024");
});
await iconPage.locator("svg").screenshot({
  path: `${iconOutputDir}/legacy-master-1024.png`,
  type: "png"
});
await iconBrowser.close();
```

Inspect `legacy-master-1024.png` against `refined-droplet-source.png`. Confirm that the normalized vector, not raster edge noise, defines the silhouette and that the output is exactly 1024×1024.

- [ ] **Step 5: Generate density PNGs with `sips`**

```bash
GASSTATION_LEGACY_MASTER="app/build/reports/app-icon-refinement/legacy-master-1024.png"
test -f "$GASSTATION_LEGACY_MASTER"
sips -z 48 48 "$GASSTATION_LEGACY_MASTER" --out app/src/main/res/mipmap-mdpi/ic_launcher.png
sips -z 72 72 "$GASSTATION_LEGACY_MASTER" --out app/src/main/res/mipmap-hdpi/ic_launcher.png
sips -z 96 96 "$GASSTATION_LEGACY_MASTER" --out app/src/main/res/mipmap-xhdpi/ic_launcher.png
sips -z 144 144 "$GASSTATION_LEGACY_MASTER" --out app/src/main/res/mipmap-xxhdpi/ic_launcher.png
sips -z 192 192 "$GASSTATION_LEGACY_MASTER" --out app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
```

Expected: all commands report the requested output sizes.

- [ ] **Step 6: Run GREEN resource tests**

```bash
./gradlew \
  :app:testDemoDebugUnitTest \
  --tests com.gasstation.AppIconSourceContractTest \
  --tests com.gasstation.AppIconResourceTest \
  :app:processDemoDebugResources \
  :app:processProdDebugResources \
  --warning-mode fail
```

Expected: all dimensions, new hashes, adaptive resolution, monochrome resolution, and non-bitmap modern foreground checks pass.

- [ ] **Step 7: Inspect all five PNGs and commit**

View the five files at 1:1 scale. Confirm that the 48px icon still reads as a droplet and that no interpolation halo is visible against yellow.

```bash
git add \
  app/src/main/res/drawable/ic_launcher_foreground.xml \
  app/src/main/res/drawable/ic_launcher_monochrome.xml \
  app/src/main/res/mipmap-*/ic_launcher.png \
  app/src/testDemo/java/com/gasstation/AppIconSourceContractTest.kt \
  app/src/testDemo/java/com/gasstation/AppIconResourceTest.kt
git diff --cached --check
git commit -m "feat: regenerate launcher icon variants"
```

---

### Task 4: Audit every other icon surface without rewriting trademarks

**Files:**
- Create: `docs/design-assets/app-icon/icon-audit.md`
- Verify: `core/designsystem/src/test/snapshots/urban-signal-*.png`
- Verify: `feature/station-list/src/test/snapshots/populated.png`
- Verify: `feature/station-list/src/test/snapshots/populated-dark.png`
- Verify: `feature/settings/src/test/snapshots/settings-overview.png`
- Verify: `feature/settings/src/test/snapshots/settings-brand-detail.png`
- Verify: `feature/watchlist/src/test/snapshots/watchlist-five-rows.png`

**Interfaces:**
- Consumes: committed icon source mapping and existing Roborazzi goldens.
- Produces: a written PASS/FAIL audit covering app-owned launch icons, real station-brand logos, Material icons, and custom Canvas back/check/chevron icons.

- [ ] **Step 1: Run existing screenshot verification without recording new baselines**

```bash
./gradlew \
  :core:designsystem:verifyRoborazziDebug \
  :feature:station-list:verifyRoborazziDebug \
  :feature:settings:verifyRoborazziDebug \
  :feature:watchlist:verifyRoborazziDebug \
  --warning-mode fail
```

Expected: PASS. Do not run any `recordRoborazzi` task.

- [ ] **Step 2: Inspect the exact audit surfaces**

Inspect:

- launcher/splash: refined droplet is the only known replacement target;
- station list/settings/watchlist: SKE, GSC, HDO, SOL, RTX, E1G, SKG, and ETC remain identifiable at committed sizes;
- top-level navigation: gas-station, bookmark, and settings Material icons remain centered and readable;
- actions: refresh, bookmark outline/fill, location, filter chevrons/check remain readable;
- custom Canvas: settings back, selected check, and settings chevron have consistent stroke, alignment, and no clipping.

Expected: no non-launch icon code changes. If a reproducible mismatch appears, stop this task, add a focused failing test in that icon's owning module, and keep the fix isolated from trademark artwork.

- [ ] **Step 3: Record the evidence-backed audit**

After Step 1 passes and Step 2 confirms no additional defect, create `icon-audit.md` with this exact result:

```markdown
# Icon Audit

## Scope

Splash/launcher, station-brand logos, top-level Material icons, action icons, and custom settings Canvas icons.

## Result

- Splash and launcher: replaced by the refined-droplet asset set.
- Station-brand logos: PASS; retained as authentic source assets and not AI-regenerated.
- Material navigation/action icons: PASS; no clipping or mapping defect.
- Custom back/check/chevron icons: PASS in committed light/dark snapshots; no production change.

## Evidence

- `:core:designsystem:verifyRoborazziDebug`
- `:feature:station-list:verifyRoborazziDebug`
- `:feature:settings:verifyRoborazziDebug`
- `:feature:watchlist:verifyRoborazziDebug`

No Roborazzi baseline was re-recorded for unchanged app-body icon surfaces.
```

- [ ] **Step 4: Commit the audit**

```bash
git add docs/design-assets/app-icon/icon-audit.md
git diff --cached --check
git commit -m "docs: record app icon audit"
```

---

### Task 5: Prove runtime behavior, benchmark startup, and close live documentation

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/test-strategy.md`
- Modify: `docs/verification-matrix.md`
- Modify: `CHANGELOG.md`
- Evidence only: `app/build/reports/app-icon-refinement/*`
- Evidence only: `/tmp/gasstation-app-icon-before.json`
- Evidence only: `/tmp/gasstation-app-icon-after.json`

**Interfaces:**
- Consumes: complete icon resource set from Tasks 1–3 and PASS audit from Task 4.
- Produces: API 30/API 37 default/reduced-motion recordings, launcher screenshots, startup comparison, synchronized live docs, and a canonical clean verification result.

- [ ] **Step 1: Run focused host and build verification**

```bash
./gradlew \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  :app:assembleDemoRelease \
  :app:assembleProdRelease \
  --warning-mode fail
```

Expected: all demo/prod unit tests and debug/release builds pass.

- [ ] **Step 2: Resolve API 30 and API 37 devices**

```bash
GASSTATION_SDK_ROOT="$(sed -n 's/^sdk.dir=//p' local.properties)"
"$GASSTATION_SDK_ROOT/emulator/emulator" -list-avds
"$GASSTATION_SDK_ROOT/platform-tools/adb" devices -l
GASSTATION_API30_SERIAL=""
GASSTATION_API37_SERIAL=""
for GASSTATION_CANDIDATE_SERIAL in $(
  "$GASSTATION_SDK_ROOT/platform-tools/adb" devices |
    awk 'NR > 1 && $2 == "device" { print $1 }'
); do
  GASSTATION_CANDIDATE_API="$(
    "$GASSTATION_SDK_ROOT/platform-tools/adb" \
      -s "$GASSTATION_CANDIDATE_SERIAL" shell getprop ro.build.version.sdk |
      tr -d '\r'
  )"
  case "$GASSTATION_CANDIDATE_API" in
    30) GASSTATION_API30_SERIAL="$GASSTATION_CANDIDATE_SERIAL" ;;
    37) GASSTATION_API37_SERIAL="$GASSTATION_CANDIDATE_SERIAL" ;;
  esac
done
test -n "$GASSTATION_API30_SERIAL"
test -n "$GASSTATION_API37_SERIAL"
```

Use the existing `GasStation_API_30` and `Pixel_8` AVDs. Start either missing AVD in a persistent terminal and wait until `sys.boot_completed=1`. Resolve serials by `ro.build.version.sdk`; do not guess emulator port numbers.

- [ ] **Step 3: Record splash and launcher evidence for both APIs**

Install demo debug on both devices, then record each API at animator scale `1` and `0`:

```bash
mkdir -p app/build/reports/app-icon-refinement
GASSTATION_SDK_ROOT="$(sed -n 's/^sdk.dir=//p' local.properties)"
GASSTATION_API30_SERIAL=""
GASSTATION_API37_SERIAL=""
for GASSTATION_CANDIDATE_SERIAL in $(
  "$GASSTATION_SDK_ROOT/platform-tools/adb" devices |
    awk 'NR > 1 && $2 == "device" { print $1 }'
); do
  GASSTATION_CANDIDATE_API="$(
    "$GASSTATION_SDK_ROOT/platform-tools/adb" \
      -s "$GASSTATION_CANDIDATE_SERIAL" shell getprop ro.build.version.sdk |
      tr -d '\r'
  )"
  case "$GASSTATION_CANDIDATE_API" in
    30) GASSTATION_API30_SERIAL="$GASSTATION_CANDIDATE_SERIAL" ;;
    37) GASSTATION_API37_SERIAL="$GASSTATION_CANDIDATE_SERIAL" ;;
  esac
done
test -n "$GASSTATION_API30_SERIAL"
test -n "$GASSTATION_API37_SERIAL"
for GASSTATION_API_SERIAL in \
  "30:$GASSTATION_API30_SERIAL" \
  "37:$GASSTATION_API37_SERIAL"
do
  GASSTATION_API_LEVEL="${GASSTATION_API_SERIAL%%:*}"
  GASSTATION_DEVICE_SERIAL="${GASSTATION_API_SERIAL#*:}"
  ANDROID_SERIAL="$GASSTATION_DEVICE_SERIAL" ./gradlew :app:installDemoDebug --warning-mode fail
  for GASSTATION_ANIMATOR_SCALE in 1 0
  do
    "$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_DEVICE_SERIAL" \
      shell settings put global animator_duration_scale "$GASSTATION_ANIMATOR_SCALE"
    "$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_DEVICE_SERIAL" \
      shell am force-stop com.gasstation.demo
    "$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_DEVICE_SERIAL" \
      shell screenrecord --size 720x1280 --time-limit 5 \
      "/sdcard/app-icon-api${GASSTATION_API_LEVEL}-scale${GASSTATION_ANIMATOR_SCALE}.mp4" &
    GASSTATION_RECORD_PID=$!
    sleep 0.5
    "$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_DEVICE_SERIAL" \
      shell am start -S -W -n com.gasstation.demo/com.gasstation.MainActivity
    wait "$GASSTATION_RECORD_PID"
    "$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_DEVICE_SERIAL" pull \
      "/sdcard/app-icon-api${GASSTATION_API_LEVEL}-scale${GASSTATION_ANIMATOR_SCALE}.mp4" \
      "app/build/reports/app-icon-refinement/"
  done
  "$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_DEVICE_SERIAL" \
    shell settings put global animator_duration_scale 1
done
```

Open the launcher app drawer on each device, verify the GasStation label is visible, and capture the installed icon:

```bash
for GASSTATION_API_SERIAL in \
  "30:$GASSTATION_API30_SERIAL" \
  "37:$GASSTATION_API37_SERIAL"
do
  GASSTATION_API_LEVEL="${GASSTATION_API_SERIAL%%:*}"
  GASSTATION_DEVICE_SERIAL="${GASSTATION_API_SERIAL#*:}"
  "$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_DEVICE_SERIAL" \
    shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
  "$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_DEVICE_SERIAL" \
    shell input swipe 360 1100 360 300 300
  "$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_DEVICE_SERIAL" \
    exec-out screencap -p \
    > "app/build/reports/app-icon-refinement/launcher-api${GASSTATION_API_LEVEL}.png"
done
```

Expected:

- API 30: static refined drop, no ring, no clipping, no blank/residual splash.
- API 37: one restrained drop settle, no ring/loop, no clipping, no blank/residual splash.
- Scale 0: immediate exit with no custom-exit residue.
- Launcher: centered drop under legacy/adaptive mask; API 37 themed monochrome preview is recognizable.

- [ ] **Step 4: Run the after benchmark and enforce the 10% threshold**

Run the same API 37 startup benchmark from Task 2 and save the newest JSON:

```bash
GASSTATION_SDK_ROOT="$(sed -n 's/^sdk.dir=//p' local.properties)"
GASSTATION_API37_SERIAL=""
for GASSTATION_CANDIDATE_SERIAL in $(
  "$GASSTATION_SDK_ROOT/platform-tools/adb" devices |
    awk 'NR > 1 && $2 == "device" { print $1 }'
); do
  if [ "$("$GASSTATION_SDK_ROOT/platform-tools/adb" -s "$GASSTATION_CANDIDATE_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')" = "37" ]; then
    GASSTATION_API37_SERIAL="$GASSTATION_CANDIDATE_SERIAL"
    break
  fi
done
test -n "$GASSTATION_API37_SERIAL"
ANDROID_SERIAL="$GASSTATION_API37_SERIAL" ./gradlew \
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
cp "$GASSTATION_AFTER_JSON" /tmp/gasstation-app-icon-after.json
jq -e -s '
  def startup: .benchmarks[] | select(.name == "startupToFirstContent");
  def medians: {
    initial: .metrics.timeToInitialDisplayMs.median,
    full: .metrics.timeToFullDisplayMs.median
  };
  {
    before: (.[0] | startup | medians),
    after: (.[1] | startup | medians)
  } as $r |
  $r + {
    regressionPercent: {
      initial: ((($r.after.initial / $r.before.initial) - 1) * 100),
      full: ((($r.after.full / $r.before.full) - 1) * 100)
    }
  } |
  if (.regressionPercent.initial > 10 or .regressionPercent.full > 10)
  then error("startup median regression exceeded 10 percent")
  else .
  end
' \
  /tmp/gasstation-app-icon-before.json \
  /tmp/gasstation-app-icon-after.json
```

Expected: both medians stay within the 10% threshold. If one exceeds it, run exactly one idle/cool retest; a repeated regression requires reducing/removing the new AVD settle before proceeding.

- [ ] **Step 5: Synchronize live documentation**

Update:

- `docs/architecture.md`: shared `ic_brand_drop` geometry, API 24–30 static path, API 31+ drop-only settle, unchanged exit lifecycle.
- `docs/test-strategy.md`: source contract, legacy dimensions/hashes, API 30/API 37 visual evidence, and why body-icon Roborazzi baselines stay unchanged.
- `docs/verification-matrix.md`: focused Gradle command, two-API recordings, adaptive/monochrome launcher screenshots, and startup threshold.
- `CHANGELOG.md`: replace the Unreleased `Signal Pulse` wording with the refined-droplet launcher/splash result while retaining reduced-motion-safe exit.

Do not change README unless a checked-in README image directly shows the old launcher or splash.

- [ ] **Step 6: Run canonical verification**

```bash
scripts/agent/check-contracts.sh
scripts/agent/verify.sh auto
git diff --check
git status --short
```

Expected: all gates pass and only intended resource, test, asset, audit, live-doc, and changelog files remain.

- [ ] **Step 7: Commit documentation and final verification contract**

```bash
git add \
  docs/architecture.md \
  docs/test-strategy.md \
  docs/verification-matrix.md \
  CHANGELOG.md
git diff --cached --check
git commit -m "docs: document refined app icon verification"
```

- [ ] **Step 8: Perform final whole-branch review**

Review the entire execution branch against:

- `docs/superpowers/specs/2026-07-28-app-icon-refinement-design.md`
- this plan;
- the exact Global Constraints;
- API 30/API 37 recordings and launcher screenshots;
- before/after benchmark JSON;
- `git diff --check` and `scripts/agent/verify.sh auto`.

Do not mark complete if source provenance is missing, any ring resource remains, modern icons resolve through a bitmap, station trademarks were altered, runtime evidence is incomplete, or repeated startup regression exceeds 10%.
