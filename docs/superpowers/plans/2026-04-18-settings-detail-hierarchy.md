# Settings Detail Hierarchy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `찾기 범위` 상세 화면을 부모 `찾기 설정` 화면과 같은 카드 문법으로 다시 구성해 전환 위화감을 없앤다.

**Architecture:** `SettingsDetailScreen`의 현재 커스텀 인트로와 카드형 옵션 그룹을 제거하고, `LegacyChromeCard` 하나 안에 `LegacySectionHeading`과 플랫 옵션 로우를 넣는다. 옵션 로우는 `SettingsScreen`과 같은 밀도를 유지하는 전용 컴포저블로 구성하고, 선택 상태는 내부 체크 아이콘과 semantics만으로 표현한다.

**Tech Stack:** Kotlin, Jetpack Compose, Robolectric Compose UI test, existing `core:designsystem` legacy chrome components

---

### Task 1: Lock The New Hierarchy In Tests

**Files:**
- Modify: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsScreenTest.kt`
- Reference: `docs/superpowers/specs/2026-04-18-settings-detail-hierarchy-design.md`

- [ ] **Step 1: Write the failing test expectations for the new header structure**

```kotlin
composeRule.onAllNodesWithText("찾기 범위").assertCountEquals(1)
composeRule.onNodeWithText("탐색 설정").assertExists()
composeRule.onNodeWithText("주변 주유소를 불러올 반경을 정합니다.").assertExists()
composeRule.onNodeWithTag(SETTINGS_OPTIONS_GROUP_TAG).assertExists()
```

- [ ] **Step 2: Run the targeted test to verify the current implementation fails**

Run: `./gradlew :feature:settings:testDebugUnitTest --tests com.gasstation.feature.settings.SettingsScreenTest`
Expected: FAIL because the current detail screen renders the custom intro block instead of the approved `탐색 설정` card header.

- [ ] **Step 3: Update the detail-screen assertions to match the approved hierarchy**

```kotlin
@Test
fun `settings detail screen keeps parent group hierarchy inside a single card`() {
    composeRule.setContent {
        SettingsDetailScreen(
            section = SettingsSection.SearchRadius,
            options = listOf(
                SettingOptionUiModel(
                    label = "3km",
                    subtitle = "가장 촘촘하게 주변 가격을 비교합니다.",
                    action = SettingsAction.SearchRadiusSelected(SearchRadius.KM_3),
                    isSelected = true,
                ),
            ),
            onBackClick = {},
            onOptionClick = {},
        )
    }

    composeRule.onAllNodesWithText("찾기 범위").assertCountEquals(1)
    composeRule.onNodeWithText("탐색 설정").assertExists()
    composeRule.onNodeWithText("주변 주유소를 불러올 반경을 정합니다.").assertExists()
    composeRule.onNodeWithTag(SETTINGS_OPTIONS_GROUP_TAG).assertExists()
}
```

- [ ] **Step 4: Re-run the targeted test and confirm it still fails for the right reason**

Run: `./gradlew :feature:settings:testDebugUnitTest --tests com.gasstation.feature.settings.SettingsScreenTest`
Expected: FAIL with assertions around missing `탐색 설정` header or the old detail layout still being present.

- [ ] **Step 5: Commit the test-first checkpoint**

```bash
git add feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsScreenTest.kt
git commit -m "test: lock settings detail hierarchy"
```

### Task 2: Rebuild The Detail Screen As A Single Card With Flat Rows

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt`
- Reference: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt`
- Test: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsScreenTest.kt`

- [ ] **Step 1: Remove the custom intro block and grouped surface implementation**

```kotlin
// Delete:
private fun SettingsDetailIntro(...)
private fun SettingsDetailOptionsGroup(...)
```

- [ ] **Step 2: Replace the body with one `LegacyChromeCard` that contains the header and flat rows**

```kotlin
item {
    LegacyChromeCard(modifier = Modifier.testTag(SETTINGS_OPTIONS_GROUP_TAG)) {
        LegacySectionHeading(
            title = section.group.title,
            subtitle = section.subtitle,
        )
        options.forEachIndexed { index, option ->
            SettingsDetailOptionRow(
                option = option,
                onClick = { onOptionClick(option) },
            )
            if (index != options.lastIndex) {
                SettingsDetailDivider()
            }
        }
    }
}
```

- [ ] **Step 3: Implement a flat option row that matches the parent settings menu density**

```kotlin
@Composable
private fun SettingsDetailOptionRow(
    option: SettingOptionUiModel,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(GasStationTheme.corner.small))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .semantics {
                selected = option.isSelected
                role = Role.RadioButton
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = option.label,
                style = GasStationTheme.typography.cardTitle,
                color = ColorBlack,
            )
            option.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = GasStationTheme.typography.body,
                    color = ColorGray2,
                )
            }
        }
        if (option.isSelected) {
            Spacer(modifier = Modifier.width(16.dp))
            SelectedCheckIcon()
        }
    }
}
```

- [ ] **Step 4: Add the internal divider so options read as one list instead of separate cards**

```kotlin
@Composable
private fun SettingsDetailDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ColorGray),
    )
}
```

- [ ] **Step 5: Run the targeted tests and confirm the new hierarchy passes**

Run: `./gradlew :feature:settings:testDebugUnitTest --tests com.gasstation.feature.settings.SettingsScreenTest`
Expected: PASS with the detail screen rendering one card, one top-bar title, and internal flat rows.

- [ ] **Step 6: Commit the implementation**

```bash
git add feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsScreenTest.kt
git commit -m "feat: align settings detail hierarchy"
```

### Task 3: Verify The Final Surface In Context

**Files:**
- Modify: none
- Verify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt`
- Verify: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsScreenTest.kt`

- [ ] **Step 1: Run the focused module tests one more time**

Run: `./gradlew :feature:settings:testDebugUnitTest`
Expected: PASS for the settings module unit and UI tests.

- [ ] **Step 2: Inspect the final diff to ensure the old card-in-card structure is gone**

Run: `git diff -- feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsScreenTest.kt`
Expected: The diff shows `LegacyChromeCard` plus internal dividers, and no custom intro surface or per-option bordered cards.

- [ ] **Step 3: Commit the verification checkpoint if needed**

```bash
git add docs/superpowers/plans/2026-04-18-settings-detail-hierarchy.md
git commit -m "docs: add settings detail hierarchy plan"
```
