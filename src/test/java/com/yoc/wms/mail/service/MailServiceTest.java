package com.yoc.wms.mail.service;

import com.yoc.wms.mail.domain.Recipient;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * MailService 단위 테스트 (Pure Functions만 테스트)
 *
 * 테스트 범위:
 * - joinRecipientEmails() - 수신인 이메일 문자열 생성
 * - truncateErrorMessage() - 에러 메시지 자르기
 * - recipientsToEmailArray() - 수신인 목록을 이메일 배열로 변환
 *
 * Mock/verify 없음 (Chicago School 테스트 방식)
 * 운영 환경 100% 호환 (Mockito 불필요)
 *
 * @since v2.4.0 (Pure Functions 테스트)
 */
public class MailServiceTest {

    private MailService service;

    @Before
    public void setUp() {
        service = new MailService();
    }

    // ===== joinRecipientEmails() 테스트 =====

    @Test
    public void joinRecipientEmails_singleRecipient() {
        // Given
        List<Recipient> recipients = Arrays.asList(
                Recipient.builder().email("admin@company.com").build()
        );

        // When
        String result = service.joinRecipientEmails(recipients);

        // Then
        assertEquals("admin@company.com", result);
    }

    @Test
    public void joinRecipientEmails_multipleRecipients() {
        // Given
        List<Recipient> recipients = Arrays.asList(
                Recipient.builder().email("admin1@company.com").build(),
                Recipient.builder().email("admin2@company.com").build(),
                Recipient.builder().email("user@company.com").build()
        );

        // When
        String result = service.joinRecipientEmails(recipients);

        // Then
        assertEquals("admin1@company.com,admin2@company.com,user@company.com", result);
    }

    @Test
    public void joinRecipientEmails_nullInput() {
        // When
        String result = service.joinRecipientEmails(null);

        // Then
        assertEquals("", result);
    }

    @Test
    public void joinRecipientEmails_emptyList() {
        // When
        String result = service.joinRecipientEmails(Collections.emptyList());

        // Then
        assertEquals("", result);
    }

    @Test
    public void joinRecipientEmails_withUserId() {
        // Given - userId가 있어도 email만 사용
        List<Recipient> recipients = Arrays.asList(
                Recipient.builder().email("admin@company.com").userId("ADMIN1").build(),
                Recipient.builder().email("user@company.com").userId("USER1").build()
        );

        // When
        String result = service.joinRecipientEmails(recipients);

        // Then
        assertEquals("admin@company.com,user@company.com", result);
    }

    @Test
    public void joinRecipientEmails_manyRecipients() {
        // Given - 10명의 수신인
        List<Recipient> recipients = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            recipients.add(Recipient.builder().email("user" + i + "@company.com").build());
        }

        // When
        String result = service.joinRecipientEmails(recipients);

        // Then
        String[] emails = result.split(",");
        assertEquals(10, emails.length);
        assertEquals("user1@company.com", emails[0]);
        assertEquals("user10@company.com", emails[9]);
    }

    // ===== truncateErrorMessage() 테스트 =====

    @Test
    public void truncateErrorMessage_withinLimit() {
        // Given
        String errorMessage = "Short error message";

        // When
        String result = service.truncateErrorMessage(errorMessage, 2000);

        // Then
        assertEquals("Short error message", result);
    }

    @Test
    public void truncateErrorMessage_exceedsLimit() {
        // Given
        StringBuilder longMessage = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longMessage.append("Error! ");
        }
        String errorMessage = longMessage.toString();  // 2100자

        // When
        String result = service.truncateErrorMessage(errorMessage, 2000);

        // Then
        assertEquals(2000, result.length());
        assertEquals(errorMessage.substring(0, 2000), result);
    }

    @Test
    public void truncateErrorMessage_exactLimit() {
        // Given
        StringBuilder exactMessage = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            exactMessage.append("A");
        }
        String errorMessage = exactMessage.toString();

        // When
        String result = service.truncateErrorMessage(errorMessage, 2000);

        // Then
        assertEquals(2000, result.length());
        assertEquals(errorMessage, result);
    }

    @Test
    public void truncateErrorMessage_nullInput() {
        // When
        String result = service.truncateErrorMessage(null, 2000);

        // Then
        assertNull(result);
    }

    @Test
    public void truncateErrorMessage_emptyString() {
        // When
        String result = service.truncateErrorMessage("", 2000);

        // Then
        assertEquals("", result);
    }

    @Test
    public void truncateErrorMessage_variousLimits() {
        // Given
        String errorMessage = "This is a test error message";

        // When & Then
        assertEquals("This ", service.truncateErrorMessage(errorMessage, 5));
        assertEquals("This is a ", service.truncateErrorMessage(errorMessage, 10));
        assertEquals(errorMessage, service.truncateErrorMessage(errorMessage, 100));
    }

    @Test
    public void truncateErrorMessage_unicodeCharacters() {
        // Given - 한글 문자
        String errorMessage = "한글 에러 메시지입니다.";

        // When
        String result = service.truncateErrorMessage(errorMessage, 5);

        // Then
        assertEquals(5, result.length());
        assertEquals("한글 에러", result);
    }

    // ===== recipientsToEmailArray() 테스트 =====

    @Test
    public void recipientsToEmailArray_singleRecipient() {
        // Given
        List<Recipient> recipients = Arrays.asList(
                Recipient.builder().email("admin@company.com").build()
        );

        // When
        String[] result = service.recipientsToEmailArray(recipients);

        // Then
        assertEquals(1, result.length);
        assertEquals("admin@company.com", result[0]);
    }

    @Test
    public void recipientsToEmailArray_multipleRecipients() {
        // Given
        List<Recipient> recipients = Arrays.asList(
                Recipient.builder().email("admin1@company.com").build(),
                Recipient.builder().email("admin2@company.com").build(),
                Recipient.builder().email("user@company.com").build()
        );

        // When
        String[] result = service.recipientsToEmailArray(recipients);

        // Then
        assertEquals(3, result.length);
        assertEquals("admin1@company.com", result[0]);
        assertEquals("admin2@company.com", result[1]);
        assertEquals("user@company.com", result[2]);
    }

    @Test
    public void recipientsToEmailArray_preservesOrder() {
        // Given - 순서 확인
        List<Recipient> recipients = Arrays.asList(
                Recipient.builder().email("z@test.com").build(),
                Recipient.builder().email("a@test.com").build(),
                Recipient.builder().email("m@test.com").build()
        );

        // When
        String[] result = service.recipientsToEmailArray(recipients);

        // Then - 입력 순서 유지 (정렬 안 됨)
        assertEquals("z@test.com", result[0]);
        assertEquals("a@test.com", result[1]);
        assertEquals("m@test.com", result[2]);
    }

    @Test
    public void recipientsToEmailArray_withUserId() {
        // Given - userId가 있어도 email만 추출
        List<Recipient> recipients = Arrays.asList(
                Recipient.builder().email("admin@company.com").userId("ADMIN1").build(),
                Recipient.builder().email("user@company.com").userId("USER1").build()
        );

        // When
        String[] result = service.recipientsToEmailArray(recipients);

        // Then
        assertEquals(2, result.length);
        assertEquals("admin@company.com", result[0]);
        assertEquals("user@company.com", result[1]);
    }

    @Test
    public void recipientsToEmailArray_manyRecipients() {
        // Given - 100명의 수신인
        List<Recipient> recipients = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            recipients.add(Recipient.builder().email("user" + i + "@company.com").build());
        }

        // When
        String[] result = service.recipientsToEmailArray(recipients);

        // Then
        assertEquals(100, result.length);
        assertEquals("user1@company.com", result[0]);
        assertEquals("user100@company.com", result[99]);
    }

    @Test
    public void recipientsToEmailArray_arrayTypeCheck() {
        // Given
        List<Recipient> recipients = Arrays.asList(
                Recipient.builder().email("test@company.com").build()
        );

        // When
        String[] result = service.recipientsToEmailArray(recipients);

        // Then - 타입 확인
        assertTrue(result instanceof String[]);
        assertNotNull(result);
    }

    // ===== Edge Cases =====

    @Test
    public void joinRecipientEmails_specialCharactersInEmail() {
        // Given - 특수 문자 포함 이메일
        List<Recipient> recipients = Arrays.asList(
                Recipient.builder().email("user.name+tag@company.com").build(),
                Recipient.builder().email("user_name@company.co.kr").build()
        );

        // When
        String result = service.joinRecipientEmails(recipients);

        // Then
        assertEquals("user.name+tag@company.com,user_name@company.co.kr", result);
    }

    @Test
    public void truncateErrorMessage_withNewlines() {
        // Given - 개행 문자 포함
        String errorMessage = "Line1\nLine2\nLine3\n";

        // When
        String result = service.truncateErrorMessage(errorMessage, 10);

        // Then
        assertEquals(10, result.length());
        assertEquals("Line1\nLine", result);
    }

    @Test
    public void recipientsToEmailArray_duplicateEmails() {
        // Given - 중복 이메일 (recipientsToEmailArray는 중복 제거 안 함)
        List<Recipient> recipients = Arrays.asList(
                Recipient.builder().email("admin@company.com").build(),
                Recipient.builder().email("admin@company.com").build()
        );

        // When
        String[] result = service.recipientsToEmailArray(recipients);

        // Then - 중복 제거 없이 그대로 변환
        assertEquals(2, result.length);
        assertEquals("admin@company.com", result[0]);
        assertEquals("admin@company.com", result[1]);
    }
}
