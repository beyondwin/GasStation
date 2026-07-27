# Splash Signal Pulse Design

> 작성일: 2026-07-27
> 상태: 승인됨
> 기준 커밋: `9ac7d1f`

## 목표

GasStation의 기존 노란 launch field와 검정 물방울 identity를 유지하면서 Android 버전별 splash 구현을 하나의 계약으로 통일하고, 시작 시간을 인위적으로 늘리지 않는 짧은 `Signal Pulse`와 첫 Compose 화면으로의 부드러운 종료 전환을 제공한다.

현재 구현은 API 24–30에서 `windowBackground` layer-list, API 31 이상에서 framework splash 속성을 사용한다. 표시 자체는 정상적이지만 정적 아이콘만 있고, `MainActivity`가 `setTheme()`으로 직접 일반 테마를 적용하며, 버전 공통 exit animation과 API 24–30 전용 검증이 없다.

## 성공 기준

- API 24–30은 노란 배경과 정적 검정 물방울을 표시한다.
- API 31 이상은 같은 배경에서 한 번만 재생되는 짧은 `Signal Pulse` AVD를 표시한다.
- 모든 지원 버전은 첫 앱 프레임이 준비되면 즉시 splash 종료를 시작한다.
- 권한, 위치, seed, DataStore, 네트워크 준비 상태가 splash 수명을 결정하지 않는다.
- splash와 첫 Compose 화면 사이에 흰색 또는 검정색 blank frame이 나타나지 않는다.
- 시스템 애니메이션을 끈 환경에서는 custom exit animation 없이 splash를 즉시 제거한다.
- demo/prod와 debug/release가 같은 launch contract를 사용한다.
- 같은 기기와 빌드 조건에서 변경 전후 startup evidence를 비교했을 때 의미 있는 first-display 회귀가 없다.

## 비목표

- 별도 Splash Activity 또는 Compose splash 화면
- 앱 이름, tagline, 하단 branding image
- adaptive launcher icon 변경
- 전체 앱의 다크 모드 전환
- flavor별 splash 분기
- startup 상태를 기다리기 위한 `setKeepOnScreenCondition`
- 반복 애니메이션, 진행률 표현, 로딩 완료 연출

## 검토한 방향

### A. Signal Pulse — 선택

기존 물방울이 빠르게 나타나 한 번 호흡하고 안정되는 motion이다. 로고를 바꾸지 않고 Urban Signal identity를 강화하며, 짧은 AVD와 정적 fallback의 시각 차이도 작다.

### B. Fuel Fill

물방울이 아래에서 위로 채워져 주유 맥락은 명확하지만 로딩 진행률처럼 보일 수 있다. animation completion을 기다리지 않는 정책과도 의미가 충돌한다.

### C. Route Signal

점선 경로가 물방울에 도착해 외부 지도 handoff를 암시하지만 GasStation보다 일반 길찾기 앱처럼 보일 수 있고 기존 symbol보다 장식이 앞선다.

## 소유권과 의존성

Splash는 Android launch chrome이므로 `app` 모듈이 전부 소유한다.

- `app`: starting theme, splash drawable/AVD, exit animation wiring, resource/Activity tests
- `gradle/libs.versions.toml`: `androidx.core:core-splashscreen:1.2.0` stable dependency
- `core:designsystem`: 변경하지 않는다. Compose theme이나 feature UI가 launch chrome을 소유하지 않는다.
- `feature:*`, `domain:*`, `data:*`: 변경하지 않는다.

새 모듈, domain model, repository, ViewModel, navigation route를 만들지 않는다.

## 시작 테마 구조

`Theme.GasStation.Launcher`는 AndroidX `Theme.SplashScreen` 계열을 사용하고 다음 계약을 갖는다.

- `windowSplashScreenBackground`: 기존 launcher yellow
- `windowSplashScreenAnimatedIcon`: 지원 버전에 맞는 동일 이름의 static 또는 animated drawable
- `postSplashScreenTheme`: `Theme.GasStation`

`MainActivity.onCreate()`는 `super.onCreate()` 직전에 `installSplashScreen()`을 호출한다. 현재의 수동 `setTheme(R.style.Theme_GasStation)` 호출은 제거한다.

Splash를 유지하는 조건은 등록하지 않는다. Hilt injection, demo seed, preferences, permission, location acquisition은 기존대로 앱 startup 이후 진행하며 splash lifecycle과 결합하지 않는다.

## 버전별 동작

| Android 버전 | 아이콘 | 종료 전환 | 비고 |
| --- | --- | --- | --- |
| API 24–30 | 정적 검정 물방울 | AndroidX splash view fade/scale 또는 애니메이션 비활성 시 즉시 제거 | AVD 재생을 기대하지 않는다 |
| API 31–32 | `Signal Pulse` AVD | 동일한 exit contract | AVD 자체 timing을 사용한다 |
| API 33 이상 | `Signal Pulse` AVD | 동일한 exit contract | deprecated framework duration 속성에 의존하지 않는다 |

앱은 현재 light-first이다. 시스템 night mode에서도 노란 배경과 검정 물방울을 유지한다. 전체 앱이 semantic dark surface로 전환되기 전에는 어두운 splash를 추가하지 않는다. API 30과 API 31의 night-mode resource test에서도 동일한 yellow/black을 resolve해 “override 누락”이 아니라 의도된 brand-constant launch임을 고정한다.

## Signal Pulse Motion

브라우저 선택용 preview는 움직임을 비교하기 위해 느리고 반복됐지만 실제 AVD는 한 번만 재생한다.

### AVD

- 총 길이: 300ms
- 0–100ms: 물방울 group alpha `0 → 1`, scale `0.82 → 1.04`
- 100–300ms: 물방울 scale `1.04 → 1.0`
- 80–260ms: 신호 링이 낮은 alpha로 한 번 확장한 뒤 사라짐
- 물방울과 링은 Android splash icon safe area 안에 유지
- 반복, 회전, bounce, path fill 진행률 없음

AVD 전용 vector는 기존 `ic_brand_drop`과 동일한 path data를 이름 있는 group 안에 둔다. 기존 launcher icon vector는 변경하지 않는다. API 24–30용 drawable은 최종 안정 frame과 같은 기존 정적 검정 물방울을 사용한다.

실제 launch가 AVD보다 빨리 끝나면 animation completion을 기다리지 않는다. 기존 실기기 first-display p50 347ms보다 짧게 설계해 일반 cold start에서 완료 가능성을 높이되, 완료 자체를 correctness 조건으로 삼지 않는다.

## Exit Motion

첫 Activity frame이 준비되면 splash view에 180ms exit transition을 적용한다.

- splash view alpha `1 → 0`
- icon scale `1.0 → 1.06`
- 과도한 translation이나 slide 없음
- animation end와 cancel 모두 `SplashScreenViewProvider.remove()` 호출
- system animation이 비활성화돼 있으면 animation 없이 즉시 `remove()`

Exit transition은 로딩 시간이 아니라 첫 화면과의 시각적 연결이다. `setKeepOnScreenCondition`, sleep, timer, animation completion wait를 사용하지 않는다.

## 접근성과 안정성

- 시스템 animator scale이 0이면 custom exit motion을 건너뛴다.
- motion은 한 번만 재생하고 빠른 scale/alpha에 한정한다.
- splash에는 읽어야 하는 문구나 사용자 action을 두지 않는다.
- 물방울과 배경은 기존 black/yellow 대비를 유지한다.
- exit listener는 정상 종료와 취소 어느 경우에도 view를 제거해 splash 잔류를 막는다.
- 잘못된 resource reference나 AVD 구조는 resource processing/test에서 실패하게 하고 런타임 복구 분기를 추가하지 않는다.

## 컴포넌트와 예상 변경 표면

### Build

- `gradle/libs.versions.toml`
  - `coreSplashscreen = "1.2.0"`
  - `androidx-core-splashscreen` alias
- `app/build.gradle.kts`
  - `implementation(libs.androidx.core.splashscreen)`

### Resources

- `app/src/main/res/values/themes.xml`
  - AndroidX starting theme와 `postSplashScreenTheme`
- `app/src/main/res/values-v31/themes.xml`
  - framework/API 31+ AVD contract
- `app/src/main/res/drawable/`
  - API 24–30 static splash icon
- `app/src/main/res/drawable-v31/`
  - `Signal Pulse` AVD와 animated vector target
- `app/src/main/res/animator-v31/`
  - scale/alpha animator resources

기존 `ic_launcher` adaptive icon과 manifest의 `@mipmap/ic_launcher` 계약은 유지한다.

### Activity

- `app/src/main/java/com/gasstation/MainActivity.kt`
  - `installSplashScreen()`
  - exit listener
  - animation-disabled immediate removal

Exit motion과 animations-enabled 판단은 app-internal `SplashExitAnimator`로 분리한다. `MainActivity`는 splash 설치와 listener 연결만 담당한다. helper는 launch chrome만 소유하며 startup readiness나 feature state를 받지 않는다.

## 테스트 전략

### Host/resource tests

- API 30 starting theme가 yellow background, static icon, post theme을 resolve
- API 31 starting theme가 AVD resource와 post theme을 resolve
- API 33+ 계약이 deprecated duration attr에 의존하지 않음
- static/animated resource가 bitmap이 아니며 safe-area wrapper를 사용
- demo/prod manifest가 같은 launcher theme을 사용
- animation-enabled exit policy가 180ms fade/scale spec을 선택
- animation-disabled exit policy가 immediate removal을 선택
- animation completion/cancel path 모두 remove를 보장

기존 `SplashThemeResourceTest`를 버전별 계약으로 확장하고, exit 선택은 Android view callback을 직접 과도하게 mock하기보다 작은 app-internal policy/helper 단위로 검증한다.

### Build tests

- `:app:testDemoDebugUnitTest`
- `:app:testProdDebugUnitTest`
- `:app:processDemoDebugResources`
- `:app:processProdDebugResources`
- `:app:assembleDemoDebug`
- `:app:assembleProdDebug`
- `:app:assembleDemoRelease`
- `:app:assembleProdRelease`

Release assemble은 R8 minification과 release packaging 이후에도 launch resource가 보존되는지 확인한다. 현재 비활성인 resource shrinking을 이 작업에서 새로 켜지 않는다.

### Runtime evidence

- API 30 emulator cold launch recording
- 최신 설치 가능 API emulator cold launch recording
- 각 버전에서 animation scale 기본값과 0배 확인
- yellow splash와 첫 Compose frame 사이 blank frame, icon clipping, residual splash 없음 확인
- demo와 prod는 동일 theme/resource contract이므로 live Opinet 호출 없이 assemble/resource evidence로 flavor parity 확인

현재 로컬에는 API 37 AVD만 있으므로 implementation verification 단계에서 API 30 system image와 전용 AVD를 준비한다. API 30 runtime evidence가 없으면 host/resource test 결과와 별개로 이 작업은 완료로 판정하지 않는다.

### Startup performance

구현을 시작하기 전에 기준 커밋에서 baseline을 먼저 수집한다. 동일 기기, 동일 build type, 동일 compilation mode에서 변경 전후 cold startup을 각각 10회 측정한다.

- 인위적 keep condition이 코드에 없어야 한다.
- first-display median이 baseline 대비 10% 넘게 악화되면 원인을 조사한다.
- full-display median이 baseline 대비 10% 넘게 악화되면 원인을 조사한다.
- threshold 초과가 device thermal/noise가 아니라 반복 재현되면 exit motion 또는 dependency integration을 조정한다.

기존 다른 기기에서 기록한 수치와 새 측정값을 직접 비교하지 않는다.

## 문서 갱신

구현 시 다음 live 문서를 실제 변경과 함께 확인한다.

- `docs/architecture.md`: AndroidX starting theme와 `MainActivity` launch sequence
- `docs/test-strategy.md`: API 30/31+ splash resource와 runtime evidence 역할
- `docs/verification-matrix.md`: focused splash test/build/runtime 명령
- `CHANGELOG.md`: 사용자에게 보이는 launch polish

README screenshot과 feature 문서는 본문 화면이 바뀌지 않으므로 기본 변경 대상이 아니다.

## 수용 체크리스트

- [ ] AndroidX SplashScreen 1.2.0이 version catalog를 통해 `app`에 연결됨
- [ ] `MainActivity`가 `installSplashScreen()`을 `super.onCreate()` 전에 호출함
- [ ] 수동 `setTheme()`과 splash hold condition이 없음
- [ ] API 24–30 static icon과 API 31+ AVD가 같은 final symbol을 사용함
- [ ] AVD가 한 번만 300ms 재생됨
- [ ] exit transition이 180ms 이내이고 animations-off에서 즉시 제거됨
- [ ] end/cancel 경로 모두 splash view를 제거함
- [ ] demo/prod debug/release build와 focused tests가 통과함
- [ ] API 30과 최신 API runtime evidence가 모두 남음
- [ ] 동일 환경 startup 비교에서 반복 가능한 10% 초과 회귀가 없음
- [ ] 관련 live 문서가 실제 구현과 일치함

## 참고

- [Android SplashScreen guide](https://developer.android.com/develop/ui/views/launch/splash-screen)
- [AndroidX SplashScreen API](https://developer.android.com/reference/androidx/core/splashscreen/SplashScreen)
- [AndroidX stable channel](https://developer.android.com/jetpack/androidx/versions/stable-channel)
