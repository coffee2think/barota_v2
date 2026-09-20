# 2026-09-20 — 제한된 경로용 실행 하드코딩 제거

## 1. 배경 / 문제

사용자가 과거 1호선 일부 역만으로 방향과 API 동작을 확인하던 제한 코드가 실제 실행 경로에 남아 있지 않도록 전수 점검하고, 데이터 기반 노선 확장의 효과가 역 선택·실시간 조회·시간표 판정까지 이어지도록 요청했다. 실제 판정을 확인하기 위한 디버그 카드 진단 표시는 유지해야 했다.

## 2. 검토 결과

* 과거 `RouteDirectionResolver.LINE_ONE_DOWN_STATIONS`, 1호선 전용 분기, `subwayId="1001"` 고정값은 이미 `route_network.json` 기반 그래프 탐색으로 제거된 상태였다.
* 역 선택 Repository와 ViewModel은 `stations.json` 전체를 로드하며 일부 역만 고르는 제한이 없었다.
* 남아 있던 실행 하드코딩은 실시간 DTO의 `subwayId`를 다시 노선명으로 바꾸는 `when` 목록과 시간표 Repository의 `(1..9)` 지원 목록이었다.
* API 응답 역명을 요청 역명과 완전 일치로 비교하여, 괄호형 부역명·`역` 접미사·`응암순환` 표기 차이가 있는 역이 누락될 가능성이 있었다.

## 3. 결정 및 이유

노선명, 실시간 API ID, 시간표 API 노선명은 모두 노선 그래프 데이터가 소유하도록 했다. 실시간 응답은 이미 선택 경로의 `subwayId`로 필터링되므로, DTO를 도메인 모델로 바꿀 때 Resolver가 확정한 노선명을 사용한다.

시간표 지원 여부도 코드의 노선 목록으로 판단하지 않고 `RouteLine.timetableLineName`의 존재 여부로 판단한다. API 역명 비교는 노선 데이터의 명시적 API 역명을 우선 사용하면서 괄호형 부역명과 `역`/`순환` 접미 표기를 정규화한다.

## 4. 구현 내용

* `RouteLine`에 선택형 `timetableLineName`을 추가하고 1~9호선 자산에 값을 기록했다.
* `DirectionalRoute`와 `TrainArrival`이 시간표 노선명을 전달하도록 연결했다.
* `SeoulArrivalRepository.subwayLineName()`의 하드코딩된 ID→노선명 `when`을 제거했다.
* `SeoulTrainStopRepository.supportedLines`의 `(1..9)` 하드코딩을 제거했다.
* 실시간 API 역명 비교가 `자양(뚝섬한강공원)`, `서울/서울역`, `응암순환(상선)`과 같은 표기 차이를 처리하도록 일반화했다.
* 실제 카드의 디버그 판정 정보 표시는 유지했다.
* 전체 노선 데이터의 모든 서비스 edge가 Resolver에서 처리되는지 자동 테스트를 추가했다.

## 5. 검증 결과

* 노선 자산 검증: 성공 — 9개 노선, 16개 서비스, 634개 서비스 edge, 398개 고유 역.
* 제한 하드코딩 검색: `LINE_ONE_DOWN_STATIONS`, `subwayLineName`, `supportedLines`, 1호선 전용 비교 및 Java/Kotlin 코드의 `subwayId="1001"` 없음.
* `testDebugUnitTest`: 54개 성공, 실패 0, 오류 0, 건너뜀 0.
* 전체 서비스 edge를 데이터 기반 Resolver로 해석하는 테스트와 7호선 괄호형 API 역명·노선 메타데이터 전달 테스트를 추가했다.
* `compileDebugAndroidTestKotlin`: 성공.
* `lintDebug`: 성공, 오류 0, 경고 3.
* `assembleDebug`: 성공.

## 6. 남은 작업 / 보류 사항

* 서울시 실시간 도착 API 자체가 제공하지 않는 서울 외 구간은 코드 제한을 제거해도 데이터가 반환되지 않을 수 있다. 이는 다른 공급자 연동 또는 서버 통합이 필요한 별도 범위다.
* 2호선 순환 본선은 두 방향이 모두 유효하므로 하드코딩으로 한 방향을 고르지 않고 방향 선택 필요 상태를 유지한다.
