package com.yoc.wms.mail.enums;

public enum SendStatus {
    PENDING("PENDING", "대기"),
    SUCCESS("SUCCESS", "성공"),
    FAILURE("FAILURE", "실패"),
    RETRY("RETRY", "재시도");

    private final String code;
    private final String description;

    SendStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static SendStatus fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Send status code cannot be null or empty");
        }

        for (SendStatus status : values()) {
            if (status.code.equalsIgnoreCase(code.trim())) {
                return status;
            }
        }

        throw new IllegalArgumentException("Unknown send status: " + code);
    }
}