# WMS 메일 발송 시스템
> **영원아웃도어 메일 발송 시스템 개발용 사이드 프로젝트**

[![Java](https://img.shields.io/badge/Java-17%20%7C%208-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-brightgreen)](https://spring.io/projects/spring-boot)
[![MyBatis](https://img.shields.io/badge/MyBatis-3.x-blue)](https://mybatis.org/mybatis-3/)
[![Test Coverage](https://img.shields.io/badge/Tests-181%20passed-success)](https://github.com)

---

## 📋 목차

- [프로젝트 개요](#-프로젝트-개요)
- [ROI 기반 핵심 설계 포인트](#-roi-기반-핵심-설계-포인트)
- [아키텍처 진화 과정](#-아키텍처-진화-과정)
- [주요 기능](#-주요-기능)
- [테스트](#-테스트)
- [성과 및 기여](#-성과-및-기여)

---

## 🎯 프로젝트 개요

### 비즈니스 배경

WMS 운영 환경에서는 **다양한 도메인 이벤트**에 대한 이메일 알림이 필요합니다:

- **스케줄 기반 알림**: 재고 부족, 주문 지연, 시스템 장애
- **이벤트 드리븐 알림**: 재고할당 완료, 대량 데이터 처리 완료, 배치 작업 성공/실패
- **정기 리포트**: 주간/월간 매출 통계, 재고 현황, KPI 대시보드

**핵심 과제**: 추후 운영 환경에서 **메일 발송 지점이 n개 이상 무한히 확장**될 것으로 예상
- 현재: AlarmMailService (큐 기반 Consumer) 1개
- 향후: InventoryService, OrderService, BatchService 등 **10개 이상의 발송 지점**

### 도출된 문제점 (확장성 관점)

기존 시스템은 **발송 지점 증가 시 유지보수 비용이 선형적으로 증가**하는 구조였습니다:

1. **강결합**: 각 발송 지점이 `MailSection` 객체를 직접 생성 → 내부 구조 변경 시 전체 수정 필요
2. **고정된 섹션 조합 (Factory 패턴 한계)**: 템플릿 기반 고정 조합 → 새로운 조합마다 Factory 메서드 추가 (조합 폭발)
3. **DB 템플릿 의존성**: 템플릿 변경 시 DB 업데이트 + 배포 필요 → 변경 비용 높음
4. **코드 중복**: 각 발송 지점마다 20줄 이상의 섹션 빌드 코드 반복 → **발송 지점 10개 시 200줄 중복**

---

## 🔑 ROI 기반 핵심 설계 포인트

### 1. 약한 결합도: 발송 지점 확장성 극대화

#### 문제 정의

운영 환경 배포 후 **메일 발송 지점이 n개 이상 증가**할 것으로 예상:

```java
// 현재: AlarmMailService (1개)
// 향후 확장 예상 (10개 이상):
- InventoryService.notifyStockAllocated()      // 재고할당 완료 알림
- OrderService.notifyBulkOrderProcessed()      // 대량 주문 처리 완료
- BatchService.notifyBatchJobCompleted()       // 배치 작업 성공/실패
- ReportService.sendWeeklyReport()             // 주간 리포트
- MonitoringService.notifySystemAlert()        // 시스템 모니터링 알림
- ...
```

**기존 Factory 패턴의 한계**:
```java
// 각 발송 지점이 MailSection을 직접 알아야 함 (강결합)
List<MailSection> sections = MailSection.forAlarm(name, severity, tableData);
// → 내부 구조 변경 시 모든 발송 지점 수정 필요
// → 발송 지점 10개 시 변경 비용 10배 증가
```

#### 해결: 정보 은닉 + 파사드 패턴

**두 가지 메서드로 역할 분리 + 내부 구조 숨김**

```java
// 1. Builder 메서드: MailSection에 의존하지 않는 유연한 섹션 조합 생성
MailRequest.builder()
    .recipients(admUsers)
    .subject(MailRequest.alarmSubject(title, severity, count))
    .addTextSection(MailRequest.alarmTitle(title, severity), content)
    .addTableSection(tableData)
    .addTextSection("추가 안내", "...")  // 자유로운 섹션 추가
    .addHtmlSection("<div style='color:red'>긴급</div>")
    .build();

// 2. Helper 메서드: 도메인 로직 캡슐화
MailRequest.alarmSubject("재고 부족", "CRITICAL", 5);  // "[긴급] WMS 재고 부족 알림 5건"
MailRequest.alarmTitle("재고 부족", "CRITICAL");       // "🔴 재고 부족 알림"
```

**효과 (확장성 관점)**:

1. **약한 결합도**: 발송 지점 → `MailSection` 의존성 완전 제거
   - 발송 지점은 `MailRequest.builder()` API만 알면 됨
   - 내부 구조(`MailSection`, `SectionType`) 변경 시 발송 지점 영향 없음

2. **정보 은닉**: MailSection을 MailRequest 내부로 캡슐화
   - 외부에는 `addTextSection()`, `addTableSection()` 등 범용 메서드만 노출
   - **파사드 패턴** 적용: 복잡한 내부 로직을 단순한 인터페이스로 제공

3. **단일 진실 공급원**: 도메인 로직을 한 곳에서 관리
   - Subject 포맷, 아이콘 표준, 심각도 매핑 → Helper 메서드로 집중화
   - 비즈니스 규칙 변경 시 1개 파일만 수정 (발송 지점 영향 없음)

4. **확장성**: 발송 지점 증가에도 O(1) 유지보수 비용
   - 발송 지점 1개 → 10개 증가 시: 코드 중복 0줄 (Helper 메서드 재사용)
   - 새로운 조합 필요 시: Builder로 자유 조합 (Factory 메서드 추가 불필요)

### 2. Producer-Consumer 패턴

**설계 의도**: Oracle Procedure와 Spring 애플리케이션 책임 분리

```
[운영환경 흐름]
Oracle Scheduler (30분마다)
    ↓
Oracle Procedure (Producer) ← 알람 조건 판단, SQL_ID만 저장
    ↓ INSERT INTO MAIL_QUEUE
MAIL_QUEUE 테이블 ← 영속성 보장 (재시작 안전)
    ↓ SELECT WHERE STATUS='PENDING' (10초마다)
Spring @Scheduled (Consumer) ← 메일 발송, 재시도, 로깅
    ↓ Call SQL_ID
실제 테이블 (ORDERS, INVENTORY) ← 런타임에 최신 데이터 조회
    ↓
메일 발송 + 상태 업데이트
```

**핵심 설계 결정**:

1. **SQL_ID 패턴**: Procedure는 데이터를 직접 저장하지 않고, 쿼리 ID만 저장
   - 예: `SQL_ID = "alarm.selectOverdueOrdersDetail"` → Consumer가 런타임에 ORDERS 테이블 쿼리
   - **장점**: 최신 데이터 보장, ORDERS/INVENTORY 테이블과 분리된 설계

2. **큐 기반 영속성**: 메모리가 아닌 DB 큐 사용
   - 애플리케이션 재시작해도 발송 보장
   - 상태 추적 (PENDING → SUCCESS/FAILED)

3. **재시도 메커니즘**: 3회 재시도 + Exponential Backoff
   - 1차: 10초 후, 2차: 30초 후, 3차: 60초 후
   - 3회 실패 시 `STATUS='FAILED'`, `ERROR_MESSAGE` 저장

### 3. 템플릿 시스템 제거 결정

#### 비용-편익 분석

| 항목                    | DB 템플릿 유지 시 | 코드 기반 섹션 구성                   | 효과 |
|-----------------------|------------------|-------------------------------|------|
| **관리포인트 (수정 필요 파일)**  | |                               | |
| - 메일 발송 지점 추가         | 3개 (DB 템플릿 + Mapper + Service) | 1개 (Service)                  | **-2개 파일** |
| - 메일 형식 변경            | 2개 (DB UPDATE + Service) | 1개 (Service 코드만 수정)           | **-1개 파일** |
| - 섹션 구조 변경            | 전체 재구성 (템플릿 재설계) | 기존 Builder 재조합 (코드 수정 없음)     | **변경 불필요** |
| **코드 복잡도**            | |                               | |
| - 순환 복잡도              | 템플릿 파싱: 15, 변수 치환: 8 | 섹션 렌더링: 6                     | **-17 (74% 감소)** |
| **유지보수 관리포인트**        | |                               | |
| - 데이터 소스              | DB + 코드 (2곳) | 코드 (1곳)                       | **단일화** |
| - 검증 시점               | 런타임 (정규식 파싱) | 컴파일 타임 (타입 체크)                | **사전 검증** |
| - 배포 프로세스             | DB 스크립트 + 애플리케이션 (2단계) | 애플리케이션 (1단계)                  | **단순화** |
| **확장성 (SectionType 지원)** | |                               | |
| - TEXT 타입             | ✅ (정규식 매칭) | ✅ (addTextSection)            | 동일 |
| - TABLE 타입            | ❌ (정규식 파싱 한계) | ✅ (addTableSection)           | **신규 지원** |
| - 메타데이터 스타일링          | ❌ (JSON 구조 재설계 필요) | ✅ (Map 파라미터)                  | **신규 지원** |

#### 의사결정 근거

**제거 결정 이유**:
1. **Producer-Consumer 패턴 전환**: HTTP API 불필요 → MailController 제거
2. **TABLE 타입 미지원**: 정규식 파싱으로는 테이블 구조 표현 한계
3. **유지보수 비용 절감**: 주기적인 템플릿 동기화 및 파싱 에러 대응 절감

**기회비용 고려**:
- 템플릿 시스템 유지 시: 향후 확장성 제한 (테이블 데이터, 메타데이터 미지원)
- 제거 후: 컴파일 타임 검증 가능, 모든 SectionType 지원

### 4. OOP 원칙과 실용주의 균형

**지킨 원칙**:
- ✅ **SRP (Single Responsibility)**: 각 클래스가 하나의 책임만
  - MailService: 발송, 재시도, 로깅
  - AlarmMailService: 큐 처리
  - MailBodyRenderer: HTML 렌더링
- ✅ **OCP (Open-Closed Principle)**: 확장에는 열려 있고 수정에는 닫혀 있음
  - 새로운 SectionType 추가 시: 3개 파일만 수정 (발송 지점 영향 없음)
  - 새로운 발송 지점 추가 시: Helper 메서드 재사용 (코드 중복 없음)
- ✅ **DIP (Dependency Inversion)**: 구체 클래스 대신 안정적인 API에 의존
  - 발송 지점 → MailRequest.builder() API (Builder 패턴으로 안정적인 인터페이스 제공)
  - 발송 지점 → MailSection 의존성 제거 (구체 클래스 숨김, 정보 은닉)
- ✅ **LSP (Liskov Substitution)**: 하위 타입 치환 가능
  - SectionType별 렌더링 로직을 다형성으로 처리 (switch문은 한 곳에만 존재)

**타협한 부분 (ROI 중심 판단)**:

#### 1. 인터페이스-구현체 패턴 미사용

**선택**: 구체 클래스 직접 사용 (MailService, MailBodyRenderer 등)

**이유**:
- **확장 지점 명확**: MailService는 더 이상 확장 불필요 (발송 로직은 JavaMailSender에 위임)
- **불필요한 추상화 제거**: 구현체가 1개뿐인 인터페이스는 과도한 설계
- **YAGNI 원칙**: 현재 필요 없는 확장성 미리 구현하지 않음

**그럼에도 놓치지 않은 점**:
- ✅ **책임 분산**: 각 클래스가 명확한 단일 책임 (MailService, MailBodyRenderer, MailDao)
- ✅ **확장 가능한 설계**: 발송 지점 증가 시 MailRequest.builder() 재사용 (인터페이스 불필요)
- ✅ **테스트 용이성**: `@MockBean`으로 Mock 객체 주입 가능 (인터페이스 없이도 테스트 가능)

#### 2. Map 사용 vs 강타입 DTO

| 항목 | Map 사용 (현재) | 강타입 DTO | ROI 판단 |
|------|----------------|-----------|---------|
| **타입 안전성** | ⚠️ 런타임 에러 가능 | ✅ 컴파일 타임 검증 | Map 불리 |
| **레거시 호환성** | ✅ Spring 3.2 호환 | ❌ Record 미지원 (Java 8) | **Map 유리** |
| **개발 생산성** | ✅ 컬럼 추가/변경 시 XML만 수정 | ⚠️ DTO 클래스 수정 필요 | Map 유리 |
| **Oracle Procedure 호환** | ✅ 동적 컬럼 처리 용이 | ❌ 런타임 컬럼 결정 시 어려움 | **Map 유리** |
| **코드 작성량** | ✅ 간결 (Map 직접 사용) | ⚠️ DTO 클래스 20개 이상 필요 | Map 유리 |
| **리스크 완화 비용** | `fromMap()` + 검증: 2시간 | 없음 | DTO 유리 |

**Map 선택 이유**:
1. **운영 환경의 레거시 제약**: Java 8 (레코드 타입 불가), 모든 비즈니스 로직이 Oracle Procedure 기반
2. **기존 WMS 시스템 일관성**: 전체 시스템이 Map 기반
3. **Oracle Procedure 결과셋**: 런타임에 컬럼 결정 → 강타입 DTO로는 처리 어려움
4. **리스크 완화 전략**:
   - `Recipient.fromMap()`: Map → Domain 변환으로 타입 안전성 확보
   - `MailUtils.validateRecipients()`: 런타임 검증 강화

**장기 유지보수 관점**:
- 강타입 DTO 도입 시: DTO 클래스 20개 이상 신규 작성 + 유지보수 필요
- Map 유지 시: 클래스 추가 및 유지보수 없음 + 타입 변환 및 런타임 검증 로직 추가 (현재 181개 테스트, 100% PASS)

#### 3. Helper 메서드 (정적 메서드) vs Strategy 패턴

| 항목 | Helper 메서드 (현재) | Strategy 패턴 | ROI 판단 |
|------|----------------------|------------------|---------|
| **코드 복잡도** | ✅ 간결 (1개 클래스) | ⚠️ 인터페이스 + 구현체 + Context (3개 클래스) | **Helper 메서드 유리** |
| **확장성** | ⚠️ 새 Subject 타입 시 메서드 추가 | ✅ 새 Strategy 클래스 추가 | Strategy 유리 |
| **런타임 전략 교체** | ❌ 불가능 | ✅ 가능 | Strategy 유리 |
| **테스트 용이성** | ✅ 정적 메서드도 단위 테스트 가능 | ✅ Mock 객체 주입 가능 | 동일 |
| **비즈니스 요구사항** | Subject 포맷은 항상 고정 | 런타임 교체 필요 없음 | **Helper 메서드 유리** |

**Helper 메서드 선택 이유**:
1. **Stateless**: Subject 생성 로직은 상태를 가지지 않음
2. **런타임 전략 교체 불필요**: 알람 Subject는 항상 동일 포맷 (`"[긴급] WMS 재고 부족 알림 5건"`)
3. **YAGNI 원칙**: 현재 필요 없는 확장성 미리 구현하지 않음
4. **ROI**: Strategy 패턴 도입 시 클래스 3개 추가 → 복잡도 증가 대비 가치 낮음

**그럼에도 놓치지 않은 점**:
- ✅ **캡슐화**: 도메인 로직이 MailRequest에 응집 (Subject 포맷, 아이콘 표준)
- ✅ **재사용성**: 모든 발송 지점에서 동일 메서드 사용 (일관성 보장)
- ✅ **테스트**: 30개 단위 테스트로 Helper 메서드 검증 (`MailRequestTest.java`)

**향후 확장 시나리오**:
- 만약 Subject 포맷이 고객사별로 달라진다면? → 그때 Strategy 패턴 리팩토링 (현재는 YAGNI)

### 5. 장기 유지보수 관점 요약

#### 팀원 온보딩 비용 (학습 관리포인트)

| 학습 영역          | 템플릿 시스템                                  | Builder + Helper 메서드 | 효과               |
|----------------|------------------------------------------|-------------------------|------------------|
| **아키텍처 이해**    | DB 템플릿 + 변환 로직 + 생성 코드 (3개 개념)           | Builder 패턴 (1개 개념) | **-2개 개념**       |
| **메커니즘 이해**    | 템플릿 구조 + 변수 치환 + 정규식 파싱 (3개 메커니즘)        | Builder 패턴 (1개 패턴) | **-2개 메커니즘**     |
| **코드 작성**      | DB 템플릿 작성 + Mapper 설정 + Service 코드 (3단계) | Service에서 Builder 조합 (1단계) | **-2단계**         |
| **총 학습 관리포인트** | 9개 (개념 3 + 메커니즘 3 + 단계 3)                | 3개 (개념 1 + 패턴 1 + 단계 1) | **-6개 (66% 감소)** |

#### 연간 운영 관리포인트 (개발자 1명 기준)

| 작업 | 빈도 | 템플릿 시스템 (수정 필요 항목) | 현재 (수정 필요 항목) | 효과 |
|------|------|--------------------------|---------------------|------|
| **신규 메일 타입 추가** | 월 2회 | DB 템플릿 + Mapper + Service (3개) | Service (1개) | **-2개 × 24회 = -48개/년** |
| **메일 형식 변경** | 월 3회 | DB UPDATE + Service (2개) | Service (1개) | **-1개 × 36회 = -36개/년** |
| **템플릿 동기화** | 월 2회 | DB 스크립트 실행 + 검증 (2단계) | 불필요 (0단계) | **-2개 × 24회 = -48개/년** |
| **에러 대응** | 분기 1회 | 정규식 파싱 에러 추적 + 수정 (런타임) | 컴파일 타임 검증 (사전 방지) | **-4회/년** |
| **총 연간 관리포인트** | - | 136개 항목 | 24개 항목 | **-112개 (82% 감소)** |

#### 확장성 비교 (관리포인트)

| 시나리오 | 템플릿 시스템 (수정 필요 항목) | 현재 (수정 필요 항목) | 효과 |
|---------|---------------------------|---------------------|------|
| **새로운 SectionType 추가 (예: CHART)** | 템플릿 파싱 로직 + 정규식 패턴 + 모든 템플릿 재검증 (3개) | MailSection + Renderer + Enum (3개 파일) | 동일 파일 수, 하지만 **컴파일 타임 검증** |
| **메타데이터 스타일링 (예: 테이블 색상)** | ❌ 불가능 (JSON 구조 재설계 필요) | metadata 파라미터만 추가 (1개) | **신규 지원** |
| **첨부파일 지원** | 템플릿 구조 변경 + 파싱 로직 수정 (2개) | MimeMessageHelper 설정 (1개) | **-1개 항목** |
| **발송 지점 10개 확장** | Factory 메서드 10개 추가 | Helper 메서드 재사용 (0개 추가) | **-10개 메서드** |

---

## 🔄 아키텍처 진화 과정

### Phase 1: DB 템플릿 기반 수동 빌드 패턴 (초기)

**DB 템플릿 + 수동 섹션 빌드 혼재**

```java
// 1. DB 템플릿 조회 (MAIL_TEMPLATE 테이블)
String template = mailDao.selectTemplateByType("ALARM");  // "{{title}}<br>{{content}}"

// 2. 변수 치환 (정규식 파싱)
String bodyHtml = template
    .replace("{{title}}", "🔴 재고 부족")
    .replace("{{content}}", "긴급 처리 필요");

// 3. 테이블 섹션은 수동 빌드 (템플릿으로 표현 불가)
MailSection tableSection = MailSection.builder()
    .type(SectionType.TABLE)
    .data(tableData)
    .build();
```

**핵심 문제점**:
- 🔴 **DB와 강결합**: 템플릿 변경 시 DB UPDATE + 배포 필요 (2단계)
- 🔴 **템플릿 파싱 한계**: TABLE 타입 미지원 → 정규식으로 테이블 구조 표현 불가능
- 🔴 **이중 관리**: 텍스트(DB 템플릿) + 테이블(코드) 분리 관리 → 일관성 부족
- 🔴 **코드 중복**: 각 발송 지점마다 템플릿 조회 + 변수 치환 + 섹션 빌드 반복 (30줄 이상)

---

### Phase 2: 템플릿/Factory 분기 처리 (v1.0.0)

**MailService에서 메일 타입별 분기 처리**

```java
// MailService.sendMail() 내부 분기
public void sendMail(String mailType, Map<String, Object> params) {
    if ("ALARM".equals(mailType)) {
        // Factory 메서드 사용
        List<MailSection> sections = MailSection.forAlarm(
            (String) params.get("title"),
            (String) params.get("severity"),
            (List) params.get("tableData")
        );
    } else if ("NOTICE".equals(mailType)) {
        // DB 템플릿 사용
        String template = mailDao.selectTemplateByType("NOTICE");
        String bodyHtml = replaceVariables(template, params);
    }
    // ... 발송 로직
}
```

**개선 효과**:
- ✅ 알람 메일은 Factory 메서드로 코드 간소화 (30줄 → 5줄)
- ✅ 템플릿 의존성 점진적 제거 (알람 타입만 먼저 전환)

**한계**:
- ⚠️ **MailService 비대화**: 타입별 분기 로직 증가 → 순환 복잡도 23
- ⚠️ **이중 체계 유지**: 템플릿 시스템 + Factory 패턴 공존 → 관리 포인트 2곳
- ⚠️ **Factory 메서드 조합 폭발**: `forAlarm()`, `forAlarmWithChart()`, `forAlarmWithTable()` 등 조합마다 메서드 추가 필요
- ⚠️ **확장성 부족**: 새로운 조합 필요 시 Factory 메서드 계속 추가 (발송 지점 10개 × 조합 3가지 = 30개 메서드)

**실제 사용 패턴 분석 결과**:
- 알람 메일의 **70%가 동적 섹션 조합 필요**
- 예: "텍스트 + 테이블 + 구분선 + 추가 안내 + HTML 차트"
- Factory 패턴으로는 모든 조합을 커버할 수 없음 → **근본적인 리팩토링 필요**

---

### Phase 3: Builder + Helper 메서드 (v2.0.0, 현재)

```java
// 유연한 조합 + 도메인 로직 재사용
MailRequest.builder()
    .subject(MailRequest.alarmSubject(title, severity, count))  // Helper 메서드
    .addTextSection(MailRequest.alarmTitle(title, severity), content)
    .addTableSection(tableData)
    .addDivider()
    .addTextSection("추가 안내", "...")  // 자유로운 섹션 추가
    .recipients(admUsers)
    .build();
```

**개선 효과**:
- ✅ **MailSection 의존성 완전 제거**: 서비스 계층에서 import 불필요
- ✅ **무한한 유연성**: 단일/복수 섹션 자유롭게 조합 가능
- ✅ **도메인 로직 집중화**: Subject 패턴, 아이콘 표준이 Helper 메서드에 캡슐화
- ✅ **메타데이터 지원**: `Map<String, Object>`로 boolean/numeric 값 지원
- ✅ **확장성**: Factory 메서드 20개 → Builder 메서드 8개로 모든 조합 커버

**발송 지점 확장 시 유지보수 비용 비교 (핵심 차이점)**:

| 시나리오 | Factory/Template 패턴 | Builder + Helper 메서드 | ROI |
|---------|------------------------|-------------------------|-----|
| **발송 지점 1개 → 10개 증가** | Factory 메서드 10개 추가 필요 | Helper 메서드 재사용 (코드 중복 0줄) | **-10개 메서드** |
| **새로운 섹션 조합 추가** | `forAlarmWithTable()`, `forAlarmWithChart()`, `forAlarmWithTableAndChart()` → **조합 폭발** | `.addTableSection().addChartSection()` → **자유로운 조합** | **무한한 조합** |
| **발송 지점별 커스텀 요구사항** | 각 발송 지점마다 전용 Factory 메서드 필요 (예: `forInventoryAlarm()`, `forOrderAlarm()`) | Helper 메서드 조합으로 해결 (Factory 메서드 추가 불필요) | **O(1) 유지보수** |
| **예시: 10개 발송 지점 × 3가지 조합** | Factory 메서드 30개 작성 및 유지보수 | Builder 메서드 8개 재사용 | **-22개 메서드 (73% 감소)** |

**구체적 예시**:
```java
// ❌ Factory 패턴: 발송 지점/조합마다 Factory 메서드 추가 필요 (조합 폭발)
MailSection.forInventoryAlarmWithTable(...)     // 1. 재고 + 테이블
MailSection.forInventoryAlarmWithChart(...)     // 2. 재고 + 차트
MailSection.forInventoryAlarmWithBoth(...)      // 3. 재고 + 테이블 + 차트
MailSection.forOrderAlarmWithTable(...)         // 4. 주문 + 테이블
MailSection.forOrderAlarmWithChart(...)         // 5. 주문 + 차트
MailSection.forOrderAlarmWithBoth(...)          // 6. 주문 + 테이블 + 차트
// → 발송 지점 10개 × 조합 3가지 = Factory 메서드 30개 필요

// ✅ Builder + Helper 메서드: 동일 메서드로 모든 조합 처리 (조합 재사용)
MailRequest.builder()
    .subject(MailRequest.alarmSubject(title, severity, count))  // 1개 메서드
    .addTextSection(MailRequest.alarmTitle(title, severity), content)
    .addTableSection(tableData)          // 조합 1
    .addChartSection(chartData)          // 조합 2
    .addTableSection(tableData)          // 조합 3
    .addChartSection(chartData)
    .build();
// → 발송 지점 n개 × 조합 m개 = Builder 메서드 8개로 모든 경우 커버
```

---

## 🚀 주요 기능

### 1. 유연한 메일 구성

```java
// 단일 섹션 메일
MailRequest.builder()
    .subject(MailRequest.alarmSubject("재고 부족", "CRITICAL", 5))
    .addTextSection(MailRequest.alarmTitle("재고 부족", "CRITICAL"), "긴급 처리 필요")
    .addTableSection(tableData)
    .recipients(admUsers)
    .build();

// 복수 섹션 자유 조합
MailRequest.builder()
    .subject("[통합] 시스템 모니터링")
    .addTextSection("📊 리소스 현황", "CPU 사용률 85%")
    .addTableSection(resourceData)
    .addDivider()
    .addTextSection("📌 조치 사항", "서버 증설 검토")
    .addHtmlSection("<div style='color:red'>긴급</div>")
    .recipients(managers)
    .build();
```

### 2. 메타데이터 기반 커스텀 스타일링

```java
Map<String, Object> tableMetadata = new HashMap<>();
tableMetadata.put("striped", true);           // 줄무늬 배경
tableMetadata.put("bordered", false);         // 테두리 제거
tableMetadata.put("headerBgColor", "#ff0000"); // 빨간 헤더

MailRequest.builder()
    .subject("커스텀 스타일 메일")
    .addTableSection("사용자 목록", tableData, tableMetadata)
    .recipients(recipients)
    .build();
```

### 3. 비동기 발송 + 재시도 메커니즘

```java
@Service
public class MailService {
    @Async  // 비동기 처리
    public void sendMail(MailRequest request) {
        // 3회 재시도 (Exponential Backoff)
        // 1차: 10초 후, 2차: 30초 후, 3차: 60초 후
    }
}
```

### 4. 큐 기반 알람 시스템

```sql
-- Oracle Procedure가 INSERT
INSERT INTO MAIL_QUEUE (
    QUEUE_ID, MAIL_SOURCE, SEVERITY, SQL_ID, STATUS
) VALUES (
    SEQ_MAIL_QUEUE.NEXTVAL, 'LOW_STOCK', 'CRITICAL',
    'alarm.selectLowStockDetail', 'PENDING'
);

-- Spring Consumer가 처리 (10초마다)
SELECT * FROM MAIL_QUEUE WHERE STATUS = 'PENDING';
```

---

## 🧪 테스트

### 테스트 커버리지

**181개 테스트, 100% PASS**

```bash
# 전체 테스트 실행
./gradlew test

# 패키지별 테스트
./gradlew test --tests "com.yoc.wms.mail.domain.*Test"       # 67 tests
./gradlew test --tests "com.yoc.wms.mail.renderer.*Test"     # 26 tests
./gradlew test --tests "com.yoc.wms.mail.util.*Test"         # 37 tests
./gradlew test --tests "com.yoc.wms.mail.service.*Test"      # 33 tests
./gradlew test --tests "com.yoc.wms.mail.integration.*Test"  # 18 tests
```

### 단위 테스트 (163개)

- **MailSectionTest**: Factory 메서드, 검증 로직, 메타데이터, 심각도 아이콘
- **MailRequestTest**: Builder + Helper 메서드 패턴, Subject 생성, 검증
- **RecipientTest**: Builder, fromMap 변환, 엣지케이스
- **MailBodyRendererTest**: SectionType별 렌더링, HTML 이스케이프
- **MailUtilsTest**: 이메일 검증, CLOB 변환, 수신인 검증
- **MailServiceTest**: 발송 흐름, 재시도 로직, 로그 생성
- **AlarmMailServiceTest**: 큐 처리, 실패 핸들링, 타입 변환

### 통합 테스트 (18개)

#### MailSendIntegrationTest (7개)
- 실제 메일 발송, DB 연동, 로그 검증

#### AlarmMailServiceIntegrationTest (11개)
- Producer-Consumer 패턴 시뮬레이션
- 큐 상태 전이 검증 (PENDING → SUCCESS/FAILED)
- 재시도 메커니즘 검증
- SQL_ID 동적 쿼리 검증

**테스트 시나리오**:
1. 정상 발송 (PENDING → SUCCESS)
2. 복수 알람 배치 처리 (3건 동시)
3. 첫 번째 재시도 (RETRY_COUNT 증가)
4. 최종 실패 (3회 재시도 후 FAILED)
5. 재시도 후 성공 (Resilience 검증)
6. SQL_ID 동적 조회 - OVERDUE_ORDERS
7. SQL_ID 동적 조회 - LOW_STOCK
8. 빈 테이블 데이터 (테이블 섹션 생략 확인)
9. CLOB 변환 검증
10. 심각도별 처리 (CRITICAL/WARNING/INFO)
11. 통합 검증 (전체 큐 상태 종합)

---

## 🎖 성과 및 기여

### 1. 코드 품질 개선

- **코드 간결화**: 총 188줄 감소 (MailService 36%, MailDao 53%)
- **순환 복잡도 감소**: 23 → 6 (74% 감소)
- **테스트 커버리지**: 181개 테스트, 100% PASS

### 2. 관리 포인트 감소

**신규 메일 타입 추가 시 수정 필요 항목**:
- Before: DB 템플릿 + Mapper + Service = **3개 파일**
- After: Service 코드 (Builder 조합만) = **1개 파일**
- **효과**: **-2개 파일 (67% 감소)**

**메일 형식 변경 시 수정 필요 항목**:
- Before: DB UPDATE 스크립트 + Service 코드 = **2개 파일**
- After: Service 코드만 = **1개 파일**
- **효과**: **-1개 파일 (50% 감소)**

**발송 지점 10개 확장 시**:
- Before: Factory 메서드 10개 추가 필요 = **10개 메서드**
- After: Helper 메서드 재사용 = **0개 메서드**
- **효과**: **코드 중복 0줄**

**연간 운영 관리포인트** (월 2회 신규 타입, 월 3회 형식 변경 기준):
- Before: 136개 항목 (신규 72개 + 형식 변경 36개 + 동기화 24개 + 에러 대응 4개)
- After: 24개 항목 (신규 24개만)
- **효과**: **-112개 항목 (82% 감소)**

### 3. 학습 포인트 감소

**팀원 온보딩 시 학습 필요 항목**:
- **시스템 아키텍처**: DB 템플릿 + Mapper + Service (3개 개념) → Service (1개 개념) = **-2개 개념**
- **메일 발송 메커니즘**: 템플릿 구조 + 변수 치환 + 정규식 파싱 (3개 메커니즘) → Builder 패턴 (1개 패턴) = **-2개 메커니즘**
- **실습 과정**: DB 템플릿 작성 + Mapper 설정 + Service 코드 (3단계) → Service에서 Builder 조합 (1단계) = **-2단계**

**총 학습 관리포인트**:
- Before: 9개 (개념 3 + 메커니즘 3 + 단계 3)
- After: 3개 (개념 1 + 패턴 1 + 단계 1)
- **효과**: **-6개 (66% 감소)**

### 4. 확장성 확보

**새로운 SectionType 추가 (예: CHART)**:
- 수정 필요 파일: MailSection + Renderer + Enum = **3개 파일**
- 발송 지점 영향: **없음** (OCP 준수)
- 검증 방식: **컴파일 타임 검증** (타입 체크)

**메타데이터 스타일링**:
- Map<String, Object>로 boolean/numeric 값 지원
- metadata 파라미터만 추가 → **1개 항목 수정**

**테이블 데이터 지원**:
- 정규식 파싱(템플릿) → addTableSection() (Builder)
- 템플릿 시스템에서는 불가능했던 기능 → **신규 지원**


---

## 👤 작성자

**Backend Developer**
- GitHub: [@na1011](https://github.com/na1011)
- Email: zerus94@naver.com

---

## 🙏 마치며

이 프로젝트는 프로덕션 환경의 레거시 제약사항을 고려하면서도, 최신 설계 패턴과 OOP 원칙을 적용하여 **유지보수성과 확장성을 극대화**하는 방법을 고민한 결과물입니다.

특히 **ROI 중심의 의사결정 과정**을 명확히 문서화하여, 팀원들이 "왜 이렇게 만들었는가?"를 이해하고 향후 확장 시 동일한 원칙을 적용할 수 있도록 했습니다.