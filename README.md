# 지하철 바로타

서울시 지하철 데이터를 활용할 Android 앱의 프로토타입입니다. 출발역과 도착역을 선택하고, 출발역으로 들어오는 실시간 열차 정보를 확인할 수 있습니다.

## 현재 구현된 기능

- 출발역 및 도착역 선택
- 역명 또는 호선으로 검색하는 Material 3 바텀시트
- 동일한 출발역·도착역 선택 방지
- 출발역과 도착역 맞바꾸기
- 선택 완료 전 조회 버튼 비활성화
- 서울시 실시간 지하철 도착정보 조회 화면
- 서울교통공사 열차시간표와 열차번호를 결합한 목적지 정차 여부 판정
- 노선·방향·도착 메시지·현재 위치·급행·막차 표시
- 로딩, 오류, 빈 결과와 수동 새로고침
- 디버그 APK에서 열차 카드별 목적지 정차·미정차 UX 테스트
- 타입 안전한 Navigation Compose 화면 이동
- 라이트·다크 모드와 Android 동적 색상
- ViewModel 단위 테스트와 Compose UI 테스트

현재 `stations.json`은 화면 동작 검증을 위한 대표 역 61개를 포함합니다. 다음 API 연동 단계에서 서울시 또는 서울교통공사의 공식 전체 역 데이터로 교체할 예정입니다.

## 개발 환경

- Application ID: `com.gilbit.barota`
- minSdk: 26 (Android 8.0)
- targetSdk: 36 (Android 16)
- compileSdk: 37
- JDK: 17

선택한 기술과 대안은 [TECH_DECISION_REPORT.md](TECH_DECISION_REPORT.md)에 정리되어 있습니다.

## 빌드 및 테스트

Android SDK 경로가 설정된 환경에서 다음 명령을 실행합니다.

먼저 프로젝트 루트에 `local.properties` 파일을 만들고 서울시 API 키를 입력합니다. 예시는 `local.properties.example`에서 확인할 수 있습니다.

```properties
SEOUL_SUBWAY_API_KEY=발급받은_키
SEOUL_TIMETABLE_API_KEY=발급받은_열차시간표_키
```

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

UI 테스트 코드를 컴파일하려면 다음 명령을 실행합니다.

```powershell
.\gradlew.bat compileDebugAndroidTestKotlin
```

생성되는 APK 경로는 `app/build/outputs/apk/debug/app-debug.apk`입니다.

## 다음 단계

1. Galaxy S23에서 열차번호별 시간표 매칭 결과를 검증합니다.
2. 공식 전체 역 목록으로 프로토타입 데이터를 교체합니다.
3. 공휴일·임시 시간표와 1~9호선 외 노선의 판정 범위를 확장합니다.
4. 공개 배포 전 Spring Boot 프록시와 캐시·호출 제한을 적용합니다.

개발 과정과 결정 배경은 [`docs/development-log/DEVELOPMENT_LOG.md`](docs/development-log/DEVELOPMENT_LOG.md)에 계속 기록합니다.
