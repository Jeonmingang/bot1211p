# LegendAlert (v1.0.1)

- 콘솔 로그를 Log4j2 Appender로 감시
- 2줄 로그(알림 + 좌표)를 4초 윈도우로 자동 결합해서 바이옴/좌표/근처플레이어를 최대한 채집
- 전설 감지 시 인게임 방송 + 디스코드 웹훅 발송
- 옵션: 서버채팅 → 디스코드 웹훅 단방향 브릿지

## 설치
1) JAR을 `plugins/`에 넣고 서버 1회 실행 → `config.yml` 생성  
2) `webhook_url`에 디스코드 웹훅 URL 입력  
3) `/legendalert reload`

## 명령어
- `/legendalert reload`
- `/legendalert test <Pokemon>`

## 로그 패턴
`config.yml > patterns`의 named group: `pokemon`, `biome`, `location`, `x`, `y`, `z`  
x/y/z가 잡히면 `{location}`은 `x=.. y=.. z=..`로 자동 조립.

