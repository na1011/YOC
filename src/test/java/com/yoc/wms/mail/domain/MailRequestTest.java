package com.yoc.wms.mail.domain;

import com.yoc.wms.mail.exception.ValueChainException;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * MailRequest 단위 테스트
 *
 * 테스트 범위:
 * - Builder + Helper Methods 패턴 정상 동작
 * - 검증 로직 (subject, sections, recipients)
 * - 엣지케이스 (빈 리스트, null)
 * - Subject 생성 로직
 */
public class MailRequestTest {

    private static final Recipient TEST_RECIPIENT = Recipient.builder()
        .userId("test")
        .email("test@company.com")
        .build();

    // ==================== Builder + Helper Methods 테스트 ====================

    @Test
    public void builderHelper_alarm_withTable() {
        // Given
        String title = "지연 주문 알림";
        String content = "10건의 지연 주문이 발견되었습니다.";
        String severity = "WARNING";
        List<Map<String, String>> tableData = Arrays.asList(
            createMap("orderId", "001"),
            createMap("orderId", "002")
        );
        List<Recipient> recipients = Collections.singletonList(TEST_RECIPIENT);

        // When
        MailRequest request = MailRequest.builder()
            .subject(MailRequest.alarmSubject(title, severity, tableData.size()))
            .addTextSection(MailRequest.alarmTitle(title, severity), content)
            .addTableSection(tableData)
            .addDivider()
            .recipients(recipients)
            .mailType("ALARM")
            .mailSource("OVERDUE_ORDERS")
            .build();

        // Then
        assertNotNull(request);
        assertEquals("ALARM", request.getMailType());
        assertEquals("OVERDUE_ORDERS", request.getMailSource());
        assertTrue(request.getSubject().contains("[경고]"));
        assertTrue(request.getSubject().contains("2건"));
        assertEquals(3, request.getSections().size()); // TEXT + TABLE + DIVIDER
        assertEquals(1, request.getRecipients().size());
    }

    @Test
    public void builderHelper_alarm_withoutTable() {
        // Given
        String title = "시스템 알림";
        String content = "정상 처리";
        String severity = "INFO";

        // When
        MailRequest request = MailRequest.builder()
            .subject(MailRequest.alarmSubject(title, severity, 0))
            .addTextSection(MailRequest.alarmTitle(title, severity), content)
            .addDivider()
            .recipients(Collections.singletonList(TEST_RECIPIENT))
            .mailType("ALARM")
            .mailSource("SYSTEM_CHECK")
            .build();

        // Then
        assertEquals(2, request.getSections().size()); // TEXT + DIVIDER만
        assertTrue(request.getSubject().contains("0건"));
    }

    @Test
    public void builderHelper_criticalSeverity() {
        // Given
        String title = "재고 부족";
        String content = "긴급 처리 필요";
        String severity = "CRITICAL";

        // When
        MailRequest request = MailRequest.builder()
            .subject(MailRequest.alarmSubject(title, severity, 0))
            .addTextSection(MailRequest.alarmTitle(title, severity), content)
            .addDivider()
            .recipients(Collections.singletonList(TEST_RECIPIENT))
            .mailType("ALARM")
            .mailSource("LOW_STOCK")
            .build();

        // Then
        assertTrue(request.getSubject().contains("[긴급]"));
    }

    @Test
    public void builderHelper_warningSeverity() {
        // Given
        String title = "품질 이슈";
        String content = "확인 필요";
        String severity = "WARNING";

        // When
        MailRequest request = MailRequest.builder()
            .subject(MailRequest.alarmSubject(title, severity, 0))
            .addTextSection(MailRequest.alarmTitle(title, severity), content)
            .addDivider()
            .recipients(Collections.singletonList(TEST_RECIPIENT))
            .mailType("ALARM")
            .mailSource("QUALITY_ISSUE")
            .build();

        // Then
        assertTrue(request.getSubject().contains("[경고]"));
    }

    @Test
    public void builderHelper_notice() {
        // Given
        String title = "시스템 점검 안내";
        String content = "12월 1일 점검 예정입니다.";

        // When
        MailRequest request = MailRequest.builder()
            .subject(title)
            .addTextSection(MailRequest.noticeTitle(title), content)
            .addDivider()
            .recipients(Collections.singletonList(TEST_RECIPIENT))
            .mailType("NOTICE")
            .build();

        // Then
        assertEquals("NOTICE", request.getMailType());
        assertEquals(title, request.getSubject());
        assertEquals(2, request.getSections().size());
        assertNull(request.getMailSource());
    }

    @Test
    public void builderHelper_report_withTable() {
        // Given
        String reportTitle = "일일 처리 현황";
        String description = "최근 처리 건수";
        List<Map<String, String>> tableData = Arrays.asList(
            createMap("date", "2024-11-01", "count", "150"),
            createMap("date", "2024-11-02", "count", "200")
        );

        // When
        MailRequest request = MailRequest.builder()
            .subject(reportTitle)
            .addTextSection(MailRequest.reportTitle(reportTitle), description)
            .addTableSection(tableData)
            .addDivider()
            .recipients(Collections.singletonList(TEST_RECIPIENT))
            .mailType("REPORT")
            .build();

        // Then
        assertEquals("REPORT", request.getMailType());
        assertEquals(reportTitle, request.getSubject());
        assertEquals(3, request.getSections().size());
    }

    @Test
    public void builderHelper_report_withoutTable() {
        // Given
        String reportTitle = "요약 보고";
        String description = "특이사항 없음";

        // When
        MailRequest request = MailRequest.builder()
            .subject(reportTitle)
            .addTextSection(MailRequest.reportTitle(reportTitle), description)
            .addDivider()
            .recipients(Collections.singletonList(TEST_RECIPIENT))
            .mailType("REPORT")
            .build();

        // Then
        assertEquals(2, request.getSections().size());
    }

    @Test
    public void builderHelper_simpleText() {
        // Given
        String subject = "테스트 메일";
        String title = "안내";
        String content = "테스트 메시지입니다.";

        // When
        MailRequest request = MailRequest.builder()
            .subject(subject)
            .addTextSection(title, content)
            .recipients(Collections.singletonList(TEST_RECIPIENT))
            .mailType("DIRECT")
            .build();

        // Then
        assertEquals("DIRECT", request.getMailType());
        assertEquals(subject, request.getSubject());
        assertEquals(1, request.getSections().size());
    }

    // ==================== Builder 패턴 테스트 ====================

    @Test
    public void builder_allFields() {
        // Given
        Recipient ccRecipient = Recipient.builder()
            .userId("cc")
            .email("cc@company.com")
            .build();

        // When
        MailRequest request = MailRequest.builder()
            .subject("테스트 제목")
            .addTextSection("제목", "내용")
            .addRecipient(TEST_RECIPIENT)
            .addCcRecipient(ccRecipient)
            .mailType("DIRECT")
            .mailSource("TEST")
            .build();

        // Then
        assertEquals("테스트 제목", request.getSubject());
        assertEquals(1, request.getSections().size());
        assertEquals(1, request.getRecipients().size());
        assertEquals(1, request.getCcRecipients().size());
        assertTrue(request.hasCc());
        assertEquals("DIRECT", request.getMailType());
        assertEquals("TEST", request.getMailSource());
    }

    @Test
    public void builder_noCc() {
        // When
        MailRequest request = MailRequest.builder()
            .subject("제목")
            .addTextSection("내용")
            .addRecipient(TEST_RECIPIENT)
            .build();

        // Then
        assertFalse(request.hasCc());
        assertNotNull(request.getCcRecipients());
        assertTrue(request.getCcRecipients().isEmpty());
    }

    @Test
    public void builder_defaultMailType() {
        // When
        MailRequest request = MailRequest.builder()
            .subject("제목")
            .addTextSection("내용")
            .addRecipient(TEST_RECIPIENT)
            .build();

        // Then
        assertEquals("DIRECT", request.getMailType());
    }

    @Test
    public void builder_addDivider() {
        // When
        MailRequest request = MailRequest.builder()
            .subject("제목")
            .addTextSection("첫 번째 섹션")
            .addDivider()
            .addTextSection("두 번째 섹션")
            .addRecipient(TEST_RECIPIENT)
            .build();

        // Then
        assertEquals(3, request.getSections().size());
    }

    @Test
    public void builder_multipleSections() {
        // Given
        List<Map<String, String>> tableData = Collections.singletonList(
            createMap("key", "value")
        );

        // When
        MailRequest request = MailRequest.builder()
            .subject("제목")
            .addTextSection("섹션 1", "내용 1")
            .addTableSection(tableData)
            .addDivider()
            .addTextSection("섹션 2", "내용 2")
            .addRecipient(TEST_RECIPIENT)
            .build();

        // Then
        assertEquals(4, request.getSections().size());
    }

    // ==================== 검증 로직 테스트 ====================

    @Test
    public void validation_nullSubject() {
        // When & Then
        try {
            MailRequest.builder()
                .addDivider()
                .addRecipient(TEST_RECIPIENT)
                .build();
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("Subject is required"));
        }
    }

    @Test
    public void validation_emptySubject() {
        // When & Then
        try {
            MailRequest.builder()
                .subject("")
                .addDivider()
                .addRecipient(TEST_RECIPIENT)
                .build();
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("Subject is required"));
        }
    }

    @Test
    public void validation_blankSubject() {
        // When & Then
        try {
            MailRequest.builder()
                .subject("   ")
                .addDivider()
                .addRecipient(TEST_RECIPIENT)
                .build();
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            // Exception caught successfully
        }
    }

    @Test
    public void validation_emptySections() {
        // When & Then
        try {
            MailRequest.builder()
                .subject("제목")
                .addRecipient(TEST_RECIPIENT)
                .build();
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("At least one section is required"));
        }
    }

    @Test
    public void validation_nullRecipients() {
        // When & Then
        try {
            MailRequest.builder()
                .subject("제목")
                .addDivider()
                .recipients(null)
                .build();
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("At least one recipient is required"));
        }
    }

    @Test
    public void validation_emptyRecipients() {
        // When & Then
        try {
            MailRequest.builder()
                .subject("제목")
                .addDivider()
                .recipients(Collections.<Recipient>emptyList())
                .build();
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("At least one recipient is required"));
        }
    }

    // ==================== Subject 생성 로직 테스트 ====================

    @Test
    public void helper_alarmSubject_critical_zeroCount() {
        // When
        String subject = MailRequest.alarmSubject("알림", "CRITICAL", 0);

        // Then
        assertTrue(subject.matches("\\[긴급\\] WMS 알림 0건"));
    }

    @Test
    public void helper_alarmSubject_warning_multipleCount() {
        // When
        String subject = MailRequest.alarmSubject("지연 주문", "WARNING", 3);

        // Then
        assertTrue(subject.matches("\\[경고\\] WMS 지연 주문 3건"));
    }

    @Test
    public void helper_alarmSubject_info() {
        // When
        String subject = MailRequest.alarmSubject("정보", "INFO", 0);

        // Then
        assertTrue(subject.contains("[경고]")); // INFO도 [경고]로 표시
    }

    @Test
    public void helper_alarmTitle() {
        // When & Then
        assertTrue(MailRequest.alarmTitle("제목", "CRITICAL").contains("🔴"));
        assertTrue(MailRequest.alarmTitle("제목", "WARNING").contains("⚠️"));
        assertTrue(MailRequest.alarmTitle("제목", "INFO").contains("ℹ️"));
    }

    @Test
    public void helper_noticeTitle() {
        // When
        String title = MailRequest.noticeTitle("시스템 점검");

        // Then
        assertTrue(title.contains("📢"));
        assertTrue(title.contains("시스템 점검"));
    }

    @Test
    public void helper_reportTitle() {
        // When
        String title = MailRequest.reportTitle("월간 보고서");

        // Then
        assertTrue(title.contains("📊"));
        assertTrue(title.contains("월간 보고서"));
    }

    // ==================== Helper Methods ====================

    private Map<String, String> createMap(String... keyValues) {
        Map<String, String> map = new HashMap<String, String>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
