package com.yoc.wms.mail.enums;

public enum SectionType {
    TABLE("TABLE", "테이블"),
    TEXT("TEXT", "텍스트"),
    HTML("HTML", "HTML"),
    DIVIDER("DIVIDER", "구분선");

    private final String code;
    private final String description;

    SectionType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static SectionType fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Section type code cannot be null or empty");
        }

        for (SectionType type : values()) {
            if (type.code.equalsIgnoreCase(code.trim())) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unknown section type: " + code);
    }
}