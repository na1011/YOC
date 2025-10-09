package com.yoc.wms.mail.util;

import com.yoc.wms.mail.domain.Recipient;
import com.yoc.wms.mail.exception.ValueChainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Clob;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
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
class MailUtilsTest {

    // ==================== isValidEmail() 테스트 ====================

    @Test
    @DisplayName("이메일 검증: 정상 형식")
    void isValidEmail_valid() {
        assertTrue(MailUtils.isValidEmail("user@company.com"));
        assertTrue(MailUtils.isValidEmail("test.user@company.co.kr"));
        assertTrue(MailUtils.isValidEmail("test+tag@example.com"));
        assertTrue(MailUtils.isValidEmail("admin_123@test-domain.io"));
        assertTrue(MailUtils.isValidEmail("a@b.co"));
    }

    @Test
    @DisplayName("이메일 검증: 비정상 형식 - @ 없음")
    void isValidEmail_noAtSign() {
        assertFalse(MailUtils.isValidEmail("usercompany.com"));
    }

    @Test
    @DisplayName("이메일 검증: 비정상 형식 - 도메인 없음")
    void isValidEmail_noDomain() {
        assertFalse(MailUtils.isValidEmail("user@"));
        assertFalse(MailUtils.isValidEmail("user@.com"));
    }

    @Test
    @DisplayName("이메일 검증: 비정상 형식 - 로컬파트 없음")
    void isValidEmail_noLocalPart() {
        assertFalse(MailUtils.isValidEmail("@company.com"));
    }

    @Test
    @DisplayName("이메일 검증: 비정상 형식 - TLD 없음")
    void isValidEmail_noTld() {
        assertFalse(MailUtils.isValidEmail("user@company"));
    }

    @Test
    @DisplayName("이메일 검증: 비정상 형식 - 공백 포함")
    void isValidEmail_withSpaces() {
        assertFalse(MailUtils.isValidEmail("user @company.com"));
        assertFalse(MailUtils.isValidEmail("user@ company.com"));
    }

    @Test
    @DisplayName("이메일 검증: null")
    void isValidEmail_null() {
        assertFalse(MailUtils.isValidEmail(null));
    }

    @Test
    @DisplayName("이메일 검증: 빈 문자열")
    void isValidEmail_empty() {
        assertFalse(MailUtils.isValidEmail(""));
    }

    @Test
    @DisplayName("이메일 검증: 공백만")
    void isValidEmail_blank() {
        assertFalse(MailUtils.isValidEmail("   "));
    }

    @Test
    @DisplayName("이메일 검증: TLD 1글자 - 실패")
    void isValidEmail_tldTooShort() {
        assertFalse(MailUtils.isValidEmail("user@company.c"));
    }

    @Test
    @DisplayName("이메일 검증: 다수 @ 기호")
    void isValidEmail_multipleAtSigns() {
        assertFalse(MailUtils.isValidEmail("user@@company.com"));
        assertFalse(MailUtils.isValidEmail("user@test@company.com"));
    }

    // ==================== validateRecipients() 테스트 ====================

    @Test
    @DisplayName("수신인 검증: 정상 케이스")
    void validateRecipients_valid() {
        // Given
        List<Recipient> recipients = Arrays.asList(
            Recipient.builder().email("user1@company.com").build(),
            Recipient.builder().email("user2@company.com").build()
        );

        // When & Then
        assertDoesNotThrow(() -> MailUtils.validateRecipients(recipients));
    }

    @Test
    @DisplayName("수신인 검증: null 목록")
    void validateRecipients_null() {
        // When & Then
        ValueChainException ex = assertThrows(ValueChainException.class,
            () -> MailUtils.validateRecipients(null)
        );
        assertTrue(ex.getMessage().contains("수신인이 없습니다"));
    }

    @Test
    @DisplayName("수신인 검증: 빈 목록")
    void validateRecipients_empty() {
        // When & Then
        ValueChainException ex = assertThrows(ValueChainException.class,
            () -> MailUtils.validateRecipients(Collections.emptyList())
        );
        assertTrue(ex.getMessage().contains("수신인이 없습니다"));
    }

    @Test
    @DisplayName("수신인 검증: 이메일 주소 null")
    void validateRecipients_nullEmail() {
        // Given
        List<Recipient> recipients = Collections.singletonList(
            Recipient.builder().userId("user1").email(null).build()
        );

        // When & Then
        ValueChainException ex = assertThrows(ValueChainException.class,
            () -> MailUtils.validateRecipients(recipients)
        );
        assertTrue(ex.getMessage().contains("이메일 주소가 없습니다"));
    }

    @Test
    @DisplayName("수신인 검증: 빈 이메일 주소")
    void validateRecipients_emptyEmail() {
        // Given
        List<Recipient> recipients = Collections.singletonList(
            Recipient.builder().userId("user1").email("").build()
        );

        // When & Then
        ValueChainException ex = assertThrows(ValueChainException.class,
            () -> MailUtils.validateRecipients(recipients)
        );
        assertTrue(ex.getMessage().contains("이메일 주소가 없습니다"));
    }

    @Test
    @DisplayName("수신인 검증: 공백 이메일 주소")
    void validateRecipients_blankEmail() {
        // Given
        List<Recipient> recipients = Collections.singletonList(
            Recipient.builder().userId("user1").email("   ").build()
        );

        // When & Then
        ValueChainException ex = assertThrows(ValueChainException.class,
            () -> MailUtils.validateRecipients(recipients)
        );
        assertTrue(ex.getMessage().contains("이메일 주소가 없습니다"));
    }

    @Test
    @DisplayName("수신인 검증: 잘못된 이메일 형식")
    void validateRecipients_invalidFormat() {
        // Given
        List<Recipient> recipients = Collections.singletonList(
            Recipient.builder().email("invalid-email").build()
        );

        // When & Then
        ValueChainException ex = assertThrows(ValueChainException.class,
            () -> MailUtils.validateRecipients(recipients)
        );
        assertTrue(ex.getMessage().contains("잘못된 이메일 형식"));
    }

    @Test
    @DisplayName("수신인 검증: 중복된 이메일 (동일)")
    void validateRecipients_duplicateEmail() {
        // Given
        List<Recipient> recipients = Arrays.asList(
            Recipient.builder().email("user@company.com").build(),
            Recipient.builder().email("user@company.com").build()
        );

        // When & Then
        ValueChainException ex = assertThrows(ValueChainException.class,
            () -> MailUtils.validateRecipients(recipients)
        );
        assertTrue(ex.getMessage().contains("중복된 이메일"));
    }

    @Test
    @DisplayName("수신인 검증: 중복된 이메일 (대소문자 차이)")
    void validateRecipients_duplicateCaseInsensitive() {
        // Given
        List<Recipient> recipients = Arrays.asList(
            Recipient.builder().email("User@Company.com").build(),
            Recipient.builder().email("user@company.com").build()
        );

        // When & Then
        ValueChainException ex = assertThrows(ValueChainException.class,
            () -> MailUtils.validateRecipients(recipients)
        );
        assertTrue(ex.getMessage().contains("중복된 이메일"));
    }

    @Test
    @DisplayName("수신인 검증: 여러 오류 중 첫 번째만 보고")
    void validateRecipients_firstErrorOnly() {
        // Given - null 이메일이 먼저 검증됨
        List<Recipient> recipients = Arrays.asList(
            Recipient.builder().email(null).build(),
            Recipient.builder().email("invalid").build()
        );

        // When & Then
        ValueChainException ex = assertThrows(ValueChainException.class,
            () -> MailUtils.validateRecipients(recipients)
        );
        assertTrue(ex.getMessage().contains("이메일 주소가 없습니다"));
    }

    // ==================== convertToString() 테스트 ====================

    @Test
    @DisplayName("CLOB 변환: String 타입 - 그대로 반환")
    void convertToString_string() {
        // When
        String result = MailUtils.convertToString("테스트 문자열");

        // Then
        assertEquals("테스트 문자열", result);
    }

    @Test
    @DisplayName("CLOB 변환: null - 빈 문자열 반환")
    void convertToString_null() {
        // When
        String result = MailUtils.convertToString(null);

        // Then
        assertEquals("", result);
    }

    @Test
    @DisplayName("CLOB 변환: Clob 타입 - getSubString() 호출")
    void convertToString_clob() throws Exception {
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
    @DisplayName("CLOB 변환: Clob 타입 - 빈 CLOB")
    void convertToString_emptyClob() throws Exception {
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
    @DisplayName("CLOB 변환: Clob 타입 - 변환 실패")
    void convertToString_clobFails() throws Exception {
        // Given
        Clob clob = mock(Clob.class);
        when(clob.length()).thenThrow(new RuntimeException("DB error"));

        // When & Then
        ValueChainException ex = assertThrows(ValueChainException.class,
            () -> MailUtils.convertToString(clob)
        );
        assertTrue(ex.getMessage().contains("CLOB 변환 실패"));
    }

    @Test
    @DisplayName("CLOB 변환: 기타 객체 - toString() 호출")
    void convertToString_otherObject() {
        // Given
        Integer number = 12345;

        // When
        String result = MailUtils.convertToString(number);

        // Then
        assertEquals("12345", result);
    }

    @Test
    @DisplayName("CLOB 변환: 긴 CLOB")
    void convertToString_largeClobContent() throws Exception {
        // Given
        String longText = "A".repeat(100000);
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
    @DisplayName("수신인 포맷: 정상 케이스")
    void formatRecipientsToString_valid() {
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
    @DisplayName("수신인 포맷: 단일 수신인")
    void formatRecipientsToString_single() {
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
    @DisplayName("수신인 포맷: null 목록 - 빈 문자열")
    void formatRecipientsToString_null() {
        // When
        String result = MailUtils.formatRecipientsToString(null);

        // Then
        assertEquals("", result);
    }

    @Test
    @DisplayName("수신인 포맷: 빈 목록 - 빈 문자열")
    void formatRecipientsToString_empty() {
        // When
        String result = MailUtils.formatRecipientsToString(Collections.emptyList());

        // Then
        assertEquals("", result);
    }

    @Test
    @DisplayName("수신인 포맷: 많은 수신인")
    void formatRecipientsToString_many() {
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
    @DisplayName("엣지케이스: 매우 긴 이메일 주소")
    void edgeCase_veryLongEmail() {
        // Given
        String localPart = "a".repeat(64); // RFC 5321 최대 로컬파트 길이
        String email = localPart + "@company.com";

        // When & Then
        assertTrue(MailUtils.isValidEmail(email));
    }

    @Test
    @DisplayName("엣지케이스: 국제화 도메인 (IDN) - 현재 미지원")
    void edgeCase_idn() {
        // 현재 패턴은 ASCII만 지원
        assertFalse(MailUtils.isValidEmail("user@한글.com"));
    }

    @Test
    @DisplayName("엣지케이스: 특수문자 조합")
    void edgeCase_specialCharacters() {
        assertTrue(MailUtils.isValidEmail("user+filter@sub-domain.example.com"));
        assertTrue(MailUtils.isValidEmail("test_user.name@test-domain.co.uk"));
    }

    @Test
    @DisplayName("엣지케이스: CLOB 최대 길이")
    void edgeCase_maxClobLength() throws Exception {
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