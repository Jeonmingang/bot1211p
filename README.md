# LegendarySpawnDiscord (1.3.0)

Pixelmon 9.3.9 (MC 1.21.1 / NeoForge / Arclight)에서 전설 포켓몬 스폰을 감지하여 **디스코드**로 알림을 보내는 Spigot 플러그인입니다.

## 핵심
- **웹훅 모드(권장)**: 디스코드 웹훅만으로 전송. JDA 종료 시 `ShutdownEvent` CNFE 회피.
- **JDA 모드(선택)**: 봇 토큰/채널 ID 사용. (JDA 클래스 프리로드 + shade 리로케이션으로 충돌 최소화)
- **NeoForge `EVENT_BUS`**에 **LegendarySpawnEvent.DoSpawn** 리스너 등록. 실패 시 **로그 패턴 감시**로 폴백.

## 설치
1. `mvn -U -B -DskipTests package` → `target/LegendarySpawnDiscord-1.3.0.jar` 생성.
2. JAR을 `plugins/`에 넣고 서버 실행.
3. 생성된 `plugins/LegendarySpawnDiscord/config.yml` 편집:
   - 웹훅 사용(추천): `discord.webhook_url` 입력.
   - 봇 사용: `discord.token`, `discord.channel_id` 입력(권한: 메시지/임베드).

## 호환성
- Java 21 필요. 최소 NeoForge 버전 21.1.200 권장 (Pixelmon 1.21.1 요구사항 기준).

## 문제 해결
- 알림 미출력 시: Pixelmon 설치 확인 → EVENT_BUS 등록 로그 확인 → 안 되면 fallback 로그 패턴을 사용(기본 켜짐).
