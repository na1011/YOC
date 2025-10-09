package com.yoc.wms.mail.domain;

import java.util.Map;
import java.util.Objects;

/**
 * 메일 수신인 정보
 *
 *  @author 김찬기
 *  @since 1.0
 */
public class Recipient {

    private final String userId;    // user → userId
    private final String email;
    private final String group;

    private Recipient(String userId, String email, String group) {
        this.userId = userId;
        this.email = email;
        this.group = group;
    }

    public static Recipient fromMap(Map<String, Object> map) {
        String userId = (String) map.get("userId");    // user → userId
        String email = (String) map.get("email");
        String group = (String) map.get("group");

        // USER_ID 대문자 정규화 (대소문자 안전성)
        if (userId != null) {
            userId = userId.toUpperCase();
        }

        // 이메일 소문자 정규화 (대소문자 안전성)
        if (email != null) {
            email = email.toLowerCase();
        }

        return new Recipient(userId, email, group);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String userId;
        private String email;
        private String group;

        public Builder userId(String userId) {    // user → userId
            // USER_ID 대문자 정규화 (대소문자 안전성)
            this.userId = (userId != null) ? userId.toUpperCase() : null;
            return this;
        }

        public Builder email(String email) {
            // 이메일 소문자 정규화
            this.email = (email != null) ? email.toLowerCase() : null;
            return this;
        }

        public Builder group(String group) {
            this.group = group;
            return this;
        }

        public Recipient build() {
            return new Recipient(userId, email, group);
        }
    }

    public String getUserId() {    // getUser → getUserId
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getGroup() {
        return group;
    }

    @Override
    public String toString() {
        return String.format("Recipient[userId=%s, email=%s, group=%s]", userId, email, group);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Recipient)) return false;
        Recipient that = (Recipient) o;
        // 이메일은 이미 소문자로 저장되므로 직접 비교
        return email != null && email.equals(that.email);
    }

    @Override
    public int hashCode() {
        // 이메일은 이미 소문자이므로 그대로 해시코드 생성
        return email != null ? email.hashCode() : 0;
    }
}