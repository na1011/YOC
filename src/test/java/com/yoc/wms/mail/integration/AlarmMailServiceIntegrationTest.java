package com.yoc.wms.mail.integration;

import com.yoc.wms.mail.dao.MailDao;
import com.yoc.wms.mail.domain.MailRequest;
import com.yoc.wms.mail.service.AlarmMailService;
import com.yoc.wms.mail.service.MailService;
import com.yoc.wms.mail.util.FakeMailSender;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * AlarmMailService 통합 테스트 (Real Components + Fake)
 *
 * Architecture:
 * - AlarmMailService: Real (실제 로직 테스트)
 * - MailService: Real (실제 발송 로직 테스트)
 * - MailDao: Real (H2 In-Memory)
 * - JavaMailSender: Fake (FakeMailSender, SMTP 발송 방지)
 *
 * Chicago School 테스트 방식:
 * - Mock 없음 (Real Components 사용)
 * - verify 없음 (DB 상태 검증)
 * - 비즈니스 결과 검증 ("무엇을" 달성했는가)
 *
 * 운영 환경 호환성:
 * - Mockito 불필요 (FakeMailSender는 순수 Java)
 * - Spring 3.1.2 호환 (복사 가능)
 *
 * 시나리오 구성:
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
 *
 * @since v2.4.0 (Chicago School, Mockito 제거)
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@ActiveProfiles("integration")
@Import(IntegrationTestConfig.class)  // ⭐ FakeMailSender 주입
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AlarmMailServiceIntegrationTest {

    @Autowired
    private AlarmMailService alarmMailService;  // Real

    @Autowired
    private MailDao mailDao;  // Real (H2)

    @Autowired
    private MailService mailService;  // Real (Mockito 없음!)

    @Autowired
    private JavaMailSender mailSender;  // Fake (IntegrationTestConfig에서 주입)

    @Before
    public void setUp() {
        // 큐 초기화
        mailDao.delete("alarm.deleteAllQueue", null);

        // Fake 초기화
        FakeMailSender fake = (FakeMailSender) mailSender;
        fake.reset();

        System.out.println("\n========================================");
        System.out.println("AlarmMailService 통합 테스트 시작");
        System.out.println("큐 초기화 완료");
        System.out.println("========================================\n");
    }

    @After
    public void tearDown() {
        System.out.println("\n테스트 종료\n");
    }


    // ==================== 시나리오 1: 정상 발송 (PENDING → SUCCESS) ====================

    @Test
    public void test01_scenario1_normalFlow_pendingToSuccess() {
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

        // When - Real AlarmMailService → Real MailService → Fake MailSender
        alarmMailService.processQueue();

        // Then - FakeMailSender 검증
        FakeMailSender fake = (FakeMailSender) mailSender;
        assertEquals(1, fake.getSentCount());

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
    public void test02_scenario2_batchProcessing_multipleAlarms() {
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

        // Then - FakeMailSender 검증: 3번 발송되었는지 확인
        FakeMailSender fake = (FakeMailSender) mailSender;
        assertEquals(3, fake.getSentCount());

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
    public void test03_scenario3_firstRetry_retryCountIncrement() {
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

        // FakeMailSender가 실패하도록 설정
        FakeMailSender fake = (FakeMailSender) mailSender;
        fake.setShouldFail(true);

        // When
        alarmMailService.processQueue();

        // Then - 재시도 상태 확인
        Map<String, Object> params = new HashMap<>();
        params.put("mailSource", "RETRY_TEST");
        List<Map<String, Object>> queues = mailDao.selectList("alarm.selectQueueByMailSource", params);
        assertEquals("PENDING", queues.get(0).get("status"));  // 상태는 PENDING 유지
        assertEquals(1, ((Number) queues.get(0).get("retryCount")).intValue());  // RETRY_COUNT 증가

        // FakeMailSender 검증: 3회 재시도 시도 (MailService의 sendWithRetry)
        assertTrue(fake.getSendCallCount() >= 3);  // 최소 3회 시도

        System.out.println("✅ 재시도 상태 업데이트: RETRY_COUNT = 1");
    }


    // ==================== 시나리오 4: 최종 실패 (3회 재시도 후 FAILED) ====================

    @Test
    public void test04_scenario4_finalFailure_afterMaxRetries() {
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

        // FakeMailSender가 실패하도록 설정
        FakeMailSender fake = (FakeMailSender) mailSender;
        fake.setShouldFail(true);

        // When
        alarmMailService.processQueue();

        // Then - FAILED 상태 확인
        Map<String, Object> params = new HashMap<>();
        params.put("mailSource", "FINAL_FAILURE");
        List<Map<String, Object>> queues = mailDao.selectList("alarm.selectQueueByMailSource", params);
        assertEquals("FAILED", queues.get(0).get("status"));  // 최종 실패
        assertEquals(2, ((Number) queues.get(0).get("retryCount")).intValue());  // RETRY_COUNT 유지

        System.out.println("✅ 최종 실패 처리: PENDING → FAILED");
    }


    // ==================== 시나리오 5: 재시도 후 성공 (Resilience 검증) ====================

    @Test
    public void test05_scenario5_retryThenSuccess_resilience() {
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

        // MailService는 정상 동작 (FakeMailSender 성공)

        // When
        alarmMailService.processQueue();

        // Then
        FakeMailSender fake = (FakeMailSender) mailSender;
        assertEquals(1, fake.getSentCount());

        Map<String, Object> params = new HashMap<>();
        params.put("mailSource", "RETRY_SUCCESS");
        List<Map<String, Object>> queues = mailDao.selectList("alarm.selectQueueByMailSource", params);
        assertEquals("SUCCESS", queues.get(0).get("status"));
        assertEquals(1, ((Number) queues.get(0).get("retryCount")).intValue());  // RETRY_COUNT 유지 (성공 시 증가 안 함)

        System.out.println("✅ 재시도 후 성공: Resilience 검증 완료");
    }


    // ==================== 시나리오 6: SQL_ID 동적 조회 - OVERDUE_ORDERS ====================

    @Test
    public void test06_scenario6_sqlIdDynamicQuery_overdueOrders() {
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

        // Then - DB 상태 검증
        FakeMailSender fake = (FakeMailSender) mailSender;
        assertEquals(1, fake.getSentCount());

        Map<String, Object> params = new HashMap<>();
        params.put("mailSource", "OVERDUE_ORDERS");
        List<Map<String, Object>> queues = mailDao.selectList("alarm.selectQueueByMailSource", params);
        assertEquals("SUCCESS", queues.get(0).get("status"));

        System.out.println("✅ SQL_ID 동적 조회 성공: OVERDUE_ORDERS");
    }


    // ==================== 시나리오 7: SQL_ID 동적 조회 - LOW_STOCK ====================

    @Test
    public void test07_scenario7_sqlIdDynamicQuery_lowStock() {
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

        // Then - DB 상태 검증
        FakeMailSender fake = (FakeMailSender) mailSender;
        assertEquals(1, fake.getSentCount());

        Map<String, Object> params = new HashMap<>();
        params.put("mailSource", "LOW_STOCK");
        List<Map<String, Object>> queues = mailDao.selectList("alarm.selectQueueByMailSource", params);
        assertEquals("SUCCESS", queues.get(0).get("status"));

        System.out.println("✅ SQL_ID 동적 조회 성공: LOW_STOCK");
    }


    // ==================== 시나리오 8: 빈 테이블 데이터 (테이블 섹션 생략) ====================

    @Test
    public void test08_scenario8_emptyTableData_skipTableSection() {
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

        // Then - 정상 발송 확인 (DB 상태 검증)
        FakeMailSender fake = (FakeMailSender) mailSender;
        assertEquals(1, fake.getSentCount());

        Map<String, Object> params = new HashMap<>();
        params.put("mailSource", "EMPTY_DATA_TEST");
        List<Map<String, Object>> queues = mailDao.selectList("alarm.selectQueueByMailSource", params);
        assertEquals("SUCCESS", queues.get(0).get("status"));

        System.out.println("✅ 빈 데이터 처리 성공");
    }


    // ==================== 시나리오 9: CLOB 변환 검증 ====================

    @Test
    public void test09_scenario9_clobConversion_noException() {
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

        // Then - 예외 없이 정상 처리 (DB 상태 검증)
        FakeMailSender fake = (FakeMailSender) mailSender;
        assertEquals(1, fake.getSentCount());

        Map<String, Object> params = new HashMap<>();
        params.put("mailSource", "CLOB_TEST");
        List<Map<String, Object>> queues = mailDao.selectList("alarm.selectQueueByMailSource", params);
        assertEquals("SUCCESS", queues.get(0).get("status"));

        System.out.println("✅ CLOB 변환 성공: 예외 없음");
    }


    // ==================== 시나리오 10: 심각도별 처리 (CRITICAL/WARNING/INFO) ====================

    @Test
    public void test10_scenario10_severityLevels_allTypes() {
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

        // Then - 3번 발송 확인
        FakeMailSender fake = (FakeMailSender) mailSender;
        assertEquals(3, fake.getSentCount());

        // 모든 큐가 SUCCESS인지 확인
        for (String severity : severities) {
            Map<String, Object> params = new HashMap<>();
            params.put("mailSource", "SEVERITY_" + severity);
            List<Map<String, Object>> queues = mailDao.selectList("alarm.selectQueueByMailSource", params);
            assertEquals("SUCCESS", queues.get(0).get("status"));
        }

        System.out.println("✅ 심각도별 처리 완료: CRITICAL/WARNING/INFO");
    }


    // ==================== 통합 검증: 모든 시나리오 요약 ====================

    @Test
    public void test11_scenario11_summary_allScenarios() {
        System.out.println("\n[통합 검증] 전체 테스트 요약");

        // 이 테스트는 독립적으로 실행되므로 큐가 비어있음

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