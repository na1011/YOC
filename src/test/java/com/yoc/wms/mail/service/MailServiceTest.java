package com.yoc.wms.mail.service;

import com.yoc.wms.mail.config.MailConfig;
import com.yoc.wms.mail.dao.MailDao;
import com.yoc.wms.mail.domain.MailRequest;
import com.yoc.wms.mail.domain.Recipient;
import com.yoc.wms.mail.exception.ValueChainException;
import com.yoc.wms.mail.renderer.MailBodyRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.internet.MimeMessage;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MailService 단위 테스트
 *
 * 테스트 범위:
 * - sendMail() 정상 흐름
 * - 수신인 검증 실패
 * - HTML 구조 생성
 * - 로그 생성 및 상태 업데이트
 * - 재시도 로직
 *
 * 주의: @Async 메서드는 실제 비동기 동작하지 않음 (단위 테스트)
 */
@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MailDao mailDao;

    @Mock
    private MailBodyRenderer renderer;

    @Mock
    private MailConfig mailConfig;

    @InjectMocks
    private MailService mailService;

    @Mock
    private MimeMessage mimeMessage;

    private MailRequest testRequest;
    private List<Recipient> testRecipients;

    @BeforeEach
    void setUp() {
        testRecipients = Collections.singletonList(
            Recipient.builder()
                .userId("test")
                .email("test@company.com")
                .build()
        );

        testRequest = MailRequest.builder()
            .subject("테스트 제목")
            .addTextSection("테스트 내용")
            .recipients(testRecipients)
            .mailType("DIRECT")
            .build();

        // MailConfig 기본 동작 설정 (lenient)
        lenient().when(mailConfig.getContactInfo()).thenReturn("문의: 010-1234-5678");

        // JavaMailSender Mock 설정 (lenient)
        lenient().when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Renderer Mock 설정 (lenient)
        lenient().when(renderer.render(anyList())).thenReturn("<div>Rendered Body</div>");
    }

    // ==================== sendMail() 정상 흐름 테스트 ====================

    @Test
    @DisplayName("sendMail: 정상 발송 - 전체 흐름 검증")
    void sendMail_success() throws Exception {
        // Given
        when(mailDao.insert(eq("mail.insertMailSendLog"), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(1);
            params.put("logId", 123L);
            return 1;
        });

        // When
        mailService.sendMail(testRequest);

        // Then - 비동기이지만 테스트 환경에서는 동기로 실행됨
        // 실제 검증은 sendMailAsync를 직접 호출하여 수행
        verify(mailDao, timeout(1000).times(1)).insert(eq("mail.insertMailSendLog"), anyMap());
        verify(mailSender, timeout(1000).times(1)).send(any(MimeMessage.class));
        verify(mailDao, timeout(1000).times(1)).update(eq("mail.updateMailSendLogStatus"), anyMap());
    }

    @Test
    @DisplayName("sendMailAsync: 로그 생성 검증")
    void sendMailAsync_logCreation() {
        // Given
        when(mailDao.insert(eq("mail.insertMailSendLog"), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(1);
            params.put("logId", 100L);

            // 파라미터 검증
            assertEquals("테스트 제목", params.get("subject"));
            assertEquals("DIRECT", params.get("mailType"));
            assertEquals("test@company.com", params.get("recipients"));
            assertEquals("PENDING", params.get("sendStatus"));
            assertEquals(0, params.get("retryCount"));

            return 1;
        });

        // When - protected 메서드 직접 호출을 위해 reflection 사용하지 않고 public sendMail 호출
        mailService.sendMail(testRequest);

        // Then
        verify(mailDao, timeout(1000)).insert(eq("mail.insertMailSendLog"), anyMap());
    }

    @Test
    @DisplayName("sendMailAsync: HTML 구조 생성")
    void sendMailAsync_htmlGeneration() {
        // Given
        when(renderer.render(anyList())).thenReturn("<p>Body Content</p>");
        when(mailDao.insert(eq("mail.insertMailSendLog"), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(1);
            params.put("logId", 200L);

            String bodyHtml = (String) params.get("bodyHtml");
            assertNotNull(bodyHtml);
            assertTrue(bodyHtml.contains("<!DOCTYPE html>"));
            assertTrue(bodyHtml.contains("<body"));
            assertTrue(bodyHtml.contains("WMS 시스템 알림"));
            assertTrue(bodyHtml.contains("<p>Body Content</p>"));

            return 1;
        });

        // When
        mailService.sendMail(testRequest);

        // Then
        verify(renderer, timeout(1000)).render(anyList());
    }

    @Test
    @DisplayName("sendMailAsync: 연락처 섹션 자동 추가")
    void sendMailAsync_contactSectionAdded() {
        // Given
        when(mailConfig.getContactInfo()).thenReturn("담당자: 홍길동");
        when(mailDao.insert(anyString(), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(1);
            params.put("logId", 300L);
            return 1;
        });

        // When
        mailService.sendMail(testRequest);

        // Then
        verify(renderer, timeout(1000)).render(argThat(sections -> {
            // 원래 1개 섹션 + 연락처 섹션 (DIVIDER + TEXT) = 3개
            return sections.size() == 3;
        }));
    }

    // ==================== 수신인 검증 테스트 ====================

    @Test
    @DisplayName("sendMailAsync: 잘못된 이메일 - 검증 실패")
    void sendMailAsync_invalidEmail() {
        // Given
        MailRequest invalidRequest = MailRequest.builder()
            .subject("제목")
            .addTextSection("내용")
            .addRecipient(Recipient.builder().email("invalid-email").build())
            .build();

        // When & Then
        assertThrows(ValueChainException.class, () ->
            mailService.sendMail(invalidRequest)
        );
    }

    @Test
    @DisplayName("sendMailAsync: 빈 수신인 목록 - 빌드 시 검증 실패")
    void sendMailAsync_emptyRecipients() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            MailRequest.builder()
                .subject("제목")
                .addTextSection("내용")
                .recipients(Collections.emptyList())
                .build()
        );
    }

    // ==================== CC 수신인 테스트 ====================

    @Test
    @DisplayName("sendMailAsync: CC 수신인 포함")
    void sendMailAsync_withCc() {
        // Given
        List<Recipient> ccRecipients = Collections.singletonList(
            Recipient.builder().email("cc@company.com").build()
        );
        MailRequest requestWithCc = MailRequest.builder()
            .subject("제목")
            .addTextSection("내용")
            .recipients(testRecipients)
            .ccRecipients(ccRecipients)
            .mailType("ALARM")
            .build();

        when(mailDao.insert(anyString(), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(1);
            params.put("logId", 400L);

            assertEquals("cc@company.com", params.get("ccRecipients"));

            return 1;
        });

        // When
        mailService.sendMail(requestWithCc);

        // Then
        verify(mailDao, timeout(1000)).insert(eq("mail.insertMailSendLog"), anyMap());
    }

    // ==================== 재시도 로직 테스트 ====================

    @Test
    @DisplayName("재시도: 첫 시도 실패 후 성공")
    void sendWithRetry_secondAttemptSuccess() throws Exception {
        // Given
        when(mailDao.insert(anyString(), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(1);
            params.put("logId", 500L);
            return 1;
        });

        // 첫 번째 send() 호출은 실패, 두 번째는 성공
        doThrow(new RuntimeException("Network error"))
            .doNothing()
            .when(mailSender).send(any(MimeMessage.class));

        // When
        mailService.sendMail(testRequest);

        // Then - 재시도로 인해 send() 2회 호출
        verify(mailSender, timeout(15000).times(2)).send(any(MimeMessage.class));

        // 최종적으로 SUCCESS 상태 업데이트
        verify(mailDao, timeout(15000)).update(eq("mail.updateMailSendLogStatus"), argThat(params ->
            "SUCCESS".equals(params.get("sendStatus"))
        ));
    }

    @Test
    @DisplayName("재시도: 3회 모두 실패")
    void sendWithRetry_allAttemptsFail() throws Exception {
        // Given
        when(mailDao.insert(anyString(), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(1);
            params.put("logId", 600L);
            return 1;
        });

        // 모든 send() 호출 실패
        doThrow(new RuntimeException("Persistent error"))
            .when(mailSender).send(any(MimeMessage.class));

        // When & Then
        assertThrows(ValueChainException.class, () ->
            mailService.sendMail(testRequest)
        );

        // 3회 시도 확인
        verify(mailSender, timeout(30000).times(3)).send(any(MimeMessage.class));

        // FAILURE 상태 업데이트
        verify(mailDao, timeout(30000)).update(eq("mail.updateMailSendLogStatus"), argThat(params ->
            "FAILURE".equals(params.get("sendStatus"))
        ));
    }

    // ==================== 엣지케이스 테스트 ====================

    @Test
    @DisplayName("엣지케이스: 매우 긴 에러 메시지")
    void edgeCase_longErrorMessage() throws Exception {
        // Given
        String longError = "E".repeat(3000);
        when(mailDao.insert(anyString(), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(1);
            params.put("logId", 700L);
            return 1;
        });

        doThrow(new RuntimeException(longError))
            .when(mailSender).send(any(MimeMessage.class));

        // When
        try {
            mailService.sendMail(testRequest);
        } catch (Exception ignored) {
            // 예외는 무시
        }

        // Then - 에러 메시지가 저장되었는지 확인
        verify(mailDao, timeout(30000)).update(eq("mail.updateMailSendLogStatus"), anyMap());
    }

    @Test
    @DisplayName("엣지케이스: mailSource null")
    void edgeCase_nullMailSource() {
        // Given
        MailRequest requestWithNullSource = MailRequest.builder()
            .subject("제목")
            .addTextSection("내용")
            .recipients(testRecipients)
            .mailType("DIRECT")
            .mailSource(null)
            .build();

        when(mailDao.insert(anyString(), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(1);
            params.put("logId", 800L);
            assertNull(params.get("mailSource"));
            return 1;
        });

        // When
        mailService.sendMail(requestWithNullSource);

        // Then
        verify(mailDao, timeout(1000)).insert(eq("mail.insertMailSendLog"), anyMap());
    }

    @Test
    @DisplayName("엣지케이스: 여러 섹션")
    void edgeCase_multipleSections() {
        // Given
        MailRequest multiSectionRequest = MailRequest.builder()
            .subject("제목")
            .addTextSection("A")
            .addDivider()
            .addTextSection("B")
            .recipients(testRecipients)
            .build();

        when(mailDao.insert(anyString(), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(1);
            params.put("logId", 900L);
            return 1;
        });

        // When
        mailService.sendMail(multiSectionRequest);

        // Then - 3개 섹션 + 연락처 섹션 (DIVIDER + TEXT) = 5개
        verify(renderer, timeout(1000)).render(argThat(sections ->
            sections.size() == 5
        ));
    }

    @Test
    @DisplayName("엣지케이스: 다수 수신인")
    void edgeCase_multipleRecipients() {
        // Given
        List<Recipient> manyRecipients = Arrays.asList(
            Recipient.builder().email("user1@company.com").build(),
            Recipient.builder().email("user2@company.com").build(),
            Recipient.builder().email("user3@company.com").build(),
            Recipient.builder().email("user4@company.com").build(),
            Recipient.builder().email("user5@company.com").build()
        );

        MailRequest requestWithMany = MailRequest.builder()
            .subject("제목")
            .addTextSection("내용")
            .recipients(manyRecipients)
            .build();

        when(mailDao.insert(anyString(), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(1);
            params.put("logId", 1000L);

            String recipients = (String) params.get("recipients");
            assertEquals(4, countOccurrences(recipients, ",")); // 5개 = 4개 콤마

            return 1;
        });

        // When
        mailService.sendMail(requestWithMany);

        // Then
        verify(mailDao, timeout(1000)).insert(eq("mail.insertMailSendLog"), anyMap());
    }

    // ==================== Helper Methods ====================

    private int countOccurrences(String str, String substr) {
        if (str == null || substr == null) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(substr, index)) != -1) {
            count++;
            index += substr.length();
        }
        return count;
    }
}