package com.yoc.wms.mail.domain;

import java.util.Map;

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
            this.userId = userId;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
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
}