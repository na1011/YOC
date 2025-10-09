package com.yoc.wms.mail.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 메일 스타일 통합 설정
 * properties 파일에서 값 로드
 *
 *  @author 김찬기
 *  @since 1.0
 */
@Component
public class MailConfig {
    // ==================== 연락처 정보 ====================
    private static final String CONTACT1_NAME = "김찬기";
    private static final String CONTACT1_EMAIL = "chanki_kim@youngone.co.kr";

    private static final String CONTACT2_NAME = "최승협";
    private static final String CONTACT2_EMAIL = "seunghyub@youngone.co.kr";

    private static final String CONTACT3_NAME = "허성빈";
    private static final String CONTACT3_EMAIL = "seongbin_heo@youngone.co.kr";

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

    /**
     * 연락처 정보 반환 (HTML 형식)
     */
    public String getContactInfo() {
        return CONTACT1_NAME + ": " + CONTACT1_EMAIL + "\n" +
                CONTACT2_NAME + ": " + CONTACT2_EMAIL + "\n" +
                CONTACT3_NAME + ": " + CONTACT3_EMAIL;
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