package com.yoc.wms.mail.domain;

import com.yoc.wms.mail.enums.SectionType;
import com.yoc.wms.mail.exception.ValueChainException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 메일 발송 요청 DTO
 *
 *  @author 김찬기
 *  @since 1.0
 */
public class MailRequest {

    private final String subject;
    private final List<MailSection> sections;
    private final List<Recipient> recipients;
    private final List<Recipient> ccRecipients;
    private final String mailType;        // ALARM, DIRECT, REPORT 등
    private final String mailSource;      // 알람타입 또는 발송 소스

    private MailRequest(Builder builder) {
        this.subject = builder.subject;
        this.sections = builder.sections;
        this.recipients = builder.recipients;
        this.ccRecipients = builder.ccRecipients;
        this.mailType = builder.mailType != null ? builder.mailType : "DIRECT";
        this.mailSource = builder.mailSource;

        validate();
    }

    private void validate() {
        if (subject == null || subject.trim().isEmpty()) {
            throw new ValueChainException("Subject is required");
        }
        if (sections == null || sections.isEmpty()) {
            throw new ValueChainException("At least one section is required");
        }
        if (recipients == null || recipients.isEmpty()) {
            throw new ValueChainException("At least one recipient is required");
        }
    }

    // Getters
    public String getSubject() {
        return subject;
    }

    public List<MailSection> getSections() {
        return sections;
    }

    public List<Recipient> getRecipients() {
        return recipients;
    }

    public List<Recipient> getCcRecipients() {
        return ccRecipients;
    }

    public String getMailType() {
        return mailType;
    }

    public String getMailSource() {
        return mailSource;
    }

    public boolean hasCc() {
        return ccRecipients != null && !ccRecipients.isEmpty();
    }

    // ==================== Helper Methods (도메인 로직 캡슐화) ====================

    /**
     * 알람 Subject 생성 (Helper Method)
     *
     * 심각도와 건수를 포함한 표준화된 Subject를 생성합니다.
     *
     * Format:
     * - CRITICAL: "[긴급] WMS {title} {count}건"
     * - WARNING/INFO: "[경고] WMS {title} {count}건"
     *
     * Example:
     *   String subject = MailRequest.alarmSubject("재고 부족 알림", "CRITICAL", 5);
     *   // Result: "[긴급] WMS 재고 부족 알림 5건"
     *
     * @param title 알람 제목 (null 불가)
     * @param severity 심각도 (CRITICAL/WARNING/INFO, 대소문자 무관)
     * @param count 건수 (0 이상)
     * @return 표준화된 Subject
     * @since v2.0.0
     */
    public static String alarmSubject(String title, String severity, int count) {
        String prefix = "CRITICAL".equalsIgnoreCase(severity) ? "[긴급]" : "[경고]";
        return prefix + " WMS " + title + " " + count + "건";
    }

    /**
     * 알람 제목 생성 (Helper Method - 심각도 아이콘 포함)
     *
     * 심각도에 따른 아이콘을 제목 앞에 추가합니다.
     *
     * Severity Icons:
     * - CRITICAL: 🔴 (빨간 원)
     * - WARNING: ⚠️ (경고 표시)
     * - INFO: ℹ️ (정보 표시)
     *
     * Example:
     *   String title = MailRequest.alarmTitle("재고 부족 알림", "CRITICAL");
     *   // Result: "🔴 재고 부족 알림"
     *
     * @param title 알람 제목
     * @param severity 심각도 (CRITICAL/WARNING/INFO, 대소문자 무관)
     * @return 아이콘 + 제목
     * @since v2.0.0
     */
    public static String alarmTitle(String title, String severity) {
        return getSeverityIcon(severity) + " " + title;
    }

    /**
     * 공지 제목 생성 (아이콘 포함)
     *
     * @param title 제목
     * @return 아이콘 + 제목 (예: "📢 시스템 점검 안내")
     */
    public static String noticeTitle(String title) {
        return "📢 " + title;
    }

    /**
     * 보고서 제목 생성 (아이콘 포함)
     *
     * @param title 제목
     * @return 아이콘 + 제목 (예: "📊 월간 매출 보고서")
     */
    public static String reportTitle(String title) {
        return "📊 " + title;
    }

    /**
     * 심각도 아이콘 조회
     */
    private static String getSeverityIcon(String severity) {
        if ("CRITICAL".equalsIgnoreCase(severity)) return "🔴";
        if ("WARNING".equalsIgnoreCase(severity)) return "⚠️";
        return "ℹ️";
    }

    // ==================== Builder Pattern ====================

    /**
     * 범용 Builder
     */
    public static class Builder {
        private String subject;
        private List<MailSection> sections = new ArrayList<>();
        private List<Recipient> recipients = new ArrayList<>();
        private List<Recipient> ccRecipients = new ArrayList<>();
        private String mailType;
        private String mailSource;

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        // ==================== 범용 섹션 빌더 메서드 ====================

        /**
         * 텍스트 섹션 추가
         *
         * @param title 제목
         * @param content 내용
         * @return Builder
         */
        public Builder addTextSection(String title, String content) {
            this.sections.add(MailSection.builder()
                .type(SectionType.TEXT)
                .title(title)
                .content(content)
                .build());
            return this;
        }

        /**
         * 텍스트 섹션 추가 (제목 없음)
         *
         * @param content 내용
         * @return Builder
         */
        public Builder addTextSection(String content) {
            return addTextSection(null, content);
        }

        /**
         * 텍스트 섹션 추가 (메타데이터 포함)
         *
         * @param title 제목
         * @param content 내용
         * @param metadata 메타데이터 (fontSize, textAlign, color 등)
         * @return Builder
         */
        public Builder addTextSection(String title, String content, Map<String, Object> metadata) {
            this.sections.add(MailSection.builder()
                .type(SectionType.TEXT)
                .title(title)
                .content(content)
                .metadata(metadata)
                .build());
            return this;
        }

        /**
         * 테이블 섹션 추가
         *
         * @param data 테이블 데이터
         * @return Builder
         */
        public Builder addTableSection(List<Map<String, String>> data) {
            this.sections.add(MailSection.builder()
                .type(SectionType.TABLE)
                .data(data)
                .build());
            return this;
        }

        /**
         * 테이블 섹션 추가 (제목 포함)
         *
         * @param title 제목
         * @param data 테이블 데이터
         * @return Builder
         */
        public Builder addTableSection(String title, List<Map<String, String>> data) {
            this.sections.add(MailSection.builder()
                .type(SectionType.TABLE)
                .title(title)
                .data(data)
                .build());
            return this;
        }

        /**
         * 테이블 섹션 추가 (메타데이터 포함)
         *
         * @param title 제목
         * @param data 테이블 데이터
         * @param metadata 메타데이터 (striped, bordered, headerColor 등)
         * @return Builder
         */
        public Builder addTableSection(String title, List<Map<String, String>> data,
                                        Map<String, Object> metadata) {
            this.sections.add(MailSection.builder()
                .type(SectionType.TABLE)
                .title(title)
                .data(data)
                .metadata(metadata)
                .build());
            return this;
        }

        /**
         * HTML 섹션 추가
         *
         * @param htmlContent HTML 내용
         * @return Builder
         */
        public Builder addHtmlSection(String htmlContent) {
            this.sections.add(MailSection.builder()
                .type(SectionType.HTML)
                .content(htmlContent)
                .build());
            return this;
        }

        /**
         * HTML 섹션 추가 (제목 포함)
         *
         * @param title 제목
         * @param htmlContent HTML 내용
         * @return Builder
         */
        public Builder addHtmlSection(String title, String htmlContent) {
            this.sections.add(MailSection.builder()
                .type(SectionType.HTML)
                .title(title)
                .content(htmlContent)
                .build());
            return this;
        }

        /**
         * 구분선 추가
         *
         * @return Builder
         */
        public Builder addDivider() {
            this.sections.add(MailSection.builder()
                .type(SectionType.DIVIDER)
                .build());
            return this;
        }

        /**
         * 구분선 추가 (메타데이터 포함)
         *
         * @param metadata 메타데이터 (height, color, margin 등)
         * @return Builder
         */
        public Builder addDivider(Map<String, Object> metadata) {
            this.sections.add(MailSection.builder()
                .type(SectionType.DIVIDER)
                .metadata(metadata)
                .build());
            return this;
        }

        // ==================== 수신인 메서드 ====================

        public Builder recipients(List<Recipient> recipients) {
            this.recipients = recipients != null ? recipients : new ArrayList<>();
            return this;
        }

        public Builder addRecipient(Recipient recipient) {
            this.recipients.add(recipient);
            return this;
        }

        public Builder ccRecipients(List<Recipient> ccRecipients) {
            this.ccRecipients = ccRecipients != null ? ccRecipients : new ArrayList<>();
            return this;
        }

        public Builder addCcRecipient(Recipient recipient) {
            this.ccRecipients.add(recipient);
            return this;
        }

        // ==================== 메타 정보 메서드 ====================

        public Builder mailType(String mailType) {
            this.mailType = mailType;
            return this;
        }

        public Builder mailSource(String mailSource) {
            this.mailSource = mailSource;
            return this;
        }

        public MailRequest build() {
            return new MailRequest(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}