package com.yoc.wms.mail.domain;

import com.yoc.wms.mail.enums.SectionType;

import java.util.*;

/**
 * 메일 섹션 도메인
 *
 * 설계:
 * - Factory Pattern을 통해 일관된 섹션 구성 제공
 * - 모든 타입(TABLE, TEXT, HTML, DIVIDER)을 단일 클래스로 표현
 * - type에 따라 사용하는 필드가 다름
 * - 검증은 생성 시 수행
 */
public class MailSection {

    private final SectionType type;
    private final String title;
    private final Map<String, Object> metadata;
    private final String content;              // TEXT, HTML용
    private final List<Map<String, String>> data;  // TABLE용

    private MailSection(Builder builder) {
        this.type = builder.type;
        this.title = builder.title;
        this.metadata = builder.metadata != null ? builder.metadata : new HashMap<String, Object>();
        this.content = builder.content;
        this.data = builder.data;

        validate();
    }

    /**
     * 생성 시 검증
     */
    private void validate() {
        if (type == null) {
            throw new IllegalArgumentException("Section type is required");
        }

        switch (type) {
            case TABLE:
                if (data == null || data.isEmpty()) {
                    throw new IllegalArgumentException("TABLE type requires data");
                }
                break;
            case TEXT:
            case HTML:
                if (content == null || content.trim().isEmpty()) {
                    throw new IllegalArgumentException(type + " type requires content");
                }
                if (type == SectionType.HTML && containsUnsafeHtml(content)) {
                    throw new IllegalArgumentException("HTML content contains unsafe elements");
                }
                break;
            case DIVIDER:
                // 검증 불필요
                break;
        }
    }

    /**
     * 기본적인 XSS 방어 검증
     */
    private boolean containsUnsafeHtml(String html) {
        String lowerHtml = html.toLowerCase();
        return lowerHtml.contains("<script") ||
                lowerHtml.contains("javascript:") ||
                lowerHtml.contains("onerror=") ||
                lowerHtml.contains("onload=");
    }

    // Getters
    public SectionType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getContent() {
        return content;
    }

    public List<Map<String, String>> getData() {
        return data;
    }

    // 편의 메서드
    public boolean hasTitle() {
        return title != null && !title.trim().isEmpty();
    }

    /**
     * 메타데이터 조회 (기본값 포함)
     */
    public String getMetadataOrDefault(String key, String defaultValue) {
        if (metadata == null || !metadata.containsKey(key)) {
            return defaultValue;
        }
        Object value = metadata.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    public boolean hasMetadata(String key) {
        return metadata != null && metadata.containsKey(key);
    }

    // ==================== Static Factory Methods ====================

    /**
     * 알람 메일용 섹션 세트 생성 (QUEUE에서 받은 커스텀 텍스트 사용)
     *
     * @param sectionTitle QUEUE의 SECTION_TITLE (예: "지연 주문 현황")
     * @param sectionContent QUEUE의 SECTION_CONTENT (본문 텍스트)
     * @param severity 심각도 (INFO, WARNING, CRITICAL) - 아이콘 결정
     * @param tableData 테이블 데이터 (SQL_ID 조회 결과)
     * @return [TEXT(아이콘+제목+본문), TABLE(데이터), DIVIDER] 섹션 리스트
     */
    public static List<MailSection> forAlarmWithCustomText(String sectionTitle,
                                                           String sectionContent,
                                                           String severity,
                                                           List<Map<String, String>> tableData) {
        List<MailSection> sections = new ArrayList<>();

        // 심각도 아이콘
        String icon = getSeverityIcon(severity);

        // 제목 섹션 (아이콘 + Oracle Procedure가 작성한 텍스트)
        sections.add(builder()
                .type(SectionType.TEXT)
                .title(icon + " " + sectionTitle)
                .content(sectionContent)
                .build());

        // 테이블 섹션
        if (tableData != null && !tableData.isEmpty()) {
            sections.add(builder()
                    .type(SectionType.TABLE)
                    .data(tableData)
                    .build());
        }

        // 구분선
        sections.add(builder()
                .type(SectionType.DIVIDER)
                .build());

        return sections;
    }

    /**
     * 공지 메일용 섹션 세트 생성
     *
     * @param title 공지 제목
     * @param content 공지 내용
     * @return [TEXT(제목+내용), DIVIDER] 섹션 리스트
     */
    public static List<MailSection> forNotice(String title, String content) {
        List<MailSection> sections = new ArrayList<>();

        sections.add(builder()
                .type(SectionType.TEXT)
                .title(title)
                .content(content)
                .build());

        sections.add(builder()
                .type(SectionType.DIVIDER)
                .build());

        return sections;
    }

    /**
     * 보고서 메일용 섹션 세트 생성
     *
     * @param reportTitle 보고서 제목
     * @param description 보고서 설명
     * @param tableData 테이블 데이터
     * @return [TEXT(설명), TABLE(데이터), DIVIDER] 섹션 리스트
     */
    public static List<MailSection> forReport(String reportTitle, String description,
                                              List<Map<String, String>> tableData) {
        List<MailSection> sections = new ArrayList<>();

        // 설명 섹션
        sections.add(builder()
                .type(SectionType.TEXT)
                .title(reportTitle)
                .content(description)
                .build());

        // 테이블 섹션
        if (tableData != null && !tableData.isEmpty()) {
            sections.add(builder()
                    .type(SectionType.TABLE)
                    .data(tableData)
                    .build());
        }

        // 구분선
        sections.add(builder()
                .type(SectionType.DIVIDER)
                .build());

        return sections;
    }

    /**
     * 단순 텍스트 섹션 생성
     *
     * @param title 제목
     * @param content 내용
     * @return TEXT 섹션 단일 리스트
     */
    public static List<MailSection> forSimpleText(String title, String content) {
        List<MailSection> sections = new ArrayList<>();

        sections.add(builder()
                .type(SectionType.TEXT)
                .title(title)
                .content(content)
                .build());

        return sections;
    }

    /**
     * 심각도 아이콘 반환
     */
    private static String getSeverityIcon(String severity) {
        if (severity == null) {
            return "📋";
        }

        switch (severity.toUpperCase()) {
            case "CRITICAL":
                return "🚨";
            case "WARNING":
                return "⚠️";
            case "INFO":
                return "ℹ️";
            default:
                return "📋";
        }
    }

    /**
     * 연락처 섹션 생성
     *
     * @param contactInfo 연락처 정보
     * @return 연락처 섹션
     */
    public static List<MailSection> forContact(String contactInfo) {
        List<MailSection> sections = Arrays.asList(
                MailSection.builder()
                        .type(SectionType.DIVIDER)
                        .build(),
                MailSection.builder()
                        .type(SectionType.TEXT)
                        .title("📞 문의")
                        .content(contactInfo)
                        .build()
        );

        return sections;
    }

    // ==================== Builder Pattern ====================

    /**
     * Builder Pattern
     */
    public static class Builder {
        private SectionType type;
        private String title;
        private Map<String, Object> metadata;
        private String content;
        private List<Map<String, String>> data;

        public Builder type(SectionType type) {
            this.type = type;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder data(List<Map<String, String>> data) {
            this.data = data;
            return this;
        }

        public MailSection build() {
            return new MailSection(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}