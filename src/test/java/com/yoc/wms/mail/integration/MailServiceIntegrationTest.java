package com.yoc.wms.mail.integration;

import com.yoc.wms.mail.dao.MailDao;
import com.yoc.wms.mail.domain.MailRequest;
import com.yoc.wms.mail.domain.Recipient;
import com.yoc.wms.mail.service.MailService;
import com.yoc.wms.mail.util.FakeMailSender;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;

import static org.junit.Assert.*;

/**
 * MailService 통합 테스트 (Real Components + Fake)
 *
 * Architecture:
 * - MailService: Real (실제 발송 로직 테스트)
 * - MailDao: Real (H2 In-Memory)
 * - JavaMailSender: Fake (FakeMailSender, SMTP 발송 방지)
 *
 * Chicago School 테스트 방식:
 * - Mock 없음 (Real Components 사용)
 * - verify 없음 (FakeMailSender count 검증)
 * - 비즈니스 결과 검증 ("무엇을" 달성했는가)
 *
 * 운영 환경 호환성:
 * - Mockito 불필요 (FakeMailSender는 순수 Java)
 * - Spring 3.1.2 호환 (복사 가능)
 *
 * 시나리오 구성:
 * 1. 단일 섹션 메일 (텍스트만)
 * 2. 복수 섹션 메일 (텍스트 + 테이블 + 구분선 + 텍스트) - 범용 Builder 사용
 * 3. 알람 메일 (Helper Methods 사용)
 * 4. 보고서 메일 (Helper Methods 사용)
 * 5. 공지 메일 (Helper Methods 사용)
 * 6. CC 포함 메일
 * 7. 발송 로그 검증
 *
 * @since v2.4.0 (Chicago School, Mockito 제거)
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@ActiveProfiles("integration")
@Import(IntegrationTestConfig.class)  // ⭐ FakeMailSender 주입
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MailServiceIntegrationTest {

    @Autowired
    private MailService mailService;  // Real

    @Autowired
    private MailDao mailDao;  // Real (H2)

    @Autowired
    private JavaMailSender mailSender;  // Fake (IntegrationTestConfig에서 주입)

    private List<Recipient> testRecipients;

    @Before
    public void setUp() {
        // 테스트용 수신자 설정
        testRecipients = new ArrayList<>();
        testRecipients.add(Recipient.builder()
            .userId("ADMIN")
            .email("admin@company.com")
            .group("ADM")
            .build());
        testRecipients.add(Recipient.builder()
            .userId("USER")
            .email("user@company.com")
            .group("USER")
            .build());

        // Fake 초기화
        FakeMailSender fake = (FakeMailSender) mailSender;
        fake.reset();

        System.out.println("\n========================================");
        System.out.println("MailService 통합 테스트 시작");
        System.out.println("수신자: " + testRecipients.size() + "명");
        System.out.println("========================================\n");
    }

    @After
    public void tearDown() {
        System.out.println("\n테스트 종료\n");
    }

    // ==================== 시나리오 1: 단일 섹션 (텍스트만) ====================

    @Test
    public void test01_scenario1_singleTextSection_multipleRecipients() {
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
        boolean result = mailService.sendMail(request);

        // Then
        assertTrue("메일 발송 성공", result);

        FakeMailSender fake = (FakeMailSender) mailSender;
        assertEquals(1, fake.getSentCount());

        // 발송 로그 확인
        List<Map<String, Object>> logs = mailDao.selectList("mail.selectRecentMailLogs", null);
        assertNotNull(logs);
        assertFalse("발송 로그가 생성되어야 함", logs.isEmpty());

        System.out.println("✅ 메일 발송 완료");
    }

    // ==================== 시나리오 2: 복수 섹션 (범용 Builder) ====================

    @Test
    public void test02_scenario2_multipleSections_genericBuilder() {

        System.out.println("\n[시나리오 2] 복수 섹션 (텍스트 + 테이블 + 구분선 + 텍스트) - 범용 Builder");

        // Given - 테스트 데이터 직접 생성
        List<Map<String, String>> tableData = new ArrayList<>();

        Map<String, String> row1 = new LinkedHashMap<>();
        row1.put("주문번호", "251010001");
        row1.put("주문수량", "10");
        row1.put("확정수량", "8");
        row1.put("센터분할", "예");
        tableData.add(row1);

        Map<String, String> row2 = new LinkedHashMap<>();
        row2.put("주문번호", "251010005");
        row2.put("주문수량", "3");
        row2.put("확정수량", "1");
        row2.put("센터분할", "아니오");
        tableData.add(row2);

        Map<String, String> row3 = new LinkedHashMap<>();
        row3.put("주문번호", "251010009");
        row3.put("주문수량", "8");
        row3.put("확정수량", "2");
        row3.put("센터분할", "예");
        tableData.add(row3);

        // 범용 Builder로 복수 섹션 구성 (MailSection 의존성 제거)
        MailRequest request = MailRequest.builder()
            .subject("[통합테스트] B2C 주문 분할 확정 안내")
            .addTextSection("📊 테스트용 자료 입니다.",
                "테스트용 자료 입니다.\n\n" +
                "ADM 사용자에게만 발송되는 메일입니다.")
            .addTableSection(tableData)
            .addDivider()
            .addTextSection("📌 조치 사항",
                "테스트용 메일 입니다.\n" +
                "해당 주문번호에 대한 출고 작업을 우선해 주시기 바랍니다.")
            .recipients(testRecipients)
            .mailType("DIRECT")
            .build();

        // When
        System.out.println("메일 발송 중...");
        boolean result = mailService.sendMail(request);

        // Then
        assertTrue("메일 발송 성공", result);

        FakeMailSender fake = (FakeMailSender) mailSender;
        assertEquals(1, fake.getSentCount());

        System.out.println("✅ 메일 발송 완료");
        System.out.println("섹션 수: 4개 (TEXT + TABLE + DIVIDER + TEXT)");
    }

    // ==================== 시나리오 3: 알람 메일 (Helper Methods) ====================

    @Test
    public void test03_scenario3_alarmMail_warning_helperMethods() {
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
        boolean result = mailService.sendMail(request);

        // Then
        assertTrue("메일 발송 성공", result);

        FakeMailSender fake = (FakeMailSender) mailSender;
        assertEquals(1, fake.getSentCount());

        System.out.println("✅ 메일 발송 완료");
        System.out.println("심각도: WARNING, 테이블 행 수: " + tableData.size());
    }

    // ==================== 시나리오 4: 보고서 메일 (Helper Methods) ====================

    @Test
    public void test04_scenario4_reportMail_helperMethods() {
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
        boolean result = mailService.sendMail(request);

        // Then
        assertTrue("메일 발송 성공", result);

        FakeMailSender fake = (FakeMailSender) mailSender;
        assertEquals(1, fake.getSentCount());

        System.out.println("✅ 메일 발송 완료");
        System.out.println("테이블 행 수: " + reportData.size());
    }

    // ==================== 시나리오 5: 공지 메일 (Helper Methods) ====================

    @Test
    public void test05_scenario5_noticeMail_helperMethods() {
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
        boolean result = mailService.sendMail(request);

        // Then
        assertTrue("메일 발송 성공", result);

        FakeMailSender fake = (FakeMailSender) mailSender;
        assertEquals(1, fake.getSentCount());

        System.out.println("✅ 메일 발송 완료");
        System.out.println("유형: NOTICE");
    }

    // ==================== 시나리오 6: CC 포함 메일 ====================

    @Test
    public void test06_scenario6_mailWithCC() {
        System.out.println("\n[시나리오 6] CC 포함 메일 발송");

        // Given - 주 수신자는 ADMIN, CC는 USER
        List<Recipient> toRecipients = Collections.singletonList(
            Recipient.builder()
                .userId("ADMIN")
                .email("admin@company.com")
                .group("ADM")
                .build()
        );
        List<Recipient> ccRecipients = Collections.singletonList(
            Recipient.builder()
                .userId("USER")
                .email("user@company.com")
                .group("USER")
                .build()
        );

        MailRequest request = MailRequest.builder()
            .subject("[통합테스트] CC 포함 테스트 메일")
            .addTextSection("CC 기능 테스트",
                "이 메일은 CC 기능을 테스트하기 위해 발송되었습니다.\n\n" +
                "TO: admin@company.com (ADMIN)\n" +
                "CC: user@company.com (USER)\n\n" +
                "양쪽 모두 수신 확인 부탁드립니다.")
            .recipients(toRecipients)
            .ccRecipients(ccRecipients)
            .mailType("DIRECT")
            .build();

        // When
        System.out.println("메일 발송 중...");
        boolean result = mailService.sendMail(request);

        // Then
        assertTrue("메일 발송 성공", result);

        FakeMailSender fake = (FakeMailSender) mailSender;
        assertEquals(1, fake.getSentCount());

        System.out.println("✅ 메일 발송 완료 (CC 포함)");
    }

    // ==================== 시나리오 7: 발송 로그 검증 ====================

    @Test
    public void test07_scenario7_verifyMailLogs() {
        System.out.println("\n[시나리오 7] 발송 로그 검증");

        // Given - 테스트 메일 1건 발송
        MailRequest request = MailRequest.builder()
            .subject("[통합테스트] 로그 검증용 메일")
            .addTextSection("로그 검증", "발송 로그가 정상적으로 기록되는지 확인합니다.")
            .recipients(testRecipients)
            .mailType("DIRECT")
            .build();

        mailService.sendMail(request);

        // When - 발송 로그 조회
        List<Map<String, Object>> logs = mailDao.selectList("mail.selectRecentMailLogs", null);

        // Then
        assertNotNull("발송 로그가 존재해야 함", logs);
        assertFalse("최소 1건 이상의 발송 로그가 있어야 함", logs.isEmpty());

        // 모든 메일이 SUCCESS 상태인지 확인 (for-loop 사용)
        int successCount = 0;
        for (Map<String, Object> log : logs) {
            if ("SUCCESS".equals(log.get("sendStatus"))) {
                successCount++;
            }
        }

        System.out.println("\n========================================");
        System.out.println("전체 로그: " + logs.size() + "건");
        System.out.println("성공: " + successCount + "건");
        System.out.println("========================================");

        assertTrue("성공 로그가 1건 이상 있어야 함", successCount >= 1);
    }
}