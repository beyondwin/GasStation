# 주유주유소
운전 중 터치를 최소화하여 사고 위험을 줄이고, 현재 위치에서 가깝고 저렴한 주유소를 안내하는데에 초점이 맞춰져 있습니다.

주유주유소 Android에는 다음과 같은 기능이 있습니다.
- 현재 위치 기준으로 가장 가까운 또는 저렴한 주유소 찾기
- 주유소 브랜드별 리스트 표시
- 카카오맵, 티맵, 네이버맵과 연동하여 터치 한번으로 길찾기 및 안내
- 주유소 찾기 범위 설정 (3, 4, 5Km)
- 카카오 OPEN API, 공공유가정보 API를 통해 주유소 정보 표시

## Tech stack & Libraries
- Minimum SDK level 24
- [Kotlin](https://kotlinlang.org/) based, [Coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- [Hilt](https://dagger.dev/hilt/) for dependency injection.
- JetPack
  - Lifecycle - dispose of observing data when lifecycle state changes.
  - Compose - Android’s modern toolkit for building native UI
  - ViewModel - UI related data holder, lifecycle aware.
- [Retrofit2 & OkHttp3](https://github.com/square/retrofit) - construct the REST APIs and paging network data.
- [Timber](https://github.com/JakeWharton/timber) - logging.

## Architecture
This project is based on MVVM architecture and a repository pattern.

![architecture](https://developer.android.com/topic/libraries/architecture/images/final-architecture.png)

## Screenshot
<p align="center">
  <img width="33%" src="https://github.com/CoreaNim/GasStation/assets/2545783/28edeff4-a27d-40ac-bac9-f5fb39d972ed">
  <img width="33%" src="https://github.com/CoreaNim/GasStation/assets/2545783/3990ae64-b14b-40a5-bfbf-9fd0c6b6975f">
  <img width="33%" src="https://github.com/CoreaNim/GasStation/assets/2545783/7cf95d9c-4ef4-479b-8288-2b1f1ee3c163">
</p>

