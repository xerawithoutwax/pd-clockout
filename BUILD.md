# PD칼퇴도우미 v0.2.0 빌드 방법

## 1) Android Studio에서 직접 APK 빌드
1. Android Studio에서 이 프로젝트 폴더를 엽니다.
2. Gradle Sync를 기다립니다.
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)`를 누릅니다.
4. 완료 후 `app/build/outputs/apk/debug/app-debug.apk`에서 APK를 꺼냅니다.

## 2) GitHub Actions로 APK 빌드
1. 프로젝트 전체를 GitHub 저장소에 올립니다.
2. `.github/workflows/android-debug.yml` 파일이 포함되어 있어야 합니다.
3. 저장소의 **Actions** 탭에서 `Build PD Clockout APK` 워크플로를 실행합니다.
4. 성공하면 **Artifacts**의 `PDClockout-v0.2.0-debug` 또는 기본 산출물에서 APK를 다운로드합니다.

## 이번 리빌드 포함 내용
- UI 전면 리디자인
- 입력창 흰 배경/흰 글자 문제 수정
- PD칼퇴도우미 블랙 워드마크 방향 반영
- yt-dlp 업데이트 채널 컴파일 오류 수정
