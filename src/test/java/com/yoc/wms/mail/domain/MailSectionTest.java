package com.yoc.wms.mail.domain;

import com.yoc.wms.mail.enums.SectionType;
import com.yoc.wms.mail.exception.ValueChainException;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * MailSection 단위 테스트
 *
 * 테스트 범위:
 * - Factory Method 정상 동작 검증
 * - 타입별 검증 로직 확인
 * - 엣지케이스 (null, empty, XSS)
 * - 메타데이터 조회 로직
 */
public class MailSectionTest {

    // ==================== Factory Method 테스트 ====================

    @Test
    public void forAlarmWithCustomText_withTable() {
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
    public void forAlarmWithCustomText_withoutTable() {
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
    public void forAlarmWithCustomText_withEmptyTable() {
        // When
        List<MailSection> sections = MailSection.forAlarmWithCustomText(
            "알림", "내용", "INFO", Collections.<Map<String, String>>emptyList()
        );

        // Then
        assertEquals(2, sections.size()); // TEXT + DIVIDER만
    }


    @Test
    public void forNotice() {
        // When
        List<MailSection> sections = MailSection.forNotice("시스템 점검 안내", "12월 1일 00:00 ~ 04:00");

        // Then
        assertEquals(2, sections.size());
        MailSection textSection = sections.get(0);
        assertEquals("시스템 점검 안내", textSection.getTitle());
        assertEquals("12월 1일 00:00 ~ 04:00", textSection.getContent());
    }

    @Test
    public void forReport_withTable() {
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
    public void forReport_withoutTable() {
        // When
        List<MailSection> sections = MailSection.forReport("요약 보고", "특이사항 없음", null);

        // Then
        assertEquals(2, sections.size()); // TEXT + DIVIDER만
    }

    @Test
    public void forSimpleText() {
        // When
        List<MailSection> sections = MailSection.forSimpleText("안내", "테스트 메시지입니다.");

        // Then
        assertEquals(1, sections.size());
        MailSection section = sections.get(0);
        assertEquals(SectionType.TEXT, section.getType());
        assertEquals("안내", section.getTitle());
    }

    @Test
    public void forContact() {
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
    public void validation_nullType() {
        // When & Then
        try {
            MailSection.builder().content("내용").build();
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("type is required"));
        }
    }

    @Test
    public void validation_tableWithoutData() {
        // When & Then
        try {
            MailSection.builder().type(SectionType.TABLE).build();
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("TABLE type requires data"));
        }
    }

    @Test
    public void validation_tableWithEmptyData() {
        // When & Then
        try {
            MailSection.builder()
                .type(SectionType.TABLE)
                .data(Collections.<Map<String, String>>emptyList())
                .build();
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("TABLE type requires data"));
        }
    }

    @Test
    public void validation_textWithoutContent() {
        // When & Then
        try {
            MailSection.builder().type(SectionType.TEXT).build();
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("TEXT type requires content"));
        }
    }

    @Test
    public void validation_textWithBlankContent() {
        // When & Then
        try {
            MailSection.builder()
                .type(SectionType.TEXT)
                .content("   ")
                .build();
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("TEXT type requires content"));
        }
    }

    @Test
    public void validation_htmlWithScriptTag() {
        // When & Then
        try {
            MailSection.builder()
                .type(SectionType.HTML)
                .content("<p>Hello</p><script>alert('xss')</script>")
                .build();
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            assertTrue(ex.getMessage().contains("unsafe elements"));
        }
    }

    @Test
    public void validation_htmlWithJavascriptProtocol() {
        // When & Then
        try {
            MailSection.builder()
                .type(SectionType.HTML)
                .content("<a href='javascript:void(0)'>Click</a>")
                .build();
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            // Expected exception
            assertTrue(ex.getMessage().contains("unsafe elements"));
        }
    }

    @Test
    public void validation_htmlWithOnError() {
        // When & Then
        try {
            MailSection.builder()
                .type(SectionType.HTML)
                .content("<img src='x' onerror='alert(1)'>")
                .build();
            fail("Expected ValueChainException");
        } catch (ValueChainException ex) {
            // Expected exception
            assertTrue(ex.getMessage().contains("unsafe elements"));
        }
    }

    @Test
    public void validation_dividerNoRequirement() {
        // When
        MailSection divider = MailSection.builder().type(SectionType.DIVIDER).build();

        // Then
        assertNotNull(divider);
        assertEquals(SectionType.DIVIDER, divider.getType());
    }

    // ==================== 메타데이터 테스트 ====================

    @Test
    public void metadata_get() {
        // Given
        Map<String, Object> metadata = new HashMap<String, Object>();
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
    public void metadata_defaultValue() {
        // Given
        MailSection section = MailSection.builder()
            .type(SectionType.DIVIDER)
            .build();

        // When & Then
        assertEquals("blue", section.getMetadataOrDefault("color", "blue"));
        assertFalse(section.hasMetadata("color"));
    }

    @Test
    public void metadata_nullSafe() {
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
    public void hasTitle_true() {
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
    public void hasTitle_false() {
        // Given
        MailSection section = MailSection.builder()
            .type(SectionType.TEXT)
            .content("내용")
            .build();

        // When & Then
        assertFalse(section.hasTitle());
    }

    @Test
    public void hasTitle_blank() {
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
    public void severityIcon_critical() {
        List<MailSection> sections = MailSection.forAlarmWithCustomText(
            "긴급", "내용", "CRITICAL", null
        );
        assertTrue(sections.get(0).getTitle().contains("🚨"));
    }

    @Test
    public void severityIcon_warning() {
        List<MailSection> sections = MailSection.forAlarmWithCustomText(
            "경고", "내용", "WARNING", null
        );
        assertTrue(sections.get(0).getTitle().contains("⚠️"));
    }

    @Test
    public void severityIcon_info() {
        List<MailSection> sections = MailSection.forAlarmWithCustomText(
            "정보", "내용", "INFO", null
        );
        assertTrue(sections.get(0).getTitle().contains("ℹ️"));
    }

    @Test
    public void severityIcon_unknown() {
        List<MailSection> sections = MailSection.forAlarmWithCustomText(
            "알림", "내용", "UNKNOWN", null
        );
        assertTrue(sections.get(0).getTitle().contains("📋"));
    }

    @Test
    public void severityIcon_null() {
        List<MailSection> sections = MailSection.forAlarmWithCustomText(
            "알림", "내용", null, null
        );
        assertTrue(sections.get(0).getTitle().contains("📋"));
    }

    @Test
    public void severityIcon_lowercase() {
        List<MailSection> sections = MailSection.forAlarmWithCustomText(
            "경고", "내용", "warning", null
        );
        assertTrue(sections.get(0).getTitle().contains("⚠️"));
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
