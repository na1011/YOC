package com.yoc.wms.mail.util;

import com.yoc.wms.mail.domain.Recipient;
import com.yoc.wms.mail.exception.ValueChainException;
import org.junit.Test;

import java.sql.Clob;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
}
