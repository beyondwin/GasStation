# App Icon Refinement Design

> 작성일: 2026-07-28
> 상태: 승인됨
> 기준 커밋: `2102493`

## 목표

GasStation의 스플래시와 런처 아이콘에서 과도하게 확대되어 거칠게 보이는 기존 물방울을 GPT Image 2.0으로 만든 정제된 원본을 바탕으로 교체한다.

새 심볼은 현재의 물방울 정체성과 yellow, black, white 색 체계를 유지하되 Android 스플래시, adaptive icon, themed icon, 구형 런처 아이콘에서 모두 선명하고 안정적으로 보여야 한다.

스플래시 외 아이콘도 함께 감사하되 실제 문제가 확인된 앱 소유 아이콘만 수정한다. 실제 주유소 상표 로고를 AI로 재생성하거나 문제 증거가 없는 Material 아이콘을 일괄 교체하지 않는다.

## 현재 문제

현재 스플래시와 런처 아이콘은 `ic_brand_drop`의 단순한 단일 path를 공유한다. 이 path는 큰 스플래시 영역에서 다음 문제를 드러낸다.

- 물방울의 위쪽 꼭짓점과 아래쪽 곡선 연결이 거칠다.
- 내부 디테일이 전혀 없어 큰 크기에서 임시 도형처럼 보인다.
- API 31 이상 스플래시에서는 심볼이 시각적으로 과대 확대되어 보인다.
- 구형 density별 launcher PNG도 같은 단순 실루엣을 사용해 새 앱 정체성을 보완하지 못한다.

최근 구현된 AndroidX SplashScreen 수명과 exit 계약은 정상이며 아이콘 조형과 별개의 문제다. 이번 변경은 startup readiness, 앱 데이터 흐름, 화면 상태를 바꾸지 않는다.

## 성공 기준

- 스플래시의 물방울이 현재보다 여백 있게 보이고 잘리거나 과대 확대되지 않는다.
- 물방울 실루엣은 48px 구형 런처부터 최신 고밀도 adaptive icon까지 한눈에 식별된다.
- 스플래시, adaptive icon, monochrome themed icon, 구형 launcher PNG가 같은 핵심 geometry를 사용한다.
- 앱의 Urban Signal 색인 yellow `#FFDC00`, black `#222222`, 필요 시 white만 사용한다.
- API 24–30 정적 스플래시와 API 31 이상 애니메이션 스플래시가 같은 final frame을 공유한다.
- 기존 splash exit와 reduced-motion 계약, demo/prod parity, startup 성능을 보존한다.
- 다른 아이콘은 실제 clipping, blur, 잘못된 mapping, 낮은 식별성 증거가 있을 때만 수정한다.

## 비목표

- 앱 이름, tagline, 문구가 있는 스플래시
- 주유기, 지도 핀, 가격 기호를 새 앱 심볼에 추가
- gradient, shadow, 광택, 3D, 사진형 표현
- 별도 Splash Activity 또는 Compose splash route
- splash 수명을 늘리는 delay, readiness condition, animation completion wait
- 실제 주유소 브랜드 로고의 AI 재생성 또는 임의 재해석
- 문제 증거가 없는 Material navigation/action icon의 일괄 교체
- feature, domain, data, design system의 동작 변경

## 검토한 구현 접근

### 1. 생성 PNG 직접 사용

GPT Image 결과를 runtime PNG로 직접 배치하는 방식이다.

- 장점: 생성 결과를 가장 빠르고 그대로 반영할 수 있다.
- 단점: adaptive mask, monochrome, splash safe zone, density별 축소에서 blur와 edge artifact가 다시 생길 수 있다.

채택하지 않는다.

### 2. GPT Image 원본을 벡터 master로 정리

GPT Image로 고해상도 원본을 만든 뒤 핵심 실루엣을 단순화된 Android vector path로 정리한다. Runtime splash와 modern launcher는 벡터를 사용하고 구형 launcher만 동일 geometry에서 density별 PNG를 파생한다.

- 장점: 생성 시안의 개성을 유지하면서 Android 크기와 mask별 선명도를 통제할 수 있다.
- 단점: 생성 결과를 그대로 복사하지 않고 vector 정리와 시각 회귀 검증이 필요하다.

이 방식을 채택한다.

### 3. 생성 이미지는 참고로만 사용하고 완전 수작업 재설계

- 장점: path와 safe zone을 가장 엄격하게 통제할 수 있다.
- 단점: GPT Image 결과를 실제 자산의 출발점으로 사용한다는 요청에서 멀어진다.

채택하지 않는다.

## 시각 방향

선택한 방향은 `A. 정제된 물방울`이다.

### 형태

- 하나의 안정적인 물방울 실루엣을 핵심으로 한다.
- 좌우 무게와 아래 곡률을 균형 있게 정리한다.
- 위쪽 꼭짓점은 날카로운 바늘 모양이 아니라 작은 크기에서도 깨끗하게 닫히는 곡선 연결을 사용한다.
- 내부에는 작은 yellow 또는 transparent highlight 한 개까지 허용한다.
- 48px에서 사라지는 선, 얇은 테두리, 작은 글자, 복수의 장식 요소는 사용하지 않는다.

### 색

- Primary yellow: `#FFDC00`
- Primary black: `#222222`
- Optional white: `#FFFFFF`

현재 launcher resource의 `#FFFED70A` background와 `#111111` foreground는 새 master geometry를 적용할 때 각각 `#FFDC00`, `#222222`로 통일한다. 이 색상 변경은 생성 이미지의 근사 색을 복사하는 방식이 아니라 Android color resource에 정확한 hex 값으로 고정한다.

스플래시와 adaptive icon의 기본 배경은 yellow, foreground는 black이다. Monochrome themed icon은 Android가 tint를 적용할 수 있는 단색 mask로 제공한다.

### 배치

- Android splash와 adaptive icon의 서로 다른 mask를 고려해 심볼 주변에 충분한 투명 여백을 둔다.
- circle, squircle, rounded-square mask에서 핵심 실루엣이 잘리지 않아야 한다.
- 현재 스플래시 캡처보다 시각적 크기를 줄여 화면 중앙에 안정적으로 놓이게 한다.
- 배경색이나 전체 정사각형 이미지를 foreground bitmap에 구워 넣지 않는다.

## GPT Image 생성 계약

GPT Image 2.0에는 다음 조건으로 원본을 요청한다.

- square app icon source, high resolution
- flat geometric vector-like mark
- refined black fuel droplet on Urban Signal yellow
- balanced curves and generous negative space
- one restrained internal highlight at most
- no text, letters, currency symbol, map pin, gas pump, gradients, shadows, mockup frame, device frame, or photographic texture
- centered composition suitable for Android adaptive icon masks

첫 결과를 자동 채택하지 않는다. 다음 조건을 만족할 때까지 제한된 횟수로 재생성 또는 수정한다.

- silhouette edge가 깨끗함
- 작은 크기에서 물방울로 식별됨
- 내부 highlight가 monochrome 변환을 방해하지 않음
- circle과 squircle crop에서 안전함
- 앱의 yellow, black, white 정체성과 일치함

선택한 생성 원본, 최종 prompt, 선택 이유는 `docs/design-assets/app-icon/`에 보존한다. Runtime은 이 원본 파일을 직접 참조하지 않는다.

## 소유권과 구조

Splash와 launcher icon은 Android launch chrome이므로 `app` 모듈이 소유한다.

- `docs/design-assets/app-icon/`
  - 선택한 GPT Image 원본
  - prompt와 provenance
  - 생성 원본에서 runtime vector로 정리한 기준 설명
- `app/src/main/res/drawable/`
  - 공통 final-frame vector
  - API 24–30 static splash wrapper
  - adaptive foreground와 monochrome wrapper
- `app/src/main/res/drawable-v31/`
  - 같은 final geometry를 사용하는 API 31+ AVD
- `app/src/main/res/animator-v31/`
  - 물방울 등장과 안정화에 필요한 짧은 alpha/scale animator
- `app/src/main/res/mipmap-*`
  - 같은 geometry에서 렌더한 구형 launcher PNG

Manifest와 theme은 기존 `@mipmap/ic_launcher`, `@drawable/ic_splash_foreground` ID를 계속 사용한다. 새 dependency, module, runtime image loader는 추가하지 않는다.

`core:designsystem`, `feature:*`, `domain:*`, `data:*`는 변경하지 않는다.

## 스플래시 모션

선택 방향이 정제된 물방울이므로 기존 signal ring은 제거한다. API 31 이상 AVD는 물방울만 짧게 나타나 안정되는 motion을 사용한다.

- 총 길이는 현재 300ms 이하를 유지한다.
- alpha와 작은 scale 변화만 사용한다.
- 회전, bounce, translation, 반복은 사용하지 않는다.
- animation completion을 기다리지 않는다.
- API 24–30은 동일한 final-frame static drawable을 표시한다.
- `SplashExitAnimator`의 180ms fade/scale와 animations-off immediate removal 계약은 유지한다.

## 다른 아이콘 감사

### 교체 대상

- splash foreground
- adaptive launcher foreground
- monochrome themed icon
- 구형 density별 launcher PNG

이 자산들은 같은 거친 물방울 geometry를 공유하므로 함께 교체한다.

### 유지 대상

- 실제 주유소 브랜드 logo PNG
- Material navigation icon
- Material bookmark, refresh, location, filter check/chevron icon
- 현재 snapshot에서 정상인 custom back, selected check, settings chevron

주유소 브랜드 logo는 실제 상표 자산이고 현재 station list와 settings snapshot에서 식별 가능하다. AI 재생성은 정확성, 상표 형태, 사용자 신뢰를 훼손할 수 있으므로 하지 않는다.

Custom Canvas icon은 실기기 또는 snapshot 감사에서 clipping, 비정상 stroke, 크기 불균형이 재현될 때만 관련 test와 함께 수정한다.

## 오류 처리와 반복 정책

- 생성 원본이 vector 정리에 적합하지 않으면 bitmap을 억지로 채택하지 않고 prompt를 좁혀 다시 생성한다.
- vector 정리 후 작은 크기에서 highlight가 뭉개지면 highlight를 제거하고 silhouette만 유지한다.
- adaptive mask에서 crop되면 foreground wrapper 여백을 늘린다.
- API별 splash 크기가 다르면 theme ID나 startup 흐름을 분기하지 않고 resource-qualified wrapper에서 safe area를 맞춘다.
- resource processing 또는 resource test가 실패하면 runtime fallback을 추가하지 않고 잘못된 reference와 XML 구조를 수정한다.
- 다른 아이콘 감사에서 주관적 차이만 있고 실제 결함 증거가 없으면 변경하지 않는다.

## 접근성

- Splash에는 읽어야 할 text나 action을 추가하지 않는다.
- Launcher icon 자체에 content description을 넣지 않는다. Android launcher와 application label이 접근성 이름을 제공한다.
- Monochrome mask는 foreground와 background의 분리 가능한 단색 silhouette를 유지한다.
- 시스템 animation이 꺼진 환경에서는 기존 계약대로 custom exit를 즉시 제거한다.
- 앱 내부 icon-only action의 content description, semantics, test tag는 이번 변경으로 제거하지 않는다.

## 테스트와 검증

### 정적 자산 검사

- GPT Image 원본과 prompt provenance 존재
- runtime foreground가 bitmap 직접 참조가 아닌 vector/XML
- static splash, AVD final frame, adaptive foreground가 같은 핵심 geometry 사용
- monochrome resource가 정상 resolve
- mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi legacy PNG가 동일 source에서 파생

### Host/resource test

- `AppIconResourceTest`
- `SplashThemeResourceTest`
- `SplashExitAnimatorTest`
- demo/prod manifest가 같은 launcher theme과 resource ID 사용
- API 30 static resource와 API 31 이상 animated resource resolve
- 기존 180ms exit와 animations-off immediate removal 계약 유지

### 시각 검증

- 48px, 72px, 96px, 144px, 192px legacy icon 비교
- circle, squircle, rounded-square adaptive mask preview
- monochrome themed icon preview
- API 30 cold launch
- API 37 cold launch
- 각 API의 animation scale 기본값과 0배
- yellow field와 첫 Compose frame 사이 blank frame 없음
- icon clipping, 과대 확대, 잔상 없음

### 앱 회귀 검증

- `:app:testDemoDebugUnitTest`
- `:app:testProdDebugUnitTest`
- `:app:assembleDemoDebug`
- `:app:assembleProdDebug`
- `:app:assembleDemoRelease`
- `:app:assembleProdRelease`
- 기존 design-system과 feature snapshot을 변경 전후로 비교하고, 감사 대상 icon을 수정하지 않았다면 snapshot baseline은 갱신하지 않음
- `scripts/agent/check-contracts.sh`
- `scripts/agent/verify.sh auto`
- `git diff --check`

Startup 리소스와 motion이 바뀌므로 같은 기기와 빌드 조건에서 cold-start benchmark를 재확인한다. 반복 가능한 first-display 또는 full-display 10% 초과 회귀가 있으면 safe-area wrapper와 motion resource를 조사한다.

## 문서 갱신

구현과 함께 다음 문서를 실제 결과에 맞춰 확인한다.

- `docs/architecture.md`
- `docs/test-strategy.md`
- `docs/verification-matrix.md`
- `CHANGELOG.md`

README 본문 화면은 바뀌지 않으므로 기본 변경 대상이 아니다. Launcher 또는 splash 이미지가 README에 직접 노출되어 있을 때만 해당 이미지를 갱신한다.

## 예상 변경 표면

- `docs/design-assets/app-icon/*`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/drawable/ic_brand_drop.xml`
- `app/src/main/res/drawable/ic_brand_drop_monochrome.xml`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- `app/src/main/res/drawable/ic_splash_foreground.xml`
- `app/src/main/res/drawable-v31/ic_splash_signal_pulse_vector.xml`
- `app/src/main/res/drawable-v31/ic_splash_foreground.xml`
- 물방울 alpha/scale용 `app/src/main/res/animator-v31/*`
- 기존 ring 전용 animator 제거
- `app/src/main/res/mipmap-*/ic_launcher.png`
- `AppIconResourceTest`, `SplashThemeResourceTest`, `SplashExitAnimatorTest`
- 검증 결과에 따라 live documentation

기존 resource ID를 유지할 수 있으면 파일명은 바꾸지 않는다. `ic_splash_signal_pulse_vector`처럼 의미가 달라지는 내부 전용 이름은 구현 시 focused reference search 후 더 정확한 이름으로 변경할 수 있다.

## 수용 체크리스트

- [ ] 선택된 GPT Image 원본과 prompt provenance가 보존됨
- [ ] 정제된 물방울이 48px부터 splash 크기까지 선명함
- [ ] Urban Signal yellow, black, white 외 불필요한 색이 없음
- [ ] splash와 launcher가 같은 final geometry를 사용함
- [ ] adaptive circle과 squircle mask에서 잘림이 없음
- [ ] monochrome themed icon이 정상 표시됨
- [ ] API 24–30 static splash와 API 31+ AVD final frame이 일치함
- [ ] 기존 signal ring이 제거되고 300ms 이하 물방울 settle motion만 남음
- [ ] 기존 exit와 reduced-motion 계약이 유지됨
- [ ] 실제 주유소 상표 logo를 AI로 변경하지 않음
- [ ] 다른 icon은 재현 가능한 결함이 있을 때만 수정됨
- [ ] demo/prod debug/release와 canonical verification이 통과함
- [ ] API 30과 API 37 runtime evidence가 남음
- [ ] 반복 가능한 10% 초과 startup 회귀가 없음
