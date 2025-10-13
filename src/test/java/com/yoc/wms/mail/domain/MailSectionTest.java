package com.yoc.wms.mail.domain;

import com.yoc.wms.mail.enums.SectionType;
import com.yoc.wms.mail.exception.ValueChainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MailSection 단위 테스트
 *
 * 테스트 범위:
 * - Factory Method 정상 동작 검증
 * - 타입별 검증 로직 확인
 * - 엣지케이스 (null, empty, XSS)
 * - 메타데이터 조회 로직
 */
class MailSectionTest {

    // ==================== Factory Method 테스트 ====================

    @Test
    @DisplayName("forAlarmWithCustomText: 정상 생성 - 테이블 데이터 포함")
    void forAlarmWithCustomText_withTable() {
        // Given
        String title = "지연 주문 현황";
        String content = "현재 10건의 지연 주문이 발견되었습니다.";
        String severity = "WARNING";
        List<Map<String, String>> tableData = Arrays.asList(
            createMap("orderId", "001", "status", "DELAYED"),
            createMap("orderId", "002", "status", "DELAYED")
        );

        // When
        List<MailSection> sections = MailSection.forAlarmWithCustomText(title, content, severity, tableData);

        // Then
        assertEquals(3, sections.size());

        // TEXT 섹션 검증
        MailSection textSection = sections.get(0);
        assertEquals(SectionType.TEXT, textSection.getType());
        assertTrue(textSection.getTitle().contains("⚠️"));
        assertTrue(textSection.getTitle().contains(title));
        assertEquals(content, textSection.getContent());

        // TABLE 섹션 검증
        MailSection tableSection = sections.get(1);
        assertEquals(SectionType.TABLE, tableSection.getType());
        assertEquals(2, tableSection.getData().size());

        // DIVIDER 섹션 검증
        MailSection divider = sections.get(2);
        assertEquals(SectionType.DIVIDER, divider.getType());
    }

    @Test
    @DisplayName("forAlarmWithCustomText: 테이블 데이터 없음 - TABLE 섹션 생략")
    void forAlarmWithCustomText_withoutTable() {
        // Given
        String title = "시스템 알림";
        String content = "정상 처리되었습니다.";
        String severity = "INFO";

        // When
        List<MailSection> sections = MailSection.forAlarmWithCustomText(title, content, severity, null);

        // Then
        assertEquals(2, sections.size()); // TEXT + DIVIDER만
        assertEquals(SectionType.TEXT, sections.get(0).getType());
        assertEquals(SectionType.DIVIDER, sections.get(1).getType());
    }

    @Test
    @DisplayName("forAlarmWithCustomText: 빈 테이블 데이터 - TABLE 섹션 생략")
    void forAlarmWithCustomText_withEmptyTable() {
        // When
        List<MailSection> sections = MailSection.forAlarmWithCustomText(
            "알림", "내용", "INFO", Collections.emptyList()
        );

        // Then
        assertEquals(2, sections.size()); // TEXT + DIVIDER만
    }


    @Test
    @DisplayName("forNotice: 공지 섹션 생성")
    void forNotice() {
        // When
        List<MailSection> sections = MailSection.forNotice("시스템 점검 안내", "12월 1일 00:00 ~ 04:00");

        // Then
        assertEquals(2, sections.size());
        MailSection textSection = sections.get(0);
        assertEquals("시스템 점검 안내", textSection.getTitle());
        assertEquals("12월 1일 00:00 ~ 04:00", textSection.getContent());
    }

    @Test
    @DisplayName("forReport: 보고서 섹션 생성 - 테이블 포함")
    void forReport_withTable() {
        // Given
        List<Map<String, String>> tableData = Arrays.asList(
            createMap("date", "2024-11-01", "count", "150"),
            createMap("date", "2024-11-02", "count", "200")
        );

        // When
        List<MailSection> sections = MailSection.forReport("일일 처리 현황", "최근 처리 건수입니다.", tableData);

        // Then
        assertEquals(3, sections.size()); // TEXT + TABLE + DIVIDER
        assertEquals("일일 처리 현황", sections.get(0).getTitle());
        assertEquals(2, sections.get(1).getData().size());
    }

    @Test
    @DisplayName("forReport: 보고서 섹션 생성 - 테이블 없음")
    void forReport_withoutTable() {
        // When
        List<MailSection> sections = MailSection.forReport("요약 보고", "특이사항 없음", null);

        // Then
        assertEquals(2, sections.size()); // TEXT + DIVIDER만
    }

    @Test
    @DisplayName("forSimpleText: 단순 텍스트 섹션")
    void forSimpleText() {
        // When
        List<MailSection> sections = MailSection.forSimpleText("안내", "테스트 메시지입니다.");

        // Then
        assertEquals(1, sections.size());
        MailSection section = sections.get(0);
        assertEquals(SectionType.TEXT, section.getType());
        assertEquals("안내", section.getTitle());
    }

    @Test
    @DisplayName("forContact: 연락처 섹션")
    void forContact() {
        // When
//        MailSection section = MailSection.forContact("담당자: 홍길동 (010-1234-5678)");
        List<MailSection> section = MailSection.forContact("담당자: 홍길동 (010-1234-5678)");

        // Then
        assertEquals(SectionType.DIVIDER, section.get(0).getType());
        assertEquals(SectionType.TEXT, section.get(1).getType());
        assertTrue(section.get(1).getTitle().contains("📞"));
        assertEquals("담당자: 홍길동 (010-1234-5678)", section.get(1).getContent());
    }

    // ==================== 검증 로직 테스트 ====================

    @Test
    @DisplayName("검증 실패: type이 null")
    void validation_nullType() {
        // When & Then
        ValueChainException ex = assertThrows(ValueChainException.class, () ->
            MailSection.builder().content("내용").build()
        );
        assertTrue(ex.getMessage().contains("type is required"));
    }

    @Test
    @DisplayName("검증 실패: TABLE 타입에 data가 null")
    void validation_tableWithoutData() {
        // When & Then
        ValueChainException ex = assertThrows(ValueChainException.class, () ->
            MailSection.builder().type(SectionType.TABLE).build()
        );
        assertTrue(ex.getMessage().contains("TABLE type requires data"));
    }

    @Test
    @DisplayName("검증 실패: TABLE 타입에 빈 data")
    void validation_tableWithEmptyData() {
        // When & Then
        ValueChainException ex = assertThrows(ValueChainException.class, () ->
            MailSection.builder()
                .type(SectionType.TABLE)
                .data(Collections.emptyList())
                .build()
        );
        assertTrue(ex.getMessage().contains("TABLE type requires data"));
    }

    @Test
    @DisplayName("검증 실패: TEXT 타입에 content가 null")
    void validation_textWithoutContent() {
        // When & Then
        ValueChainException ex = assertThrows(ValueChainException.class, () ->
            MailSection.builder().type(SectionType.TEXT).build()
        );
        assertTrue(ex.getMessage().contains("TEXT type requires content"));
    }

    @Test
    @DisplayName("검증 실패: TEXT 타입에 공백 content")
    void validation_textWithBlankContent() {
        // When & Then
        ValueChainException ex = assertThrows(ValueChainException.class, () ->
            MailSection.builder()
                .type(SectionType.TEXT)
                .content("   ")
                .build()
        );
        assertTrue(ex.getMessage().contains("TEXT type requires content"));
    }

    @Test
    @DisplayName("검증 실패: HTML 타입에 <script> 태그 포함")
    void validation_htmlWithScriptTag() {
        // When & Then
        ValueChainException ex = assertThrows(ValueChainException.class, () ->
            MailSection.builder()
                .type(SectionType.HTML)
                .content("<p>Hello</p><script>alert('xss')</script>")
                .build()
        );
        assertTrue(ex.getMessage().contains("unsafe elements"));
    }

    @Test
    @DisplayName("검증 실패: HTML 타입에 javascript: 포함")
    void validation_htmlWithJavascriptProtocol() {
        // When & Then
        assertThrows(ValueChainException.class, () ->
            MailSection.builder()
                .type(SectionType.HTML)
                .content("<a href='javascript:void(0)'>Click</a>")
                .build()
        );
    }

    @Test
    @DisplayName("검증 실패: HTML 타입에 onerror 이벤트")
    void validation_htmlWithOnError() {
        // When & Then
        assertThrows(ValueChainException.class, () ->
            MailSection.builder()
                .type(SectionType.HTML)
                .content("<img src='x' onerror='alert(1)'>")
                .build()
        );
    }

    @Test
    @DisplayName("검증 성공: DIVIDER는 별도 필드 불필요")
    void validation_dividerNoRequirement() {
        // When
        MailSection divider = MailSection.builder().type(SectionType.DIVIDER).build();

        // Then
        assertNotNull(divider);
        assertEquals(SectionType.DIVIDER, divider.getType());
    }

    // ==================== 메타데이터 테스트 ====================

    @Test
    @DisplayName("메타데이터: 정상 조회")
    void metadata_get() {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("color", "red");
        metadata.put("size", "large");

        // When
        MailSection section = MailSection.builder()
            .type(SectionType.TEXT)
            .content("내용")
            .metadata(metadata)
            .build();

        // Then
        assertEquals("red", section.getMetadataOrDefault("color", "blue"));
        assertEquals("large", section.getMetadataOrDefault("size", "medium"));
        assertTrue(section.hasMetadata("color"));
    }

    @Test
    @DisplayName("메타데이터: 기본값 반환")
    void metadata_defaultValue() {
        // Given
        MailSection section = MailSection.builder()
            .type(SectionType.DIVIDER)
            .build();

        // When & Then
        assertEquals("blue", section.getMetadataOrDefault("color", "blue"));
        assertFalse(section.hasMetadata("color"));
    }

    @Test
    @DisplayName("메타데이터: null 메타데이터 처리")
    void metadata_nullSafe() {
        // Given
        MailSection section = MailSection.builder()
            .type(SectionType.DIVIDER)
            .metadata(null)
            .build();

        // When & Then
        assertNotNull(section.getMetadata());
        assertEquals("default", section.getMetadataOrDefault("key", "default"));
    }

    // ==================== 편의 메서드 테스트 ====================

    @Test
    @DisplayName("hasTitle: 제목 있음")
    void hasTitle_true() {
        // Given
        MailSection section = MailSection.builder()
            .type(SectionType.TEXT)
            .title("제목")
            .content("내용")
            .build();

        // When & Then
        assertTrue(section.hasTitle());
    }

    @Test
    @DisplayName("hasTitle: 제목 없음")
    void hasTitle_false() {
        // Given
        MailSection section = MailSection.builder()
            .type(SectionType.TEXT)
            .content("내용")
            .build();

        // When & Then
        assertFalse(section.hasTitle());
    }

    @Test
    @DisplayName("hasTitle: 공백 제목")
    void hasTitle_blank() {
        // Given
        MailSection section = MailSection.builder()
            .type(SectionType.TEXT)
            .title("   ")
            .content("내용")
            .build();

        // When & Then
        assertFalse(section.hasTitle());
    }

    // ==================== 심각도 아이콘 테스트 ====================

    @Test
    @DisplayName("심각도 아이콘: CRITICAL")
    void severityIcon_critical() {
        List<MailSection> sections = MailSection.forAlarmWithCustomText(
            "긴급", "내용", "CRITICAL", null
        );
        assertTrue(sections.get(0).getTitle().contains("🚨"));
    }

    @Test
    @DisplayName("심각도 아이콘: WARNING")
    void severityIcon_warning() {
        List<MailSection> sections = MailSection.forAlarmWithCustomText(
            "경고", "내용", "WARNING", null
        );
        assertTrue(sections.get(0).getTitle().contains("⚠️"));
    }

    @Test
    @DisplayName("심각도 아이콘: INFO")
    void severityIcon_info() {
        List<MailSection> sections = MailSection.forAlarmWithCustomText(
            "정보", "내용", "INFO", null
        );
        assertTrue(sections.get(0).getTitle().contains("ℹ️"));
    }

    @Test
    @DisplayName("심각도 아이콘: 알 수 없는 값 - 기본 아이콘")
    void severityIcon_unknown() {
        List<MailSection> sections = MailSection.forAlarmWithCustomText(
            "알림", "내용", "UNKNOWN", null
        );
        assertTrue(sections.get(0).getTitle().contains("📋"));
    }

    @Test
    @DisplayName("심각도 아이콘: null - 기본 아이콘")
    void severityIcon_null() {
        List<MailSection> sections = MailSection.forAlarmWithCustomText(
            "알림", "내용", null, null
        );
        assertTrue(sections.get(0).getTitle().contains("📋"));
    }

    @Test
    @DisplayName("심각도 아이콘: 소문자 입력 - 정상 처리")
    void severityIcon_lowercase() {
        List<MailSection> sections = MailSection.forAlarmWithCustomText(
            "경고", "내용", "warning", null
        );
        assertTrue(sections.get(0).getTitle().contains("⚠️"));
    }

    // ==================== Helper Methods ====================

    private Map<String, String> createMap(String... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}