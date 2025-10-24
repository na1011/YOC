package com.yoc.wms.mail.renderer;

import com.yoc.wms.mail.config.MailConfig;
import com.yoc.wms.mail.exception.ValueChainException;
import com.yoc.wms.mail.domain.MailSection;
import com.yoc.wms.mail.enums.SectionType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.*;

import static org.junit.Assert.*;
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
@RunWith(MockitoJUnitRunner.class)
public class MailBodyRendererTest {

    @Mock
    private MailConfig mailConfig;

    @InjectMocks
    private MailBodyRenderer renderer;

    @Before
    public void setUp() {
        // MailConfig 기본값 설정
        when(mailConfig.getSectionMargin()).thenReturn("margin: 16px 0;");
        when(mailConfig.getTitleStyle()).thenReturn("font-size: 18px; font-weight: bold; margin-bottom: 8px;");
        when(mailConfig.getDefaultFontStyle()).thenReturn("font-family: Arial, sans-serif;");
        when(mailConfig.getDefaultFontSize()).thenReturn("14px");
        when(mailConfig.getDefaultPadding()).thenReturn("8px");
        when(mailConfig.getTableBorderColor()).thenReturn("#ddd");
        when(mailConfig.getTableHeaderBgColor()).thenReturn("#f5f5f5");
        when(mailConfig.getTableHeaderTextColor()).thenReturn("#333");
        when(mailConfig.getTableStripedBgColor()).thenReturn("#fafafa");
        when(mailConfig.getDividerHeight()).thenReturn("1px");
        when(mailConfig.getDividerColor()).thenReturn("#ddd");
        when(mailConfig.getDividerMargin()).thenReturn("16px 0");
    }

    // ==================== renderWithStructure() 테스트 ====================

    @Test
    public void renderWithStructure_fullDocument() {
        // Given
        List<MailSection> sections = Collections.singletonList(
            MailSection.builder().type(SectionType.TEXT).content("테스트 내용").build()
        );

        // When
        String html = renderer.renderWithStructure(sections, "WMS 시스템 알림", "자동 발송 메일입니다.");

        // Then
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("<html>"));
        assertTrue(html.contains("<head><meta charset='UTF-8'></head>"));
        assertTrue(html.contains("<body"));
        assertTrue(html.contains("WMS 시스템 알림"));
        assertTrue(html.contains("테스트 내용"));
        assertTrue(html.contains("자동 발송 메일입니다."));
        assertTrue(html.contains("</body>"));
        assertTrue(html.contains("</html>"));
    }

    @Test
    public void renderWithStructure_escapeSystemTitle() {
        // Given
        List<MailSection> sections = Collections.singletonList(
            MailSection.builder().type(SectionType.TEXT).content("내용").build()
        );

        // When
        String html = renderer.renderWithStructure(sections, "<script>alert('xss')</script>", "Footer");

        // Then
        assertTrue(html.contains("&lt;script&gt;"));
        assertFalse(html.contains("<script>alert('xss')</script>"));
    }

    @Test
    
    public void renderWithStructure_escapeFooterMessage() {
        // Given
        List<MailSection> sections = Collections.singletonList(
            MailSection.builder().type(SectionType.TEXT).content("내용").build()
        );

        // When
        String html = renderer.renderWithStructure(sections, "Title", "<b>Bold</b> & Text");

        // Then
        assertTrue(html.contains("&lt;b&gt;Bold&lt;/b&gt; &amp; Text"));
        assertFalse(html.contains("<b>Bold</b>"));
    }

    @Test
    
    public void renderWithStructure_multipleSections() {
        // Given
        List<MailSection> sections = Arrays.asList(
            MailSection.builder().type(SectionType.TEXT).content("A").build(),
            MailSection.builder().type(SectionType.DIVIDER).build(),
            MailSection.builder().type(SectionType.TEXT).content("B").build()
        );

        // When
        String html = renderer.renderWithStructure(sections, "Title", "Footer");

        // Then
        assertTrue(html.contains("A"));
        assertTrue(html.contains("B"));
        assertTrue(html.contains("<hr"));
    }

    @Test
    
    public void renderWithStructure_emptySections() {
        // When
        String html = renderer.renderWithStructure(Collections.emptyList(), "Title", "Footer");

        // Then
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("Title"));
        assertTrue(html.contains("Footer"));
    }

    // ==================== render() 통합 테스트 ====================

    @Test
    
    public void render_multipleSections() {
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
    
    public void render_nullSections() {
        // When
        String html = renderer.render(null);

        // Then
        assertEquals("", html);
    }

    @Test
    
    public void render_emptySections() {
        // When
        String html = renderer.render(Collections.emptyList());

        // Then
        assertEquals("", html);
    }

    // ==================== renderText() 테스트 ====================

    @Test
    
    public void renderText_withTitle() {
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
    
    public void renderText_withoutTitle() {
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
    
    public void renderText_newlineConversion() {
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
    
    public void renderText_withMetadata() {
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
    
    public void renderTable_basic() {
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
    
    public void renderTable_withTitle() {
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
    
    public void renderTable_noBorder() {
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
    
    public void renderTable_striped() {
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
    
    public void renderTable_customHeaderColors() {
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

    @Test(expected = ValueChainException.class)
    public void renderTable_emptyData() {
        // When & Then
        // validate()에서 예외 발생 (TABLE type requires data)
        MailSection.builder()
            .type(SectionType.TABLE)
            .data(Collections.<Map<String, String>>emptyList())
            .build();
    }

    // ==================== renderHtml() 테스트 ====================

    @Test
    
    public void renderHtml_rawHtml() {
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
    
    public void renderHtml_withTitle() {
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
    
    public void renderDivider_basic() {
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
    
    public void renderDivider_customStyle() {
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
    
    public void escapeHtml_specialCharacters() {
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
    
    public void escapeHtml_allSpecialChars() {
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
    
    public void escapeHtml_inTable() {
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
    
    public void escapeHtml_null() {
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
    
    public void unknownSectionType() {
        // Given - 리플렉션으로 강제 생성 (실제로는 불가능)
        // 현재 코드는 enum으로 모든 타입이 정의되어 있어 테스트 불가
        // switch default 분기는 사실상 도달 불가능
    }

    @Test
    
    public void table_singleRow() {
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
    
    public void table_multipleColumns() {
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
    public void text_veryLong() {
        // Given
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("A");
        }
        String longText = sb.toString();
        MailSection section = MailSection.builder()
            .type(SectionType.TEXT)
            .content(longText)
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then
        assertTrue(html.length() > 10000);
    }

    // ==================== columnOrder 메타데이터 테스트 (v2.5.0) ====================

    @Test
    public void table_withColumnOrder() {
        // Given - 특정 순서로 컬럼 렌더링
        Map<String, String> row = new LinkedHashMap<>();
        row.put("ORDER_ID", "ORD-001");
        row.put("CUSTOMER", "홍길동");
        row.put("ORDER_DATE", "2025-01-15");
        row.put("DAYS_OVERDUE", "5");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("columnOrder", "ORDER_ID,CUSTOMER,ORDER_DATE,DAYS_OVERDUE");

        MailSection section = MailSection.builder()
            .type(SectionType.TABLE)
            .data(Collections.singletonList(row))
            .metadata(metadata)
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then - 컬럼이 지정된 순서대로 렌더링되어야 함
        assertTrue(html.contains("ORDER_ID"));
        assertTrue(html.contains("CUSTOMER"));
        assertTrue(html.contains("ORDER_DATE"));
        assertTrue(html.contains("DAYS_OVERDUE"));

        // 순서 확인: ORDER_ID가 CUSTOMER보다 먼저 나와야 함
        int orderIdPos = html.indexOf("ORDER_ID");
        int customerPos = html.indexOf("CUSTOMER");
        int orderDatePos = html.indexOf("ORDER_DATE");
        int daysOverduePos = html.indexOf("DAYS_OVERDUE");

        assertTrue(orderIdPos < customerPos);
        assertTrue(customerPos < orderDatePos);
        assertTrue(orderDatePos < daysOverduePos);
    }

    @Test
    public void table_withColumnOrder_partialColumns() {
        // Given - columnOrder에 일부 컬럼만 지정 (나머지는 무시됨)
        Map<String, String> row = new LinkedHashMap<>();
        row.put("col1", "A");
        row.put("col2", "B");
        row.put("col3", "C");
        row.put("col4", "D");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("columnOrder", "col2,col4");  // col1, col3는 제외

        MailSection section = MailSection.builder()
            .type(SectionType.TABLE)
            .data(Collections.singletonList(row))
            .metadata(metadata)
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then - col2와 col4만 렌더링되어야 함
        assertTrue(html.contains("col2"));
        assertTrue(html.contains("col4"));
        assertTrue(html.contains(">B<"));
        assertTrue(html.contains(">D<"));

        // col1과 col3는 렌더링되지 않음 (순서에 포함되지 않음)
        assertFalse(html.contains("col1"));
        assertFalse(html.contains("col3"));
    }

    @Test
    public void table_withColumnOrder_nonExistentColumns() {
        // Given - columnOrder에 존재하지 않는 컬럼 지정
        Map<String, String> row = new LinkedHashMap<>();
        row.put("col1", "A");
        row.put("col2", "B");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("columnOrder", "col1,nonExistent,col2");

        MailSection section = MailSection.builder()
            .type(SectionType.TABLE)
            .data(Collections.singletonList(row))
            .metadata(metadata)
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then - 존재하는 컬럼만 렌더링
        assertTrue(html.contains("col1"));
        assertTrue(html.contains("col2"));
        assertFalse(html.contains("nonExistent"));
    }

    @Test
    public void table_withColumnOrder_emptyString() {
        // Given - columnOrder가 빈 문자열
        Map<String, String> row = new LinkedHashMap<>();
        row.put("col1", "A");
        row.put("col2", "B");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("columnOrder", "   ");  // 빈 문자열

        MailSection section = MailSection.builder()
            .type(SectionType.TABLE)
            .data(Collections.singletonList(row))
            .metadata(metadata)
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then - 빈 문자열은 무시되고 기존 keySet() 순서로 렌더링
        assertTrue(html.contains("col1"));
        assertTrue(html.contains("col2"));
    }

    @Test
    public void table_withColumnOrder_null() {
        // Given - columnOrder가 null (기본 동작)
        Map<String, String> row = new LinkedHashMap<>();
        row.put("col1", "A");
        row.put("col2", "B");

        MailSection section = MailSection.builder()
            .type(SectionType.TABLE)
            .data(Collections.singletonList(row))
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then - 기존 keySet() 순서로 렌더링
        assertTrue(html.contains("col1"));
        assertTrue(html.contains("col2"));
    }

    @Test
    public void table_withColumnOrder_withWhitespace() {
        // Given - columnOrder에 공백 포함
        Map<String, String> row = new LinkedHashMap<>();
        row.put("col1", "A");
        row.put("col2", "B");
        row.put("col3", "C");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("columnOrder", "col1 , col2 , col3");  // 공백 포함

        MailSection section = MailSection.builder()
            .type(SectionType.TABLE)
            .data(Collections.singletonList(row))
            .metadata(metadata)
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then - trim되어 정상 렌더링
        assertTrue(html.contains("col1"));
        assertTrue(html.contains("col2"));
        assertTrue(html.contains("col3"));

        // 순서 확인
        int col1Pos = html.indexOf("col1");
        int col2Pos = html.indexOf("col2");
        int col3Pos = html.indexOf("col3");

        assertTrue(col1Pos < col2Pos);
        assertTrue(col2Pos < col3Pos);
    }

    @Test
    public void table_withColumnOrder_multipleRows() {
        // Given - 여러 행에 columnOrder 적용
        Map<String, String> row1 = new LinkedHashMap<>();
        row1.put("ORDER_ID", "ORD-001");
        row1.put("CUSTOMER", "홍길동");
        row1.put("amount", "10000");

        Map<String, String> row2 = new LinkedHashMap<>();
        row2.put("ORDER_ID", "ORD-002");
        row2.put("CUSTOMER", "김철수");
        row2.put("amount", "20000");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("columnOrder", "CUSTOMER,ORDER_ID,amount");  // 순서 변경

        MailSection section = MailSection.builder()
            .type(SectionType.TABLE)
            .data(Arrays.asList(row1, row2))
            .metadata(metadata)
            .build();

        // When
        String html = renderer.render(Collections.singletonList(section));

        // Then - 모든 행이 같은 컬럼 순서로 렌더링
        assertTrue(html.contains("홍길동"));
        assertTrue(html.contains("김철수"));

        // 헤더 순서 확인: CUSTOMER가 ORDER_ID보다 먼저
        int customerHeaderPos = html.indexOf("CUSTOMER");
        int orderIdHeaderPos = html.indexOf("ORDER_ID");
        assertTrue(customerHeaderPos < orderIdHeaderPos);
    }

    // ==================== forContact() Factory Method 테스트 ====================

    @Test
    
    public void forContact_sectionStructure() {
        // Given
        String contactInfo = "IT: it@company.com";

        // When
        List<MailSection> sections = MailSection.forContact(contactInfo);

        // Then
        assertNotNull(sections);
        assertEquals(2, sections.size());
        assertEquals(SectionType.DIVIDER, sections.get(0).getType());
        assertEquals(SectionType.TEXT, sections.get(1).getType());
        assertEquals("📞 문의", sections.get(1).getTitle());
        assertEquals(contactInfo, sections.get(1).getContent());
    }

    @Test
    
    public void forContact_htmlRendering() {
        // Given
        String contactInfo = "IT: it@company.com";
        List<MailSection> sections = MailSection.forContact(contactInfo);

        // When
        String html = renderer.render(sections);

        // Then
        assertTrue(html.contains("<hr")); // 구분선
        assertTrue(html.contains("📞 문의")); // 제목
        assertTrue(html.contains("IT: it@company.com")); // 내용
    }

    @Test
    
    public void forContact_multipleContacts() {
        // Given
        String contactInfo = "IT: it@company.com\nHR: hr@company.com\n법무: legal@company.com";
        List<MailSection> sections = MailSection.forContact(contactInfo);

        // When
        String html = renderer.render(sections);

        // Then
        assertTrue(html.contains("📞 문의"));
        assertTrue(html.contains("IT: it@company.com"));
        assertTrue(html.contains("HR: hr@company.com"));
        assertTrue(html.contains("법무: legal@company.com"));

        // 줄바꿈이 <br>로 변환되었는지 확인
        int brCount = countOccurrences(html, "<br>");
        assertEquals(2, brCount); // 3줄이므로 줄바꿈 2개
    }

    @Test
    
    public void forContact_singleContact() {
        // Given
        String contactInfo = "IT Support: support@company.com";
        List<MailSection> sections = MailSection.forContact(contactInfo);

        // When
        String html = renderer.render(sections);

        // Then
        assertTrue(html.contains("📞 문의"));
        assertTrue(html.contains("IT Support: support@company.com"));
        assertFalse(html.contains("<br>")); // 줄바꿈 없음
    }

    @Test
    
    public void forContact_htmlEscape() {
        // Given
        String contactInfo = "IT: <admin@company.com> & Support";
        List<MailSection> sections = MailSection.forContact(contactInfo);

        // When
        String html = renderer.render(sections);

        // Then
        assertTrue(html.contains("&lt;admin@company.com&gt;"));
        assertTrue(html.contains("&amp; Support"));
        assertFalse(html.contains("<admin@company.com>")); // 이스케이프되어야 함
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