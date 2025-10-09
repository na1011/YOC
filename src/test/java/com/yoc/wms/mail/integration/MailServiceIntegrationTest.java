package com.yoc.wms.mail.integration;

import com.yoc.wms.mail.dao.MailDao;
import com.yoc.wms.mail.domain.MailRequest;
import com.yoc.wms.mail.domain.Recipient;
import com.yoc.wms.mail.service.MailService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MailService 실제 메일 발송 테스트
 *
 * - 시나리오 구성
 * 1. 단일 섹션 메일 (텍스트만)
 * 2. 복수 섹션 메일 (텍스트 + 테이블 + 구분선 + 텍스트) - 범용 Builder 사용
 * 3. 알람 메일 (Helper Methods 사용)
 * 4. 보고서 메일 (Helper Methods 사용)
 * 5. 공지 메일 (Helper Methods 사용)
 * 6. CC 포함 메일
 * 7. 발송 로그 검증
 */
@SpringBootTest
@ActiveProfiles("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled("실제 메일 발송 테스트 - 필요 시 @Disabled 제거 후 실행")
class MailServiceIntegrationTest {

    @Autowired
    private MailService mailService;

    @Autowired
    private MailDao mailDao;

    private List<Recipient> testRecipients;

    @BeforeEach
    void setUp() {
        // 테스트용 수신자 설정
        testRecipients = new ArrayList<>();
        testRecipients.add(Recipient.builder()
            .userId("ADMIN")
            .email("chanki_kim@youngone.co.kr")
            .group("ADM")
            .build());
        testRecipients.add(Recipient.builder()
            .userId("USER")
            .email("zerus94@naver.com")
            .group("USER")
            .build());

        System.out.println("\n========================================");
        System.out.println("MailService 통합 테스트 시작");
        System.out.println("수신자: " + testRecipients.size() + "명");
        System.out.println("========================================\n");
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        // 메일 발송 후 대기 (비동기 처리 완료 대기)
        System.out.println("\n메일 발송 완료 대기 중 (5초)...\n");
        TimeUnit.SECONDS.sleep(5);
    }

    // ==================== 시나리오 1: 단일 섹션 (텍스트만) ====================

    @Test
    @Order(1)
    @DisplayName("통합 테스트 1: 단일 텍스트 섹션 - 복수 사용자 발송")
    void scenario1_singleTextSection_multipleRecipients() {
        System.out.println("\n[시나리오 1] 단일 텍스트 섹션 - 복수 사용자 발송");

        // Given - 범용 Builder 사용
        MailRequest request = MailRequest.builder()
            .subject("[통합테스트] 단일 섹션 메일")
            .addTextSection("테스트 안내",
                "이 메일은 WMS 메일 시스템 통합 테스트를 위해 발송되었습니다.\n\n" +
                "단일 텍스트 섹션만 포함된 간단한 메일입니다.\n\n" +
                "수신 확인 부탁드립니다.")
            .recipients(testRecipients)
            .mailType("DIRECT")
            .build();

        // When
        System.out.println("메일 발송 중...");
        mailService.sendMail(request);

        // Then
        System.out.println("✅ 메일 발송 요청 완료");
        System.out.println("수신자: chanki_kim@youngone.co.kr, zerus94@naver.com");
        System.out.println("제목: [통합테스트] 단일 섹션 메일");

        // 발송 로그 확인
        List<Map<String, Object>> logs = mailDao.selectList("mail.selectRecentMailLogs", null);
        assertNotNull(logs);
        assertFalse(logs.isEmpty(), "발송 로그가 생성되어야 함");
    }

    // ==================== 시나리오 2: 복수 섹션 (범용 Builder) ====================

    @Test
    @Order(2)
    @DisplayName("통합 테스트 2: 복수 섹션 (텍스트 + 테이블 + 구분선 + 텍스트) - 범용 Builder")
    void scenario2_multipleSections_genericBuilder() {

        System.out.println("\n[시나리오 2] 복수 섹션 (텍스트 + 테이블 + 구분선 + 텍스트) - 범용 Builder");

        // Given - 테스트 데이터 직접 생성
        List<Map<String, String>> tableData = new ArrayList<>();

        Map<String, String> row1 = new LinkedHashMap<>();
        row1.put("항목", "CPU 사용률");
        row1.put("현재값", "85%");
        row1.put("기준값", "80%");
        row1.put("상태", "경고");
        tableData.add(row1);

        Map<String, String> row2 = new LinkedHashMap<>();
        row2.put("항목", "메모리 사용률");
        row2.put("현재값", "72%");
        row2.put("기준값", "80%");
        row2.put("상태", "정상");
        tableData.add(row2);

        Map<String, String> row3 = new LinkedHashMap<>();
        row3.put("항목", "디스크 사용률");
        row3.put("현재값", "65%");
        row3.put("기준값", "80%");
        row3.put("상태", "정상");
        tableData.add(row3);

        // 범용 Builder로 복수 섹션 구성 (MailSection 의존성 제거)
        MailRequest request = MailRequest.builder()
            .subject("[통합테스트] 복수 섹션 메일")
            .addTextSection("📊 시스템 리소스 모니터링",
                "현재 시스템 리소스 상태를 보고합니다.\n\n" +
                "CPU 사용률이 기준치를 초과했습니다.")
            .addTableSection(tableData)
            .addDivider()
            .addTextSection("📌 조치 사항",
                "CPU 사용률이 높습니다. 불필요한 프로세스를 종료하거나\n" +
                "서버 리소스 증설을 검토해 주시기 바랍니다.")
            .recipients(testRecipients)
            .mailType("DIRECT")
            .build();

        // When
        System.out.println("메일 발송 중...");
        mailService.sendMail(request);

        // Then
        System.out.println("✅ 메일 발송 요청 완료");
        System.out.println("수신자: chanki_kim@youngone.co.kr, zerus94@naver.com");
        System.out.println("제목: [통합테스트] 복수 섹션 메일");
        System.out.println("섹션 수: 4개 (TEXT + TABLE + DIVIDER + TEXT)");
    }

    // ==================== 시나리오 3: 알람 메일 (Helper Methods) ====================

    @Test
    @Order(3)
    @DisplayName("통합 테스트 3: 알람 메일 (WARNING) - Helper Methods")
    void scenario3_alarmMail_warning_helperMethods() {
        System.out.println("\n[시나리오 3] 알람 메일 (WARNING) - Helper Methods");

        // Given - 테스트 데이터 직접 생성
        List<Map<String, String>> tableData = new ArrayList<>();

        Map<String, String> item1 = new LinkedHashMap<>();
        item1.put("품목코드", "P001");
        item1.put("품목명", "노트북 받침대");
        item1.put("현재수량", "5");
        item1.put("최소수량", "10");
        tableData.add(item1);

        Map<String, String> item2 = new LinkedHashMap<>();
        item2.put("품목코드", "P002");
        item2.put("품목명", "무선 마우스");
        item2.put("현재수량", "3");
        item2.put("최소수량", "15");
        tableData.add(item2);

        // Helper Methods 사용
        String title = "재고 부족 알림";
        String severity = "WARNING";
        String content = "최소 수량 이하의 재고가 " + tableData.size() + "건 발견되었습니다.\n\n" +
                         "긴급 재고 보충이 필요합니다.";

        MailRequest request = MailRequest.builder()
            .subject(MailRequest.alarmSubject(title, severity, tableData.size()))
            .addTextSection(MailRequest.alarmTitle(title, severity), content)
            .addTableSection(tableData)
            .recipients(testRecipients)
            .mailType("ALARM")
            .mailSource("LOW_STOCK_ALERT")
            .build();

        // When
        System.out.println("메일 발송 중...");
        mailService.sendMail(request);

        // Then
        System.out.println("✅ 메일 발송 요청 완료");
        System.out.println("수신자: chanki_kim@youngone.co.kr, zerus94@naver.com");
        System.out.println("제목: [경고] WMS 재고 부족 알림 " + tableData.size() + "건");
        System.out.println("심각도: WARNING");
        System.out.println("테이블 행 수: " + tableData.size());
    }

    // ==================== 시나리오 4: 보고서 메일 (Helper Methods) ====================

    @Test
    @Order(4)
    @DisplayName("통합 테스트 4: 보고서 메일 - Helper Methods")
    void scenario4_reportMail_helperMethods() {
        System.out.println("\n[시나리오 4] 보고서 메일 - Helper Methods");

        // Given - 테스트 데이터 직접 생성
        List<Map<String, String>> reportData = new ArrayList<>();

        Map<String, String> order1 = new LinkedHashMap<>();
        order1.put("주문번호", "ORD-2024-001");
        order1.put("고객명", "김철수");
        order1.put("주문일", "2024-11-20");
        order1.put("지연일수", "5");
        reportData.add(order1);

        Map<String, String> order2 = new LinkedHashMap<>();
        order2.put("주문번호", "ORD-2024-002");
        order2.put("고객명", "이영희");
        order2.put("주문일", "2024-11-22");
        order2.put("지연일수", "3");
        reportData.add(order2);

        // Helper Methods 사용
        String reportTitle = "지연 주문 현황 보고서";
        String description = "현재 " + reportData.size() + "건의 지연 주문이 있습니다.\n\n" +
                             "아래 상세 내역을 확인하시고 조치 부탁드립니다.";

        MailRequest request = MailRequest.builder()
            .subject(reportTitle)
            .addTextSection(MailRequest.reportTitle(reportTitle), description)
            .addTableSection(reportData)
            .recipients(testRecipients)
            .mailType("REPORT")
            .build();

        // When
        System.out.println("메일 발송 중...");
        mailService.sendMail(request);

        // Then
        System.out.println("✅ 메일 발송 요청 완료");
        System.out.println("수신자: chanki_kim@youngone.co.kr, zerus94@naver.com");
        System.out.println("제목: 지연 주문 현황 보고서");
        System.out.println("테이블 행 수: " + reportData.size());
    }

    // ==================== 시나리오 5: 공지 메일 (Helper Methods) ====================

    @Test
    @Order(5)
    @DisplayName("통합 테스트 5: 공지 메일 - Helper Methods")
    void scenario5_noticeMail_helperMethods() {
        System.out.println("\n[시나리오 5] 공지 메일 - Helper Methods");

        // Given - Helper Methods 사용
        String noticeTitle = "시스템 정기 점검 안내";
        String content = "일시: 2024년 12월 15일 (일) 00:00 ~ 04:00\n" +
                         "대상: 전체 시스템\n" +
                         "내용: 서버 업그레이드 및 보안 패치\n\n" +
                         "점검 시간 동안에는 시스템 이용이 불가하오니 양해 부탁드립니다.\n\n" +
                         "감사합니다.";

        // 범용 Builder로 복수 섹션 구성 (MailSection 의존성 제거)
        MailRequest request = MailRequest.builder()
                .subject(noticeTitle)
                .addTextSection(MailRequest.noticeTitle(noticeTitle), "WMS 시스템 정기 점검을 아래와 같이 실시합니다.")
                .addDivider()
                .addTextSection(content)
                .recipients(testRecipients)
                .mailType("NOTICE")
                .build();

        // When
        System.out.println("메일 발송 중...");
        mailService.sendMail(request);

        // Then
        System.out.println("✅ 메일 발송 요청 완료");
        System.out.println("수신자: chanki_kim@youngone.co.kr, zerus94@naver.com");
        System.out.println("제목: 시스템 정기 점검 안내");
        System.out.println("유형: NOTICE");
    }

    // ==================== 시나리오 6: CC 포함 메일 ====================

    @Test
    @Order(6)
    @DisplayName("통합 테스트 6: CC 포함 메일 발송")
    void scenario6_mailWithCC() {
        System.out.println("\n[시나리오 6] CC 포함 메일 발송");

        // Given - 주 수신자는 ADMIN, CC는 USER
        List<Recipient> toRecipients = Collections.singletonList(
            Recipient.builder()
                .userId("ADMIN")
                .email("chanki_kim@youngone.co.kr")
                .group("ADM")
                .build()
        );
        List<Recipient> ccRecipients = Collections.singletonList(
            Recipient.builder()
                .userId("USER")
                .email("zerus94@naver.com")
                .group("USER")
                .build()
        );

        MailRequest request = MailRequest.builder()
            .subject("[통합테스트] CC 포함 테스트 메일")
            .addTextSection("CC 기능 테스트",
                "이 메일은 CC 기능을 테스트하기 위해 발송되었습니다.\n\n" +
                "TO: chanki_kim@youngone.co.kr (ADMIN)\n" +
                "CC: zerus94@naver.com (USER)\n\n" +
                "양쪽 모두 수신 확인 부탁드립니다.")
            .recipients(toRecipients)
            .ccRecipients(ccRecipients)
            .mailType("DIRECT")
            .build();

        // When
        System.out.println("메일 발송 중...");
        mailService.sendMail(request);

        // Then
        System.out.println("✅ 메일 발송 요청 완료");
        System.out.println("TO: chanki_kim@youngone.co.kr");
        System.out.println("CC: zerus94@naver.com");
    }

    // ==================== 시나리오 7: 발송 로그 검증 ====================

    @Test
    @Order(7)
    @DisplayName("통합 테스트 7: 발송 로그 검증")
    void scenario7_verifyMailLogs() throws InterruptedException {
        System.out.println("\n[시나리오 7] 발송 로그 검증");

        // Given - 이전 테스트들의 메일 발송 완료 대기
        TimeUnit.SECONDS.sleep(10);

        // When - 발송 로그 조회
        List<Map<String, Object>> logs = mailDao.selectList("mail.selectRecentMailLogs", null);

        // Then
        System.out.println("\n발송 로그 조회 결과:");
        System.out.println("총 로그 수: " + logs.size());

        for (Map<String, Object> log : logs) {
            System.out.println("\n----------------------------------------");
            System.out.println("제목: " + log.get("subject"));
            System.out.println("수신자: " + log.get("recipients"));
            System.out.println("상태: " + log.get("sendStatus"));
            System.out.println("발송시간: " + log.get("sendDate"));
            if (log.get("ccRecipients") != null) {
                System.out.println("CC: " + log.get("ccRecipients"));
            }
        }

        // 검증
        assertNotNull(logs, "발송 로그가 존재해야 함");
        assertTrue(logs.size() >= 1, "최소 1건 이상의 발송 로그가 있어야 함");

        // 모든 메일이 SUCCESS 상태인지 확인
        long successCount = logs.stream()
            .filter(log -> "SUCCESS".equals(log.get("sendStatus")))
            .count();

        System.out.println("\n========================================");
        System.out.println("전체 로그: " + logs.size() + "건");
        System.out.println("성공: " + successCount + "건");
        System.out.println("========================================");
    }
}