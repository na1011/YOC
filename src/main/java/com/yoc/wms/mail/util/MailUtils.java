package com.yoc.wms.mail.util;

import com.yoc.wms.mail.domain.Recipient;
import com.yoc.wms.mail.exception.ValueChainException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 메일 발송 관련 유틸리티
 *
 * <h3>주요 기능</h3>
 * <ul>
 *   <li>이메일 주소 형식 검증</li>
 *   <li>CLOB → String 변환 (H2/Oracle 호환)</li>
 *   <li>수신인 목록 포맷팅</li>
 * </ul>
 *
 * @author WMS Team
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
     * CLOB을 String으로 변환
     * - String: 그대로 반환
     * - java.sql.Clob: getSubString() 사용
     * - null: 빈 문자열 반환
     * - 기타: toString() 호출
     *
     * @param obj 변환할 객체
     * @return 변환된 문자열
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
     *
     * @param recipients 수신인 목록
     * @return 콤마 구분 이메일 문자열 (예: "user1@a.com,user2@a.com")
     */
    public static String formatRecipientsToString(List<Recipient> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return "";
        }

        return recipients.stream()
                .map(Recipient::getEmail)
                .collect(Collectors.joining(","));
    }
}