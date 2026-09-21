# 열차 목적지 정차 여부 판정 로직 현황

- 작성일: 2026-09-20
- 기준: 현재 저장소의 구현 코드 및 테스트
- 범위: 사용자가 고른 출발역에 접근 중인 각 열차가 목적지역에 앞으로 정차하는지 판정하는 과정

## 1. 요약

현재 앱은 실시간 도착정보만으로 정차 여부를 판단하지 않는다. 실시간 응답에서 얻은 열차번호·노선·방향·종착역을 서울시 열차시간표 API의 출발역 및 목적지역 조회 결과와 결합한다.

판정 결과는 다음 세 가지다.

| 상태 | 의미 | 사용자 화면 |
| --- | --- | --- |
| `STOPS` | 목적지역에 앞으로 정차함을 확인 | 초록 카드, `😊 목적지 정차 · 타도 돼요` |
| `DOES_NOT_STOP` | 출발역에서는 확인되지만 목적지역에 없거나, 목적지를 이미 지난 시간표임 | 빨간 점멸 카드, `😠 목적지 미정차 · 타면 안 돼요` |
| `UNKNOWN` | API 키·열차번호·지원 범위·시간·조회 결과 등의 문제로 확정할 수 없음 | 기본 카드, 정차/미정차 문구 없음 |

핵심 원칙은 **정차하지 않는다는 근거가 충분할 때만 `DOES_NOT_STOP`으로 표시하고, 확인 실패는 `UNKNOWN`으로 남기는 것**이다.

## 2. 전체 처리 흐름

```text
출발역·목적지역 선택
  → RouteDirectionResolver가 직통 노선과 진행 방향 결정
  → SeoulArrivalRepository가 출발역 실시간 도착정보 조회
  → 출발역·노선·방향 필터링
  → 이미 해당 역을 출발한 열차 제거 및 도착 순서 정렬
  → 남은 열차마다 SeoulTrainStopRepository 호출
      ├─ 종착역 == 목적지역이면 즉시 STOPS
      └─ 그 외에는 목적지역/출발역 시간표 조회 후 비교
  → 판정 상태와 진단 정보를 TrainArrival에 복사
  → ArrivalScreen이 카드 및 화면 배경에 반영
```

진입점은 [`ArrivalViewModel`](../../app/src/main/java/com/gilbit/barota/ui/arrival/ArrivalViewModel.kt)이다. 출발역·목적지역·양쪽 노선 목록을 [`RouteDirectionResolver`](../../app/src/main/java/com/gilbit/barota/domain/RouteDirectionResolver.kt)에 넘겨 `DirectionalRoute`를 만들고, 해결된 경로만 `ArrivalRepository.getArrivals()`로 조회한다.

현재 일반 화면의 경로 판정은 **1호선 종로3가–독산 구간의 직통 이동만 지원**한다. 따라서 정차 판정 저장소 자체는 1~9호선을 허용하지만, 현재 UI의 정상 호출 경로에서 실제로 도달 가능한 범위는 이보다 좁다.

## 3. 판정에 사용하는 데이터

### 실시간 도착정보

[`SeoulArrivalRepository`](../../app/src/main/java/com/gilbit/barota/data/repository/SeoulArrivalRepository.kt)가 서울시 실시간 도착정보에서 다음 값을 `TrainArrival`로 변환한다.

- `btrainNo` → `trainNumber`: 시간표와 연결하는 정확 일치 키
- `subwayId` → `line`: 시간표 조회 노선
- `updnLine` → `direction`: 시간표 조회 방향
- `bstatnNm` 또는 `trainLineNm` → `terminalStation`: 종착역 즉시 판정
- `statnNm`, `subwayId`, `updnLine`: 판정 대상 열차를 출발역·노선·방향에 맞게 사전 필터링

`orderIncomingArrivals()`는 현재 역에서 이미 `DEPARTED` 상태인 열차를 제외하고 도착 예정 순으로 정렬한다. 정차 판정은 이 최종 목록에 남은 열차에만 수행된다.

### 열차시간표

[`SeoulTrainStopRepository`](../../app/src/main/java/com/gilbit/barota/data/repository/SeoulTrainStopRepository.kt)는 열차마다 목적지역과 출발역에 대해 `getTrainSch`를 각각 한 번씩 호출한다. 요청에는 다음 조건이 들어간다.

- 열차의 방향, 노선, 열차번호
- 조회할 역명(`역` 접미사 제거)
- 서울 시간대 기준 현재 시각
- 토요일·일요일은 `주말`, 그 외는 `평일`
- 한 요청의 조회 범위 `1..100`

응답 행에서는 `trainno`가 실시간 열차번호와 정확히 같고, `stnNm`이 조회 역명과 같은 행만 남긴다. 역명 비교 시 앞뒤 공백, 끝의 `역`, 이름 내부 공백을 제거한다.

## 4. 상세 판정 순서

판정은 아래 순서를 따르며, 먼저 만족한 조건에서 즉시 종료한다.

| 순서 | 조건 | 상태 | 진단 사유 | API 호출 |
| --- | --- | --- | --- | --- |
| 1 | 열차 종착역과 목적지역이 같음 | `STOPS` | `TERMINAL_MATCH` | 없음 |
| 2 | 시간표 API 키가 비어 있음 | `UNKNOWN` | `MISSING_API_KEY` | 없음 |
| 3 | 실시간 응답의 열차번호가 비어 있음 | `UNKNOWN` | `MISSING_TRAIN_NUMBER` | 없음 |
| 4 | 노선이 1~9호선이 아님 | `UNKNOWN` | `UNSUPPORTED_LINE` | 없음 |
| 5 | 출발역 시간표에 같은 열차가 없음 | `UNKNOWN` | `ORIGIN_TRAIN_NOT_FOUND` | 목적지·출발지 조회 |
| 6 | 출발역에는 있고 목적지역 시간표에는 없음 | `DOES_NOT_STOP` | `DESTINATION_TRAIN_NOT_FOUND` | 목적지·출발지 조회 |
| 7 | 목적지역 시각이 출발역 시각보다 뒤인 행 조합이 하나라도 있음 | `STOPS` | `FUTURE_DESTINATION_FOUND` | 목적지·출발지 조회 |
| 8 | 양쪽에 비교 가능한 시각은 있지만 목적지역 시각이 더 뒤인 조합이 없음 | `DOES_NOT_STOP` | `DESTINATION_ALREADY_PASSED` | 목적지·출발지 조회 |
| 9 | 양쪽 행은 있으나 시각을 비교할 수 없음 | `UNKNOWN` | `SCHEDULE_TIME_UNAVAILABLE` | 목적지·출발지 조회 |

중요한 세부 동작은 다음과 같다.

- 종착역 비교가 API 키, 열차번호, 지원 노선 검사보다 먼저다. 따라서 종착역이 목적지와 일치하면 다른 값이 부족해도 `STOPS`다.
- 시간표 조회 순서는 목적지역이 먼저, 출발역이 다음이다.
- 출발역 기준 시각은 출발 시각을 우선 사용하고 없으면 도착 시각을 사용한다.
- 목적지역 기준 시각은 도착 시각을 우선 사용하고 없으면 출발 시각을 사용한다.
- 목적지역 시각이 출발역 시각보다 **엄격히 큰 경우(`>`)**만 앞으로 남은 정차로 본다. 같은 시각은 포함하지 않는다.
- 같은 열차번호로 여러 행이 있으면 출발역 행과 목적지역 행의 모든 조합 중 하나라도 위 조건을 만족하면 `STOPS`다.
- 시각 문자열 끝의 `H:mm:ss` 또는 `HH:mm:ss`를 초로 바꾼다. 분·초는 0~59만 허용하며, 시는 별도 상한을 두지 않아 `24:xx:xx` 같은 영업일 연장 표기를 비교할 수 있다.

## 5. 실패 및 동시성 처리

[`SeoulArrivalRepository`](../../app/src/main/java/com/gilbit/barota/data/repository/SeoulArrivalRepository.kt)는 열차별 판정을 `async`로 수행하되 `Semaphore(4)`로 동시에 진행되는 판정을 최대 4개로 제한한다. 한 판정이 일반 예외로 실패하면 해당 열차만 다음 값으로 바꾸고 다른 열차 목록은 유지한다.

- 상태: `UNKNOWN`
- 진단 사유: `LOOKUP_FAILED`

코루틴 취소 예외는 `UNKNOWN`으로 바꾸지 않고 다시 던진다. 따라서 화면 전환이나 새 조회로 이전 작업이 취소됐을 때 오래된 결과가 정상 결과처럼 남지 않는다.

시간표 응답 본문이 없거나 헤더의 `resultCode`가 `00`이 아니면 예외로 취급되고, 위의 열차별 `LOOKUP_FAILED` 처리로 들어간다.

## 6. 모델과 진단 정보

[`TrainArrival`](../../app/src/main/java/com/gilbit/barota/data/model/TrainArrival.kt)은 최종 판정과 진단 정보를 함께 가진다.

```kotlin
destinationStopStatus: DestinationStopStatus
destinationStopDiagnostic: DestinationStopDiagnostic
```

`DestinationStopDiagnostic`에는 판정 사유와 출발역·목적지역에서 같은 열차번호로 매칭된 시간표 행 수가 들어간다. API 호출 전 종료된 판정은 매칭 건수가 `null`이고, 실제 조회 후에는 0 이상의 값이다.

디버그 빌드의 카드에는 노선·방향·열차번호·종착역, 실제 판정, 진단 사유, 양쪽 매칭 건수가 표시된다. 릴리스 화면에는 이 내부 진단 정보가 표시되지 않는다.

## 7. 화면 반영

[`ArrivalScreen`](../../app/src/main/java/com/gilbit/barota/ui/arrival/ArrivalScreen.kt)은 각 열차 카드에 그 열차의 `destinationStopStatus`를 적용한다.

- `STOPS`: 초록색 카드
- `DOES_NOT_STOP`: 두 빨간색을 700ms 간격으로 왕복하는 점멸 카드
- `UNKNOWN`: 기본 표면색 카드

화면 전체 배경은 **최종 정렬된 목록의 첫 번째 열차** 상태를 사용한다. 정차 판정이 목록 순서를 다시 바꾸지는 않는다. 디버그 화면의 미리보기 상태가 켜져 있으면 실제 판정 대신 미리보기 값이 카드와 화면 배경에 표시되지만, 실제 판정값은 모델과 디버그 정보에 그대로 남는다.

## 8. 현재 제약 및 주의점

- 정차 판정은 실제 운행 위치가 아니라 계획 시간표 기반이다. 임시 운행 변경이나 현장 상황과 다를 수 있다.
- 실시간 열차번호와 시간표의 `trainno`가 완전히 같아야 한다. 번호 체계가 다르면 출발역 매칭 실패로 `UNKNOWN`이 된다.
- 출발역에서 열차가 확인된 상태에서 목적지역 행이 0건이면 API 데이터 누락 여부와 무관하게 `DOES_NOT_STOP`으로 판정한다.
- 저장소는 1~9호선을 지원 목록으로 선언했지만, 현재 경로 방향 결정기는 1호선 일부 구간만 실제 지원한다.
- 평일/주말 구분만 있으며 공휴일을 별도로 처리하지 않는다.
- 각 열차는 일반적으로 시간표 API를 두 번 호출한다. 열차별 병렬 제한은 있지만 결과 캐시는 없다.
- 조회 날짜와 평일/주말은 호출 시점의 서울 현재 시각을 사용한다. 실시간 데이터의 수신 시각을 기준으로 삼지는 않는다.

## 9. 테스트로 확인되는 동작

[`SeoulTrainStopRepositoryTest`](../../app/src/test/java/com/gilbit/barota/data/repository/SeoulTrainStopRepositoryTest.kt)는 다음 핵심 경우를 검증한다.

- 목적지역의 이후 시간표가 있으면 `STOPS`
- 출발역에만 열차가 있으면 `DOES_NOT_STOP`
- 양쪽 모두에서 열차를 찾지 못하면 `UNKNOWN`
- 목적지역 시각이 출발역보다 앞이면 `DOES_NOT_STOP`
- 미지원 노선은 API 호출 없이 `UNKNOWN`
- 종착역과 목적지가 같으면 API 호출 없이 `STOPS`

[`SeoulArrivalRepositoryTest`](../../app/src/test/java/com/gilbit/barota/data/repository/SeoulArrivalRepositoryTest.kt)는 노선·역·방향 필터 뒤 남은 열차만 판정하는지, 최종 순서를 유지하는지, 개별 시간표 실패를 `UNKNOWN/LOOKUP_FAILED`로 격리하는지, 취소 예외를 전파하는지를 검증한다.

[`ArrivalScreenTest`](../../app/src/androidTest/java/com/gilbit/barota/ui/arrival/ArrivalScreenTest.kt)는 상태별 카드 표현, 첫 번째 최종 목록 항목에 따른 화면 배경, 디버그 진단 표시를 검증한다.

## 10. 관련 구현 파일

- 판정 인터페이스: [`TrainStopRepository.kt`](../../app/src/main/java/com/gilbit/barota/data/repository/TrainStopRepository.kt)
- 실제 판정 알고리즘: [`SeoulTrainStopRepository.kt`](../../app/src/main/java/com/gilbit/barota/data/repository/SeoulTrainStopRepository.kt)
- 실시간 열차 조회 및 판정 결합: [`SeoulArrivalRepository.kt`](../../app/src/main/java/com/gilbit/barota/data/repository/SeoulArrivalRepository.kt)
- 판정 상태 및 진단 모델: [`TrainArrival.kt`](../../app/src/main/java/com/gilbit/barota/data/model/TrainArrival.kt)
- 시간표 응답 모델: [`TrainScheduleResponse.kt`](../../app/src/main/java/com/gilbit/barota/data/remote/TrainScheduleResponse.kt)
- 경로 및 방향 결정: [`RouteDirectionResolver.kt`](../../app/src/main/java/com/gilbit/barota/domain/RouteDirectionResolver.kt)
- 화면 표현: [`ArrivalScreen.kt`](../../app/src/main/java/com/gilbit/barota/ui/arrival/ArrivalScreen.kt)
- 의존성 연결과 API 키 주입: [`AppModule.kt`](../../app/src/main/java/com/gilbit/barota/di/AppModule.kt)
