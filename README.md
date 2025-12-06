# Wallet Server (Backend Engineer Assignment)

대용량 트래픽 상황에서도 데이터 무결성을 보장하는 **월렛 동시 출금 및 잔액 관리 시스템**입니다.

## 🚀 프로젝트 실행 방법

### 1. 요구 사항
- Java 17+
- Docker & Docker Compose

### 2. 프로젝트 실행

**Option A: 전체 시스템 실행 (App + DB + Redis)**
Docker Compose로 애플리케이션과 인프라를 한 번에 실행합니다.
```bash
docker-compose up -d
```
> 서버가 8080 포트에서 실행됩니다.

**Option B: 로컬 개발 환경 실행 (DB + Redis Only)**
IDE에서 애플리케이션을 실행하고 싶다면, 인프라만 Docker로 띄웁니다.
```bash
docker-compose up -d db redis
```
이후 아래 명령어로 애플리케이션을 실행하세요.
```bash
./gradlew bootRun
```
- **Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

---

## 💡 설계 결정 및 동시성 제어 전략

### 1. 동시성 제어 기법: Redis Distributed Lock (feat. Redisson)
동시성 문제 해결을 위해 **Redis 분산 락(Distributed Lock)**을 메인 전략으로 채택했습니다.

#### 선택 이유
- **DB 부하 분산**: `SELECT ... FOR UPDATE` (Pessimistic Lock) 사용 시, 대량의 요청 대기열이 DB 커넥션을 점유하여 전체 시스템 장애(Connection Pool Exhaustion)로 이어질 수 있습니다. Redis Lock은 락 획득 대기를 Redis에서 처리하므로 DB를 보호할 수 있습니다.
- **Spin Lock 방지**: Redisson은 Pub/Sub 방식을 사용하여, 락 해제 시 구독 중인 클라이언트에게 알림을 줍니다. 이는 불필요한 재시도(Busy waiting)로 인한 Redis 부하를 줄여줍니다.

#### Trade-off 및 한계
- **인프라 의존성 증가**: Redis라는 외부 인프라에 강하게 의존하게 됩니다. Redis 장애 시 서비스 전체 장애로 이어질 위험이 있습니다.
- **네트워크 오버헤드**: DB Lock에 비해 네트워크 홉(Hop)이 추가되므로 단건 처리 응답 속도는 미세하게 느릴 수 있습니다.

### 2. 고가용성을 위한 Hybrid Fallback 전략
Redis 장애 상황을 대비하여 **DB Pessimistic Lock**을 Fallback으로 구현했습니다.
- **동작 방식**: Redis 연결 실패(`RedisConnectionException`) 감지 시, 즉시 DB Lock 모드로 전환하여 서비스 중단을 방지합니다.
- **안정성 확보**: 비록 DB 부하는 증가하겠지만, "출금 불가"라는 서비스 중단 상황보다는 "느리더라도 처리됨"을 선택하여 가용성을 확보했습니다.

### 3. Idempotency (멱등성) 보장
- **구현**: `transactionId`를 Unique Key로 관리하여 중복 요청을 방지합니다.
- **정책**: 동일한 `transactionId`로 재요청 시, 에러(409)를 반환하는 대신 **기존 성공 응답을 그대로 반환**하여 클라이언트가 안심하고 재시도(Retry) 할 수 있도록 설계했습니다.

---

## 🧪 동시성 테스트 결과

### 1. 테스트 환경
- **Target**: `POST /api/wallets/{walletId}/withdraw`
- **Condition**: 동일한 월렛 ID에 대해 다수의 스레드가 동시에 출금 요청.

### 2. 시나리오별 결과 요약

| 시나리오 | 동시 요청 수 | 결과 | 비고 |
|---|---|---|---|
| **Case 1: 제어 미적용** | 100건 | ❌ **실패** (Race Condition) | 최종 잔액 불일치 발생 (데이터 무결성 깨짐) |
| **Case 2: Redis Lock** | 2,000건 | ✅ **성공** | **Spring Retry + FairLock** 적용으로 안정적 처리 |
| **Case 3: Fallback (DB)** | 2,000건 | ✅ **성공** | Redis 장애 시 DB Lock으로 전환되어 처리 완료 |

### 3. 상세 증빙 로그

#### Case 1: 제어 미적용 (실패)
`WalletNoLockTest` 실행 결과, 100건의 요청이 모두 성공 응답을 받았음에도 잔액이 0원이 되지 않는 현상 발생.
```text
[Thread-1] ... Request processed
...
INFO ... WalletNoLockTest : === No-Lock Test Result ===
INFO ... WalletNoLockTest : Actual Balance: 930000.00 (Expected: 0)
INFO ... WalletNoLockTest : Race Condition Confirmed: Balance IS NOT ZERO
```

#### Case 2: Redis Distributed Lock 적용 (성공)
`WalletE2ETest` 실행 결과, 2,000건의 대량 트래픽 상황에서도 데이터 무결성 보장.
```text
[Thread-1998] Request success. Status: 200 OK
[Thread-1999] Request success. Status: 200 OK
...
INFO ... WalletE2ETest : Success count: 2000
INFO ... WalletE2ETest : Fail count: 0
INFO ... WalletE2ETest : Final Balance: 0
```

#### Case 3: Fallback - DB Lock 적용 (성공)
Redis 연결 끊김 시뮬레이션 상황에서 DB Lock으로 자동 전환되어 처리.
```text
ERROR ... WalletLockFacade : Redis 장애 감지! DB Lock으로 전환합니다.
INFO ... WalletFallbackE2ETest : [Thread-100] Request success. Status: 200 OK
...
INFO ... WalletFallbackE2ETest : Success count: 2000
INFO ... WalletFallbackE2ETest : Final Balance: 0
```

---

## 🛠 기술 스택
- **Language**: Java 17
- **Framework**: Spring Boot 3.4.0
- **Database**: PostgreSQL (Docker), H2 (Test)
- **Cache/Lock**: Redis (Redisson)
- **Test**: JUnit 5, Spring Boot Test, Mockito
