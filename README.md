# PD칼퇴도우미 (Android) v0.1.0

Windows `엠빅칼퇴도우미 V3.4.9`의 **다운로더 기능만** Android용으로 재구성한 1차 소스입니다.

## 구현 범위

- 구성안 전체 붙여넣기 → 모든 `http/https` URL 순서대로 추출
- 미지원 링크도 `그림N` 번호를 차지하도록 보존
- 동일 YouTube 영상/동일 URL 중복 다운로드 생략 + 최초 파일에 중복 그림 번호 병기
- YouTube / Instagram / Facebook / TikTok 분류
- URL 추적 파라미터 정리
- 최대 2160p / 1440p / 1080p / 720p / 480p / MP3
- 1080p 이하는 V3.4.9와 같은 H.264(avc1)+AAC(mp4a) 우선 포맷 선택
- 1440p/2160p는 MP4/H.264/AAC Premiere 지향 yt-dlp 후처리 체인 (첫 실기기 codec 검증 필요)
- 파일명: `그림N_제목_출처플랫폼_계정.ext` (제목 20자 자연 절단)
- Android 공유 메뉴 → `PD칼퇴도우미`로 URL 전달
- foreground WorkManager 다운로드 / 진행률 / 취소
- 결과는 `Download/PD칼퇴도우미`에 MediaStore로 게시
- yt-dlp nightly 코어 수동 업데이트 및 코어성 오류 1회 자동 복구
- YouTube 403 시 V3.4.9 계열 대체 player 재시도

## Android 쪽에서 달라진 구조

Windows용 Go/Win32 실행 파일을 포팅한 것이 아니라, V3.4.9의 다운로드 정책을 Android Java로 다시 구현했습니다.
yt-dlp와 FFmpeg는 `youtubedl-android 0.18.1`을 사용합니다.

원본 앱처럼 임의 Windows 경로에 직접 쓰지 않고 앱 내부 작업폴더에 먼저 받은 뒤 MediaStore를 통해
`Download/PD칼퇴도우미`로 옮깁니다. Android Scoped Storage 대응을 위해 의도한 구조입니다.

## 빌드

1. Android Studio에서 이 폴더를 엽니다.
2. Android SDK 36을 설치합니다.
3. JDK 17 이상을 사용합니다.
4. Gradle Sync 후 `app`을 빌드합니다.
5. 사내용이면 `Build > Build APK(s)`의 debug APK부터 실기기 테스트를 권장합니다.
6. 또는 포함된 `.github/workflows/android-debug.yml`로 GitHub Actions에서 debug APK를 빌드할 수 있습니다.

현재 이 생성 환경에는 Android SDK가 없어 APK까지 실제 컴파일 검증하지는 못했습니다.
첫 실기기 검증은 특히 아래 순서로 권장합니다.

1. YouTube 1080p 1건
2. 구성안에 지원/미지원 URL이 섞인 다중 링크
3. 동일 YouTube URL 중복
4. Instagram Reel
5. Facebook Reel
6. TikTok
7. 1440p/2160p 후 Premiere import
8. 화면 OFF 상태 foreground 다운로드
9. 취소 버튼으로 실제 yt-dlp 프로세스 종료 여부

## 현재 1차판에서 의도적으로 제외/보류

- 아카이브 검색 / 뷰카운터 / TVING 업로더
- 아카이브의 `uploadN_..._video/thumb` 백업 다운로드 UI
- Windows NAS 자동 업데이트
- Instagram 캐러셀에서 `img_index`가 없는 경우 사진을 건너뛰고 첫 영상 슬라이드를 자동 탐색하는 V3.4.9의 추가 probe
- TikTok 영상은 PC V3.4.9처럼 TikWM 공개 URL 보조 엔진을 사용합니다. MP3 모드만 yt-dlp 직접 처리합니다.

마지막 두 항목은 실기기 테스트 결과가 필요합니다. Android 라이브러리 자체의 Instagram/16KB page 관련 공개 이슈가 있어
첫 APK에서 동작 확인 후 fallback을 붙이는 편이 안전합니다.


## 배포 전 확인

`youtubedl-android`는 GPL-3.0 라이선스 프로젝트입니다. 사내 테스트를 넘어 APK를 제3자에게 배포하거나 공개 배포할 경우 의존 라이브러리의 라이선스 의무를 별도로 검토하세요.

2026-09에 upstream에는 일부 16KB page-size 단말에서 FFmpeg 네이티브 라이브러리 로딩이 실패할 수 있다는 공개 이슈가 올라와 있습니다. 이 때문에 첫 APK는 반드시 실제 사용 단말에서 1080p 병합/MP3/고해상도 후처리까지 확인하는 것을 전제로 합니다.
