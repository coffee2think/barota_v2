# 역 선택 목록 개인화와 사용 이력 저장

## 1. 배경 / 문제

기존 역 선택 화면은 655개 역을 이름순으로 보여 주고 검색 결과에도 같은 순서를 유지했다. 반복 이용하는 역에 접근하려면 매번 검색해야 했으며, 사용자 선택 이력을 저장하는 계층은 없었다.

## 2. 현재 구조와 검토한 선택지

* `AssetStationRepository`는 JSON 역 데이터의 기본 이름순 목록을 제공한다. 655개 station ID가 모두 고유함을 확인했다.
* `StationSelectionViewModel`은 StateFlow로 선택 대상, 검색어와 출발·도착역을 관리한다. `selectStation`에서 같은 출발·도착역 선택을 거부하고 선택을 확정한다.
* `StationSelectionScreen`은 바텀시트의 LazyColumn을 렌더링한다. 실제 조회는 화면의 도착 정보 확인 버튼에서 시작된다.
* Hilt Repository 구조는 있으나 사용자 이력 저장 기술과 별도 UseCase 계층은 없다.
* Room은 구조화된 저장과 migration에 적합하지만 두 개의 작은 테이블에 코드 생성 의존성을 새로 추가한다. DataStore/SharedPreferences는 설정 저장에는 단순하나 역별 카운터와 방향 있는 pair 조회에는 별도 구조화 작업이 필요하다.
* Android 기본 SQLite는 새 라이브러리 없이 두 테이블의 원자적 카운터 갱신과 pair 조회를 제공한다. 수동 SQL과 명시적 migration 관리가 필요하다는 부담은 있다.

## 3. 결정 및 이유

이번 범위에서는 기본 SQLite와 기존 Repository/Hilt 구조를 사용했다. 전체 역과 이력은 작으므로 정렬은 domain의 순수 함수로 수행하고 결과를 ViewModel 상태에 보관한다. DB 조회를 Compose 내부에 넣거나 범용 추천 프레임워크를 도입하지 않는다.

pair는 A안으로 함께 저장한다. 도착 정보 확인 이벤트에 경로 기록을 연결하는 정도로 구현할 수 있으므로 별도 추천 UI를 추가하지 않고도 이후 확장 데이터를 확보할 수 있다. 강남→서울역과 서울역→강남은 서로 다른 pair다.

## 4. 구현 내용

### UI 및 정렬

* 빈 검색어: 사용 이력이 있는 역 중 합산 횟수 DESC → 최근 사용 시각 DESC → 이름 ASC → ID ASC로 최대 5개를 `자주 찾는 역`에 표시한다. 아래 `전체 역`은 이름순을 유지하며 shortcut의 역도 포함한다.
* 이력이 없으면 `전체 역`만 표시한다. 검색어는 기존과 같이 앞뒤 공백을 제거하고 대소문자를 무시한다.
* 검색 중에는 이름 정확 일치 → prefix → 부분 일치 → 호선 부분 일치 순으로 구분하고, 같은 등급에서만 합산 횟수·최근 시각·이름·ID로 정렬한다. 모든 검색 결과를 표시하며 추천 5개 제한을 적용하지 않는다.
* 이력 없는 역은 횟수 0과 최근 사용 없음으로 처리한다. 역 목록에서 사라진 ID의 이력은 표시하지 않는다.
* 두 섹션은 서로 다른 LazyColumn 키를 사용한다. 검색어가 바뀌면 스크롤을 맨 위로 이동한다.

### 저장과 이벤트

* 역별 `stationId`, `originCount`, `destinationCount`, `lastUsedAt`을 저장한다. 합산 횟수는 계산 속성이며 timestamp 단위는 epoch milliseconds다.
* 선택 값 반영 후 이벤트에서만 역할별 카운터를 증가시킨다. 같은 역을 재선택할 때는 picker를 다시 열고 확정한 경우에만 추가 기록한다.
* 선택 대상이 즉시 닫히므로 연속된 중복 클릭은 기록하지 않는다. 재컴포지션, 조회·검색·스크롤, 상태 구독, 맞바꾸기와 거부된 선택에는 저장 호출이 없다.
* 방향 있는 pair는 완성된 경로로 도착 정보 확인을 누른 시점에 count와 lastUsedAt을 갱신한다. 이때 역별 횟수는 다시 증가시키지 않는다. 저장 진행 중에는 중복 조회를 막는다.
* ViewModel의 채널로 저장을 순서대로 처리하고 Repository는 Dispatchers.IO, Mutex와 SQLite 트랜잭션으로 갱신한다. 저장 성공 시 Flow의 역 이력을 갱신한다.
* 이력 읽기 실패 시 기본 목록을 사용할 수 있고, 저장 실패 시 선택은 유지하며 조회 이동도 가능하다. 사용자에게 저장 실패 메시지를 남기며 실패한 기록을 성공한 것으로 간주하지 않는다.

### 데이터 생명주기와 확장

* 앱 내부 DB `station-usage.db`에 저장하므로 재실행과 앱 업데이트 후에도 유지된다. 앱 데이터 삭제 시 초기화된다. 기존 앱의 `allowBackup=true` 정책을 따르므로 Android 백업/복원 여부는 시스템 정책에 따른다. 자체 서버 동기화는 없다.
* 첫 스키마는 버전 1이며 이전 사용자 이력 저장소가 없어 이번 도입에는 데이터 이전이 필요하지 않다. 향후 DB 버전을 올릴 때 `onUpgrade`에 데이터를 보존하는 migration을 추가해야 한다. 알려지지 않은 migration은 데이터를 삭제하는 대신 명시적으로 실패시킨다.
* 향후 역할별 추천은 보존된 두 카운터로 구현할 수 있다. pair 추천은 `getPairs(originId)`로 출발역별 조합을 읽을 수 있다.
* 즐겨찾기, 가중치·감쇠, 시간대·위치 추천은 이번 구현에 포함하지 않는다.

### 변경 파일과 역할

| 파일 | 변경 이유 |
| --- | --- |
| `data/model/StationUsage.kt` | 출발·도착별 역 이력과 방향 있는 pair 모델 |
| `data/repository/StationUsageRepository.kt` | 관찰·선택 기록·pair 기록/조회 계약 |
| `data/repository/SqliteStationUsageRepository.kt` | DB 스키마, IO·트랜잭션, Flow 제공 |
| `domain/StationOrdering.kt` | 추천 및 검색 정확도 우선 정렬 |
| `di/AppModule.kt` | 이력 Repository singleton 바인딩 |
| `ui/selection/StationSelectionViewModel.kt` | 이력 구독, 목록 계산, 확정 이벤트와 저장 실패 처리 |
| `ui/selection/StationSelectionScreen.kt` | 섹션 표시, 고유 목록 키, 검색 스크롤과 조회 이벤트 연결 |
| `test/.../StationOrderingTest.kt` | 이름순·5개 제한·동률·검색 정확도 검증 |
| `test/.../FakeStationUsageRepository.kt` | ViewModel 테스트용 저장소 |
| `test/.../StationSelectionViewModelTest.kt` | 역할별 기록·중복·재선택·pair·저장 실패 검증 |
| `androidTest/.../SqliteStationUsageRepositoryTest.kt` | DB 재오픈, 동시 갱신, timestamp와 pair 방향 검증 |
| `androidTest/.../StationSelectionScreenTest.kt` | 섹션과 중복 shortcut 렌더링·재컴포지션 이벤트 검증 |

## 5. 검증 결과

* `testDebugUnitTest`: 전체 74개 통과, 실패·오류·건너뜀 0. 신규 정렬 및 ViewModel 테스트 9개 포함.
* `compileDebugAndroidTestKotlin`: 성공. 새 SQLite·Compose 테스트 소스 컴파일 확인.
* `lintDebug`: 성공.
* `git diff --check`: 통과.
* 처음에는 Gradle 옵션 전달 오류와 Kotlin SQL 바인딩 배열의 타입 추론 오류가 있었고 수정했다. 이후 Android SDK 파일 접근이 샌드박스에서 차단되어 승인된 환경으로 재실행한 최종 검증이 성공했다.
* `adb devices -l`에 연결된 기기가 없고 SDK에 에뮬레이터 실행 파일이 없어 instrumented 테스트와 실제 화면 검증은 실행하지 못했다. DB 파일 재오픈과 재컴포지션 검증은 테스트를 작성하고 컴파일했으나 런타임 통과로 주장하지 않는다.

## 6. 남은 작업 / 보류 사항

* 연결된 기기에서 새 SQLite·Compose 테스트를 실행하고 긴 목록, 키보드와 검색어 변경 시 스크롤 동작을 확인한다.
* 이번 변경은 `main` 기준의 `feature/personalized-station-order` 브랜치에서 구현했고 아직 병합하지 않았다.
* 버전업 후보는 신규 기능 추가에 해당하는 `1.1.0`이다. 앱 설정은 아직 `versionName=1.0`, `versionCode=1`이며 로컬에는 릴리스 태그가 없다. 최신 GitHub 릴리스 상태는 확인하지 않았다. 버전 변경과 릴리스 준비 여부는 사용자 확인 대기이며 태그·버전 파일·Release를 변경하지 않았다.
