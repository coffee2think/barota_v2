# 2026-09-17 GitHub 업로드 제외 규칙 정리

## 1. 배경 / 문제

- GitHub 업로드 전에 로컬 설정, API 키, 서명 정보와 생성 파일을 Git 관리 대상에서 제외할 필요가 있었다.
- 기존 규칙은 Gradle 캐시, IDE 설정, local.properties와 기본 서명 키를 제외했지만 Kotlin 캐시, 환경 변수 파일, 별도로 복사한 배포 패키지 등에 대한 규칙은 없었다.

## 2. 검토한 선택지

- 프로젝트에 맞는 파일·디렉터리 패턴을 추가하면서 소스, 설정 예제와 빌드에 필요한 파일을 유지하는 방법을 검토했다.
- JAR 또는 properties 파일 전체를 제외하면 Gradle Wrapper나 공유 빌드 설정까지 빠질 수 있어 적용하지 않았다.

## 3. 결정 및 이유

- Android/Gradle 프로젝트의 생성 파일 및 로컬 전용 설정을 용도별로 제외한다.
- API 키를 읽는 local.properties의 기존 제외 규칙을 유지하고, 환경 변수 예제는 공유할 수 있도록 예외를 둔다.
- 개발 문서, 공개 역 데이터 CSV, Gradle Wrapper와 gradle.properties는 저장소에 포함할 수 있도록 유지한다.

## 4. 구현 내용

- .gitignore를 캐시·빌드 산출물, IDE 설정, 비공개 설정, 서명 키, Android 패키지·프로파일, 로그·임시 파일, OS 메타데이터로 정리했다.
- .kotlin/, out/, .vscode/, .env 계열, secrets.properties, keystore.properties, key.properties와 서명 키 확장자를 추가했다.
- APK/AAB/APKS, 프로파일과 로그·임시 파일 등을 추가했다.
- build/ 패턴은 모든 깊이의 build 디렉터리에 적용되므로 중복된 */build/ 규칙을 제거했다.

## 5. 검증 결과

- git check-ignore로 제외할 경로 30개와 유지할 경로 14개를 확인했고 모두 예상대로 동작했다.
- local.properties.example, 환경 변수 예제, Gradle Wrapper JAR, 소스 데이터 및 개발 기록은 제외되지 않는다.
- 현재 Git 추적 파일은 없고, 이미 추적 중인 제외 대상도 없음을 확인했다.
- git diff --check는 성공했다. 변경 파일은 아직 미추적 상태이므로 이 명령의 검사 범위에는 포함되지 않는다.
- 앱 실행 동작을 변경하지 않아 빌드 및 앱 테스트는 실행하지 않았다.

## 6. 남은 작업 / 보류 사항

- 향후 별도 이름의 비공개 설정 파일을 추가하면 해당 경로를 제외 규칙에 추가한다.
- 이번 검증은 제외 규칙에 대한 확인이며 소스 및 전체 Git 이력의 비밀 정보 검사는 수행하지 않았다.
