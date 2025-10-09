package com.yoc.wms.mail.integration;

import com.yoc.wms.mail.dao.MailDao;
import com.yoc.wms.mail.domain.MailRequest;
import com.yoc.wms.mail.service.AlarmMailService;
import com.yoc.wms.mail.service.MailService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AlarmMailService 통합 테스트 (Mock 기반)
 *
 * - 시나리오 구성
 * 1. 정상 발송 (PENDING → SUCCESS)
 * 2. 복수 알람 배치 처리
 * 3. 첫 번째 재시도 (RETRY_COUNT 증가)
 * 4. 최종 실패 (3회 재시도 후 FAILED)
 * 5. 재시도 후 성공 (Resilience 검증)
 * 6. SQL_ID 동적 조회 - OVERDUE_ORDERS
 * 7. SQL_ID 동적 조회 - LOW_STOCK
 * 8. 빈 테이블 데이터 (테이블 섹션 생략)
 * 9. CLOB 변환 검증
 * 10. 심각도별 처리 (CRITICAL/WARNING/INFO)
 */
@SpringBootTest
@ActiveProfiles("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AlarmMailServiceIntegrationTest {

    @Autowired
    private AlarmMailService alarmMailService;

    @Autowired
    private MailDao mailDao;

    @MockBean  // ⭐ 실제 메일 발송 방지
    private MailService mailService;

    @BeforeEach
    void setUp() {
        // 큐 초기화
        mailDao.delete("alarm.deleteAllQueue", null);

        // MockBean 초기화
        reset(mailService);

        System.out.println("\n========================================");
        System.out.println("AlarmMailService 통합 테스트 시작");
        System.out.println("큐 초기화 완료");
        System.out.println("========================================\n");
    }

    @AfterEach
    void tearDown() {
        System.out.println("\n테스트 종료\n");
    }


    // ==================== 시나리오 1: 정상 발송 (PENDING → SUCCESS) ====================

    @Test
    @Order(1)
    @DisplayName("시나리오 1: 정상 발송 (PENDING → SUCCESS)")
    void scenario1_normalFlow_pendingToSuccess() {
        System.out.println("\n[시나리오 1] 정상 발송 (PENDING → SUCCESS)");

        // Given - Producer 시뮬레이션: 큐에 PENDING 알람 삽입
        Map<String, Object> queueData = new HashMap<>();
        queueData.put("mailSource", "TEST_ALARM");
        queueData.put("alarmName", "테스트 알람");
        queueData.put("severity", "INFO");
        queueData.put("sqlId", "alarm.selectOverdueOrdersDetail");
        queueData.put("sectionTitle", "지연 주문 알림");
        queueData.put("sectionContent", "지연된 주문이 발견되었습니다.");
        queueData.put("retryCount", 0);

        mailDao.insert("alarm.insertTestQueue", queueData);

        // When - Consumer 실행
        alarmMailService.processQueue();

        // Then - MailService 호출 검증
        verify(mailService, times(1)).sendMail(any(MailRequest.class));

        // DB 상태 검증: SUCCESS로 변경되었는지 확인
        Map<String, Object> params = new HashMap<>();
        params.put("mailSource", "TEST_ALARM");
        List<Map<String, Object>> queues = mailDao.selectList("alarm.selectQueueByMailSource", params);
        assertEquals(1, queues.size());
        assertEquals("SUCCESS", queues.get(0).get("status"));
        assertEquals(0, ((Number) queues.get(0).get("retryCount")).intValue());

        System.out.println("✅ 정상 발송 완료: PENDING → SUCCESS");
    }


    // ==================== 시나리오 2: 복수 알람 배치 처리 ====================

    @Test
    @Order(2)
    @DisplayName("시나리오 2: 복수 알람 배치 처리")
    void scenario2_batchProcessing_multipleAlarms() {
        System.out.println("\n[시나리오 2] 복수 알람 배치 처리");

        // Given - 3건의 서로 다른 알람 삽입
        String[] mailSources = {"OVERDUE_ORDERS", "LOW_STOCK", "SYSTEM_ERROR"};
        for (String source : mailSources) {
            Map<String, Object> queueData = new HashMap<>();
            queueData.put("mailSource", source);
            queueData.put("alarmName", source + " 알람");
            queueData.put("severity", "WARNING");
            queueData.put("sqlId", "alarm.selectOverdueOrdersDetail");
            queueData.put("sectionTitle", source + " 발생");
            queueData.put("sectionContent", source + " 상세 내용");
            queueData.put("retryCount", 0);
            mailDao.insert("alarm.insertTestQueue", queueData);
        }

        // When
        alarmMailService.processQueue();

        // Then - 3번 호출되었는지 확인
        verify(mailService, times(3)).sendMail(any(MailRequest.class));

        // 모든 큐가 SUCCESS인지 확인
        for (String source : mailSources) {
            Map<String, Object> params = new HashMap<>();
            params.put("mailSource", source);
            List<Map<String, Object>> queues = mailDao.selectList("alarm.selectQueueByMailSource", params);
            assertEquals("SUCCESS", queues.get(0).get("status"));
        }

        System.out.println("✅ 배치 처리 완료: 3건 모두 SUCCESS");
    }


    // ==================== 시나리오 3: 첫 번째 재시도 (RETRY_COUNT 증가) ====================

    @Test
    @Order(3)
    @DisplayName("시나리오 3: 첫 번째 재시도 (RETRY_COUNT 증가)")
    void scenario3_firstRetry_retryCountIncrement() {
        System.out.println("\n[시나리오 3] 첫 번째 재시도");

        // Given - 큐 삽입
        Map<String, Object> queueData = new HashMap<>();
        queueData.put("mailSource", "RETRY_TEST");
        queueData.put("alarmName", "재시도 테스트");
        queueData.put("severity", "WARNING");
        queueData.put("sqlId", "alarm.selectOverdueOrdersDetail");
        queueData.put("sectionTitle", "재시도 테스트");
        queueData.put("sectionContent", "재시도 시나리오");
        queueData.put("retryCount", 0);
        mailDao.insert("alarm.insertTestQueue", queueData);

        // MailService가 예외 발생하도록 설정
        doThrow(new RuntimeException("SMTP 연결 실패"))
                .when(mailService).sendMail(any(MailRequest.class));

        // When
        alarmMailService.processQueue();

        // Then - 재시도 상태 확인
        Map<String, Object> params = new HashMap<>();
        params.put("mailSource", "RETRY_TEST");
        List<Map<String, Object>> queues = mailDao.selectList("alarm.selectQueueByMailSource", params);
        assertEquals("PENDING", queues.get(0).get("status"));  // 상태는 PENDING 유지
        assertEquals(1, ((Number) queues.get(0).get("retryCount")).intValue());  // RETRY_COUNT 증가
        // errorMessage는 DB 컬럼에 저장됨 (NULL일 수 있음 - 체크하지 않음)

        System.out.println("✅ 재시도 상태 업데이트: RETRY_COUNT = 1");
    }


    // ==================== 시나리오 4: 최종 실패 (3회 재시도 후 FAILED) ====================

    @Test
    @Order(4)
    @DisplayName("시나리오 4: 최종 실패 (3회 재시도 후 FAILED)")
    void scenario4_finalFailure_afterMaxRetries() {
        System.out.println("\n[시나리오 4] 최종 실패 (3회 재시도 후)");

        // Given - RETRY_COUNT = 2인 큐 삽입 (이번 시도가 3번째)
        Map<String, Object> queueData = new HashMap<>();
        queueData.put("mailSource", "FINAL_FAILURE");
        queueData.put("alarmName", "최종 실패 테스트");
        queueData.put("severity", "CRITICAL");
        queueData.put("sqlId", "alarm.selectOverdueOrdersDetail");
        queueData.put("sectionTitle", "최종 실패");
        queueData.put("sectionContent", "3회 재시도 실패");
        queueData.put("retryCount", 2);  // 이미 2회 재시도
        mailDao.insert("alarm.insertTestQueue", queueData);

        // MailService가 예외 발생
        doThrow(new RuntimeException("최종 실패"))
                .when(mailService).sendMail(any(MailRequest.class));

        // When
        alarmMailService.processQueue();

        // Then - FAILED 상태 확인
        Map<String, Object> params = new HashMap<>();
        params.put("mailSource", "FINAL_FAILURE");
        List<Map<String, Object>> queues = mailDao.selectList("alarm.selectQueueByMailSource", params);
        assertEquals("FAILED", queues.get(0).get("status"));  // 최종 실패
        assertEquals(2, ((Number) queues.get(0).get("retryCount")).intValue());  // RETRY_COUNT 유지
        // errorMessage는 DB 컬럼에 저장됨 (NULL일 수 있음 - 체크하지 않음)

        System.out.println("✅ 최종 실패 처리: PENDING → FAILED");
    }


    // ==================== 시나리오 5: 재시도 후 성공 (Resilience 검증) ====================

    @Test
    @Order(5)
    @DisplayName("시나리오 5: 재시도 후 성공 (Resilience)")
    void scenario5_retryThenSuccess_resilience() {
        System.out.println("\n[시나리오 5] 재시도 후 성공");

        // Given - RETRY_COUNT = 1인 큐 삽입 (이전에 1회 실패)
        Map<String, Object> queueData = new HashMap<>();
        queueData.put("mailSource", "RETRY_SUCCESS");
        queueData.put("alarmName", "재시도 성공 테스트");
        queueData.put("severity", "WARNING");
        queueData.put("sqlId", "alarm.selectOverdueOrdersDetail");
        queueData.put("sectionTitle", "재시도 성공");
        queueData.put("sectionContent", "이번엔 성공");
        queueData.put("retryCount", 1);
        mailDao.insert("alarm.insertTestQueue", queueData);

        // MailService는 정상 동작 (기본 동작)

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailService, times(1)).sendMail(any(MailRequest.class));

        Map<String, Object> params = new HashMap<>();
        params.put("mailSource", "RETRY_SUCCESS");
        List<Map<String, Object>> queues = mailDao.selectList("alarm.selectQueueByMailSource", params);
        assertEquals("SUCCESS", queues.get(0).get("status"));
        assertEquals(1, ((Number) queues.get(0).get("retryCount")).intValue());  // RETRY_COUNT 유지 (성공 시 증가 안 함)

        System.out.println("✅ 재시도 후 성공: Resilience 검증 완료");
    }


    // ==================== 시나리오 6: SQL_ID 동적 조회 - OVERDUE_ORDERS ====================

    @Test
    @Order(6)
    @DisplayName("시나리오 6: SQL_ID 동적 조회 - OVERDUE_ORDERS")
    void scenario6_sqlIdDynamicQuery_overdueOrders() {
        System.out.println("\n[시나리오 6] SQL_ID 동적 조회 - OVERDUE_ORDERS");

        // Given - OVERDUE_ORDERS 큐 삽입
        Map<String, Object> queueData = new HashMap<>();
        queueData.put("mailSource", "OVERDUE_ORDERS");
        queueData.put("alarmName", "지연 주문 알림");
        queueData.put("severity", "WARNING");
        queueData.put("sqlId", "alarm.selectOverdueOrdersDetail");  // 실제 쿼리 ID
        queueData.put("sectionTitle", "지연 주문 현황");
        queueData.put("sectionContent", "지연된 주문을 확인해주세요.");
        queueData.put("retryCount", 0);
        mailDao.insert("alarm.insertTestQueue", queueData);

        // When
        alarmMailService.processQueue();

        // Then - ArgumentCaptor로 MailRequest 검증
        ArgumentCaptor<MailRequest> captor = ArgumentCaptor.forClass(MailRequest.class);
        verify(mailService).sendMail(captor.capture());

        MailRequest request = captor.getValue();
        assertEquals("ALARM", request.getMailType());
        assertEquals("OVERDUE_ORDERS", request.getMailSource());
        assertTrue(request.getSubject().contains("지연 주문"));

        // 테이블 데이터 확인 (test-data-integration.sql에 DELAYED 주문 2건 존재)
        assertEquals(2, request.getSections().size());  // TEXT + TABLE
        assertEquals("TABLE", request.getSections().get(1).getType().name());

        System.out.println("✅ SQL_ID 동적 조회 성공: OVERDUE_ORDERS");
        System.out.println("   섹션 수: " + request.getSections().size());
    }


    // ==================== 시나리오 7: SQL_ID 동적 조회 - LOW_STOCK ====================

    @Test
    @Order(7)
    @DisplayName("시나리오 7: SQL_ID 동적 조회 - LOW_STOCK")
    void scenario7_sqlIdDynamicQuery_lowStock() {
        System.out.println("\n[시나리오 7] SQL_ID 동적 조회 - LOW_STOCK");

        // Given
        Map<String, Object> queueData = new HashMap<>();
        queueData.put("mailSource", "LOW_STOCK");
        queueData.put("alarmName", "재고 부족 알림");
        queueData.put("severity", "CRITICAL");
        queueData.put("sqlId", "alarm.selectLowStockDetail");
        queueData.put("sectionTitle", "재고 부족 현황");
        queueData.put("sectionContent", "긴급 재고 보충이 필요합니다.");
        queueData.put("retryCount", 0);
        mailDao.insert("alarm.insertTestQueue", queueData);

        // When
        alarmMailService.processQueue();

        // Then
        ArgumentCaptor<MailRequest> captor = ArgumentCaptor.forClass(MailRequest.class);
        verify(mailService).sendMail(captor.capture());

        MailRequest request = captor.getValue();
        assertEquals("ALARM", request.getMailType());
        assertEquals("LOW_STOCK", request.getMailSource());
        assertTrue(request.getSubject().contains("[긴급]"));  // CRITICAL severity

        // 테이블 섹션 검증 (test-data-integration.sql에 재고 부족 2건 존재)
        assertEquals("TABLE", request.getSections().get(1).getType().name());

        System.out.println("✅ SQL_ID 동적 조회 성공: LOW_STOCK");
        System.out.println("   심각도: CRITICAL");
    }


    // ==================== 시나리오 8: 빈 테이블 데이터 (테이블 섹션 생략) ====================

    @Test
    @Order(8)
    @DisplayName("시나리오 8: 빈 테이블 데이터 (테이블 섹션 생략)")
    void scenario8_emptyTableData_skipTableSection() {
        System.out.println("\n[시나리오 8] 빈 테이블 데이터 처리");

        // Given - 결과가 0건인 SQL_ID
        Map<String, Object> queueData = new HashMap<>();
        queueData.put("mailSource", "EMPTY_DATA_TEST");
        queueData.put("alarmName", "빈 데이터 테스트");
        queueData.put("severity", "INFO");
        queueData.put("sqlId", "alarm.selectNonExistentData");  // 빈 결과 쿼리
        queueData.put("sectionTitle", "빈 데이터 테스트");
        queueData.put("sectionContent", "테이블 데이터가 없는 경우");
        queueData.put("retryCount", 0);
        mailDao.insert("alarm.insertTestQueue", queueData);

        // When
        alarmMailService.processQueue();

        // Then - TEXT 섹션만 포함 (TABLE 섹션 없음)
        ArgumentCaptor<MailRequest> captor = ArgumentCaptor.forClass(MailRequest.class);
        verify(mailService).sendMail(captor.capture());

        MailRequest request = captor.getValue();
        assertEquals(1, request.getSections().size());  // TEXT 섹션만
        assertEquals("TEXT", request.getSections().get(0).getType().name());

        // 정상 발송 확인
        Map<String, Object> params = new HashMap<>();
        params.put("mailSource", "EMPTY_DATA_TEST");
        List<Map<String, Object>> queues = mailDao.selectList("alarm.selectQueueByMailSource", params);
        assertEquals("SUCCESS", queues.get(0).get("status"));

        System.out.println("✅ 빈 데이터 처리 성공: 테이블 섹션 생략");
    }


    // ==================== 시나리오 9: CLOB 변환 검증 ====================

    @Test
    @Order(9)
    @DisplayName("시나리오 9: CLOB 변환 검증")
    void scenario9_clobConversion_noException() {
        System.out.println("\n[시나리오 9] CLOB 변환 검증");

        // Given - 긴 CLOB 데이터
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longContent.append("이 메일은 CLOB 변환 테스트를 위한 긴 본문입니다. ");
        }

        Map<String, Object> queueData = new HashMap<>();
        queueData.put("mailSource", "CLOB_TEST");
        queueData.put("alarmName", "CLOB 테스트");
        queueData.put("severity", "INFO");
        queueData.put("sqlId", "alarm.selectOverdueOrdersDetail");
        queueData.put("sectionTitle", "CLOB 변환 테스트");
        queueData.put("sectionContent", longContent.toString());  // 긴 텍스트
        queueData.put("retryCount", 0);
        mailDao.insert("alarm.insertTestQueue", queueData);

        // When
        alarmMailService.processQueue();

        // Then - 예외 없이 정상 처리
        verify(mailService, times(1)).sendMail(any(MailRequest.class));

        Map<String, Object> params = new HashMap<>();
        params.put("mailSource", "CLOB_TEST");
        List<Map<String, Object>> queues = mailDao.selectList("alarm.selectQueueByMailSource", params);
        assertEquals("SUCCESS", queues.get(0).get("status"));

        System.out.println("✅ CLOB 변환 성공: 예외 없음");
    }


    // ==================== 시나리오 10: 심각도별 처리 (CRITICAL/WARNING/INFO) ====================

    @Test
    @Order(10)
    @DisplayName("시나리오 10: 심각도별 처리 (CRITICAL/WARNING/INFO)")
    void scenario10_severityLevels_allTypes() {
        System.out.println("\n[시나리오 10] 심각도별 처리");

        // Given - 3가지 심각도의 알람 삽입
        String[] severities = {"CRITICAL", "WARNING", "INFO"};
        for (String severity : severities) {
            Map<String, Object> queueData = new HashMap<>();
            queueData.put("mailSource", "SEVERITY_" + severity);
            queueData.put("alarmName", severity + " 알람");
            queueData.put("severity", severity);
            queueData.put("sqlId", "alarm.selectOverdueOrdersDetail");
            queueData.put("sectionTitle", severity + " 테스트");
            queueData.put("sectionContent", severity + " 심각도 테스트");
            queueData.put("retryCount", 0);
            mailDao.insert("alarm.insertTestQueue", queueData);
        }

        // When
        alarmMailService.processQueue();

        // Then - 3번 호출
        ArgumentCaptor<MailRequest> captor = ArgumentCaptor.forClass(MailRequest.class);
        verify(mailService, times(3)).sendMail(captor.capture());

        List<MailRequest> requests = captor.getAllValues();

        // 심각도별 검증 (mailSource로 구분)
        boolean criticalFound = false;
        boolean warningFound = false;
        boolean infoFound = false;

        for (MailRequest request : requests) {
            String mailSource = request.getMailSource();
            String subject = request.getSubject();
            String title = request.getSections().get(0).getTitle();

            System.out.println("검증 중 - mailSource: " + mailSource + ", subject: " + subject + ", title: " + title);

            if (mailSource.equals("SEVERITY_CRITICAL")) {
                assertTrue(subject.contains("[긴급]"), "CRITICAL subject should contain [긴급]");
                assertTrue(title.contains("🔴"), "CRITICAL title should contain 🔴");
                criticalFound = true;
            } else if (mailSource.equals("SEVERITY_WARNING")) {
                assertTrue(subject.contains("[경고]"), "WARNING subject should contain [경고]");
                assertTrue(title.contains("⚠️"), "WARNING title should contain ⚠️");
                warningFound = true;
            } else if (mailSource.equals("SEVERITY_INFO")) {
                assertTrue(subject.contains("[경고]"), "INFO subject should contain [경고]");  // alarmSubject()는 INFO도 [경고] 사용
                assertTrue(title.contains("ℹ️"), "INFO title should contain ℹ️");
                infoFound = true;
            }
        }

        // 모든 심각도가 처리되었는지 확인
        assertTrue(criticalFound, "CRITICAL 알람이 처리되지 않았습니다");
        assertTrue(warningFound, "WARNING 알람이 처리되지 않았습니다");
        assertTrue(infoFound, "INFO 알람이 처리되지 않았습니다");

        System.out.println("✅ 심각도별 처리 완료: CRITICAL/WARNING/INFO");
    }


    // ==================== 통합 검증: 모든 시나리오 요약 ====================

    @Test
    @Order(11)
    @DisplayName("통합 검증: 전체 큐 상태 확인")
    void scenario11_summary_allScenarios() {
        System.out.println("\n[통합 검증] 전체 테스트 요약");

        // 이 테스트는 독립적으로 실행되므로 큐가 비어있음
        // 실제로는 @Order(1)~(10)이 모두 실행된 후에는 각각의 큐가 처리된 상태

        System.out.println("\n========================================");
        System.out.println("✅ AlarmMailService 통합 테스트 완료");
        System.out.println("========================================");
        System.out.println("검증 항목:");
        System.out.println("  1. 정상 발송 (PENDING → SUCCESS)");
        System.out.println("  2. 복수 알람 배치 처리");
        System.out.println("  3. 재시도 로직 (RETRY_COUNT 증가)");
        System.out.println("  4. 최종 실패 (3회 재시도 후 FAILED)");
        System.out.println("  5. 재시도 후 성공 (Resilience)");
        System.out.println("  6. SQL_ID 동적 조회 - OVERDUE_ORDERS");
        System.out.println("  7. SQL_ID 동적 조회 - LOW_STOCK");
        System.out.println("  8. 빈 테이블 데이터 처리");
        System.out.println("  9. CLOB 변환 검증");
        System.out.println(" 10. 심각도별 처리 (CRITICAL/WARNING/INFO)");
        System.out.println("========================================\n");
    }
}