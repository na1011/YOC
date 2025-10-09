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
            throw new IllegalArgumentException("섹션 타입은 null일 수 없습니다.");
        }

        for (SectionType type : values()) {
            if (type.code.equalsIgnoreCase(code.trim())) {
                return type;
            }
        }

        throw new IllegalArgumentException("정의되지 않은 섹션 타입: " + code);
    }
}