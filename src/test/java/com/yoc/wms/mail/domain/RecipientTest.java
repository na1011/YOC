package com.yoc.wms.mail.domain;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Recipient 단위 테스트
 *
 * 테스트 범위:
 * - Builder 패턴 정상 동작
 * - fromMap() 정상 변환
 * - 엣지케이스 (null, 빈 값)
 */
public class RecipientTest {

    // ==================== Builder 패턴 테스트 ====================

    @Test
    public void builder_allFields() {
        // Builder: 정상 생성 - 모든 필드
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
    public void builder_partialFields() {
        // Builder: 일부 필드만 설정
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
    public void builder_allNull() {
        // Builder: 모든 필드 null
        // When
        Recipient recipient = Recipient.builder().build();

        // Then
        assertNull(recipient.getUserId());
        assertNull(recipient.getEmail());
        assertNull(recipient.getGroup());
    }

    // ==================== fromMap() 테스트 ====================

    @Test
    public void fromMap_allFields() {
        // fromMap: 정상 변환 - 모든 필드
        // Given
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("USER_ID", "kim456");
        map.put("EMAIL", "kim@company.com");
        map.put("USER_GROUP", "DEV");

        // When
        Recipient recipient = Recipient.fromMap(map);

        // Then - USER_ID는 대문자로 정규화됨
        assertEquals("KIM456", recipient.getUserId());
        assertEquals("kim@company.com", recipient.getEmail());
        assertEquals("DEV", recipient.getGroup());
    }

    @Test
    public void fromMap_partialFields() {
        // fromMap: 일부 필드 누락
        // Given
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("USER_ID", "user2");
        map.put("EMAIL", "user2@company.com");

        // When
        Recipient recipient = Recipient.fromMap(map);

        // Then - USER_ID는 대문자로 정규화됨
        assertEquals("USER2", recipient.getUserId());
        assertEquals("user2@company.com", recipient.getEmail());
        assertNull(recipient.getGroup());
    }

    @Test
    public void fromMap_emptyMap() {
        // fromMap: 빈 맵
        // Given
        Map<String, Object> map = new HashMap<String, Object>();

        // When
        Recipient recipient = Recipient.fromMap(map);

        // Then
        assertNull(recipient.getUserId());
        assertNull(recipient.getEmail());
        assertNull(recipient.getGroup());
    }

    @Test
    public void fromMap_wrongKeys() {
        // fromMap: 잘못된 키 이름
        // Given
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("user", "wrong");  // 올바른 키는 userId
        map.put("mail", "wrong@test.com");  // 올바른 키는 email

        // When
        Recipient recipient = Recipient.fromMap(map);

        // Then
        assertNull(recipient.getUserId());
        assertNull(recipient.getEmail());
    }

    @Test
    public void fromMap_nullValues() {
        // fromMap: 값이 null
        // Given
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("USER_ID", null);
        map.put("EMAIL", null);
        map.put("USER_GROUP", null);

        // When
        Recipient recipient = Recipient.fromMap(map);

        // Then
        assertNull(recipient.getUserId());
        assertNull(recipient.getEmail());
        assertNull(recipient.getGroup());
    }

    // ==================== toString() 테스트 ====================

    @Test
    public void toStringTest() {
        // toString: 정상 출력
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
    public void toStringWithNull() {
        // toString: null 필드 포함
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
    public void edgeCase_emptyString() {
        // 엣지케이스: 빈 문자열
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
    public void edgeCase_whitespace() {
        // 엣지케이스: 공백 문자열
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
    public void edgeCase_specialCharacters() {
        // 엣지케이스: 특수문자 포함
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
    public void edgeCase_longString() {
        // 엣지케이스: 매우 긴 문자열
        // Given
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb1.append("a");
            sb2.append("A");
        }
        String longString = sb1.toString();
        String longStringUpper = sb2.toString();

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
    public void fromMapList_multipleMaps() {
        // fromMapList: 정상 변환 - 복수 Map
        // Given
        List<Map<String, Object>> maps = new ArrayList<Map<String, Object>>();

        Map<String, Object> map1 = new HashMap<String, Object>();
        map1.put("USER_ID", "admin");
        map1.put("EMAIL", "admin@company.com");
        map1.put("USER_GROUP", "ADM");
        maps.add(map1);

        Map<String, Object> map2 = new HashMap<String, Object>();
        map2.put("USER_ID", "user1");
        map2.put("EMAIL", "user1@company.com");
        map2.put("USER_GROUP", "USER");
        maps.add(map2);

        Map<String, Object> map3 = new HashMap<String, Object>();
        map3.put("USER_ID", "user2");
        map3.put("EMAIL", "user2@company.com");
        map3.put("USER_GROUP", "USER");
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
    public void fromMapList_emptyList() {
        // fromMapList: 빈 리스트
        // Given
        List<Map<String, Object>> maps = new ArrayList<Map<String, Object>>();

        // When
        List<Recipient> recipients = Recipient.fromMapList(maps);

        // Then
        assertNotNull(recipients);
        assertTrue(recipients.isEmpty());
    }

    @Test
    public void fromMapList_nullList() {
        // fromMapList: null 리스트
        // When
        List<Recipient> recipients = Recipient.fromMapList(null);

        // Then
        assertNotNull(recipients);
        assertTrue(recipients.isEmpty());
    }

    @Test
    public void fromMapList_duplicateEmails() {
        // fromMapList: 중복 이메일 제거
        // Given - 동일한 이메일을 가진 다른 사용자
        List<Map<String, Object>> maps = new ArrayList<Map<String, Object>>();

        Map<String, Object> map1 = new HashMap<String, Object>();
        map1.put("USER_ID", "admin1");
        map1.put("EMAIL", "admin@company.com");
        map1.put("USER_GROUP", "ADM");
        maps.add(map1);

        Map<String, Object> map2 = new HashMap<String, Object>();
        map2.put("USER_ID", "admin2");
        map2.put("EMAIL", "admin@company.com");  // 동일한 이메일 (중복)
        map2.put("USER_GROUP", "ADM");
        maps.add(map2);

        Map<String, Object> map3 = new HashMap<String, Object>();
        map3.put("USER_ID", "user1");
        map3.put("EMAIL", "user@company.com");
        map3.put("USER_GROUP", "USER");
        maps.add(map3);

        // When
        List<Recipient> recipients = Recipient.fromMapList(maps);

        // Then - 중복된 이메일은 제거되어 2명만 반환
        assertEquals(2, recipients.size());
        assertEquals("admin@company.com", recipients.get(0).getEmail());
        assertEquals("user@company.com", recipients.get(1).getEmail());
    }

    @Test
    public void fromMapList_caseInsensitiveEmailDuplicates() {
        // fromMapList: 대소문자 혼용 이메일 중복 제거
        // Given - 대소문자만 다른 이메일
        List<Map<String, Object>> maps = new ArrayList<Map<String, Object>>();

        Map<String, Object> map1 = new HashMap<String, Object>();
        map1.put("USER_ID", "admin1");
        map1.put("EMAIL", "Admin@Company.com");  // 대문자 포함
        map1.put("USER_GROUP", "ADM");
        maps.add(map1);

        Map<String, Object> map2 = new HashMap<String, Object>();
        map2.put("USER_ID", "admin2");
        map2.put("EMAIL", "admin@company.com");  // 소문자 (동일 이메일)
        map2.put("USER_GROUP", "ADM");
        maps.add(map2);

        // When
        List<Recipient> recipients = Recipient.fromMapList(maps);

        // Then - 이메일 소문자 정규화로 중복 제거되어 1명만 반환
        assertEquals(1, recipients.size());
        assertEquals("admin@company.com", recipients.get(0).getEmail());  // 소문자로 정규화
    }

    @Test
    public void fromMapList_userIdCaseNormalization() {
        // fromMapList: 대소문자 혼용 USER_ID 정규화
        // Given
        List<Map<String, Object>> maps = new ArrayList<Map<String, Object>>();

        Map<String, Object> map1 = new HashMap<String, Object>();
        map1.put("USER_ID", "admin");  // 소문자
        map1.put("EMAIL", "admin@company.com");
        map1.put("USER_GROUP", "ADM");
        maps.add(map1);

        Map<String, Object> map2 = new HashMap<String, Object>();
        map2.put("USER_ID", "UsEr1");  // 혼용
        map2.put("EMAIL", "user1@company.com");
        map2.put("USER_GROUP", "USER");
        maps.add(map2);

        // When
        List<Recipient> recipients = Recipient.fromMapList(maps);

        // Then - USER_ID는 대문자로 정규화
        assertEquals(2, recipients.size());
        assertEquals("ADMIN", recipients.get(0).getUserId());
        assertEquals("USER1", recipients.get(1).getUserId());
    }

    @Test
    public void fromMapList_orderPreserved() {
        // fromMapList: 순서 보장 (LinkedHashSet)
        // Given - 순서가 중요한 리스트
        List<Map<String, Object>> maps = new ArrayList<Map<String, Object>>();

        Map<String, Object> map1 = new HashMap<String, Object>();
        map1.put("USER_ID", "user1");
        map1.put("EMAIL", "user1@company.com");
        maps.add(map1);

        Map<String, Object> map2 = new HashMap<String, Object>();
        map2.put("USER_ID", "user2");
        map2.put("EMAIL", "user2@company.com");
        maps.add(map2);

        Map<String, Object> map3 = new HashMap<String, Object>();
        map3.put("USER_ID", "user3");
        map3.put("EMAIL", "user3@company.com");
        maps.add(map3);

        // When
        List<Recipient> recipients = Recipient.fromMapList(maps);

        // Then - 삽입 순서 유지
        assertEquals("USER1", recipients.get(0).getUserId());
        assertEquals("USER2", recipients.get(1).getUserId());
        assertEquals("USER3", recipients.get(2).getUserId());
    }

    @Test
    public void fromMapList_missingFields() {
        // fromMapList: Map 내부 필드 누락 처리
        // Given - 일부 필드가 누락된 Map
        List<Map<String, Object>> maps = new ArrayList<Map<String, Object>>();

        Map<String, Object> map1 = new HashMap<String, Object>();
        map1.put("USER_ID", "admin");
        map1.put("EMAIL", "admin@company.com");
        // group 없음
        maps.add(map1);

        Map<String, Object> map2 = new HashMap<String, Object>();
        map2.put("EMAIL", "user@company.com");
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
