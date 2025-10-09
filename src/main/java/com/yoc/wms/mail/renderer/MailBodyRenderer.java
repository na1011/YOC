package com.yoc.wms.mail.renderer;

import com.yoc.wms.mail.config.MailConfig;
import com.yoc.wms.mail.domain.MailSection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 메일 본문 렌더러
 *
 *  @author 김찬기
 *  @since 1.0
 */
@Component
public class MailBodyRenderer {

    @Autowired
    private MailConfig mailConfig;

    /**
     * 섹션 리스트를 HTML로 렌더링
     */
    public String render(List<MailSection> sections) {
        if (sections == null || sections.isEmpty()) {
            return "";
        }

        StringBuilder html = new StringBuilder();

        for (MailSection section : sections) {
            html.append(renderSection(section));
        }

        return html.toString();
    }

    /**
     * 단일 섹션 렌더링
     */
    private String renderSection(MailSection section) {
        switch (section.getType()) {
            case TABLE:
                return renderTable(section);
            case TEXT:
                return renderText(section);
            case HTML:
                return renderHtml(section);
            case DIVIDER:
                return renderDivider(section);
            default:
                throw new IllegalArgumentException("Unknown section type: " + section.getType());
        }
    }

    /**
     * 테이블 섹션 렌더링
     */
    private String renderTable(MailSection section) {
        StringBuilder html = new StringBuilder();

        html.append("<div style='").append(mailConfig.getSectionMargin()).append("'>");

        // 제목
        if (section.hasTitle()) {
            html.append("<div style='").append(mailConfig.getTitleStyle()).append("'>");
            html.append(escapeHtml(section.getTitle()));
            html.append("</div>");
        }

        // 메타데이터
        boolean bordered = "true".equals(section.getMetadataOrDefault("bordered", "true"));
        boolean striped = "true".equals(section.getMetadataOrDefault("striped", "false"));
        String headerBgColor = section.getMetadataOrDefault("headerBgColor", mailConfig.getTableHeaderBgColor());
        String headerTextColor = section.getMetadataOrDefault("headerTextColor", mailConfig.getTableHeaderTextColor());

        // 테이블
        String tableStyle = String.format("width: 100%%; border-collapse: collapse; %s",
                mailConfig.getDefaultFontStyle());
        if (bordered) {
            tableStyle += String.format(" border: 1px solid %s;", mailConfig.getTableBorderColor());
        }

        html.append("<table style='").append(tableStyle).append("'>");

        List<Map<String, String>> data = section.getData();
        if (data != null && !data.isEmpty()) {
            Map<String, String> firstRow = data.get(0);

            // 헤더
            html.append("<thead>");
            html.append("<tr style='background-color: ").append(headerBgColor)
                    .append("; color: ").append(headerTextColor).append(";'>");

            for (String header : firstRow.keySet()) {
                String thStyle = String.format("padding: %s; text-align: left; font-weight: bold;",
                        mailConfig.getDefaultPadding());
                if (bordered) {
                    thStyle += String.format(" border: 1px solid %s;", mailConfig.getTableBorderColor());
                }
                html.append("<th style='").append(thStyle).append("'>");
                html.append(escapeHtml(header));
                html.append("</th>");
            }

            html.append("</tr>");
            html.append("</thead>");

            // 데이터
            html.append("<tbody>");

            for (int i = 0; i < data.size(); i++) {
                Map<String, String> row = data.get(i);

                String rowBgColor = "";
                if (striped && i % 2 == 1) {
                    rowBgColor = "background-color: " + mailConfig.getTableStripedBgColor() + ";";
                }

                html.append("<tr style='").append(rowBgColor).append("'>");

                for (String value : row.values()) {
                    String tdStyle = String.format("padding: %s; text-align: left;",
                            mailConfig.getDefaultPadding());
                    if (bordered) {
                        tdStyle += String.format(" border: 1px solid %s;", mailConfig.getTableBorderColor());
                    }
                    html.append("<td style='").append(tdStyle).append("'>");
                    html.append(escapeHtml(value));
                    html.append("</td>");
                }

                html.append("</tr>");
            }

            html.append("</tbody>");
        }

        html.append("</table>");
        html.append("</div>");

        return html.toString();
    }

    /**
     * 텍스트 섹션 렌더링
     */
    private String renderText(MailSection section) {
        StringBuilder html = new StringBuilder();

        html.append("<div style='").append(mailConfig.getSectionMargin()).append("'>");

        if (section.hasTitle()) {
            html.append("<div style='").append(mailConfig.getTitleStyle()).append("'>");
            html.append(escapeHtml(section.getTitle()));
            html.append("</div>");
        }

        String fontSize = section.getMetadataOrDefault("fontSize", mailConfig.getDefaultFontSize());
        String textAlign = section.getMetadataOrDefault("textAlign", "left");
        String fontWeight = section.getMetadataOrDefault("fontWeight", "normal");

        String contentStyle = String.format(
                "%s font-size: %s; text-align: %s; font-weight: %s; line-height: 1.6;",
                mailConfig.getDefaultFontStyle(), fontSize, textAlign, fontWeight);

        html.append("<div style='").append(contentStyle).append("'>");
        html.append(escapeHtml(section.getContent()).replace("\n", "<br>"));
        html.append("</div>");

        html.append("</div>");

        return html.toString();
    }

    /**
     * HTML 섹션 렌더링
     */
    private String renderHtml(MailSection section) {
        StringBuilder html = new StringBuilder();

        html.append("<div style='").append(mailConfig.getSectionMargin()).append("'>");

        if (section.hasTitle()) {
            html.append("<div style='").append(mailConfig.getTitleStyle()).append("'>");
            html.append(escapeHtml(section.getTitle()));
            html.append("</div>");
        }

        html.append(section.getContent());

        html.append("</div>");

        return html.toString();
    }

    /**
     * 구분선 섹션 렌더링
     */
    private String renderDivider(MailSection section) {
        String height = section.getMetadataOrDefault("height", mailConfig.getDividerHeight());
        String color = section.getMetadataOrDefault("color", mailConfig.getDividerColor());
        String margin = section.getMetadataOrDefault("margin", mailConfig.getDividerMargin());

        String style = String.format(
                "border: none; border-top: %s solid %s; margin: %s;",
                height, color, margin);

        return "<hr style='" + style + "'>";
    }

    /**
     * HTML 이스케이프
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}