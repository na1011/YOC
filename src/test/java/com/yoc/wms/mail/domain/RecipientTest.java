package com.yoc.wms.mail.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Recipient 단위 테스트
 *
 * 테스트 범위:
 * - Builder 패턴 정상 동작
 * - fromMap() 정상 변환
 * - 엣지케이스 (null, 빈 값)
 */
class RecipientTest {

    // ==================== Builder 패턴 테스트 ====================

    @Test
    @DisplayName("Builder: 정상 생성 - 모든 필드")
    void builder_allFields() {
        // When
        Recipient recipient = Recipient.builder()
            .userId("hong123")
            .email("hong@company.com")
            .group("ADM")
            .build();

        // Then - USER_ID는 대문자로 정규화됨
        assertEquals("HONG123", recipient.getUserId());
        assertEquals("hong@company.com", recipient.getEmail());
        assertEquals("ADM", recipient.getGroup());
    }

    @Test
    @DisplayName("Builder: 일부 필드만 설정")
    void builder_partialFields() {
        // When
        Recipient recipient = Recipient.builder()
            .userId("user1")
            .email("user1@company.com")
            .build();

        // Then - USER_ID는 대문자로 정규화됨
        assertEquals("USER1", recipient.getUserId());
        assertEquals("user1@company.com", recipient.getEmail());
        assertNull(recipient.getGroup());
    }

    @Test
    @DisplayName("Builder: 모든 필드 null")
    void builder_allNull() {
        // When
        Recipient recipient = Recipient.builder().build();

        // Then
        assertNull(recipient.getUserId());
        assertNull(recipient.getEmail());
        assertNull(recipient.getGroup());
    }

    // ==================== fromMap() 테스트 ====================

    @Test
    @DisplayName("fromMap: 정상 변환 - 모든 필드")
    void fromMap_allFields() {
        // Given
        Map<String, Object> map = new HashMap<>();
        map.put("userId", "kim456");
        map.put("email", "kim@company.com");
        map.put("group", "DEV");

        // When
        Recipient recipient = Recipient.fromMap(map);

        // Then - USER_ID는 대문자로 정규화됨
        assertEquals("KIM456", recipient.getUserId());
        assertEquals("kim@company.com", recipient.getEmail());
        assertEquals("DEV", recipient.getGroup());
    }

    @Test
    @DisplayName("fromMap: 일부 필드 누락")
    void fromMap_partialFields() {
        // Given
        Map<String, Object> map = new HashMap<>();
        map.put("userId", "user2");
        map.put("email", "user2@company.com");

        // When
        Recipient recipient = Recipient.fromMap(map);

        // Then - USER_ID는 대문자로 정규화됨
        assertEquals("USER2", recipient.getUserId());
        assertEquals("user2@company.com", recipient.getEmail());
        assertNull(recipient.getGroup());
    }

    @Test
    @DisplayName("fromMap: 빈 맵")
    void fromMap_emptyMap() {
        // Given
        Map<String, Object> map = new HashMap<>();

        // When
        Recipient recipient = Recipient.fromMap(map);

        // Then
        assertNull(recipient.getUserId());
        assertNull(recipient.getEmail());
        assertNull(recipient.getGroup());
    }

    @Test
    @DisplayName("fromMap: 잘못된 키 이름")
    void fromMap_wrongKeys() {
        // Given
        Map<String, Object> map = new HashMap<>();
        map.put("user", "wrong");  // 올바른 키는 userId
        map.put("mail", "wrong@test.com");  // 올바른 키는 email

        // When
        Recipient recipient = Recipient.fromMap(map);

        // Then
        assertNull(recipient.getUserId());
        assertNull(recipient.getEmail());
    }

    @Test
    @DisplayName("fromMap: 값이 null")
    void fromMap_nullValues() {
        // Given
        Map<String, Object> map = new HashMap<>();
        map.put("userId", null);
        map.put("email", null);
        map.put("group", null);

        // When
        Recipient recipient = Recipient.fromMap(map);

        // Then
        assertNull(recipient.getUserId());
        assertNull(recipient.getEmail());
        assertNull(recipient.getGroup());
    }

    // ==================== toString() 테스트 ====================

    @Test
    @DisplayName("toString: 정상 출력")
    void toStringTest() {
        // Given
        Recipient recipient = Recipient.builder()
            .userId("test")
            .email("test@company.com")
            .group("ADM")
            .build();

        // When
        String result = recipient.toString();

        // Then - USER_ID는 대문자로 정규화됨
        assertTrue(result.contains("TEST"));
        assertTrue(result.contains("test@company.com"));
        assertTrue(result.contains("ADM"));
    }

    @Test
    @DisplayName("toString: null 필드 포함")
    void toStringWithNull() {
        // Given
        Recipient recipient = Recipient.builder()
            .userId("user")
            .build();

        // When
        String result = recipient.toString();

        // Then - USER_ID는 대문자로 정규화됨
        assertNotNull(result);
        assertTrue(result.contains("USER"));
        assertTrue(result.contains("null"));
    }

    // ==================== 엣지케이스 테스트 ====================

    @Test
    @DisplayName("엣지케이스: 빈 문자열")
    void edgeCase_emptyString() {
        // When
        Recipient recipient = Recipient.builder()
            .userId("")
            .email("")
            .group("")
            .build();

        // Then - 빈 문자열도 toUpperCase() 적용되지만 결과는 동일
        assertEquals("", recipient.getUserId());
        assertEquals("", recipient.getEmail());
        assertEquals("", recipient.getGroup());
    }

    @Test
    @DisplayName("엣지케이스: 공백 문자열")
    void edgeCase_whitespace() {
        // When
        Recipient recipient = Recipient.builder()
            .userId("   ")
            .email("   ")
            .group("   ")
            .build();

        // Then - 공백도 toUpperCase() 적용되지만 결과는 동일
        assertEquals("   ", recipient.getUserId());
        assertEquals("   ", recipient.getEmail());
        assertEquals("   ", recipient.getGroup());
    }

    @Test
    @DisplayName("엣지케이스: 특수문자 포함")
    void edgeCase_specialCharacters() {
        // When
        Recipient recipient = Recipient.builder()
            .userId("user@123")
            .email("test+tag@company.com")
            .group("ADM-DEV")
            .build();

        // Then - USER_ID는 대문자로 정규화됨
        assertEquals("USER@123", recipient.getUserId());
        assertEquals("test+tag@company.com", recipient.getEmail());
        assertEquals("ADM-DEV", recipient.getGroup());
    }

    @Test
    @DisplayName("엣지케이스: 매우 긴 문자열")
    void edgeCase_longString() {
        // Given
        String longString = "a".repeat(1000);
        String longStringUpper = "A".repeat(1000);

        // When
        Recipient recipient = Recipient.builder()
            .userId(longString)
            .email(longString + "@company.com")
            .group(longString)
            .build();

        // Then - USER_ID는 대문자로 정규화됨, group은 정규화 안 됨
        assertEquals(longStringUpper, recipient.getUserId());
        assertTrue(recipient.getEmail().startsWith(longString));
        assertEquals(longString, recipient.getGroup());  // group은 그대로 유지
    }

    // ==================== fromMapList() 테스트 ====================

    @Test
    @DisplayName("fromMapList: 정상 변환 - 복수 Map")
    void fromMapList_multipleMaps() {
        // Given
        List<Map<String, Object>> maps = new ArrayList<>();

        Map<String, Object> map1 = new HashMap<>();
        map1.put("userId", "admin");
        map1.put("email", "admin@company.com");
        map1.put("group", "ADM");
        maps.add(map1);

        Map<String, Object> map2 = new HashMap<>();
        map2.put("userId", "user1");
        map2.put("email", "user1@company.com");
        map2.put("group", "USER");
        maps.add(map2);

        Map<String, Object> map3 = new HashMap<>();
        map3.put("userId", "user2");
        map3.put("email", "user2@company.com");
        map3.put("group", "USER");
        maps.add(map3);

        // When
        List<Recipient> recipients = Recipient.fromMapList(maps);

        // Then
        assertEquals(3, recipients.size());
        assertEquals("ADMIN", recipients.get(0).getUserId());  // 대문자 정규화
        assertEquals("admin@company.com", recipients.get(0).getEmail());
        assertEquals("USER1", recipients.get(1).getUserId());  // 대문자 정규화
        assertEquals("user1@company.com", recipients.get(1).getEmail());
        assertEquals("USER2", recipients.get(2).getUserId());  // 대문자 정규화
        assertEquals("user2@company.com", recipients.get(2).getEmail());
    }

    @Test
    @DisplayName("fromMapList: 빈 리스트")
    void fromMapList_emptyList() {
        // Given
        List<Map<String, Object>> maps = new ArrayList<>();

        // When
        List<Recipient> recipients = Recipient.fromMapList(maps);

        // Then
        assertNotNull(recipients);
        assertTrue(recipients.isEmpty());
    }

    @Test
    @DisplayName("fromMapList: null 리스트")
    void fromMapList_nullList() {
        // When
        List<Recipient> recipients = Recipient.fromMapList(null);

        // Then
        assertNotNull(recipients);
        assertTrue(recipients.isEmpty());
    }

    @Test
    @DisplayName("fromMapList: 중복 이메일 제거")
    void fromMapList_duplicateEmails() {
        // Given - 동일한 이메일을 가진 다른 사용자
        List<Map<String, Object>> maps = new ArrayList<>();

        Map<String, Object> map1 = new HashMap<>();
        map1.put("userId", "admin1");
        map1.put("email", "admin@company.com");
        map1.put("group", "ADM");
        maps.add(map1);

        Map<String, Object> map2 = new HashMap<>();
        map2.put("userId", "admin2");
        map2.put("email", "admin@company.com");  // 동일한 이메일 (중복)
        map2.put("group", "ADM");
        maps.add(map2);

        Map<String, Object> map3 = new HashMap<>();
        map3.put("userId", "user1");
        map3.put("email", "user@company.com");
        map3.put("group", "USER");
        maps.add(map3);

        // When
        List<Recipient> recipients = Recipient.fromMapList(maps);

        // Then - 중복된 이메일은 제거되어 2명만 반환
        assertEquals(2, recipients.size());
        assertEquals("admin@company.com", recipients.get(0).getEmail());
        assertEquals("user@company.com", recipients.get(1).getEmail());
    }

    @Test
    @DisplayName("fromMapList: 대소문자 혼용 이메일 중복 제거")
    void fromMapList_caseInsensitiveEmailDuplicates() {
        // Given - 대소문자만 다른 이메일
        List<Map<String, Object>> maps = new ArrayList<>();

        Map<String, Object> map1 = new HashMap<>();
        map1.put("userId", "admin1");
        map1.put("email", "Admin@Company.com");  // 대문자 포함
        map1.put("group", "ADM");
        maps.add(map1);

        Map<String, Object> map2 = new HashMap<>();
        map2.put("userId", "admin2");
        map2.put("email", "admin@company.com");  // 소문자 (동일 이메일)
        map2.put("group", "ADM");
        maps.add(map2);

        // When
        List<Recipient> recipients = Recipient.fromMapList(maps);

        // Then - 이메일 소문자 정규화로 중복 제거되어 1명만 반환
        assertEquals(1, recipients.size());
        assertEquals("admin@company.com", recipients.get(0).getEmail());  // 소문자로 정규화
    }

    @Test
    @DisplayName("fromMapList: 대소문자 혼용 USER_ID 정규화")
    void fromMapList_userIdCaseNormalization() {
        // Given
        List<Map<String, Object>> maps = new ArrayList<>();

        Map<String, Object> map1 = new HashMap<>();
        map1.put("userId", "admin");  // 소문자
        map1.put("email", "admin@company.com");
        map1.put("group", "ADM");
        maps.add(map1);

        Map<String, Object> map2 = new HashMap<>();
        map2.put("userId", "UsEr1");  // 혼용
        map2.put("email", "user1@company.com");
        map2.put("group", "USER");
        maps.add(map2);

        // When
        List<Recipient> recipients = Recipient.fromMapList(maps);

        // Then - USER_ID는 대문자로 정규화
        assertEquals(2, recipients.size());
        assertEquals("ADMIN", recipients.get(0).getUserId());
        assertEquals("USER1", recipients.get(1).getUserId());
    }

    @Test
    @DisplayName("fromMapList: 순서 보장 (LinkedHashSet)")
    void fromMapList_orderPreserved() {
        // Given - 순서가 중요한 리스트
        List<Map<String, Object>> maps = new ArrayList<>();

        Map<String, Object> map1 = new HashMap<>();
        map1.put("userId", "user1");
        map1.put("email", "user1@company.com");
        maps.add(map1);

        Map<String, Object> map2 = new HashMap<>();
        map2.put("userId", "user2");
        map2.put("email", "user2@company.com");
        maps.add(map2);

        Map<String, Object> map3 = new HashMap<>();
        map3.put("userId", "user3");
        map3.put("email", "user3@company.com");
        maps.add(map3);

        // When
        List<Recipient> recipients = Recipient.fromMapList(maps);

        // Then - 삽입 순서 유지
        assertEquals("USER1", recipients.get(0).getUserId());
        assertEquals("USER2", recipients.get(1).getUserId());
        assertEquals("USER3", recipients.get(2).getUserId());
    }

    @Test
    @DisplayName("fromMapList: Map 내부 필드 누락 처리")
    void fromMapList_missingFields() {
        // Given - 일부 필드가 누락된 Map
        List<Map<String, Object>> maps = new ArrayList<>();

        Map<String, Object> map1 = new HashMap<>();
        map1.put("userId", "admin");
        map1.put("email", "admin@company.com");
        // group 없음
        maps.add(map1);

        Map<String, Object> map2 = new HashMap<>();
        map2.put("email", "user@company.com");
        // userId, group 없음
        maps.add(map2);

        // When
        List<Recipient> recipients = Recipient.fromMapList(maps);

        // Then
        assertEquals(2, recipients.size());
        assertEquals("ADMIN", recipients.get(0).getUserId());
        assertNull(recipients.get(0).getGroup());
        assertNull(recipients.get(1).getUserId());
        assertEquals("user@company.com", recipients.get(1).getEmail());
    }
}