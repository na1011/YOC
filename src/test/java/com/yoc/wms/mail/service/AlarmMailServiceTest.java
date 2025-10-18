package com.yoc.wms.mail.service;

import com.yoc.wms.mail.domain.MailRequest;
import com.yoc.wms.mail.domain.Recipient;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * AlarmMailService 단위 테스트 (Pure Functions만 테스트)
 *
 * 테스트 범위:
 * - buildAlarmMailRequest() - MailRequest 생성 로직
 * - parseRecipientIds() - 사용자 ID 파싱
 * - parseRecipientGroups() - 그룹 파싱
 * - convertToStringMap() - Map 타입 변환
 *
 * Mock/verify 없음 (Chicago School 테스트 방식)
 * 운영 환경 100% 호환 (Mockito 불필요)
 *
 * @since v2.4.0 (Pure Functions 테스트)
 */
public class AlarmMailServiceTest {

    private AlarmMailService service;

    @Before
    public void setUp() {
        service = new AlarmMailService();
    }

    // ===== buildAlarmMailRequest() 테스트 =====

    @Test
    public void buildAlarmMailRequest_criticalSeverity_withTableData() {
        // Given
        Map<String, Object> queueData = createMap(
                "severity", "CRITICAL",
                "sectionTitle", "재고 부족",
                "sectionContent", "긴급 확인이 필요합니다.",
                "mailSource", "LOW_STOCK"
        );
        List<Map<String, Object>> tableData = Arrays.asList(
                createMap("productId", "P001", "stock", 5),
                createMap("productId", "P002", "stock", 3)
        );
        List<Recipient> recipients = Arrays.asList(
                Recipient.builder().email("admin@company.com").userId("ADMIN1").build()
        );

        // When
        MailRequest result = service.buildAlarmMailRequest(queueData, tableData, recipients);

        // Then
        assertEquals("[긴급] WMS 재고 부족 2건", result.getSubject());
        assertEquals("🔴 재고 부족", result.getSections().get(0).getTitle());
        assertEquals("긴급 확인이 필요합니다.", result.getSections().get(0).getContent());
        assertEquals("LOW_STOCK", result.getMailSource());
        assertEquals("ALARM", result.getMailType());
        assertEquals(2, result.getSections().size());  // TEXT + TABLE
        assertEquals("TABLE", result.getSections().get(1).getType().name());
    }

    @Test
    public void buildAlarmMailRequest_warningSeverity_withTableData() {
        // Given
        Map<String, Object> queueData = createMap(
                "severity", "WARNING",
                "sectionTitle", "지연 주문",
                "sectionContent", "확인 바랍니다.",
                "mailSource", "OVERDUE_ORDERS"
        );
        List<Map<String, Object>> tableData = Arrays.asList(
                createMap("orderId", "001", "status", "DELAYED")
        );
        List<Recipient> recipients = Arrays.asList(
                Recipient.builder().email("user@company.com").build()
        );

        // When
        MailRequest result = service.buildAlarmMailRequest(queueData, tableData, recipients);

        // Then
        assertEquals("[경고] WMS 지연 주문 1건", result.getSubject());
        assertEquals("⚠️ 지연 주문", result.getSections().get(0).getTitle());
        assertEquals("확인 바랍니다.", result.getSections().get(0).getContent());
        assertEquals("OVERDUE_ORDERS", result.getMailSource());
        assertEquals(2, result.getSections().size());  // TEXT + TABLE
    }

    @Test
    public void buildAlarmMailRequest_infoSeverity_noTableData() {
        // Given
        Map<String, Object> queueData = createMap(
                "severity", "INFO",
                "sectionTitle", "정보 알림",
                "sectionContent", "참고하세요.",
                "mailSource", "INFO_NOTIFICATION"
        );
        List<Map<String, Object>> tableData = Collections.emptyList();  // 빈 데이터
        List<Recipient> recipients = Arrays.asList(
                Recipient.builder().email("user@company.com").build()
        );

        // When
        MailRequest result = service.buildAlarmMailRequest(queueData, tableData, recipients);

        // Then
        assertEquals("[경고] WMS 정보 알림 0건", result.getSubject());  // alarmSubject()는 INFO도 [경고]
        assertEquals("ℹ️ 정보 알림", result.getSections().get(0).getTitle());
        assertEquals("참고하세요.", result.getSections().get(0).getContent());
        assertEquals(1, result.getSections().size());  // TEXT만 (TABLE 없음)
    }

    @Test
    public void buildAlarmMailRequest_nullTableData() {
        // Given
        Map<String, Object> queueData = createMap(
                "severity", "WARNING",
                "sectionTitle", "테스트",
                "sectionContent", "NULL 테이블 데이터",
                "mailSource", "TEST"
        );
        List<Recipient> recipients = Arrays.asList(
                Recipient.builder().email("user@company.com").build()
        );

        // When
        MailRequest result = service.buildAlarmMailRequest(queueData, null, recipients);

        // Then
        assertEquals("[경고] WMS 테스트 0건", result.getSubject());
        assertEquals(1, result.getSections().size());  // TEXT만
    }

    @Test
    public void buildAlarmMailRequest_multipleRecipients() {
        // Given
        Map<String, Object> queueData = createMap(
                "severity", "CRITICAL",
                "sectionTitle", "시스템 오류",
                "sectionContent", "긴급 조치 필요",
                "mailSource", "SYSTEM_ERROR"
        );
        List<Recipient> recipients = Arrays.asList(
                Recipient.builder().email("admin1@company.com").userId("ADMIN1").build(),
                Recipient.builder().email("admin2@company.com").userId("ADMIN2").build(),
                Recipient.builder().email("admin3@company.com").userId("ADMIN3").build()
        );

        // When
        MailRequest result = service.buildAlarmMailRequest(queueData, null, recipients);

        // Then
        assertEquals(3, result.getRecipients().size());
        assertEquals("ALARM", result.getMailType());
        assertEquals("SYSTEM_ERROR", result.getMailSource());
    }

    @Test
    public void buildAlarmMailRequest_largeTableData() {
        // Given
        Map<String, Object> queueData = createMap(
                "severity", "WARNING",
                "sectionTitle", "대량 지연",
                "sectionContent", "확인 필요",
                "mailSource", "BULK_DELAY"
        );

        // 100건의 테이블 데이터
        List<Map<String, Object>> tableData = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            tableData.add(createMap("orderId", "ORDER" + i, "status", "DELAYED"));
        }

        List<Recipient> recipients = Arrays.asList(
                Recipient.builder().email("admin@company.com").build()
        );

        // When
        MailRequest result = service.buildAlarmMailRequest(queueData, tableData, recipients);

        // Then
        assertEquals("[경고] WMS 대량 지연 100건", result.getSubject());
        assertEquals(2, result.getSections().size());  // TEXT + TABLE
    }

    @Test
    public void buildAlarmMailRequest_longContent() {
        // Given
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longContent.append("긴 본문 내용입니다. ");
        }

        Map<String, Object> queueData = createMap(
                "severity", "INFO",
                "sectionTitle", "긴 내용 테스트",
                "sectionContent", longContent.toString(),
                "mailSource", "LONG_CONTENT_TEST"
        );
        List<Recipient> recipients = Arrays.asList(
                Recipient.builder().email("user@company.com").build()
        );

        // When
        MailRequest result = service.buildAlarmMailRequest(queueData, null, recipients);

        // Then
        assertEquals(longContent.toString(), result.getSections().get(0).getContent());
        assertTrue(result.getSections().get(0).getContent().length() > 1000);
    }

    @Test
    public void buildAlarmMailRequest_withNullValues() {
        // Given - NULL 값이 포함된 테이블 데이터
        Map<String, Object> queueData = createMap(
                "severity", "WARNING",
                "sectionTitle", "NULL 테스트",
                "sectionContent", "NULL 값 포함",
                "mailSource", "NULL_TEST"
        );
        List<Map<String, Object>> tableData = Arrays.asList(
                createMap("orderId", "001", "notes", null)  // NULL 값
        );
        List<Recipient> recipients = Arrays.asList(
                Recipient.builder().email("user@company.com").build()
        );

        // When
        MailRequest result = service.buildAlarmMailRequest(queueData, tableData, recipients);

        // Then
        assertEquals(2, result.getSections().size());
        // convertToStringMap()이 NULL을 빈 문자열로 변환
    }

    @Test
    public void buildAlarmMailRequest_preservesColumnOrder() {
        // Given - 컬럼 순서 테스트
        Map<String, Object> queueData = createMap(
                "severity", "INFO",
                "sectionTitle", "순서 테스트",
                "sectionContent", "컬럼 순서 확인",
                "mailSource", "ORDER_TEST"
        );

        // LinkedHashMap으로 순서 보장
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("col1", "A");
        row1.put("col2", "B");
        row1.put("col3", "C");

        List<Map<String, Object>> tableData = Arrays.asList(row1);
        List<Recipient> recipients = Arrays.asList(
                Recipient.builder().email("user@company.com").build()
        );

        // When
        MailRequest result = service.buildAlarmMailRequest(queueData, tableData, recipients);

        // Then - TABLE 섹션이 있어야 함
        assertEquals("TABLE", result.getSections().get(1).getType().name());
    }

    @Test(expected = com.yoc.wms.mail.exception.ValueChainException.class)
    public void buildAlarmMailRequest_edgeCase_emptyContent() {
        // Given - 빈 내용 (TEXT 섹션은 content 필수 → 예외 발생 예상)
        Map<String, Object> queueData = createMap(
                "severity", "INFO",
                "sectionTitle", "빈 내용",
                "sectionContent", "",
                "mailSource", "EMPTY_CONTENT"
        );
        List<Recipient> recipients = Arrays.asList(
                Recipient.builder().email("user@company.com").build()
        );

        // When - ValueChainException 발생 예상
        service.buildAlarmMailRequest(queueData, null, recipients);
    }

    // ===== parseRecipientIds() 테스트 =====

    @Test
    public void parseRecipientIds_multipleIds() {
        // Given
        String input = "admin1,user1,sales001";

        // When
        List<String> result = service.parseRecipientIds(input);

        // Then
        assertEquals(Arrays.asList("admin1", "user1", "sales001"), result);
    }

    @Test
    public void parseRecipientIds_singleId() {
        // Given
        String input = "admin1";

        // When
        List<String> result = service.parseRecipientIds(input);

        // Then
        assertEquals(Arrays.asList("admin1"), result);
    }

    @Test
    public void parseRecipientIds_withWhitespace() {
        // Given
        String input = " admin1 , user1 , sales001 ";

        // When
        List<String> result = service.parseRecipientIds(input);

        // Then
        assertEquals(Arrays.asList("admin1", "user1", "sales001"), result);
    }

    @Test
    public void parseRecipientIds_withEmptyItems() {
        // Given
        String input = "admin1,,user1,";

        // When
        List<String> result = service.parseRecipientIds(input);

        // Then
        assertEquals(Arrays.asList("admin1", "user1"), result);
    }

    @Test
    public void parseRecipientIds_nullInput() {
        // When
        List<String> result = service.parseRecipientIds(null);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseRecipientIds_emptyInput() {
        // When
        List<String> result = service.parseRecipientIds("  ");

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseRecipientIds_onlyCommas() {
        // Given
        String input = ",,,";

        // When
        List<String> result = service.parseRecipientIds(input);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseRecipientIds_mixedCase() {
        // Given - parseRecipientIds()는 대소문자 정규화하지 않음
        String input = "Admin1,USER1,SaLes001";

        // When
        List<String> result = service.parseRecipientIds(input);

        // Then
        assertEquals(Arrays.asList("Admin1", "USER1", "SaLes001"), result);
        // 대소문자 정규화는 Recipient.fromMap()에서 담당
    }

    // ===== parseRecipientGroups() 테스트 =====

    @Test
    public void parseRecipientGroups_multipleGroups() {
        // Given
        String input = "ADM,SALES,LOGISTICS";

        // When
        List<String> result = service.parseRecipientGroups(input);

        // Then
        assertEquals(Arrays.asList("ADM", "SALES", "LOGISTICS"), result);
    }

    @Test
    public void parseRecipientGroups_singleGroup() {
        // Given
        String input = "ADM";

        // When
        List<String> result = service.parseRecipientGroups(input);

        // Then
        assertEquals(Arrays.asList("ADM"), result);
    }

    @Test
    public void parseRecipientGroups_withWhitespace() {
        // Given
        String input = " ADM , SALES , LOGISTICS ";

        // When
        List<String> result = service.parseRecipientGroups(input);

        // Then
        assertEquals(Arrays.asList("ADM", "SALES", "LOGISTICS"), result);
    }

    @Test
    public void parseRecipientGroups_nullInput() {
        // When
        List<String> result = service.parseRecipientGroups(null);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseRecipientGroups_emptyInput() {
        // When
        List<String> result = service.parseRecipientGroups("  ");

        // Then
        assertTrue(result.isEmpty());
    }

    // ===== convertToStringMap() 테스트 =====

    @Test
    public void convertToStringMap_basicConversion() {
        // Given
        List<Map<String, Object>> input = Arrays.asList(
                createMap("orderId", 1, "name", "홍길동", "active", true),
                createMap("orderId", 2, "name", "김철수", "active", false)
        );

        // When
        List<Map<String, String>> result = service.convertToStringMap(input);

        // Then
        assertEquals(2, result.size());
        assertEquals("1", result.get(0).get("orderId"));
        assertEquals("홍길동", result.get(0).get("name"));
        assertEquals("true", result.get(0).get("active"));
        assertEquals("2", result.get(1).get("orderId"));
        assertEquals("김철수", result.get(1).get("name"));
        assertEquals("false", result.get(1).get("active"));
    }

    @Test
    public void convertToStringMap_withNullValues() {
        // Given
        List<Map<String, Object>> input = Arrays.asList(
                createMap("orderId", 1, "notes", null)
        );

        // When
        List<Map<String, String>> result = service.convertToStringMap(input);

        // Then
        assertEquals("1", result.get(0).get("orderId"));
        assertEquals("", result.get(0).get("notes"));  // NULL → 빈 문자열
    }

    @Test
    public void convertToStringMap_nullInput() {
        // When
        List<Map<String, String>> result = service.convertToStringMap(null);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    public void convertToStringMap_emptyInput() {
        // When
        List<Map<String, String>> result = service.convertToStringMap(Collections.emptyList());

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    public void convertToStringMap_preservesOrder() {
        // Given - LinkedHashMap으로 순서 보장
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("col1", "A");
        row.put("col2", "B");
        row.put("col3", "C");

        List<Map<String, Object>> input = Arrays.asList(row);

        // When
        List<Map<String, String>> result = service.convertToStringMap(input);

        // Then
        assertEquals(1, result.size());
        // LinkedHashMap으로 변환되므로 순서 유지
        assertTrue(result.get(0) instanceof LinkedHashMap);

        // 순서 확인 (keySet() 순회)
        List<String> keys = new ArrayList<>(result.get(0).keySet());
        assertEquals("col1", keys.get(0));
        assertEquals("col2", keys.get(1));
        assertEquals("col3", keys.get(2));
    }

    @Test
    public void convertToStringMap_variousTypes() {
        // Given - 다양한 타입 테스트
        List<Map<String, Object>> input = Arrays.asList(
                createMap(
                        "intVal", 123,
                        "longVal", 123L,
                        "doubleVal", 123.45,
                        "boolVal", true,
                        "stringVal", "test"
                )
        );

        // When
        List<Map<String, String>> result = service.convertToStringMap(input);

        // Then
        assertEquals("123", result.get(0).get("intVal"));
        assertEquals("123", result.get(0).get("longVal"));
        assertEquals("123.45", result.get(0).get("doubleVal"));
        assertEquals("true", result.get(0).get("boolVal"));
        assertEquals("test", result.get(0).get("stringVal"));
    }

    // ===== Helper Methods =====

    private Map<String, Object> createMap(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
