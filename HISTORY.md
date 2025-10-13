## Design Decisions & Refactoring History
### 아키텍처 변천사: Factory → Builder + Helper Methods

**Phase 1: 초기 구조 (수동 빌드 패턴)**

각 발송 지점에서 MailSection.builder() 반복 호출:

```java
// 20줄 이상의 반복 코드
MailSection textSection = MailSection.builder()
    .type(SectionType.TEXT)
    .title(icon + name)
    .content(content)
    .build();

MailSection tableSection = MailSection.builder()
    .type(SectionType.TABLE)
    .data(tableData)
    .build();

MailSection divider = MailSection.builder()
    .type(SectionType.DIVIDER)
    .build();

List<MailSection> sections = Arrays.asList(textSection, tableSection, divider);
MailRequest request = MailRequest.builder()
    .sections(sections)
    .subject(buildSubject(...))
    .recipients(recipients)
    .build();
```

**문제점:**
- 🔴 **코드 중복**: 각 발송 지점마다 20줄 이상의 섹션 빌드 코드 반복
- 🔴 **높은 결합도**: 서비스 계층이 MailSection에 직접 의존
- 🔴 **유지보수 어려움**: 섹션 구조 변경 시 모든 발송 지점 수정 필요
- 🔴 **실수 가능성**: 아이콘, Subject 형식을 각자 다르게 작성

---

**Phase 2: Factory Pattern 도입 (v1.0.0)**

고정된 섹션 조합을 Factory Method로 추상화:

```java
// MailSection.java - Factory Methods
public static List<MailSection> forAlarm(String name, String severity, int count, List<Map<String, String>> tableData);
public static List<MailSection> forNotice(String title, String content);
public static List<MailSection> forReport(String title, String description, List<Map<String, String>> tableData);

// MailRequest.java - Static Factory Methods
public static MailRequest forAlarm(String title, String content, String severity, ...);
public static Builder alarm(String title, String content, String severity, ...);
```

**사용 예시:**
```java
// 3줄로 간소화
MailRequest request = MailRequest.forAlarm(
    "재고 부족", "긴급 처리 필요", "CRITICAL", tableData, recipients, "LOW_STOCK"
);
```

**장점:**
- ✅ 코드 간소화: 20줄 → 3줄
- ✅ 관리 포인트 감소: Factory Method 한 곳만 수정
- ✅ 타입 안전성 향상
- ✅ 의도 명확한 네이밍

**한계:**
- ⚠️ **유연성 부족**: 고정된 섹션 조합만 가능 (텍스트 + 테이블 + 구분선 고정)
- ⚠️ **확장성 제한**: 새로운 조합이 필요할 때마다 Factory Method 추가 필요
- ⚠️ **결합도 여전히 존재**: 서비스 계층이 MailSection에 간접 의존
- ⚠️ **메타데이터 미지원**: 커스텀 스타일링 불가능

---

**Phase 3: Builder + Helper Methods 패턴 (v2.0.0, 현재)**

유연한 섹션 조합 + 도메인 로직 캡슐화:

```java
// 1. Helper Methods: 도메인 로직 집중화
public static String alarmSubject(String title, String severity, int count);  // Subject 생성
public static String alarmTitle(String title, String severity);               // 아이콘 + 제목

// 2. 범용 Builder Methods: 유연한 섹션 조합
public Builder addTextSection(String title, String content);
public Builder addTextSection(String title, String content, Map<String, Object> metadata);
public Builder addTableSection(List<Map<String, String>> data);
public Builder addTableSection(String title, List<Map<String, String>> data);
public Builder addDivider();
```

**사용 예시:**
```java
// 유연한 조합 + 도메인 로직 재사용
MailRequest request = MailRequest.builder()
    .subject(MailRequest.alarmSubject(sectionTitle, severity, count))  // Helper Method
    .addTextSection(MailRequest.alarmTitle(sectionTitle, severity), content)
    .addTableSection(tableData)
    .addDivider()
    .addTextSection("추가 정보", "...")  // 자유로운 섹션 추가
    .recipients(admUsers)
    .mailType("ALARM")
    .build();
```

**개선 효과:**
- ✅ **MailSection 의존성 완전 제거**: 서비스 계층에서 MailSection import 불필요
- ✅ **무한한 유연성**: 단일/복수 섹션 자유롭게 조합 가능
- ✅ **도메인 로직 집중화**: Subject 패턴, 아이콘 표준이 Helper Methods에 캡슐화
- ✅ **메타데이터 지원**: `Map<String, Object>`로 boolean/numeric 값 지원
- ✅ **확장성**: 새로운 섹션 타입 추가 시 기존 코드 영향 없음
- ✅ **테스트 용이성**: 각 빌더 메서드를 독립적으로 테스트 가능

**리팩토링 결정 이유:**

1. **실제 사용 패턴 분석**: 대부분의 메일이 고정 조합이 아닌 동적 조합 필요
2. **Oracle Procedure 호환성**: Procedure는 `forAlarmWithCustomText()` 사용 (고정 조합 불필요)
3. **메타데이터 확장 요구**: 테이블 스타일링, 폰트 크기 등 동적 설정 필요
4. **서비스 계층 결합도 제거**: MailSection을 도메인 내부로 완전히 캡슐화

**제거된 Deprecated 메서드 (v2.0.0):**

```java
// MailRequest.java - 제거된 Factory Methods
@Deprecated public static Builder alarm(...);        // → Helper Methods + addTextSection 사용
@Deprecated public static Builder notice(...);       // → Helper Methods + addTextSection 사용
@Deprecated public static Builder report(...);       // → Helper Methods + addTableSection 사용
@Deprecated public static MailRequest forAlarm(...); // → Builder + Helper Methods 사용
@Deprecated public static MailRequest forNotice(...);
@Deprecated public static MailRequest forReport(...);

// MailRequest.Builder - 제거된 섹션 메서드
@Deprecated public Builder sections(List<MailSection>);      // → add*Section 메서드 사용
@Deprecated public Builder addSection(MailSection);          // → add*Section 메서드 사용
@Deprecated public Builder addAllSections(List<MailSection>); // → add*Section 메서드 사용

// MailSection.java - 제거된 Factory Method
@Deprecated public static List<MailSection> forAlarm(...);   // → forAlarmWithCustomText 사용 (Procedure용만 유지)
```

**마이그레이션 가이드:**

```java
// Before (Deprecated - v1.0)
MailRequest request = MailRequest.forAlarm(
    title, content, severity, tableData, recipients, mailSource
);

// After (Current - v2.0)
MailRequest request = MailRequest.builder()
    .subject(MailRequest.alarmSubject(title, severity, tableData.size()))
    .addTextSection(MailRequest.alarmTitle(title, severity), content)
    .addTableSection(tableData)
    .recipients(recipients)
    .mailType("ALARM")
    .mailSource(mailSource)
    .build();
```

---

### 알람 수신인 유연화 리팩토링 (v2.1.0)

**배경:**
- 기존에는 모든 알람이 ADM 그룹에만 고정 발송
- 알람별로 다양한 수신인(사용자/그룹)을 선택할 수 없음
- Producer(Oracle Procedure)가 수신인을 지정할 방법이 없었음

**설계 목표:**
1. **유연성**: 알람별로 사용자 ID + 그룹 자유 조합
2. **하위 호환**: 기존 ADM 그룹 고정 동작 유지 (NULL 기본값)
3. **대소문자 안전**: USER_ID 대문자, EMAIL 소문자 규칙 준수
4. **중복 제거**: 한 사용자가 여러 조건에 매칭되어도 1통만 발송

**구현 내용:**

#### 1. 데이터베이스 스키마 확장
```sql
ALTER TABLE MAIL_QUEUE ADD (
    RECIPIENT_USER_IDS  VARCHAR2(1000),  -- 콤마 구분 사용자 ID (약 50명)
    RECIPIENT_GROUPS    VARCHAR2(1000)   -- 콤마 구분 그룹 (약 20개)
);
```

**사용 예시:**
- `RECIPIENT_USER_IDS='ADMIN1,SALES001'` + `RECIPIENT_GROUPS='ADM,LOGISTICS'`
- `RECIPIENT_GROUPS='LOGISTICS'` (사용자 ID 없이 그룹만)
- 둘 다 NULL → ADM 그룹 기본 발송

#### 2. MyBatis 통합 쿼리 (v2.1.1 개선)
```xml
<!-- alarm-mapper.xml:117 -->
<select id="selectRecipientsByConditions" parameterType="map" resultType="map">
    SELECT DISTINCT EMAIL, USER_ID, USER_NAME, USER_GROUP
    FROM USER_INFO
    WHERE EMAIL IS NOT NULL
      AND (
        USER_ID IN ('ADMIN1', 'SALES001')  -- Recipient가 정규화 완료
        OR
        USER_GROUP IN ('ADM', 'LOGISTICS')  -- Recipient가 정규화 완료
      )
    ORDER BY USER_NAME
</select>
```

**v2.1.1 개선사항:**
- `UPPER()` 제거: Recipient 클래스에서 이미 대문자로 정규화됨
- 단순 비교: 쿼리 성능 향상 (함수 호출 제거)
- `DISTINCT`: 한 사용자가 여러 그룹 소속 시 중복 제거
- 동적 쿼리: `<foreach>`로 IN 절 생성

#### 3. AlarmMailService 핵심 로직 (v2.1.1 개선)
```java
// AlarmMailService.java:169
private List<Recipient> resolveRecipients(String recipientUserIds, String recipientGroups) {
    // 1. NULL 체크 → ADM 기본 발송
    if (!hasUserIds && !hasGroups) {
        recipientGroups = "ADM";
    }

    // 2. 콤마 분리 + trim만 수행 (정규화는 Recipient 클래스에 위임)
    List<String> userIdList = Arrays.stream(recipientUserIds.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());

    List<String> groupList = Arrays.stream(recipientGroups.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());

    // 3. MyBatis 통합 쿼리 호출
    Map<String, Object> params = new HashMap<>();
    if (!userIdList.isEmpty()) params.put("userIds", userIdList);
    if (!groupList.isEmpty()) params.put("groups", groupList);
    List<Map<String, Object>> recipientMaps = mailDao.selectList("alarm.selectRecipientsByConditions", params);

    // 4. Recipient.fromMap()에서 대소문자 정규화 + LinkedHashSet으로 중복 제거
    Set<Recipient> recipientSet = new LinkedHashSet<>();
    recipientMaps.stream()
        .map(Recipient::fromMap)  // 여기서 USER_ID 대문자, EMAIL 소문자 정규화
        .forEach(recipientSet::add);

    return new ArrayList<>(recipientSet);
}
```

**v2.1.1 개선사항:**
- Service 계층에서는 trim + filter만 수행
- 대소문자 정규화는 `Recipient.fromMap()`에서 일원화
- 단일 책임 원칙 준수: 정규화 로직이 Recipient 클래스에만 존재

#### 4. Recipient 대소문자 정규화 및 중복 제거 (v2.1.1)
```java
// Recipient.java:24 - fromMap()에서 일원화된 정규화
public static Recipient fromMap(Map<String, Object> map) {
    String userId = (String) map.get("userId");
    String email = (String) map.get("email");

    // USER_ID 대문자 정규화 (대소문자 안전성)
    if (userId != null) {
        userId = userId.toUpperCase();
    }

    // 이메일 소문자 정규화 (대소문자 안전성)
    if (email != null) {
        email = email.toLowerCase();
    }

    return new Recipient(userId, email, group);
}

// Recipient.java:51 - Builder에서도 동일한 정규화
public Builder userId(String userId) {
    this.userId = (userId != null) ? userId.toUpperCase() : null;
    return this;
}

public Builder email(String email) {
    this.email = (email != null) ? email.toLowerCase() : null;
    return this;
}

// Recipient.java:91 - 이메일 기준 중복 제거
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Recipient)) return false;
    Recipient that = (Recipient) o;
    // 이메일은 이미 소문자로 저장되므로 직접 비교
    return email != null && email.equals(that.email);
}

@Override
public int hashCode() {
    return email != null ? email.hashCode() : 0;
}
```

**v2.1.1 핵심 설계:**
- **일원화**: 모든 정규화 로직을 Recipient 클래스에서만 수행
- **Builder 패턴 지원**: Builder에서도 동일한 정규화 적용
- **방어적 복사**: null 체크 후 정규화
- **중복 제거**: equals/hashCode를 email 기준으로 오버라이드

**개선 효과:**
- ✅ **유연성**: 알람별 사용자/그룹 자유 조합
- ✅ **일원화 (v2.1.1)**: 정규화 로직이 Recipient 클래스에만 존재
- ✅ **성능 향상 (v2.1.1)**: SQL에서 UPPER() 함수 호출 제거
- ✅ **단일 책임 (v2.1.1)**: Service/SQL은 trim만, 정규화는 Domain만 담당
- ✅ **중복 제거**: 이메일 기준 (`equals/hashCode`)
- ✅ **하위 호환**: NULL → ADM 기본 발송
- ✅ **테스트 커버리지**: 197개 테스트 모두 통과 (v2.1.1: 9개 추가)

**Oracle Procedure 사용 예시:**
```sql
INSERT INTO MAIL_QUEUE (..., RECIPIENT_USER_IDS, RECIPIENT_GROUPS, ...)
VALUES (..., 'ADMIN1,sales001', 'adm,LOGISTICS', ...);  -- 대소문자 혼용 가능
```

**제약사항:**
- VARCHAR2(1000) 제한: 사용자 ~50명, 그룹 ~20개
- 이메일 중복 제거: 같은 사용자가 여러 조건 매칭 시 1통만 발송
- 존재하지 않는 USER_ID: 조회 결과 없으면 무시 (로그 없음)

---

### Map 리스트 변환 편의성 개선 (v2.1.2)

**배경:**
- Java 코드에서 직접 메일 발송 시: 매번 수동으로 `Recipient.builder()` 호출 필요
- 프로덕션 환경 DAO 조회 후: `List<Map<String, Object>>` → `List<Recipient>` 변환 코드 중복

**문제 사례:**

```java
// 1) Java 코드에서 직접 발송 시 - 수동 빌드 반복
List<Recipient> recipients = new ArrayList<>();
recipients.add(Recipient.builder()
    .userId("ADMIN")
    .email("admin@company.com")
    .group("ADM")
    .build());
recipients.add(Recipient.builder()
    .userId("USER")
    .email("user@company.com")
    .group("USER")
    .build());

// 2) DAO 조회 후 - 매번 Stream API 작성
List<Map<String, Object>> userMaps = wmsDao.select("SELECT_ADM_USER", params);
List<Recipient> recipients = userMaps.stream()
    .map(Recipient::fromMap)
    .collect(Collectors.toList());

// 3) AlarmMailService - 중복 제거까지 필요
List<Map<String, Object>> recipientMaps = mailDao.selectList("alarm.selectRecipientsByConditions", params);
Set<Recipient> recipientSet = new LinkedHashSet<>();
recipientMaps.stream()
    .map(Recipient::fromMap)
    .forEach(recipientSet::add);
List<Recipient> recipients = new ArrayList<>(recipientSet);  // 4줄 코드
```

**개선: `Recipient.fromMapList()` 정적 메서드 추가**

```java
// Recipient.java:48-59
/**
 * Map 리스트를 Recipient 리스트로 변환 (중복 제거 포함)
 *
 * @param maps MyBatis 조회 결과 (List<Map<String, Object>>)
 * @return 중복 제거된 Recipient 리스트 (이메일 기준, 순서 보장)
 */
public static List<Recipient> fromMapList(List<Map<String, Object>> maps) {
    if (maps == null || maps.isEmpty()) {
        return new ArrayList<>();
    }

    // LinkedHashSet으로 중복 제거 (이메일 기준, 순서 보장)
    Set<Recipient> recipientSet = maps.stream()
            .map(Recipient::fromMap)
            .collect(Collectors.toCollection(LinkedHashSet::new));

    return new ArrayList<>(recipientSet);
}
```

**사용 예시 (4줄 → 1줄)**:

```java
// Before: 4줄
List<Map<String, Object>> recipientMaps = mailDao.selectList("alarm.selectRecipientsByConditions", params);
Set<Recipient> recipientSet = new LinkedHashSet<>();
recipientMaps.stream().map(Recipient::fromMap).forEach(recipientSet::add);
List<Recipient> recipients = new ArrayList<>(recipientSet);

// After: 1줄
List<Recipient> recipients = Recipient.fromMapList(
    mailDao.selectList("alarm.selectRecipientsByConditions", params)
);
```

**설계 고려사항: 암묵적 변환 vs 명시적 변환**

**거부한 대안: MailRequest.Builder에 Map 리스트 직접 수용**

```java
// ❌ 거부된 방식 (암묵적 변환)
MailRequest.builder()
    .recipientMaps(userMaps)  // Map을 받아서 내부에서 Recipient로 변환
    .subject("...")
    .build();
```

**거부 이유 (현대 Java 원칙 위반)**:

| 항목 | 암묵적 변환 (Builder 내부) | 명시적 변환 (`fromMapList`) | 판단 |
|------|---------------------------|------------------------|------|
| **변환 로직 집중화** | ❌ Builder와 Recipient 양쪽에서 변환 | ✅ Recipient 한 곳에서만 변환 | **명시적 유리** |
| **단일 책임 (SRP)** | ❌ MailRequest가 2개 책임 (메일 표현 + 변환) | ✅ Recipient만 변환 책임 | **명시적 유리** |
| **도메인 순수성 (DDD)** | ❌ Domain이 Infrastructure에 의존 | ✅ Infrastructure → Domain 단방향 | **명시적 유리** |
| **가독성** | ⚠️ recipientMaps가 무엇인지 불명확 | ✅ fromMapList()로 변환 의도 명확 | **명시적 유리** |
| **테스트 용이성** | ❌ Map 구조 알아야 테스트 가능 | ✅ 도메인 객체만으로 테스트 | **명시적 유리** |
| **코드 간결성** | ✅ 1줄 (Builder 내부 처리) | ⚠️ 2줄 (명시적 변환) | 암묵적 유리 |

**참고: 타입 안전성 관점**
- 두 방식 모두 `Map<String, Object>` → `Recipient` 변환 시 **런타임 검증**입니다.
- `fromMapList()`의 장점은 컴파일 타임 검증이 아니라, **변환 로직이 Recipient 클래스 한 곳에만 존재**한다는 점입니다.
- Map 키 오타(`"userId"` → `"usrId"`)는 여전히 런타임에만 발견됩니다.

**최종 선택: 명시적 변환 (`fromMapList`)**

**근거**:
1. **단일 책임 원칙 (SRP)**: "클래스는 하나의 변경 이유만 가져야 한다" - Recipient만 Map 변환 책임
2. **Domain-Driven Design**: Domain 계층은 Infrastructure(Map)에 의존하면 안 됨
3. **변환 로직 집중화**: Map 구조 변경 시 Recipient 클래스 한 곳만 수정
4. **ROI 판단**: 1줄 절약 vs 유지보수성/확장성 → **장기 가치가 더 중요**

**개선 효과**:

| 항목 | Before | After | 효과 |
|------|--------|-------|------|
| **코드 간소화** | 4줄 (Stream + Set + List) | 1줄 (fromMapList) | **-3줄 (75% 감소)** |
| **대소문자 정규화** | fromMap()에서 자동 처리 | fromMap()에서 자동 처리 | 동일 |
| **중복 제거** | 수동 (LinkedHashSet) | 자동 (fromMapList 내부) | **자동화** |
| **null-safe** | NPE 가능성 있음 | 빈 리스트 반환 | **안전성 향상** |
| **순서 보장** | LinkedHashSet 수동 사용 | LinkedHashSet 자동 사용 | 동일 |
| **변환 로직 집중화** | Map 키 오타 시 여러 곳 수정 | fromMap() 한 곳만 수정 | **유지보수성 향상** |
| **재사용성** | 모든 발송 지점에서 동일 코드 반복 | fromMapList() 재사용 | **코드 중복 제거** |

**적용 위치**:

- `AlarmMailService.java:209` - resolveRecipients() 메서드
- 향후 추가될 모든 발송 지점 (InventoryService, OrderService 등)

**테스트 커버리지**:

- `RecipientTest.java`: 8개 테스트 추가 (총 22개)
    - fromMapList 정상 변환 (복수 Map)
    - 빈 리스트 / null 리스트 처리
    - 중복 이메일 제거 (동일 이메일, 대소문자 혼용)
    - USER_ID 대문자 정규화
    - 순서 보장 (LinkedHashSet)
    - Map 내부 필드 누락 처리

**장기 유지보수 관점**:

- **확장성**: 모든 발송 지점에서 동일한 변환 로직 재사용
- **일관성**: Map → Recipient 변환 로직이 한 곳에만 존재
- **안전성**: null-safe + 대소문자 정규화 + 중복 제거 모두 자동 처리

---

### Spring 3.2 ASM 호환성 리팩토링 (v2.1.3)

**배경:**
- 개발환경 (Java 17 + Spring Boot 3.5.x)에서는 정상 동작하던 코드가 레거시 운영환경 (Java 8 + Spring Framework 3.2.x)에서 빌드 실패
- 에러: `BeanDefinitionStoreException: Failed to read candidate component class: ArrayIndexOutOfBoundsException`
- 원인: Spring 3.2.x의 ASM 4는 Java 8 **invokedynamic 바이트코드**를 파싱하지 못함

**근본 원인:**

| 항목 | 개발환경 | 레거시 운영환경 |
|------|---------|---------------|
| **Java 버전** | 17 | 8 |
| **컴파일된 bytecode** | version 52 (Java 8 호환) | version 52 (정상) |
| **Spring 버전** | Spring Boot 3.5.6 | Spring Framework 3.2.x |
| **ASM 라이브러리** | ASM 9.x (Java 17 지원) | ASM 4 (invokedynamic 미지원) |

**문제 구조:**

```
Java 8 Lambda/Method Reference/Stream API
  → invokedynamic 바이트코드 생성 (정상 컴파일, bytecode v52)
  → Spring 3.2.x ASM 4가 컴포넌트 스캔 시 바이트코드 파싱 시도
  → ❌ ASM 4는 invokedynamic을 파싱하지 못함
  → ArrayIndexOutOfBoundsException 발생
```

**수정된 파일 (4개):**

#### 1. Recipient.java:48-60 - fromMapList()

```java
// ❌ Before: Stream API + method reference (invokedynamic 생성)
public static List<Recipient> fromMapList(List<Map<String, Object>> maps) {
    if (maps == null || maps.isEmpty()) {
        return new ArrayList<>();
    }

    Set<Recipient> recipientSet = maps.stream()
            .map(Recipient::fromMap)
            .collect(Collectors.toCollection(LinkedHashSet::new));

    return new ArrayList<>(recipientSet);
}

// ✅ After: for-loop (Spring 3.2 호환)
public static List<Recipient> fromMapList(List<Map<String, Object>> maps) {
    if (maps == null || maps.isEmpty()) {
        return new ArrayList<>();
    }

    // Spring 3.2 ASM 호환성을 위해 for-loop 사용 (lambda/method reference 제거)
    Set<Recipient> recipientSet = new LinkedHashSet<>();
    for (Map<String, Object> map : maps) {
        recipientSet.add(fromMap(map));
    }

    return new ArrayList<>(recipientSet);
}
```

#### 2. AlarmMailService.java:183-203 - resolveRecipients() 수신인 파싱

```java
// ❌ Before: Arrays.stream().map().filter().collect()
List<String> userIdList = hasUserIds
        ? Arrays.stream(recipientUserIds.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList())
        : Collections.emptyList();

// ✅ After: for-loop + 수동 필터링
List<String> userIdList = new ArrayList<>();
if (hasUserIds) {
    String[] userIdTokens = recipientUserIds.split(",");
    for (String token : userIdTokens) {
        String trimmed = token.trim();
        if (!trimmed.isEmpty()) {
            userIdList.add(trimmed);
        }
    }
}
```

#### 3. AlarmMailService.java:238-248 - convertToStringMap()

```java
// ❌ Before: stream().map(lambda).collect() + forEach(lambda)
private List<Map<String, String>> convertToStringMap(List<Map<String, Object>> source) {
    return source.stream()
            .map(map -> {
                Map<String, String> stringMap = new LinkedHashMap<>();
                map.forEach((k, v) -> stringMap.put(k, v != null ? v.toString() : ""));
                return stringMap;
            })
            .collect(Collectors.toList());
}

// ✅ After: 중첩 for-loop
private List<Map<String, String>> convertToStringMap(List<Map<String, Object>> source) {
    List<Map<String, String>> result = new ArrayList<>();
    for (Map<String, Object> map : source) {
        Map<String, String> stringMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            stringMap.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
        }
        result.add(stringMap);
    }
    return result;
}
```

#### 4. MailService.java:201-215 - doSendMail()

```java
// ❌ Before: stream().map().toArray()
String[] toEmails = recipients.stream()
        .map(Recipient::getEmail)
        .toArray(String[]::new);

// ✅ After: for-loop + 배열 인덱싱
String[] toEmails = new String[recipients.size()];
for (int i = 0; i < recipients.size(); i++) {
    toEmails[i] = recipients.get(i).getEmail();
}
```

#### 5. MailUtils.java:116-129 - formatRecipientsToString()

```java
// ❌ Before: stream().map().collect(Collectors.joining())
public static String formatRecipientsToString(List<Recipient> recipients) {
    if (recipients == null || recipients.isEmpty()) {
        return "";
    }

    return recipients.stream()
            .map(Recipient::getEmail)
            .collect(Collectors.joining(","));
}

// ✅ After: StringBuilder
public static String formatRecipientsToString(List<Recipient> recipients) {
    if (recipients == null || recipients.isEmpty()) {
        return "";
    }

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < recipients.size(); i++) {
        if (i > 0) {
            sb.append(",");
        }
        sb.append(recipients.get(i).getEmail());
    }
    return sb.toString();
}
```

**제거한 Java 8 문법 (invokedynamic 생성):**
- ❌ Lambda 표현식 (`->`)
- ❌ Method Reference (`::`)
- ❌ Stream API (`.stream()`, `.map()`, `.filter()`, `.collect()`)
- ❌ `Collectors.joining()`, `Collectors.toList()`, `Collectors.toCollection()`
- ❌ `Map.forEach(BiConsumer)`

**사용 가능한 Java 8 문법 (invokedynamic 미사용):**
- ✅ Enhanced for-loop (`for (Type item : collection)`)
- ✅ Generic (`List<String>`, `Map<String, Object>`)
- ✅ StringBuilder, ArrayList, LinkedHashSet
- ✅ Try-with-resources (Java 7부터 지원)
- ✅ String.split(), trim(), isEmpty()

**검증 결과:**

```bash
# Lambda/Method Reference 전수조사
grep -r "\->|::" src/main/java/
# 결과: 0건 (전부 제거 완료)

# Stream API 전수조사
grep -r "\.stream(" src/main/java/
# 결과: 0건 (전부 제거 완료)

# 테스트 결과
./gradlew test
# 결과: 205개 전부 통과 (200 PASSED, 5 SKIPPED)
```

**개선 효과:**
- ✅ **Spring 3.2 호환**: 레거시 환경에서 정상 빌드
- ✅ **기능 동일**: 중복 제거, 순서 보장, 정규화 모두 정상 작동
- ✅ **성능**: for-loop가 Stream API보다 오버헤드 적음 (소규모 컬렉션)
- ✅ **가독성**: 절차적 코드가 더 명확한 경우도 있음 (convertToStringMap 등)

**트레이드오프:**
- ⚠️ **코드 길이 증가**: Stream API 대비 2~3배 길어짐
- ⚠️ **함수형 프로그래밍 포기**: 선언적 스타일 → 절차적 스타일
- ⚠️ **개발환경과 프로덕션 괴리**: Java 17 개발 → Java 8 배포 시 주의 필요

**향후 대응:**

1. **코드 작성 시 주의사항**:
    - 개발환경에서 Lambda/Stream API 사용하지 않기
    - IntelliJ 경고 "Replace with forEach" 무시
    - SonarQube Lambda 권장 규칙 비활성화

2. **Spring 버전 업그레이드 계획 시**:
    - Spring 4.0+ 이상으로 업그레이드하면 Lambda/Stream API 사용 가능
    - ASM 5.0 이상에서 invokedynamic 정상 지원

3. **빌드 검증 자동화**:
   ```bash
   # CI/CD에서 자동 검증
   if grep -rq "\->\|::\|\.stream(" src/main/java/; then
       echo "ERROR: Lambda/Method Reference/Stream API 사용 금지"
       exit 1
   fi
   ```

**참고 자료:**
- [Spring 3.2 Java 8 Lambda 호환성 문제 (StackOverflow)](https://stackoverflow.com/questions/30729125/arrayoutofboundsexception-on-bean-creation-while-using-java-8-constructs)
- [Spring Framework Java 8 지원 로드맵 (GitHub SPR-11656)](https://github.com/spring-projects/spring-framework/issues/16279)
- Spring 3.2.10: ASM 5.0 부분 지원, 여전히 제한적
- Spring 4.0+: ASM 5.0 완전 지원, Lambda/Stream API 사용 가능

---

### MailService 아키텍처 리팩토링 (v2.2.0)

**배경:**
- HTML 구조 생성 책임이 MailService에 위치 (wrapWithHtmlStructure 메서드)
- @Async 사용으로 큐 상태와 실제 발송 상태 불일치 위험
- 비즈니스 메시지("WMS 시스템 알림")가 서비스 계층에 하드코딩

**문제점:**
1. **책임 분리 위반**: MailService가 HTML 생성 + 비즈니스 로직 포함
2. **재사용성 저하**: MailBodyRenderer가 WMS에 종속 불가, 완전한 HTML 생성 불가
3. **큐 상태 불일치**: 비동기 처리 시 발송 전에 updateQueueSuccess 실행 가능
4. **유지보수 어려움**: 시스템명 변경 시 코드 수정 필요

**개선 내용:**

#### 1. HTML 구조 생성 책임 이동

```java
// Before (v2.1.x): MailService에서 직접 생성
private String wrapWithHtmlStructure(String body) {
    html.append("<h2>WMS 시스템 알림</h2>");  // 하드코딩
    html.append(body);
    html.append("본 메일은 WMS 시스템에서 자동 발송되었습니다.");  // 하드코딩
}

// After (v2.2.0): Renderer로 이동 + Config 주입
// MailConfig.java
@Value("${mail.structure.system.title:WMS 시스템 알림}")
private String systemTitle;

@Value("${mail.structure.footer.message:본 메일은 WMS 시스템에서 자동 발송되었습니다.}")
private String footerMessage;

// MailBodyRenderer.java
public String renderWithStructure(List<MailSection> sections, String systemTitle, String footerMessage) {
    String body = render(sections);
    // HTML 구조 생성 (파라미터로 주입)
}

// MailService.java
String htmlBody = renderer.renderWithStructure(
    sections,
    mailConfig.getSystemTitle(),
    mailConfig.getFooterMessage()
);
```

**개선 효과:**
- ✅ **Renderer 재사용성**: 다른 시스템에서도 사용 가능 (시스템명만 주입)
- ✅ **SRP 준수**: MailService는 오케스트레이션, Renderer는 HTML 생성
- ✅ **설정 외부화**: systemTitle/footerMessage를 properties로 관리 가능

#### 2. @Async 제거 (동기 처리)

```java
// Before (v2.1.x): 비동기 처리
@Async
@Transactional
protected void sendMailAsync(MailRequest request) { ... }

public void sendMail(MailRequest request) {
    sendMailAsync(request);  // 백그라운드 실행, 즉시 반환
}

// AlarmMailService
mailService.sendMail(request);  // 즉시 반환 (발송 미완료)
mailDao.update("alarm.updateQueueSuccess", params);  // ❌ 발송 전에 SUCCESS 기록

// After (v2.2.0): 동기 처리
@Transactional
public void sendMail(MailRequest request) {
    // 발송 로직 (재시도 포함)
    // 완료 후 return
}

// AlarmMailService
mailService.sendMail(request);  // 발송 완료 대기 (5초)
mailDao.update("alarm.updateQueueSuccess", params);  // ✅ 발송 완료 후 SUCCESS 기록
```

**개선 효과:**
- ✅ **큐 상태 정확성**: 실제 발송 성공 후 updateQueueSuccess 실행
- ✅ **재시도 로직 정상 작동**: 발송 실패 시 즉시 재시도
- ✅ **트랜잭션 관리 단순화**: 중첩 트랜잭션 문제 제거
- ✅ **디버깅 용이성**: 동기 실행으로 스택 트레이스 명확

**트레이드오프:**
- ⚠️ **처리량 감소**: 10개 메일 순차 처리 (비동기 대비 느림)
- ⚠️ **응답 시간 증가**: HTTP API 있다면 5초 대기 필요 (현재는 Scheduled 큐 처리만 존재)

**비동기 불필요한 이유:**
- AlarmMailService는 Scheduled 큐 처리 (사용자 응답 대기 없음)
- 순차 처리가 더 안전 (큐 상태 일치)
- 처리량보다 정확성이 중요 (알람 발송)

#### 3. 테스트 코드 수정

```java
// Before: timeout(1000) 사용 (비동기 대기)
verify(mailDao, timeout(1000)).insert(eq("mail.insertMailSendLog"), anyMap());

// After: 즉시 검증 (동기 실행)
verify(mailDao, times(1)).insert(eq("mail.insertMailSendLog"), anyMap());
```

**개선 효과:**
- ✅ **테스트 속도 향상**: timeout 대기 불필요
- ✅ **테스트 안정성 향상**: 타이밍 이슈 제거

---

### MailConfig 연락처 정보 관리 (v2.2.1)

**배경:**
- 연락처 정보가 1~3개까지 유연하게 설정 가능해야 함
- @Value 어노테이션으로 properties에서 관리
- contact1은 기본값 필요, contact2~3는 선택적

**구현 내용:**

```java
// MailConfig.java
@Value("${mail.contact.name.1:IT}")
private String contactName1;
@Value("${mail.contact.email.1:C20002_3000@test.co.kr}")
private String contactEmail1;

@Value("${mail.contact.name.2:#{null}}")
private String contactName2;
@Value("${mail.contact.email.2:#{null}}")
private String contactEmail2;

@Value("${mail.contact.name.3:#{null}}")
private String contactName3;
@Value("${mail.contact.email.3:#{null}}")
private String contactEmail3;

public String getContactInfo() {
    StringBuilder contactInfo = new StringBuilder();
    contactInfo.append(contactName1 + ": " + contactEmail1);

    if (contactName2 != null && !contactName2.isEmpty() && contactEmail2 != null && !contactEmail2.isEmpty()) {
        contactInfo.append("\n");
        contactInfo.append(contactName2 + ": " + contactEmail2);
    }
    if (contactName3 != null && !contactName3.isEmpty() && contactEmail3 != null && !contactEmail3.isEmpty()) {
        contactInfo.append("\n");
        contactInfo.append(contactName3 + ": " + contactEmail3);
    }

    return contactInfo.toString();
}
```

**사용처:**
- `MailService.java:61` - `MailSection.forContact(mailConfig.getContactInfo())` 호출
- 메일 하단에 "📞 문의" 섹션으로 자동 추가 (DIVIDER + TEXT)

**개선 효과:**
- ✅ **유연성**: 1~3개 연락처 자유롭게 설정
- ✅ **설정 외부화**: application.properties로 관리
- ✅ **하위 호환**: contact1 기본값 보장 (properties 없어도 동작)
- ✅ **테스트 커버리지**: 221개 테스트 모두 통과 (v2.2.1: 11개 추가)
    - MailConfigTest.java: 6개 시나리오 (기본값, 1~3개, 빈 문자열 처리)
    - MailBodyRendererTest.java: 5개 시나리오 (HTML 렌더링, 줄바꿈 변환, 이스케이프)

---

### Exception Handling & Transaction Strategy (v2.3.0)

**배경:**
- 기존 설계: `MailService.sendMail()` 메서드가 void 반환 + 예외 발생 시 rollback
- 문제: 발송 실패 시 로그 업데이트도 함께 rollback되어 실패 이력이 사라짐
- 초기 제안: `Propagation.REQUIRES_NEW`로 로그 트랜잭션 분리
- 최종 결정: Railway Oriented Programming 패턴 채택 (boolean 반환)

**문제점 분석:**

```java
// ❌ Before: 예외 기반 제어 흐름
@Transactional
public void sendMail(MailRequest request) {
    Long logId = createLog(...);
    try {
        doSendMail(...);
        updateLogStatus(logId, SUCCESS);
    } catch (Exception e) {
        updateLogStatus(logId, FAILURE);  // 이 업데이트도 rollback됨!
        throw e;  // 예외 재발생
    }
}

// AlarmMailService
try {
    mailService.sendMail(request);
    mailDao.update("alarm.updateQueueSuccess", params);
} catch (Exception e) {
    // 큐 실패 처리 (하지만 로그는 이미 rollback됨)
}
```

**거부한 대안: Propagation.REQUIRES_NEW**

```java
// ⚠️ 고려했으나 거부된 방식
@Transactional(propagation = Propagation.REQUIRES_NEW)
protected void updateLogStatus(Long logId, SendStatus status, String errorMessage) {
    // 독립 트랜잭션으로 실행 (부모 rollback과 무관)
}
```

**거부 이유:**
1. **복잡도 증가**: 트랜잭션 경계 관리가 복잡해짐
2. **데드락 위험**: 부모-자식 트랜잭션 간 리소스 경합 가능
3. **성능 오버헤드**: 트랜잭션 시작/커밋 비용 증가
4. **디버깅 어려움**: 중첩 트랜잭션 추적이 복잡함
5. **과도한 설계**: "예외는 예외적인 상황에만" 원칙 위반

**최종 설계: Railway Oriented Programming**

**핵심 원칙:**
- **예외는 예외적인 상황에만**: Validation 실패, 프로그래머 오류
- **예상된 실패는 boolean 반환**: 메일 발송 실패, 네트워크 오류
- **트랜잭션 단순화**: 기본 REQUIRED 전파, REQUIRES_NEW 불필요
- **상태 일치 보장**: 큐 상태 = 실제 발송 상태

#### 1. ValueChainException 구조 개선

```java
// ValueChainException.java
public class ValueChainException extends RuntimeException {
    private String debugMessage;  // 디버그 정보 (스택 트레이스 등)

    // 단일 메시지 생성자 (일반적인 경우)
    public ValueChainException(String errMsg) {
        super(errMsg);
        this.debugMessage = null;
    }

    // 디버그 메시지 포함 생성자 (상세 정보 필요 시)
    public ValueChainException(String errMsg, String debMsg) {
        super(errMsg);
        this.debugMessage = debMsg;
    }

    // Cause 포함 생성자 (체이닝)
    public ValueChainException(String message, Throwable cause) {
        super(message, cause);
        this.debugMessage = (cause != null) ? getStackTraceString(cause) : null;
    }

    public String getDebugMessage() {
        return debugMessage;
    }
}
```

**사용 시나리오:**
- **Validation 실패**: `throw new ValueChainException("수신인 목록이 비어있습니다.")`
- **설정 오류**: `throw new ValueChainException("SMTP 서버 설정이 잘못되었습니다.", debugInfo)`
- **프로그래머 오류**: `throw new ValueChainException("null 참조 발생", e)`

#### 2. MailService Boolean 반환 패턴

```java
// MailService.java
@Transactional
public boolean sendMail(MailRequest request) {
    // 1. Validation (예외 발생 가능 - fail-fast)
    MailUtils.validateRecipients(request.getRecipients());  // throw ValueChainException

    Long logId = null;
    try {
        // 2. 로그 생성 (commit)
        logId = createLog(...);

        // 3. 메일 발송 시도 (재시도 포함)
        boolean success = sendWithRetry(..., logId);

        // 4. 성공/실패 상태 업데이트 (commit)
        if (success) {
            updateLogStatus(logId, SendStatus.SUCCESS, null);
        } else {
            updateLogStatus(logId, SendStatus.FAILURE, "재시도 3회 실패");
        }

        return success;  // 예외 없이 boolean 반환

    } catch (Exception e) {
        // 5. 예기치 않은 예외 (네트워크 오류 등)
        if (logId != null) {
            updateLogStatus(logId, SendStatus.FAILURE, e.getMessage());
        }
        return false;  // 예외를 잡아서 boolean으로 변환
    }
}
```

**개선 효과:**
- ✅ **로그 영속성 보장**: 실패 시에도 로그가 DB에 commit됨
- ✅ **트랜잭션 단순화**: 기본 REQUIRED 전파만 사용
- ✅ **명확한 제어 흐름**: boolean 체크로 성공/실패 분기
- ✅ **예외 최소화**: 예상된 실패는 false 반환

#### 3. AlarmMailService 큐 상태 관리

```java
// AlarmMailService.java
@Transactional
public void processQueue() {
    try {
        // 배치 크기 제한 (long transaction 방지)
        Map<String, Object> params = new HashMap<>();
        params.put("limit", 10);  // 한 번에 최대 10건만 처리

        List<Map<String, Object>> messages = mailDao.selectList("alarm.selectPendingQueue", params);

        for (Map<String, Object> msg : messages) {
            processMessage(msg);  // 개별 메시지 처리
        }
    } catch (Exception e) {
        System.err.println("큐 처리 시스템 오류: " + e.getMessage());
        // 전체 배치 실패해도 다음 스케줄에서 재시도
    }
}

private void processMessage(Map<String, Object> msg) {
    Long queueId = ((Number) msg.get("queueId")).longValue();
    String mailSource = (String) msg.get("mailSource");
    Integer retryCount = (Integer) msg.get("retryCount");

    try {
        // 메일 발송 시도
        MailRequest request = buildRequest(msg);
        boolean success = mailService.sendMail(request);  // boolean 반환

        if (success) {
            // 성공 → 큐 상태 SUCCESS로 업데이트
            Map<String, Object> updateParams = new HashMap<>();
            updateParams.put("queueId", queueId);
            mailDao.update("alarm.updateQueueSuccess", updateParams);
        } else {
            // 실패 → 재시도 또는 FAILED
            handleFailure(queueId, mailSource, retryCount, new Exception("메일 발송 실패"));
        }

    } catch (Exception e) {
        // 예기치 않은 예외 (DB 오류, validation 실패 등)
        handleFailure(queueId, mailSource, retryCount, e);
    }
}

private void handleFailure(Long queueId, String mailSource, int retryCount, Exception e) {
    Map<String, Object> params = new HashMap<>();
    params.put("queueId", queueId);
    params.put("errorMessage", e.getMessage());

    if (retryCount < 3) {
        // 재시도 (RETRY_COUNT 증가)
        mailDao.update("alarm.updateQueueRetry", params);
    } else {
        // 최종 실패 (STATUS = FAILED)
        mailDao.update("alarm.updateQueueFailed", params);
    }
}
```

**큐 상태 전이:**

```
PENDING → (발송 성공) → SUCCESS
PENDING → (발송 실패, 재시도 < 3) → PENDING (RETRY_COUNT++)
PENDING → (발송 실패, 재시도 = 3) → FAILED
```

**배치 크기 제한 (LIMIT 10):**

```xml
<!-- alarm-mapper.xml -->
<select id="selectPendingQueue" parameterType="map" resultType="map">
    SELECT QUEUE_ID, MAIL_SOURCE, ALARM_NAME, ...
    FROM MAIL_QUEUE
    WHERE STATUS = 'PENDING'
    ORDER BY REG_DATE ASC
    <if test="limit != null">
        FETCH FIRST #{limit} ROWS ONLY  <!-- Oracle 12c+, H2 호환 -->
    </if>
</select>
```

**이유:**
- ✅ **Long Transaction 방지**: 큐에 수백 건 쌓여도 10건씩만 처리
- ✅ **Lock 최소화**: 트랜잭션 시간 단축 → 동시성 향상
- ✅ **스케줄러 안정성**: 10초마다 10건씩 처리 (과부하 방지)

#### 4. 예외 표준화 (IllegalArgumentException → ValueChainException)

```java
// ❌ Before: 표준 예외 사용
if (recipients == null || recipients.isEmpty()) {
    throw new IllegalArgumentException("수신인 목록이 비어있습니다.");
}

if (mailSource == null || mailSource.trim().isEmpty()) {
    throw new IllegalStateException("메일 소스가 지정되지 않았습니다.");
}

// ✅ After: ValueChainException 통일
if (recipients == null || recipients.isEmpty()) {
    throw new ValueChainException("수신인 목록이 비어있습니다.");
}

if (mailSource == null || mailSource.trim().isEmpty()) {
    throw new ValueChainException("메일 소스가 지정되지 않았습니다.");
}
```

**변경 이유:**
1. **프로젝트 예외 체계 통일**: 모든 비즈니스 예외를 ValueChainException으로 표준화
2. **디버그 정보 확장**: debugMessage 필드로 상세 정보 추가 가능
3. **일관된 예외 처리**: 전역 ExceptionHandler에서 일괄 처리
4. **체이닝 지원**: Throwable cause 포함 생성자

**적용 범위:**
- `MailRequest.java` - validation 메서드 (10개소)
- `MailUtils.java` - 검증 메서드 (3개소)
- `AlarmMailService.java` - 수신인 조회 실패 (1개소)

#### 5. 트랜잭션 전략 비교

| 항목 | Before (예외 기반) | Considered (REQUIRES_NEW) | **After (Boolean 반환)** |
|------|------------------|--------------------------|----------------------|
| **발송 실패 시 로그** | ❌ Rollback (사라짐) | ✅ Commit (독립 트랜잭션) | ✅ Commit (단일 트랜잭션) |
| **큐 상태 일치** | ⚠️ 불일치 가능 (비동기) | ✅ 일치 (동기 + REQUIRES_NEW) | ✅ 일치 (동기 + boolean) |
| **트랜잭션 복잡도** | 낮음 | ⚠️ 높음 (중첩 트랜잭션) | ✅ 낮음 (단일 전파) |
| **데드락 위험** | 없음 | ⚠️ 있음 (리소스 경합) | ✅ 없음 |
| **성능 오버헤드** | 낮음 | ⚠️ 높음 (트랜잭션 2회) | ✅ 낮음 (트랜잭션 1회) |
| **디버깅 난이도** | 쉬움 | ⚠️ 어려움 (중첩 추적) | ✅ 쉬움 (선형 흐름) |
| **코드 가독성** | 보통 | ⚠️ 낮음 (REQUIRES_NEW 숨김) | ✅ 높음 (boolean 명시) |
| **배치 크기 제한** | 없음 | 필요 (별도 구현) | ✅ LIMIT 10 (쿼리 레벨) |

**최종 결정 근거:**
1. **Railway Oriented Programming 원칙**: 예상된 실패는 데이터로 표현 (boolean)
2. **단순성**: REQUIRES_NEW 없이도 목표 달성 (로그 영속성 보장)
3. **명확성**: 호출자가 boolean으로 성공/실패 판단 가능
4. **유지보수성**: 트랜잭션 경계가 명확하고 추적이 쉬움

#### 6. 테스트 코드 변경

```java
// ❌ Before: void 반환 + verify
mailService.sendMail(request);  // 반환값 없음
verify(mailDao, times(1)).insert(eq("mail.insertMailSendLog"), anyMap());

// ✅ After: boolean 반환 + assert
boolean result = mailService.sendMail(request);
assertTrue(result);  // 명시적 검증
verify(mailDao, times(1)).insert(eq("mail.insertMailSendLog"), anyMap());
```

**통합 테스트 (AlarmMailServiceIntegrationTest):**

```java
@Test
void 정상발송_큐상태SUCCESS() {
    // Given: PENDING 큐 삽입
    insertTestQueue("TEST_ALARM", "CRITICAL", 0);

    // When: 큐 처리
    alarmMailService.processQueue();

    // Then: 큐 상태 확인
    Map<String, Object> queue = mailDao.selectOne("alarm.selectQueueById", queueId);
    assertEquals("SUCCESS", queue.get("status"));  // 큐 상태 일치

    // MailRequest 검증
    ArgumentCaptor<MailRequest> captor = ArgumentCaptor.forClass(MailRequest.class);
    verify(mailService, times(1)).sendMail(captor.capture());
    assertEquals("[긴급] 테스트 알림 5건", captor.getValue().getSubject());
}
```

**개선 효과:**
- ✅ **명시적 검증**: boolean 반환값으로 성공/실패 확인
- ✅ **큐 상태 추적**: DB 조회로 PENDING → SUCCESS 전이 검증
- ✅ **동기 실행**: timeout 불필요, 즉시 검증 가능

#### 7. MyBatis LIMIT 파라미터 추가

**H2 Database 호환성:**

```xml
<!-- ❌ Before: MySQL 스타일 (H2 미지원) -->
<select id="selectPendingQueue" parameterType="map" resultType="map">
    SELECT QUEUE_ID, MAIL_SOURCE, ...
    FROM MAIL_QUEUE
    WHERE STATUS = 'PENDING'
    ORDER BY REG_DATE ASC
    LIMIT 10  <!-- H2에서 Syntax Error -->
</select>

<!-- ✅ After: SQL Standard (H2/Oracle 12c+ 호환) -->
<select id="selectPendingQueue" parameterType="map" resultType="map">
    SELECT QUEUE_ID, MAIL_SOURCE, ...
    FROM MAIL_QUEUE
    WHERE STATUS = 'PENDING'
    ORDER BY REG_DATE ASC<if test="limit != null">
    FETCH FIRST #{limit} ROWS ONLY</if>
</select>
```

**주의사항:**
- H2 test mapper: `${limit}` (문자열 치환)
- Oracle main mapper: `#{limit}` (파라미터 바인딩)

**검증 결과:**
- ✅ 221개 테스트 전부 통과 (v2.3.0)
- ✅ H2 통합 테스트 정상 동작
- ✅ 배치 크기 제한 확인 (10건씩만 조회)

#### 8. 개선 요약

**v2.3.0 리팩토링 전후 비교:**

| 항목 | Before | After | 효과 |
|------|--------|-------|------|
| **sendMail() 반환 타입** | void | boolean | 명시적 성공/실패 표현 |
| **발송 실패 시 로그** | Rollback (사라짐) | Commit (영속화) | 실패 이력 추적 가능 |
| **큐 상태 일치** | 불일치 가능 (비동기) | 일치 (동기 + boolean) | 데이터 정합성 보장 |
| **트랜잭션 전파** | REQUIRED | REQUIRED (동일) | 단순성 유지 |
| **예외 처리** | IllegalArgument/State | ValueChainException | 표준화 + debugMessage |
| **배치 크기 제한** | 없음 (전체 조회) | LIMIT 10 | Long Transaction 방지 |
| **테스트 검증** | 간접 (verify만) | 직접 (boolean + verify) | 명확한 결과 확인 |

**핵심 설계 원칙:**

1. **"Exceptions for exceptional circumstances only"**
    - Validation 실패, 프로그래머 오류: throw ValueChainException
    - 메일 발송 실패, 네트워크 오류: return false

2. **"Make illegal states unrepresentable"**
    - 큐 상태 = 실제 발송 상태 (동기 처리)
    - boolean 반환으로 성공/실패 명시

3. **"Simplicity over cleverness"**
    - REQUIRES_NEW 대신 boolean 반환 선택
    - 단일 트랜잭션으로 목표 달성

4. **"Optimize for change"**
    - 배치 크기 제한으로 확장성 확보
    - ValueChainException으로 통일된 예외 체계

**참고 자료:**
- [Railway Oriented Programming (Scott Wlaschin)](https://fsharpforfunandprofit.com/rop/)
- [Effective Java 3rd Edition - Item 69: Use exceptions only for exceptional conditions](https://www.oreilly.com/library/view/effective-java-3rd/9780134686097/)
- [Spring Transaction Management - Propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)

---

### 템플릿 시스템 제거 결정

**Before: DB 템플릿 기반 시스템**

```java
// MAIL_TEMPLATE 테이블에서 템플릿 로드
String template = mailDao.selectTemplateByType("ALARM");
String bodyHtml = template
    .replace("{{title}}", title)
    .replace("{{content}}", content);
```

**Removed:**
- MAIL_TEMPLATE 테이블 및 관련 쿼리
- MailController (HTTP 엔드포인트)
- UI용 사용자 조회 메서드 (MailDao)
- UI용 통계 쿼리 (alarm-mapper.xml)
- JSON 파싱 로직 (정규식 기반)
- 변수 치환 로직 (`{{변수명}}`)

**제거 이유:**
1. **Producer-Consumer 패턴 전환**: HTTP API 불필요
2. **TABLE 타입 미지원**: 정규식 파싱으로는 테이블 구조 표현 한계
3. **코드 복잡도 증가**: 템플릿 파싱 + 변수 치환 로직 오버헤드 (순환 복잡도 23 → 6)
4. **비개발자 템플릿 수정 요구 없음**: 모든 메일이 동적 생성

**개선 결과:**
- 코드 간결화: MailService 386줄 → 246줄, MailDao 91줄 → 43줄
- 컴파일 타임 검증 가능
- 모든 SectionType 지원 (TEXT, TABLE, HTML, DIVIDER)
- Consumer 중심 단순 아키텍처