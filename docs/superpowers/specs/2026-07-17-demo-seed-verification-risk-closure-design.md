# Demo Seed Verification Risk Closure Design

## Goal

Close the remaining Urban Signal demo-seed verification gap without committing credentials or making automated gates depend on the live Opinet service.

## Decision

Keep the two responsibilities explicit:

- `generateDemoSeed` remains an authenticated, operator-invoked live refresh and continues to require `opinet.apikey`.
- A new `verifyDemoSeedAsset` task validates the committed demo asset deterministically without a key or network access.

Falling back from live generation to the existing asset is rejected because it would make a no-op look like a successful refresh. Committing captured Opinet payloads is rejected because it duplicates public data and creates a second fixture lifecycle.

## Verification Contract

The verifier parses `app/src/demo/assets/demo-station-seed.json` through the same `DemoSeedDocument` model used by the generator and fails with a precise invariant message when any of these conditions is false:

1. `seedVersion` is `1` and the origin matches the approved Gangnam Station Exit 2 coordinates and label.
2. The query list contains exactly one entry for every approved radius and fuel-type combination.
3. Station IDs are unique inside each snapshot.
4. History contains exactly one entry for every distinct `(fuelType, stationId)` produced by query-order projection and contains no orphan entries.
5. Each history series contains the generator's three points; its latest entry matches the first projected station price and `generatedAtEpochMillis`.
6. The 3 km gasoline snapshot contains the deterministic `RTO` and `ETC` portfolio stations with their approved identities.

## Integration

- `DemoSeedAssetVerifier` owns pure validation and has no Android or network dependency.
- `DemoSeedAssetVerifierMain` provides the explicit no-key CLI boundary.
- `:tools:demo-seed:verifyDemoSeedAsset` points at the committed asset.
- `:tools:demo-seed:test` also validates the real committed asset, so the existing CI unit-test job protects the contract.
- README and the verification matrix distinguish live refresh from deterministic verification.

## Error Handling

Validation uses fail-fast invariant messages containing the invalid query or history key. Missing/unreadable JSON remains a task failure. The live task keeps its existing explicit `Missing opinet.apikey` failure.

## Test Strategy

Use TDD:

1. Add verifier contract tests and a committed-asset test before implementation; capture the unresolved verifier RED.
2. Implement the pure verifier and CLI task.
3. Run focused tool tests and the explicit asset verification task.
4. Run app demo asset-loader tests, demo/prod unit tests, module guards, and the full merge gate before local-main integration.

No remote call, credential write, push, PR, or deploy is part of this change.
