package com.yoc.wms.mail.util;

import com.yoc.wms.mail.domain.Recipient;
import com.yoc.wms.mail.exception.ValueChainException;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 메일 발송 관련 유틸리티
 *
 * -이메일 주소 형식 검증
 * -CLOB → String 변환 (H2/Oracle 호환)
 * -수신인 목록 포맷팅
 *
 * @author 김찬기
 * @since 1.0
 */
public class MailUtils {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /**
     * 이메일 형식 검증
     *
     * @param email 검증할 이메일 주소
     * @return 유효하면 true
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * 수신인 목록 검증
     * - 빈 목록 체크
     * - 이메일 형식 검증
     * - 중복 체크
     *
     * @param recipients 검증할 수신인 목록
     * @throws ValueChainException 검증 실패 시
     */
    public static void validateRecipients(List<Recipient> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            throw new ValueChainException("수신인이 없습니다.");
        }

        for (Recipient recipient : recipients) {
            if (recipient.getEmail() == null || recipient.getEmail().trim().isEmpty()) {
                throw new ValueChainException("이메일 주소가 없습니다: " + recipient);
            }

            if (!isValidEmail(recipient.getEmail())) {
                throw new ValueChainException("잘못된 이메일 형식: " + recipient.getEmail());
            }
        }

        // 중복 체크 (이메일 기준, 대소문자 구분 없음)
        Set<String> uniqueEmails = new HashSet<>();
        for (Recipient r : recipients) {
            if (!uniqueEmails.add(r.getEmail().toLowerCase())) {
                throw new ValueChainException("중복된 이메일: " + r.getEmail());
            }
        }
    }

    /**
     * CLOB을 String으로 변환 (H2/Oracle 호환성)
     *
     * H2와 Oracle은 CLOB 반환 타입이 다릅니다:
     * - H2: org.h2.jdbc.JdbcClob
     * - Oracle: String 또는 oracle.sql.CLOB
     *
     * 직접 캐스팅 금지:
     *   ❌ String content = (String) msg.get("sectionContent");  // Oracle: OK, H2: ClassCastException
     *   ✅ String content = MailUtils.convertToString(msg.get("sectionContent"));  // 양쪽 OK
     *
     * Conversion Rules:
     * - String: 그대로 반환 (Oracle 일반 케이스)
     * - java.sql.Clob: getSubString() 사용 (H2, Oracle CLOB)
     * - null: 빈 문자열 반환 (null-safe)
     * - 기타: toString() 호출 (fallback)
     *
     * Usage:
     *   Map<String, Object> msg = mailDao.selectOne("alarm.selectQueue", params);
     *   String sectionContent = MailUtils.convertToString(msg.get("sectionContent"));
     *   String recipientUserIds = MailUtils.convertToString(msg.get("recipientUserIds"));
     *
     * @param obj 변환할 객체 (Map value from MyBatis)
     * @return 변환된 문자열 (null → 빈 문자열)
     * @throws ValueChainException CLOB 변환 실패 시
     */
    public static String convertToString(Object obj) {
        if (obj == null) {
            return "";
        }

        if (obj instanceof String) {
            return (String) obj;
        }

        if (obj instanceof java.sql.Clob) {
            try {
                java.sql.Clob clob = (java.sql.Clob) obj;
                long length = clob.length();
                if (length == 0) {
                    return "";
                }
                return clob.getSubString(1, (int) length);
            } catch (Exception e) {
                throw new ValueChainException("CLOB 변환 실패: " + e.getMessage(), e);
            }
        }

        return obj.toString();
    }

    /**
     * 수신인 목록을 콤마 구분 문자열로 변환
     * Spring 3.2 ASM 호환성을 위해 for-loop 사용 (lambda/method reference 제거)
     *
     * @param recipients 수신인 목록
     * @return 콤마 구분 이메일 문자열 (예: "user1@a.com,user2@a.com")
     */
    public static String formatRecipientsToString(List<Recipient> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recipients.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(recipients.get(i).getEmail());
        }
        return sb.toString();
    }

    /**
     * 콤마 구분 문자열 파싱 (Pure Function)
     *
     * 콤마로 구분된 문자열을 trim 처리하여 List로 변환합니다.
     * 빈 문자열은 제외됩니다.
     *
     * Example:
     *   Input:  " admin1 , user1 , "
     *   Output: ["admin1", "user1"]
     *
     * Spring 3.1.2 호환: for-loop 사용 (Lambda/Stream 금지)
     *
     * @param commaSeparated 콤마 구분 문자열 (NULL 가능)
     * @return trim된 문자열 리스트 (빈 문자열 제외)
     * @since v2.6.0 (리팩토링)
     */
    public static List<String> parseCommaSeparated(String commaSeparated) {
        List<String> result = new ArrayList<>();
        if (commaSeparated == null || commaSeparated.trim().isEmpty()) {
            return result;
        }

        String[] tokens = commaSeparated.split(",");
        for (String token : tokens) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Map 타입 변환 (Object → String, 테이블 렌더링용) - Pure Function
     *
     * MyBatis 조회 결과를 MailRequest.addTableSection()에 전달 가능한 형식으로 변환합니다.
     *
     * Null Handling:
     * - 입력 리스트가 null → 빈 리스트 반환
     * - 리스트 내 null 요소 → 자동 필터링 (스킵)
     * - Map 내 null 값 → 빈 문자열로 변환
     *
     * Why Auto-Filter Nulls:
     * - 일관성: parseCommaSeparated(), convertToString()도 null-safe
     * - Railway Oriented: 예상된 데이터 이상 → Fail-Safe 처리
     * - DRY: Service별 검증 코드 불필요
     * - Static Utility Pattern: 방어적, 재사용 가능
     *
     * Why LinkedHashMap:
     * - MailBodyRenderer가 map.keySet()을 순회하며 테이블 헤더 생성
     * - HashMap은 순서 미보장 → 컬럼 순서가 매번 변경될 수 있음
     * - LinkedHashMap은 삽입 순서 유지 → DB 쿼리 결과 순서 그대로 반영
     *
     * Example:
     *   Input:  [{orderId=1, customerName="홍길동", status=10}, null, {orderId=2, ...}]
     *   Output: [{orderId="1", customerName="홍길동", status="10"}, {orderId="2", ...}]
     *           (null 요소는 자동 필터링됨)
     *
     * Spring 3.1.2 ASM 호환:
     * - Before: maps.stream().map(m -> {...}).collect(Collectors.toList())
     * - After: 중첩 for-loop (Lambda 제거)
     *
     * @param source MyBatis 조회 결과 (List<Map<String, Object>>, NULL 가능)
     * @return String으로 변환된 Map 리스트 (LinkedHashMap으로 순서 보장, null 요소 제외)
     * @since v2.1.3 (AlarmMailService에서 이동)
     * @since v2.6.0 (MailUtils로 공통화)
     * @since v2.6.1 (null 요소 자동 필터링 추가)
     */
    public static List<Map<String, String>> convertToStringMap(List<Map<String, Object>> source) {
        List<Map<String, String>> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (Map<String, Object> map : source) {
            if (map == null) {
                continue;  // Skip null elements (fail-safe)
            }
            Map<String, String> stringMap = new LinkedHashMap<>();  // 순서 보장
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                stringMap.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
            }
            result.add(stringMap);
        }
        return result;
    }
}