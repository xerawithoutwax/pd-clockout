# V3.4.9 → Android Lite 포팅 메모

기준 소스: `엠빅칼퇴도우미_V3.4.9_source.zip`

## 그대로 유지한 정책

- `extractMediaURLs`: 구성안의 모든 http/https URL을 원래 순서대로 잡고, 미지원 URL도 그림 번호를 차지함.
  - Android: `UrlTools.extractUrls`, `UrlTools.buildItems`
- URL 정규화: YouTube/Instagram/Facebook/TikTok 공유 링크의 추적 파라미터를 정리.
  - Android: `UrlTools.normalize`
- 중복: YouTube는 video ID, 나머지는 정규 URL 기준으로 최초 1회만 다운로드.
  - Android: `UrlTools.identityKey`, `DownloadWorker.duplicateGroups`
- 다중 링크 파일명 접두사: `그림N_`, 중복 그룹은 `(그림1, 3, 4)_`.
  - Android: `DownloadWorker.titlePrefix`
- 제목 20자 자연 절단 및 Windows 금지문자 제거.
  - Android: `NameTools.smartTrimTitle`, `NameTools.sanitize`
- 최종 파일명: `제목_출처플랫폼_계정.ext`.
  - Android: `NameTools.buildFinalName`
- 1080/720/480: H.264(avc1) + AAC(mp4a) 우선 포맷 선택.
  - Android: `DownloadMode.formatSelector`
- 1440/2160: 고해상도 원본 우선 + MP4/H.264/AAC 지향 후처리 옵션.
  - Android: `RequestFactory`
- YouTube 실패 복구: IPv4 + 대체 player 재시도, 코어성 오류면 nightly 갱신 후 1회 재시도.
  - Android: `DownloadWorker.downloadWithRecovery`
- TikTok 공개 URL: PC와 동일 계열의 TikWM task API fallback.
  - Android: `TikWmFallback`

## Android에 맞게 바꾼 부분

- Win32 GUI → 단일 Activity.
- Windows 임의 경로/NAS → 앱 내부 임시 폴더 + MediaStore `Download/PD칼퇴도우미` 게시.
- goroutine/exec.Cmd → WorkManager foreground Worker + youtubedl-android process ID 취소.
- Windows 업데이트/NAS 배포 → 제외.
- 아카이브 검색, 뷰카운터, TVING 업로더 → 제외.

## 첫 실기기에서 반드시 확인할 항목

1. YouTube 1080p 영상+음성 병합
2. 1440p/2160p 결과의 실제 codec을 MediaInfo로 H.264/AAC 확인
3. Premiere Pro import
4. Instagram Reel / Facebook Reel
5. TikTok 공개 URL
6. Android 공유 메뉴 진입
7. 화면 OFF 다운로드 지속
8. 취소 시 현재 다운로드 종료

고해상도는 PC판처럼 별도 `ffmpeg.exe`를 직접 호출하는 구조가 아니라 yt-dlp 후처리 체인을 이용한다. 따라서 첫 실기기 빌드에서 1440/2160 codec 검증을 통과시키기 전까지 PC판과 완전 동등하다고 간주하지 않는다.
