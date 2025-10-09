package com.yoc.wms.mail.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

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

        // Then
        assertEquals("hong123", recipient.getUserId());
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

        // Then
        assertEquals("user1", recipient.getUserId());
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

        // Then
        assertEquals("kim456", recipient.getUserId());
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

        // Then
        assertEquals("user2", recipient.getUserId());
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

        // Then
        assertTrue(result.contains("test"));
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

        // Then
        assertNotNull(result);
        assertTrue(result.contains("user"));
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

        // Then
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

        // Then
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

        // Then
        assertEquals("user@123", recipient.getUserId());
        assertEquals("test+tag@company.com", recipient.getEmail());
        assertEquals("ADM-DEV", recipient.getGroup());
    }

    @Test
    @DisplayName("엣지케이스: 매우 긴 문자열")
    void edgeCase_longString() {
        // Given
        String longString = "a".repeat(1000);

        // When
        Recipient recipient = Recipient.builder()
            .userId(longString)
            .email(longString + "@company.com")
            .group(longString)
            .build();

        // Then
        assertEquals(longString, recipient.getUserId());
        assertTrue(recipient.getEmail().startsWith(longString));
        assertEquals(longString, recipient.getGroup());
    }
}