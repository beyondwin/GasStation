# Brand Icon Restoration Design

## Goal

Restore station brand icons that existed before commit `6bbb36e` and apply them consistently anywhere the app presents a station brand as a primary visual cue.

The affected surfaces are:

- Station list cards
- Watchlist cards
- Settings detail screen for the station brand filter

The result should remove the current text-only brand presentation without weakening the existing price and distance hierarchy.

## Context

Before `6bbb36e`, `GasStationItem` rendered a brand image with:

```kotlin
GasStationType.getGasStationImg(gasStations.POLL_DIV_CD)
```

The old resources were:

- `ic_ske.png`
- `ic_gsc.png`
- `ic_hdo.png`
- `ic_sol.png`
- `ic_rtx.png`
- `ic_etc.png`
- `ic_e1g.png`
- `ic_skg.png`

The current multimodule app no longer has those brand image resources in the active source tree. Brand values are mapped to text labels in feature UI models and rendered as plain text in the station list, watchlist, and settings screens.

## Design Decision

Use a shared design-system brand icon component instead of per-feature mappings.

`core:designsystem` will own the restored icon resources and the small UI mapping layer. This matches the current module contract: common visual components and repeated styling live in design system, while feature modules decide where those components appear.

The domain `Brand` enum remains the source of truth for station brands. No repository, cache, network, or domain behavior changes are required.

## Brand Mapping

Use the same mapping as the pre-`6bbb36e` implementation:

| Brand | Icon |
| --- | --- |
| `SKE` | `ic_ske` |
| `GSC` | `ic_gsc` |
| `HDO` | `ic_hdo` |
| `SOL` | `ic_sol` |
| `RTO` | `ic_rtx` |
| `RTX` | `ic_rtx` |
| `NHO` | `ic_rtx` |
| `ETC` | `ic_etc` |
| `E1G` | `ic_e1g` |
| `SKG` | `ic_skg` |

`BrandFilter.ALL` is not a real station brand. It should stay text-only in settings rather than showing a misleading brand image.

## Component Shape

Add a compact reusable brand presentation to `core:designsystem`.

Responsibilities:

- Resolve a `Brand` to the restored drawable resource.
- Render the icon at a stable size suitable for dense cards and settings rows.
- Provide an accessible content description such as `GS칼텍스 브랜드`.
- Allow the feature screen to decide whether to show the adjacent text label.

Expected API shape:

```kotlin
@Composable
fun GasStationBrandIcon(
    brand: Brand,
    contentDescription: String?,
    modifier: Modifier = Modifier,
)
```

If a row needs icon plus text, the feature can compose the icon with its existing label text. Keeping the label outside the icon component prevents the design system from owning feature-specific copy layout.

## Station List

The station list card should add the brand icon to the existing fuel and brand metadata row.

Current row:

- Fuel chip
- Brand text
- Price delta

New row:

- Fuel chip
- Brand icon
- Brand text
- Price delta

The icon should be compact, around 28-32dp, so the restored visual identity does not compete with the price and distance metrics. The brand text stays as a secondary label for readability and accessibility.

## Watchlist

The watchlist card should use the same brand presentation as the station list.

Current area:

- Station name
- Brand text

New area:

- Station name
- Brand icon plus brand text

This keeps both station-card surfaces visually consistent.

## Settings Brand Filter

Only the `SettingsSection.BrandFilter` detail screen gets brand icons.

Rows for concrete brands should render:

- Brand icon
- Label
- Description
- Selected check icon, when selected

The `전체` option remains text-only because it is a filter mode, not a station brand. Other settings sections such as radius, fuel type, sort order, and map provider remain unchanged.

To support this without overloading all settings options, `SettingOptionUiModel` will carry an optional brand value:

```kotlin
val brandIconBrand: Brand? = null
```

`optionsFor(SettingsSection.BrandFilter)` sets this for every concrete brand option and leaves it null for `ALL`.

## Accessibility

Every rendered icon must have either:

- A meaningful content description when the icon is independently discoverable by tests or accessibility services, or
- `null` content description when adjacent text already provides the same name in the same merged row.

For this implementation, use meaningful descriptions on the icon component and keep the adjacent text. Tests can assert the descriptions without relying on image internals.

## Testing

Add focused coverage:

- Brand icon resource mapping covers every `Brand`.
- Station list card renders the brand icon for a concrete brand and preserves the existing price-above-title hierarchy.
- Watchlist card renders the brand icon for a concrete brand.
- Brand filter detail screen renders icons for concrete brands and leaves `전체` without a brand icon.

Existing ViewModel, repository, and domain tests should not need changes because this is UI presentation only.

## Non-Goals

- Do not change station brand domain values.
- Do not change brand filter behavior.
- Do not change Opinet parsing or cache storage.
- Do not redesign the cards beyond restoring brand icons.
- Do not add icons to non-brand settings options.

## Risks

### Icon resources may not scale cleanly

The restored PNGs come from an older UI. If any icon looks soft at the new compact size, keep the original assets for this restoration and defer vector replacement to a separate design task.

### Layout crowding in the station list metadata row

The row already contains a fuel chip, brand text, and price delta. Use a compact icon and preserve text overflow behavior so the price delta remains visible on narrow screens.

### Design-system dependency on `domain:station`

Adding `Brand` usage in `core:designsystem` creates a direct dependency from design system to the station domain. This is acceptable for this task because the icon is a shared station-brand UI primitive. If the project later wants stricter design-system purity, the mapping can move to a small shared station UI module.

## Acceptance Criteria

- Station list cards show brand icons for station brands.
- Watchlist cards show brand icons for station brands.
- Settings brand filter rows show brand icons for concrete brands.
- Settings `전체` brand filter option remains text-only.
- Old brand icon resources are restored in the active source tree.
- Tests cover the mapping and each affected UI surface.
