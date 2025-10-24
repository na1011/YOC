package com.yoc.wms.mail.domain;

import java.util.*;

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

    /**
     * Map을 Recipient로 변환 (대소문자 정규화 포함)
     *
     * 대소문자 정규화 일원화 (v2.1.1):
     * - USER_ID: 대문자로 정규화 (DB 저장 규칙)
     * - EMAIL: 소문자로 정규화 (이메일 표준)
     * - Service/SQL 계층에서는 trim만 수행, 정규화는 이 메서드에서 일원화
     *
     * Map 구조:
     *   {USER_ID: String, EMAIL: String, USER_GROUP: String}
     *
     * Example:
     *   Map<String, Object> map = new HashMap<>();
     *   map.put("USER_ID", "admin");       // 소문자 입력
     *   map.put("EMAIL", "Admin@Test.Co.Kr");  // 대소문자 혼용
     *   map.put("USER_GROUP", "ADM");
     *
     *   Recipient r = Recipient.fromMap(map);
     *   // r.getUserId() = "ADMIN" (대문자)
     *   // r.getEmail() = "admin@test.co.kr" (소문자)
     *
     * @param map MyBatis 조회 결과 (Map<String, Object>)
     * @return 정규화된 Recipient 객체
     * @since v2.1.1 (대소문자 정규화 일원화)
     */
    public static Recipient fromMap(Map<String, Object> map) {
        String userId = (String) map.get("USER_ID");
        String email = (String) map.get("EMAIL");
        String group = (String) map.get("USER_GROUP");

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

    /**
     * Map 리스트를 Recipient 리스트로 변환 (중복 제거 포함)
     *
     * MyBatis 조회 결과를 Recipient 리스트로 변환하며, 이메일 기준 중복을 자동 제거합니다.
     *
     * Features:
     * - 대소문자 정규화: fromMap()에서 자동 처리 (v2.1.1)
     * - 중복 제거: 이메일 기준 (equals/hashCode)
     * - 순서 보장: LinkedHashSet 사용 (DB 쿼리 결과 순서 유지)
     * - null-safe: null/empty 입력 시 빈 리스트 반환
     *
     * Spring 3.2 ASM 호환 (v2.1.3):
     * - Lambda/Stream API 대신 for-loop 사용
     * - Collectors.toCollection(LinkedHashSet::new) 제거
     *
     * Before (v2.1.2):
     *   Set<Recipient> set = maps.stream()
     *       .map(Recipient::fromMap)
     *       .collect(Collectors.toCollection(LinkedHashSet::new));
     *
     * After (v2.1.3):
     *   Set<Recipient> set = new LinkedHashSet<>();
     *   for (Map<String, Object> map : maps) {
     *       set.add(fromMap(map));
     *   }
     *
     * Example:
     *   List<Map<String, Object>> maps = mailDao.selectList("alarm.selectRecipients", null);
     *   List<Recipient> recipients = Recipient.fromMapList(maps);  // 1줄로 변환 + 중복 제거
     *
     * @param maps MyBatis 조회 결과 (List<Map<String, Object>>)
     * @return 중복 제거된 Recipient 리스트 (이메일 기준, 순서 보장)
     * @since v2.1.2 (초기 도입)
     * @since v2.1.3 (Spring 3.2 호환 for-loop 전환)
     */
    public static List<Recipient> fromMapList(List<Map<String, Object>> maps) {
        if (maps == null || maps.isEmpty()) {
            return new ArrayList<>();
        }

        // LinkedHashSet으로 중복 제거 (이메일 기준, 순서 보장)
        // Spring 3.2 ASM 호환성을 위해 for-loop 사용 (lambda/method reference 제거)
        Set<Recipient> recipientSet = new LinkedHashSet<>();
        for (Map<String, Object> map : maps) {
            recipientSet.add(fromMap(map));
        }

        return new ArrayList<>(recipientSet);
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