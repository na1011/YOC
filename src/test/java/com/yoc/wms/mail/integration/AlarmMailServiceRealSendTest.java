package com.yoc.wms.mail.integration;

import com.yoc.wms.mail.dao.MailDao;
import com.yoc.wms.mail.service.AlarmMailService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AlarmMailService 실제 메일 발송 테스트
 *
 * <h3>목적</h3>
 * 메일이 실제로 어떻게 보여지는지 눈으로 확인하기 위한 테스트
 * (엣지케이스 검증은 AlarmMailServiceIntegrationTest에서 수행)
 *
 * <h3>주의사항</h3>
 * <ul>
 *   <li>실제 Gmail SMTP를 통해 메일이 발송됩니다</li>
 *   <li>@Disabled 제거 후 실행 필요</li>
 *   <li>수신자: test-data-integration.sql의 ADM 그룹 사용자</li>
 *   <li>Gmail 계정 앱 비밀번호 필요 (application-integration.properties)</li>
 * </ul>
 *
 * <h3>시나리오</h3>
 * 1. CRITICAL 알람 (지연 주문)
 * 2. WARNING 알람 (재고 부족)
 * 3. INFO 알람 (시스템 공지)
 */
@SpringBootTest
@ActiveProfiles("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled("실제 메일 발송 테스트 - 필요 시 @Disabled 제거 후 실행")
class AlarmMailServiceRealSendTest {

    @Autowired
    private AlarmMailService alarmMailService;

    @Autowired
    private MailDao mailDao;

    @BeforeEach
    void setUp() {
        // 큐 초기화
        mailDao.delete("alarm.deleteAllQueue", null);

        System.out.println("\n========================================");
        System.out.println("AlarmMailService 실제 발송 테스트");
        System.out.println("수신자: ADM 그룹 사용자 (test-data-integration.sql)");
        System.out.println("========================================\n");
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        // 비동기 처리 완료 대기
        System.out.println("\n메일 발송 완료 대기 중 (5초)...\n");
        TimeUnit.SECONDS.sleep(5);
    }


    // ==================== 시나리오 1: CRITICAL 알람 (지연 주문) ====================

    @Test
    @Order(1)
    @DisplayName("실제 발송 1: CRITICAL 알람 - 지연 주문")
    void realSend1_criticalAlarm_overdueOrders() throws InterruptedException {
        System.out.println("\n[실제 발송 1] CRITICAL 알람 - 지연 주문");

        // Given - Producer 시뮬레이션: CRITICAL 알람 삽입
        Map<String, Object> queueData = new HashMap<>();
        queueData.put("mailSource", "OVERDUE_ORDERS");
        queueData.put("alarmName", "지연 주문 알림");
        queueData.put("severity", "CRITICAL");
        queueData.put("sqlId", "alarm.selectOverdueOrdersDetail");
        queueData.put("sectionTitle", "긴급: 지연 주문 발생");
        queueData.put("sectionContent",
                "5일 이상 지연된 주문이 발견되었습니다.\n\n" +
                "고객 불만이 예상되오니 긴급 처리 부탁드립니다.\n\n" +
                "상세 내역은 아래 테이블을 참고해주세요.");
        queueData.put("retryCount", 0);

        mailDao.insert("alarm.insertTestQueue", queueData);

        // When - Consumer 실행
        System.out.println("큐 처리 시작...");
        alarmMailService.processQueue();

        // 비동기 처리 대기
        TimeUnit.SECONDS.sleep(3);

        // Then - 발송 확인 메시지
        System.out.println("\n========================================");
        System.out.println("✅ CRITICAL 알람 발송 완료");
        System.out.println("========================================");
        System.out.println("제목: [긴급] WMS 긴급: 지연 주문 발생 2건");
        System.out.println("아이콘: 🔴 (CRITICAL)");
        System.out.println("테이블: OVERDUE_ORDERS 상세 내역");
        System.out.println("수신자: ADM 그룹 사용자");
        System.out.println("\n📧 메일함을 확인해주세요!");
        System.out.println("========================================\n");
    }


    // ==================== 시나리오 2: WARNING 알람 (재고 부족) ====================

    @Test
    @Order(2)
    @DisplayName("실제 발송 2: WARNING 알람 - 재고 부족")
    void realSend2_warningAlarm_lowStock() throws InterruptedException {
        System.out.println("\n[실제 발송 2] WARNING 알람 - 재고 부족");

        // Given
        Map<String, Object> queueData = new HashMap<>();
        queueData.put("mailSource", "LOW_STOCK");
        queueData.put("alarmName", "재고 부족 알림");
        queueData.put("severity", "WARNING");
        queueData.put("sqlId", "alarm.selectLowStockDetail");
        queueData.put("sectionTitle", "재고 부족 경고");
        queueData.put("sectionContent",
                "최소 수량 이하의 재고가 발견되었습니다.\n\n" +
                "재고 보충이 필요합니다.\n\n" +
                "아래 테이블에서 부족한 품목을 확인하세요.");
        queueData.put("retryCount", 0);

        mailDao.insert("alarm.insertTestQueue", queueData);

        // When
        System.out.println("큐 처리 시작...");
        alarmMailService.processQueue();

        TimeUnit.SECONDS.sleep(3);

        // Then
        System.out.println("\n========================================");
        System.out.println("✅ WARNING 알람 발송 완료");
        System.out.println("========================================");
        System.out.println("제목: [경고] WMS 재고 부족 경고 2건");
        System.out.println("아이콘: 🟡 (WARNING)");
        System.out.println("테이블: LOW_STOCK 상세 내역");
        System.out.println("\n📧 메일함을 확인해주세요!");
        System.out.println("========================================\n");
    }


    // ==================== 시나리오 3: INFO 알람 (시스템 공지) ====================

    @Test
    @Order(3)
    @DisplayName("실제 발송 3: INFO 알람 - 시스템 공지")
    void realSend3_infoAlarm_systemNotice() throws InterruptedException {
        System.out.println("\n[실제 발송 3] INFO 알람 - 시스템 공지");

        // Given - 테이블 데이터 없는 텍스트만 알람
        Map<String, Object> queueData = new HashMap<>();
        queueData.put("mailSource", "SYSTEM_NOTICE");
        queueData.put("alarmName", "시스템 공지");
        queueData.put("severity", "INFO");
        queueData.put("sqlId", "alarm.selectNonExistentData");  // 빈 결과 쿼리
        queueData.put("sectionTitle", "WMS 시스템 점검 안내");
        queueData.put("sectionContent",
                "정기 점검이 예정되어 있습니다.\n\n" +
                "일시: 2025년 1월 15일 (수) 02:00 ~ 04:00\n" +
                "대상: 전체 시스템\n" +
                "내용: 서버 업그레이드 및 성능 개선\n\n" +
                "점검 시간 동안 시스템 이용이 불가하오니 양해 부탁드립니다.\n\n" +
                "감사합니다.");
        queueData.put("retryCount", 0);

        mailDao.insert("alarm.insertTestQueue", queueData);

        // When
        System.out.println("큐 처리 시작...");
        alarmMailService.processQueue();

        TimeUnit.SECONDS.sleep(3);

        // Then
        System.out.println("\n========================================");
        System.out.println("✅ INFO 알람 발송 완료");
        System.out.println("========================================");
        System.out.println("제목: [안내] WMS WMS 시스템 점검 안내 0건");
        System.out.println("아이콘: 🔵 (INFO)");
        System.out.println("테이블: 없음 (텍스트 섹션만)");
        System.out.println("\n📧 메일함을 확인해주세요!");
        System.out.println("========================================\n");
    }


    // ==================== 시나리오 4: 복수 섹션 알람 (텍스트 + 테이블 + 텍스트) ====================

    @Test
    @Order(4)
    @DisplayName("실제 발송 4: 복수 섹션 - 실제 큐 처리 시뮬레이션")
    void realSend4_multiSection_fullSimulation() throws InterruptedException {
        System.out.println("\n[실제 발송 4] 복수 알람 동시 발송 (배치 처리)");

        // Given - 서로 다른 3개의 알람 동시 삽입 (실제 운영환경 시뮬레이션)
        String[][] alarms = {
                {"OVERDUE_ORDERS", "지연 주문 알림", "CRITICAL", "alarm.selectOverdueOrdersDetail"},
                {"LOW_STOCK", "재고 부족 알림", "WARNING", "alarm.selectLowStockDetail"},
                {"SYSTEM_NOTICE", "시스템 공지", "INFO", "alarm.selectNonExistentData"}
        };

        for (String[] alarm : alarms) {
            Map<String, Object> queueData = new HashMap<>();
            queueData.put("mailSource", alarm[0]);
            queueData.put("alarmName", alarm[1]);
            queueData.put("severity", alarm[2]);
            queueData.put("sqlId", alarm[3]);
            queueData.put("sectionTitle", alarm[1]);
            queueData.put("sectionContent", alarm[1] + " 내용입니다.");
            queueData.put("retryCount", 0);
            mailDao.insert("alarm.insertTestQueue", queueData);
        }

        // When - 배치 처리
        System.out.println("큐 배치 처리 시작 (3건)...");
        alarmMailService.processQueue();

        TimeUnit.SECONDS.sleep(5);

        // Then
        System.out.println("\n========================================");
        System.out.println("✅ 배치 발송 완료 (3건)");
        System.out.println("========================================");
        System.out.println("1. CRITICAL - 지연 주문 알림");
        System.out.println("2. WARNING - 재고 부족 알림");
        System.out.println("3. INFO - 시스템 공지");
        System.out.println("\n📧 메일함에서 3개의 메일을 확인해주세요!");
        System.out.println("========================================\n");
    }


    // ==================== 최종 확인 메시지 ====================

    @Test
    @Order(5)
    @DisplayName("실제 발송 완료: 전체 요약")
    void realSend5_summary() {
        System.out.println("\n========================================");
        System.out.println("✅ AlarmMailService 실제 발송 테스트 완료");
        System.out.println("========================================");
        System.out.println("발송된 메일:");
        System.out.println("  1. CRITICAL 알람 - 지연 주문 (🔴)");
        System.out.println("  2. WARNING 알람 - 재고 부족 (🟡)");
        System.out.println("  3. INFO 알람 - 시스템 공지 (🔵)");
        System.out.println("  4. 배치 발송 - 3건 동시 처리");
        System.out.println("\n수신자: ADM 그룹 사용자");
        System.out.println("  - chanki_kim@youngone.co.kr");
        System.out.println("  - admin2@test.co.kr");
        System.out.println("\n📧 메일함을 확인하여 메일 형식을 검토하세요!");
        System.out.println("========================================\n");
    }
}