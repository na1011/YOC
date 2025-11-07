package com.yoc.wms.mail.util;

import com.yoc.wms.mail.domain.Recipient;
import com.yoc.wms.mail.exception.ValueChainException;
import org.junit.Test;

import java.sql.Clob;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * MailUtils 단위 테스트
 *
 * 테스트 범위:
 * - 이메일 검증 (정상/비정상 형식)
 * - CLOB 변환 (H2/Oracle 호환성)
 * - 수신인 검증 (null, empty, 형식 오류, 중복)
 * - 엣지케이스
 */
public class MailUtilsTest {

    // ==================== isValidEmail() 테스트 ====================

    @Test
    public void isValidEmail_valid() {
        assertTrue(MailUtils.isValidEmail("user@company.com"));
        assertTrue(MailUtils.isValidEmail("test.user@company.co.kr"));
        assertTrue(MailUtils.isValidEmail("test+tag@example.com"));
        assertTrue(MailUtils.isValidEmail("admin_123@test-domain.io"));
        assertTrue(MailUtils.isValidEmail("a@b.co"));
    }

    @Test
    public void isValidEmail_noAtSign() {
        assertFalse(MailUtils.isValidEmail("usercompany.com"));
    }

    @Test
    public void isValidEmail_noDomain() {
        assertFalse(MailUtils.isValidEmail("user@"));
        assertFalse(MailUtils.isValidEmail("user@.com"));
    }

    @Test
    public void isValidEmail_noLocalPart() {
        assertFalse(MailUtils.isValidEmail("@company.com"));
    }

    @Test
    public void isValidEmail_noTld() {
        assertFalse(MailUtils.isValidEmail("user@company"));
    }

    @Test
    public void isValidEmail_withSpaces() {
        assertFalse(MailUtils.isValidEmail("user @company.com"));
        assertFalse(MailUtils.isValidEmail("user@ company.com"));
    }

    @Test
    public void isValidEmail_null() {
        assertFalse(MailUtils.isValidEmail(null));
    }

    @Test
    public void isValidEmail_empty() {
        assertFalse(MailUtils.isValidEmail(""));
    }

    @Test
    public void isValidEmail_blank() {
        assertFalse(MailUtils.isValidEmail("   "));
    }

    @Test
    public void isValidEmail_tldTooShort() {
        assertFalse(MailUtils.isValidEmail("user@company.c"));
    }

    @Test
    public void isValidEmail_multipleAtSigns() {
        assertFalse(MailUtils.isValidEmail("user@@company.com"));
        assertFalse(MailUtils.isValidEmail("user@test@company.com"));
    }

    // ==================== validateRecipients() 테스트 ====================

    @Test
    public void validateRecipients_valid() {
        // Given
        List<Recipient> recipients = Arrays.asList(
            Recipient.builder().email("user1@company.com").build(),
            Recipient.builder().email("user2@company.com").build()
        );

        // When & Then
        try {
            MailUtils.validateRecipients(recipients);
        } catch (Exception e) {
            fail("Should not throw exception");
        }
    }

    @Test
    public void validateRecipients_null() {
        // When & Then
        try {
            MailUtils.validateRecipients(null);
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("수신인이 없습니다"));
        }
    }

    @Test
    public void validateRecipients_empty() {
        // When & Then
        try {
            MailUtils.validateRecipients(Collections.<Recipient>emptyList());
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("수신인이 없습니다"));
        }
    }

    @Test
    public void validateRecipients_nullEmail() {
        // Given
        List<Recipient> recipients = Collections.singletonList(
            Recipient.builder().userId("user1").email(null).build()
        );

        // When & Then
        try {
            MailUtils.validateRecipients(recipients);
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("이메일 주소가 없습니다"));
        }
    }

    @Test
    public void validateRecipients_emptyEmail() {
        // Given
        List<Recipient> recipients = Collections.singletonList(
            Recipient.builder().userId("user1").email("").build()
        );

        // When & Then
        try {
            MailUtils.validateRecipients(recipients);
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("이메일 주소가 없습니다"));
        }
    }

    @Test
    public void validateRecipients_blankEmail() {
        // Given
        List<Recipient> recipients = Collections.singletonList(
            Recipient.builder().userId("user1").email("   ").build()
        );

        // When & Then
        try {
            MailUtils.validateRecipients(recipients);
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("이메일 주소가 없습니다"));
        }
    }

    @Test
    public void validateRecipients_invalidFormat() {
        // Given
        List<Recipient> recipients = Collections.singletonList(
            Recipient.builder().email("invalid-email").build()
        );

        // When & Then
        try {
            MailUtils.validateRecipients(recipients);
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("잘못된 이메일 형식"));
        }
    }

    @Test
    public void validateRecipients_duplicateEmail() {
        // Given
        List<Recipient> recipients = Arrays.asList(
            Recipient.builder().email("user@company.com").build(),
            Recipient.builder().email("user@company.com").build()
        );

        // When & Then
        try {
            MailUtils.validateRecipients(recipients);
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("중복된 이메일"));
        }
    }

    @Test
    public void validateRecipients_duplicateCaseInsensitive() {
        // Given
        List<Recipient> recipients = Arrays.asList(
            Recipient.builder().email("User@Company.com").build(),
            Recipient.builder().email("user@company.com").build()
        );

        // When & Then
        try {
            MailUtils.validateRecipients(recipients);
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("중복된 이메일"));
        }
    }

    @Test
    public void validateRecipients_firstErrorOnly() {
        // Given - null 이메일이 먼저 검증됨
        List<Recipient> recipients = Arrays.asList(
            Recipient.builder().email(null).build(),
            Recipient.builder().email("invalid").build()
        );

        // When & Then
        try {
            MailUtils.validateRecipients(recipients);
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("이메일 주소가 없습니다"));
        }
    }

    // ==================== convertToString() 테스트 ====================

    @Test
    public void convertToString_string() {
        // When
        String result = MailUtils.convertToString("테스트 문자열");

        // Then
        assertEquals("테스트 문자열", result);
    }

    @Test
    public void convertToString_null() {
        // When
        String result = MailUtils.convertToString(null);

        // Then
        assertEquals("", result);
    }

    @Test
    public void convertToString_clob() throws Exception {
        // Given
        Clob clob = mock(Clob.class);
        when(clob.length()).thenReturn(5L);
        when(clob.getSubString(1, 5)).thenReturn("HELLO");

        // When
        String result = MailUtils.convertToString(clob);

        // Then
        assertEquals("HELLO", result);
        verify(clob).length();
        verify(clob).getSubString(1, 5);
    }

    @Test
    public void convertToString_emptyClob() throws Exception {
        // Given
        Clob clob = mock(Clob.class);
        when(clob.length()).thenReturn(0L);

        // When
        String result = MailUtils.convertToString(clob);

        // Then
        assertEquals("", result);
        verify(clob).length();
        verify(clob, never()).getSubString(anyLong(), anyInt());
    }

    @Test
    public void convertToString_clobFails() throws Exception {
        // Given
        Clob clob = mock(Clob.class);
        when(clob.length()).thenThrow(new RuntimeException("DB error"));

        // When & Then
        try {
            MailUtils.convertToString(clob);
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("CLOB 변환 실패"));
        }
    }

    @Test
    public void convertToString_otherObject() {
        // Given
        Integer number = 12345;

        // When
        String result = MailUtils.convertToString(number);

        // Then
        assertEquals("12345", result);
    }

    @Test
    public void convertToString_largeClobContent() throws Exception {
        // Given
        String longText = repeatString("A", 100000);
        Clob clob = mock(Clob.class);
        when(clob.length()).thenReturn((long) longText.length());
        when(clob.getSubString(1, longText.length())).thenReturn(longText);

        // When
        String result = MailUtils.convertToString(clob);

        // Then
        assertEquals(100000, result.length());
        assertTrue(result.startsWith("AAAA"));
    }

    // ==================== formatRecipientsToString() 테스트 ====================

    @Test
    public void formatRecipientsToString_valid() {
        // Given
        List<Recipient> recipients = Arrays.asList(
            Recipient.builder().email("user1@company.com").build(),
            Recipient.builder().email("user2@company.com").build(),
            Recipient.builder().email("user3@company.com").build()
        );

        // When
        String result = MailUtils.formatRecipientsToString(recipients);

        // Then
        assertEquals("user1@company.com,user2@company.com,user3@company.com", result);
    }

    @Test
    public void formatRecipientsToString_single() {
        // Given
        List<Recipient> recipients = Collections.singletonList(
            Recipient.builder().email("single@company.com").build()
        );

        // When
        String result = MailUtils.formatRecipientsToString(recipients);

        // Then
        assertEquals("single@company.com", result);
    }

    @Test
    public void formatRecipientsToString_null() {
        // When
        String result = MailUtils.formatRecipientsToString(null);

        // Then
        assertEquals("", result);
    }

    @Test
    public void formatRecipientsToString_empty() {
        // When
        String result = MailUtils.formatRecipientsToString(Collections.<Recipient>emptyList());

        // Then
        assertEquals("", result);
    }

    @Test
    public void formatRecipientsToString_many() {
        // Given
        List<Recipient> recipients = Arrays.asList(
            Recipient.builder().email("user1@test.com").build(),
            Recipient.builder().email("user2@test.com").build(),
            Recipient.builder().email("user3@test.com").build(),
            Recipient.builder().email("user4@test.com").build(),
            Recipient.builder().email("user5@test.com").build()
        );

        // When
        String result = MailUtils.formatRecipientsToString(recipients);

        // Then
        assertTrue(result.contains("user1@test.com"));
        assertTrue(result.contains("user5@test.com"));
        assertEquals(4, countOccurrences(result, ",")); // 5개 이메일 = 4개 콤마
    }

    // ==================== parseCommaSeparated() 테스트 ====================

    @Test
    public void parseCommaSeparated_valid() {
        // When
        List<String> result = MailUtils.parseCommaSeparated("admin1,user1,user2");

        // Then
        assertEquals(3, result.size());
        assertEquals("admin1", result.get(0));
        assertEquals("user1", result.get(1));
        assertEquals("user2", result.get(2));
    }

    @Test
    public void parseCommaSeparated_withSpaces() {
        // When
        List<String> result = MailUtils.parseCommaSeparated(" admin1 , user1 , user2 ");

        // Then
        assertEquals(3, result.size());
        assertEquals("admin1", result.get(0));
        assertEquals("user1", result.get(1));
        assertEquals("user2", result.get(2));
    }

    @Test
    public void parseCommaSeparated_emptyTokens() {
        // When
        List<String> result = MailUtils.parseCommaSeparated("admin1,,user1,");

        // Then
        assertEquals(2, result.size());
        assertEquals("admin1", result.get(0));
        assertEquals("user1", result.get(1));
    }

    @Test
    public void parseCommaSeparated_null() {
        // When
        List<String> result = MailUtils.parseCommaSeparated(null);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseCommaSeparated_empty() {
        // When
        List<String> result = MailUtils.parseCommaSeparated("");

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseCommaSeparated_onlySpaces() {
        // When
        List<String> result = MailUtils.parseCommaSeparated("   ");

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseCommaSeparated_single() {
        // When
        List<String> result = MailUtils.parseCommaSeparated("admin");

        // Then
        assertEquals(1, result.size());
        assertEquals("admin", result.get(0));
    }

    // ==================== convertToStringMap() 테스트 ====================

    @Test
    public void convertToStringMap_mixedTypes() {
        // Given
        List<Map<String, Object>> source = Arrays.asList(
            createMap("KEY1", "Value1", "KEY2", 123, "KEY3", 45.67)
        );

        // When
        List<Map<String, String>> result = MailUtils.convertToStringMap(source);

        // Then
        assertEquals(1, result.size());
        assertEquals("Value1", result.get(0).get("KEY1"));
        assertEquals("123", result.get(0).get("KEY2"));
        assertEquals("45.67", result.get(0).get("KEY3"));
    }

    @Test
    public void convertToStringMap_nullValue() {
        // Given
        List<Map<String, Object>> source = Arrays.asList(
            createMap("KEY1", "Value1", "KEY2", null)
        );

        // When
        List<Map<String, String>> result = MailUtils.convertToStringMap(source);

        // Then
        assertEquals("", result.get(0).get("KEY2"));
    }

    @Test
    public void convertToStringMap_emptyList() {
        // Given
        List<Map<String, Object>> source = new ArrayList<>();

        // When
        List<Map<String, String>> result = MailUtils.convertToStringMap(source);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    public void convertToStringMap_null() {
        // When
        List<Map<String, String>> result = MailUtils.convertToStringMap(null);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    public void convertToStringMap_multipleRows() {
        // Given
        List<Map<String, Object>> source = Arrays.asList(
            createMap("KEY", "Row1"),
            createMap("KEY", "Row2"),
            createMap("KEY", "Row3")
        );

        // When
        List<Map<String, String>> result = MailUtils.convertToStringMap(source);

        // Then
        assertEquals(3, result.size());
        assertEquals("Row1", result.get(0).get("KEY"));
        assertEquals("Row2", result.get(1).get("KEY"));
        assertEquals("Row3", result.get(2).get("KEY"));
    }

    @Test
    public void convertToStringMap_preservesOrder() {
        // Given
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("FIRST", "1");
        map.put("SECOND", "2");
        map.put("THIRD", "3");
        List<Map<String, Object>> source = Arrays.asList(map);

        // When
        List<Map<String, String>> result = MailUtils.convertToStringMap(source);

        // Then - LinkedHashMap preserves order
        Iterator<String> keys = result.get(0).keySet().iterator();
        assertEquals("FIRST", keys.next());
        assertEquals("SECOND", keys.next());
        assertEquals("THIRD", keys.next());
    }

    @Test
    public void convertToStringMap_withNullElement() {
        // Given - MyBatis "SELECT null FROM DUAL" 시나리오
        List<Map<String, Object>> source = Arrays.asList(
            createMap("KEY", "Value1"),
            null,  // null element (자동 필터링 대상)
            createMap("KEY", "Value2")
        );

        // When
        List<Map<String, String>> result = MailUtils.convertToStringMap(source);

        // Then - null element is filtered out
        assertEquals(2, result.size());
        assertEquals("Value1", result.get(0).get("KEY"));
        assertEquals("Value2", result.get(1).get("KEY"));
    }

    @Test
    public void convertToStringMap_allNullElements() {
        // Given - 모든 요소가 null
        List<Map<String, Object>> source = Arrays.asList(null, null, null);

        // When
        List<Map<String, String>> result = MailUtils.convertToStringMap(source);

        // Then - returns empty list (빈 리스트 반환)
        assertTrue(result.isEmpty());
    }

    // ==================== 엣지케이스 테스트 ====================

    @Test
    public void edgeCase_veryLongEmail() {
        // Given
        String localPart = repeatString("a", 64); // RFC 5321 최대 로컬파트 길이
        String email = localPart + "@company.com";

        // When & Then
        assertTrue(MailUtils.isValidEmail(email));
    }

    @Test
    public void edgeCase_idn() {
        // 현재 패턴은 ASCII만 지원
        assertFalse(MailUtils.isValidEmail("user@한글.com"));
    }

    @Test
    public void edgeCase_specialCharacters() {
        assertTrue(MailUtils.isValidEmail("user+filter@sub-domain.example.com"));
        assertTrue(MailUtils.isValidEmail("test_user.name@test-domain.co.uk"));
    }

    @Test
    public void edgeCase_maxClobLength() throws Exception {
        // Given
        Clob clob = mock(Clob.class);
        when(clob.length()).thenReturn(Integer.MAX_VALUE - 1L);
        when(clob.getSubString(eq(1L), anyInt())).thenReturn("content");

        // When
        String result = MailUtils.convertToString(clob);

        // Then
        assertEquals("content", result);
    }

    // ==================== Helper Methods ====================

    private String repeatString(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    private int countOccurrences(String str, String substr) {
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(substr, index)) != -1) {
            count++;
            index += substr.length();
        }
        return count;
    }

    /**
     * Map 생성 헬퍼 (가변 인자)
     */
    private Map<String, Object> createMap(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
