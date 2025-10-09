package com.yoc.wms.mail.renderer;

import com.yoc.wms.mail.config.MailConfig;
import com.yoc.wms.mail.domain.MailSection;
import com.yoc.wms.mail.enums.SectionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MailBodyRenderer 단위 테스트
 *
 * 테스트 범위:
 * - 각 SectionType별 렌더링 결과 검증
 * - 메타데이터 적용 확인
 * - HTML 이스케이프 검증
 * - 엣지케이스 (null, empty)
 */
@ExtendWith(MockitoExtension.class)
class MailBodyRendererTest {

    @Mock
    private MailConfig mailConfig;

    @InjectMocks
    private MailBodyRenderer renderer;

    @BeforeEach
    void setUp() {
        // MailConfig 기본값 설정 (lenient)
        lenient().when(mailConfig.getSectionMargin()).thenReturn("margin: 16px 0;");
        lenient().when(mailConfig.getTitleStyle()).thenReturn("font-size: 18px; font-weight: bold; margin-bottom: 8px;");
        lenient().when(mailConfig.getDefaultFontStyle()).thenReturn("font-family: Arial, sans-serif;");
        lenient().when(mailConfig.getDefaultFontSize()).thenReturn("14px");
        lenient().when(mailConfig.getDefaultPadding()).thenReturn("8px");
        lenient().when(mailConfig.getTableBorderColor()).thenReturn("#ddd");
        lenient().when(mailConfig.getTableHeaderBgColor()).thenReturn("#f5f5f5");
        lenient().when(mailConfig.getTableHeaderTextColor()).thenReturn("#333");
        lenient().when(mailConfig.getTableStripedBgColor()).thenReturn("#fafafa");
        lenient().when(mailConfig.getDividerHeight()).thenReturn("1px");
        lenient().when(mailConfig.getDividerColor()).thenReturn("#ddd");
        lenient().when(mailConfig.getDividerMargin()).thenReturn("16px 0");
    }

    // ==================== render() 통합 테스트 ====================

    @Test
    @DisplayName("render: 여러 섹션 렌더링")
    void render_multipleSections() {
        // Given
        List<MailSection> sections = Arrays.asList(
            MailSection.builder().type(SectionType.TEXT).content("A").build(),
            MailSection.builder().type(SectionType.DIVIDER).build(),
            MailSection.builder().type(SectionType.TEXT).content("B").build()
        );

        // When
        String html = renderer.render(sections);

        // Then
        assertNotNull(html);
        assertTrue(html.contains("A"));
        assertTrue(html.contains("B"));
        assertTrue(html.contains("<hr"));
    }

    @Test
    @DisplayName("render: null 섹션 - 빈 문자열 반환")
    void render_nullSections() {
        // When
        String html = renderer.render(null);

        // Then
        assertEquals("", html);
    }

    @Test
    @DisplayName("render: 빈 섹션 리스트 - 빈 문자열 반환")
    void render_emptySections() {
        // When
        String html = renderer.render(Collections.emptyList());

        // Then
        assertEquals("", html);
    }

    // ==================== renderText() 테스트 ====================

    @Test
    @DisplayName("renderText: 제목 + 내용")
    void renderText_withTitle() {
        // Given
        MailSection section = MailSection.builder()
            .type(SectionType.TEXT)
            .title("제목")
            .content("내용입니다.")
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertTrue(html.contains("제목"));
        assertTrue(html.contains("내용입니다."));
        assertTrue(html.contains("<div"));
    }

    @Test
    @DisplayName("renderText: 제목 없음")
    void renderText_withoutTitle() {
        // Given
        MailSection section = MailSection.builder()
            .type(SectionType.TEXT)
            .content("내용만 있음")
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertTrue(html.contains("내용만 있음"));
    }

    @Test
    @DisplayName("renderText: 줄바꿈 변환 (\\n → <br>)")
    void renderText_newlineConversion() {
        // Given
        MailSection section = MailSection.builder()
            .type(SectionType.TEXT)
            .content("첫 줄\n두 번째 줄\n세 번째 줄")
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertTrue(html.contains("<br>"));
        assertEquals(2, countOccurrences(html, "<br>"));
    }

    @Test
    @DisplayName("renderText: 메타데이터 적용 (fontSize, textAlign)")
    void renderText_withMetadata() {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fontSize", "20px");
        metadata.put("textAlign", "center");
        metadata.put("fontWeight", "bold");

        MailSection section = MailSection.builder()
            .type(SectionType.TEXT)
            .content("메타데이터 적용")
            .metadata(metadata)
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertTrue(html.contains("font-size: 20px"));
        assertTrue(html.contains("text-align: center"));
        assertTrue(html.contains("font-weight: bold"));
    }

    // ==================== renderTable() 테스트 ====================

    @Test
    @DisplayName("renderTable: 기본 테이블 렌더링")
    void renderTable_basic() {
        // Given
        List<Map<String, String>> data = Arrays.asList(
            createMap("name", "홍길동", "age", "30"),
            createMap("name", "김철수", "age", "25")
        );
        MailSection section = MailSection.builder()
            .type(SectionType.TABLE)
            .data(data)
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertTrue(html.contains("<table"));
        assertTrue(html.contains("<thead>"));
        assertTrue(html.contains("<tbody>"));
        assertTrue(html.contains("홍길동"));
        assertTrue(html.contains("김철수"));
        assertTrue(html.contains("name"));
        assertTrue(html.contains("age"));
    }

    @Test
    @DisplayName("renderTable: 제목 포함")
    void renderTable_withTitle() {
        // Given
        List<Map<String, String>> data = Collections.singletonList(
            createMap("id", "1")
        );
        MailSection section = MailSection.builder()
            .type(SectionType.TABLE)
            .title("사용자 목록")
            .data(data)
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertTrue(html.contains("사용자 목록"));
    }

    @Test
    @DisplayName("renderTable: bordered=false - 테두리 없음")
    void renderTable_noBorder() {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("bordered", "false");

        List<Map<String, String>> data = Collections.singletonList(
            createMap("col", "val")
        );
        MailSection section = MailSection.builder()
            .type(SectionType.TABLE)
            .data(data)
            .metadata(metadata)
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertFalse(html.contains("border: 1px solid"));
    }

    @Test
    @DisplayName("renderTable: striped=true - 줄무늬 배경")
    void renderTable_striped() {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("striped", "true");

        List<Map<String, String>> data = Arrays.asList(
            createMap("id", "1"),
            createMap("id", "2"),
            createMap("id", "3")
        );
        MailSection section = MailSection.builder()
            .type(SectionType.TABLE)
            .data(data)
            .metadata(metadata)
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertTrue(html.contains(mailConfig.getTableStripedBgColor()));
    }

    @Test
    @DisplayName("renderTable: 헤더 색상 커스터마이징")
    void renderTable_customHeaderColors() {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("headerBgColor", "#ff0000");
        metadata.put("headerTextColor", "#ffffff");

        List<Map<String, String>> data = Collections.singletonList(
            createMap("col", "val")
        );
        MailSection section = MailSection.builder()
            .type(SectionType.TABLE)
            .data(data)
            .metadata(metadata)
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertTrue(html.contains("background-color: #ff0000"));
        assertTrue(html.contains("color: #ffffff"));
    }

    @Test
    @DisplayName("renderTable: 빈 데이터 - 빌드 시 예외 발생")
    void renderTable_emptyData() {
        // When & Then
        // validate()에서 예외 발생 (TABLE type requires data)
        assertThrows(IllegalArgumentException.class, () ->
            MailSection.builder()
                .type(SectionType.TABLE)
                .data(Collections.emptyList())
                .build()
        );
    }

    // ==================== renderHtml() 테스트 ====================

    @Test
    @DisplayName("renderHtml: HTML 그대로 출력")
    void renderHtml_rawHtml() {
        // Given
        MailSection section = MailSection.builder()
            .type(SectionType.HTML)
            .content("<p><strong>Bold Text</strong></p>")
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertTrue(html.contains("<p><strong>Bold Text</strong></p>"));
    }

    @Test
    @DisplayName("renderHtml: 제목 포함")
    void renderHtml_withTitle() {
        // Given
        MailSection section = MailSection.builder()
            .type(SectionType.HTML)
            .title("HTML 섹션")
            .content("<div>내용</div>")
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertTrue(html.contains("HTML 섹션"));
        assertTrue(html.contains("<div>내용</div>"));
    }

    // ==================== renderDivider() 테스트 ====================

    @Test
    @DisplayName("renderDivider: 기본 구분선")
    void renderDivider_basic() {
        // Given
        MailSection section = MailSection.builder()
            .type(SectionType.DIVIDER)
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertTrue(html.contains("<hr"));
        assertTrue(html.contains("border-top: 1px solid #ddd"));
    }

    @Test
    @DisplayName("renderDivider: 메타데이터로 스타일 커스터마이징")
    void renderDivider_customStyle() {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("height", "3px");
        metadata.put("color", "#ff0000");
        metadata.put("margin", "32px 0");

        MailSection section = MailSection.builder()
            .type(SectionType.DIVIDER)
            .metadata(metadata)
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertTrue(html.contains("border-top: 3px solid #ff0000"));
        assertTrue(html.contains("margin: 32px 0"));
    }

    // ==================== HTML 이스케이프 테스트 ====================

    @Test
    @DisplayName("HTML 이스케이프: 특수문자 변환")
    void escapeHtml_specialCharacters() {
        // Given
        MailSection section = MailSection.builder()
            .type(SectionType.TEXT)
            .content("<script>alert('xss')</script>")
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertTrue(html.contains("&lt;script&gt;"));
        assertFalse(html.contains("<script>"));
    }

    @Test
    @DisplayName("HTML 이스케이프: &, <, >, \", ' 변환")
    void escapeHtml_allSpecialChars() {
        // Given
        MailSection section = MailSection.builder()
            .type(SectionType.TEXT)
            .content("A & B < C > D \"quote\" 'apostrophe'")
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertTrue(html.contains("&amp;"));
        assertTrue(html.contains("&lt;"));
        assertTrue(html.contains("&gt;"));
        assertTrue(html.contains("&quot;"));
        assertTrue(html.contains("&#x27;"));
    }

    @Test
    @DisplayName("HTML 이스케이프: 테이블 헤더/데이터")
    void escapeHtml_inTable() {
        // Given
        List<Map<String, String>> data = Collections.singletonList(
            createMap("<name>", "<value>")
        );
        MailSection section = MailSection.builder()
            .type(SectionType.TABLE)
            .data(data)
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertTrue(html.contains("&lt;name&gt;"));
        assertTrue(html.contains("&lt;value&gt;"));
    }

    @Test
    @DisplayName("HTML 이스케이프: null 안전 처리")
    void escapeHtml_null() {
        // Given
        MailSection section = MailSection.builder()
            .type(SectionType.TEXT)
            .title(null)
            .content("내용")
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertNotNull(html);
        assertTrue(html.contains("내용"));
    }

    // ==================== 엣지케이스 테스트 ====================

    @Test
    @DisplayName("엣지케이스: 알 수 없는 SectionType")
    void unknownSectionType() {
        // Given - 리플렉션으로 강제 생성 (실제로는 불가능)
        // 현재 코드는 enum으로 모든 타입이 정의되어 있어 테스트 불가
        // switch default 분기는 사실상 도달 불가능
    }

    @Test
    @DisplayName("엣지케이스: 테이블 단일 행")
    void table_singleRow() {
        // Given
        List<Map<String, String>> data = Collections.singletonList(
            createMap("key", "value")
        );
        MailSection section = MailSection.builder()
            .type(SectionType.TABLE)
            .data(data)
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertTrue(html.contains("key"));
        assertTrue(html.contains("value"));
    }

    @Test
    @DisplayName("엣지케이스: 테이블 다수 컬럼")
    void table_multipleColumns() {
        // Given
        Map<String, String> row = new LinkedHashMap<>();
        row.put("col1", "v1");
        row.put("col2", "v2");
        row.put("col3", "v3");
        row.put("col4", "v4");
        row.put("col5", "v5");

        MailSection section = MailSection.builder()
            .type(SectionType.TABLE)
            .data(Collections.singletonList(row))
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertTrue(countOccurrences(html, "<th") >= 5);
    }

    @Test
    @DisplayName("엣지케이스: 매우 긴 텍스트")
    void text_veryLong() {
        // Given
        String longText = "A".repeat(10000);
        MailSection section = MailSection.builder()
            .type(SectionType.TEXT)
            .content(longText)
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertTrue(html.length() > 10000);
    }

    // ==================== Helper Methods ====================

    private Map<String, String> createMap(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>(); // 순서 유지
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private int countOccurrences(String str, String substr) {
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(substr, index)) != -1) {
            count++;
            index += substr.length();
        }
        return count;
    }
}