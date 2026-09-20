Firebase(FCM) 관련 파일입니다. 현재 비활성 상태입니다.

활성화하려면:
1. Firebase 콘솔에서 프로젝트 생성 → 패키지명 com.ulsan.disasteralert 로 앱 등록
2. google-services.json 을 app/ 폴더에 배치
3. build.gradle (루트) 의 google-services classpath 주석 해제
4. app/build.gradle 의 google-services 플러그인 + firebase 의존성 주석 해제
5. AndroidManifest.xml 의 DisasterFcmService 및 meta-data 블록 주석 해제
6. 이 폴더의 DisasterFcmService.kt 를
   app/src/main/java/com/ulsan/disasteralert/notification/ 로 이동

Firebase 없이도 15분 주기 로컬 폴링은 정상 동작합니다.
FCM은 그보다 빠른 긴급 푸시가 필요할 때만 씁니다.
