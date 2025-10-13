package com.yoc.wms.mail.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * 메일 스타일 통합 설정
 * mail-config.properties 파일에서 값 로드
 *
 *  @author 김찬기
 *  @since 1.0
 */
@Component
@PropertySource("classpath:mail-config.properties")
public class MailConfig {
    // ==================== 연락처 정보 ====================
    @Value("${mail.contact.name.1:IT}")
    private String contactName1;
    @Value("${mail.contact.email.1:C20002_3000@test.co.kr}")
    private String contactEmail1;

    @Value("${mail.contact.name.2:#{null}}")
    private String contactName2;
    @Value("${mail.contact.email.2:#{null}}")
    private String contactEmail2;

    @Value("${mail.contact.name.3:#{null}}")
    private String contactName3;
    @Value("${mail.contact.email.3:#{null}}")
    private String contactEmail3;

    // ========== 폰트 설정 ==========
    @Value("${mail.style.font.family:Arial, sans-serif}")
    private String fontFamily;

    @Value("${mail.style.font.size.default:14px}")
    private String defaultFontSize;

    @Value("${mail.style.font.size.title:16px}")
    private String titleFontSize;

    // ========== 색상 설정 ==========
    @Value("${mail.style.color.primary:#4A90E2}")
    private String primaryColor;

    @Value("${mail.style.color.text.default:#555555}")
    private String defaultTextColor;

    @Value("${mail.style.color.text.title:#333333}")
    private String titleTextColor;

    @Value("${mail.style.color.background.header:#f5f5f5}")
    private String headerBackgroundColor;

    // ========== 테이블 기본 스타일 ==========
    @Value("${mail.style.table.header.bg:#4A90E2}")
    private String tableHeaderBgColor;

    @Value("${mail.style.table.header.text:#FFFFFF}")
    private String tableHeaderTextColor;

    @Value("${mail.style.table.border.color:#DDDDDD}")
    private String tableBorderColor;

    @Value("${mail.style.table.striped.bg:#F9F9F9}")
    private String tableStripedBgColor;

    // ========== 레이아웃 설정 ==========
    @Value("${mail.style.layout.section.margin:20px 0}")
    private String sectionMargin;

    @Value("${mail.style.layout.title.margin:0 0 10px 0}")
    private String titleMargin;

    @Value("${mail.style.layout.padding.default:12px}")
    private String defaultPadding;

    @Value("${mail.style.layout.max.width:800px}")
    private String maxWidth;

    // ========== 구분선 기본 스타일 ==========
    @Value("${mail.style.divider.height:1px}")
    private String dividerHeight;

    @Value("${mail.style.divider.color:#DDDDDD}")
    private String dividerColor;

    @Value("${mail.style.divider.margin:30px 0}")
    private String dividerMargin;

    // ========== HTML 구조 설정 ==========
    @Value("${mail.structure.system.title:WMS 시스템 알림}")
    private String systemTitle;

    @Value("${mail.structure.footer.message:본 메일은 WMS 시스템에서 자동 발송되었습니다.}")
    private String footerMessage;

    // ========== Getter 메서드 ==========

    public String getFontFamily() { return fontFamily; }
    public String getDefaultFontSize() { return defaultFontSize; }
    public String getTitleFontSize() { return titleFontSize; }

    public String getPrimaryColor() { return primaryColor; }
    public String getDefaultTextColor() { return defaultTextColor; }
    public String getTitleTextColor() { return titleTextColor; }
    public String getHeaderBackgroundColor() { return headerBackgroundColor; }

    public String getTableHeaderBgColor() { return tableHeaderBgColor; }
    public String getTableHeaderTextColor() { return tableHeaderTextColor; }
    public String getTableBorderColor() { return tableBorderColor; }
    public String getTableStripedBgColor() { return tableStripedBgColor; }

    public String getSectionMargin() { return sectionMargin; }
    public String getTitleMargin() { return titleMargin; }
    public String getDefaultPadding() { return defaultPadding; }
    public String getMaxWidth() { return maxWidth; }

    public String getDividerHeight() { return dividerHeight; }
    public String getDividerColor() { return dividerColor; }
    public String getDividerMargin() { return dividerMargin; }

    public String getSystemTitle() { return systemTitle; }
    public String getFooterMessage() { return footerMessage; }

    /**
     * 연락처 정보 반환 (HTML 형식)
     *
     * contact1은 기본값 보장, contact2~3는 null/빈값이면 건너뜀
     */
    public String getContactInfo() {
        StringBuilder contactInfo = new StringBuilder();
        contactInfo.append(contactName1 + ": " + contactEmail1);

        if (contactName2 != null && !contactName2.isEmpty() && contactEmail2 != null && !contactEmail2.isEmpty()) {
            contactInfo.append("\n");
            contactInfo.append(contactName2 + ": " + contactEmail2);
        }
        if (contactName3 != null && !contactName3.isEmpty() && contactEmail3 != null && !contactEmail3.isEmpty()) {
            contactInfo.append("\n");
            contactInfo.append(contactName3 + ": " + contactEmail3);
        }

        return contactInfo.toString();
    }

    // ========== 복합 스타일 빌더 ==========

    /**
     * 기본 폰트 스타일
     */
    public String getDefaultFontStyle() {
        return String.format("font-family: %s; font-size: %s; color: %s;",
                fontFamily, defaultFontSize, defaultTextColor);
    }

    /**
     * 제목 스타일
     */
    public String getTitleStyle() {
        return String.format(
                "font-family: %s; font-size: %s; font-weight: bold; margin: %s; color: %s;",
                fontFamily, titleFontSize, titleMargin, titleTextColor);
    }
}