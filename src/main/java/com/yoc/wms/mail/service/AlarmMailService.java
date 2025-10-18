package com.yoc.wms.mail.service;

import com.yoc.wms.mail.dao.MailDao;
import com.yoc.wms.mail.domain.MailRequest;
import com.yoc.wms.mail.domain.Recipient;
import com.yoc.wms.mail.exception.ValueChainException;
import com.yoc.wms.mail.util.MailUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 알람 메일 발송 서비스
 *
 * -Producer: Oracle Procedure가 MAIL_QUEUE에 INSERT
 * -Consumer: Spring이 QUEUE를 읽어 SQL_ID 호출 후 메일 발송
 *
 *  @author 김찬기
 *  @since 1.0
 */
@Service
public class AlarmMailService {

    @Autowired
    private MailDao mailDao;

    @Autowired
    private MailService mailService;

    private static final int MAX_RETRY_COUNT = 3;

    /**
     * Producer
     */
    @Scheduled(cron = "0 */30 * * * *")
    @Transactional
    public void collectAlarms() {
        System.out.println("=== [H2 환경] Producer 비활성화 (data.sql 초기 데이터 사용) ===");
        // 아무 작업도 수행하지 않음
    }

    /**
     * Consumer: 큐 처리 (10초마다)
     */
    @Scheduled(fixedRate = 10000)
    @Transactional
    public void processQueue() {
        try {
            // 배치 크기 제한 (긴 트랜잭션 방지)
            Map<String, Object> params = new HashMap<>();
            params.put("limit", 10);
            List<Map<String, Object>> messages = mailDao.selectList("alarm.selectPendingQueue", params);

            if (messages == null || messages.isEmpty()) {
                return;
            }

            System.out.println("=== 큐 처리 시작: " + messages.size() + "건 ===");

            for (Map<String, Object> msg : messages) {
                processMessage(msg);
            }

        } catch (Exception e) {
            // 시스템 오류만 catch (DB 커넥션 끊김, OutOfMemory 등)
            System.err.println("큐 처리 시스템 오류: " + e.getMessage());
            e.printStackTrace();
            // 트랜잭션 롤백됨 (정상 동작)
        }
    }

    /**
     * 개별 메시지 처리
     *
     * QUEUE에서 읽은 데이터 구조:
     *  - queueId: QUEUE_ID
     *  - mailSource: MAIL_SOURCE (예: OVERDUE_ORDERS)
     *  - alarmName: ALARM_NAME (예: 지연 주문 알림)
     *  - severity: SEVERITY (INFO/WARNING/CRITICAL)
     *  - sqlId: SQL_ID (예: alarm.selectOverdueOrdersDetail)
     *  - sectionTitle: SECTION_TITLE (Procedure가 작성한 소제목)
     *  - sectionContent: SECTION_CONTENT (Procedure가 작성한 본문)
     *  - recipientUserIds: RECIPIENT_USER_IDS (콤마 구분 사용자 ID, NULL 가능)
     *  - recipientGroups: RECIPIENT_GROUPS (콤마 구분 그룹, NULL 가능)
     */
    private void processMessage(Map<String, Object> msg) {
        Long queueId = getLong(msg.get("queueId"));
        String mailSource = (String) msg.get("mailSource");
        String severity = (String) msg.get("severity");
        String sqlId = (String) msg.get("sqlId");
        String sectionTitle = (String) msg.get("sectionTitle");
        String sectionContent = MailUtils.convertToString(msg.get("sectionContent"));
        Integer retryCount = getInteger(msg.get("retryCount"));

        // 수신인 정보 읽기
        String recipientUserIds = MailUtils.convertToString(msg.get("recipientUserIds"));
        String recipientGroups = (String) msg.get("recipientGroups");

        try {
            // 1. SQL_ID로 테이블 데이터 조회
            List<Map<String, Object>> tableData = mailDao.selectList(sqlId, null);

            // 2. 수신인 목록 동적 조회
            List<Recipient> recipients = resolveRecipients(recipientUserIds, recipientGroups);

            // 3. MailRequest 생성 (Pure Function 사용)
            MailRequest request = buildAlarmMailRequest(msg, tableData, recipients);

            // 4. MailService 호출 (boolean 반환)
            boolean success = mailService.sendMail(request);

            // 5. 성공/실패 처리
            if (success) {
                Map<String, Object> updateParams = new HashMap<>();
                updateParams.put("queueId", queueId);
                mailDao.update("alarm.updateQueueSuccess", updateParams);
                System.out.println("✅ 알람 발송 성공: " + mailSource + " (수신인 " + recipients.size() + "명)");
            } else {
                handleFailure(queueId, mailSource, retryCount, new Exception("메일 발송 실패"));
            }

        } catch (Exception e) {
            // 예상치 못한 시스템 오류 (수신인 조회 실패, SQL 오류 등)
            handleFailure(queueId, mailSource, retryCount, e);
        }
    }

    /**
     * 실패 처리 (재시도 또는 최종 실패)
     */
    private void handleFailure(Long queueId, String mailSource, Integer retryCount, Exception e) {
        String errorMessage = e.getMessage();
        if (errorMessage != null && errorMessage.length() > 2000) {
            errorMessage = errorMessage.substring(0, 2000);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("queueId", queueId);
        params.put("errorMessage", errorMessage);

        if (retryCount >= MAX_RETRY_COUNT - 1) {
            // 최종 실패
            mailDao.update("alarm.updateQueueFailed", params);
            System.err.println("❌ 알람 발송 최종 실패: " + mailSource + " - " + errorMessage);
        } else {
            // 재시도
            mailDao.update("alarm.updateQueueRetry", params);
            System.err.println("⚠️ 알람 발송 재시도 예정: " + mailSource +
                    " (시도 " + (retryCount + 2) + "/" + MAX_RETRY_COUNT + ")");
        }
    }

    // ===== Pure Functions (단위 테스트 대상) =====

    /**
     * 큐 데이터로부터 MailRequest 생성 (Pure Function)
     *
     * DAO 호출 없이 비즈니스 로직만 처리합니다.
     * processMessage()의 핵심 로직을 분리하여 단위 테스트 가능하게 만듭니다.
     *
     * @param queueData 큐에서 읽은 데이터 (severity, sectionTitle, sectionContent, mailSource 포함)
     * @param tableData SQL_ID 실행 결과 (NULL 가능)
     * @param recipients 조회된 수신인 목록
     * @return MailRequest 객체
     * @since v2.4.0 (Pure Function 분리)
     */
    public MailRequest buildAlarmMailRequest(
            Map<String, Object> queueData,
            List<Map<String, Object>> tableData,
            List<Recipient> recipients
    ) {
        String severity = (String) queueData.get("severity");
        String sectionTitle = (String) queueData.get("sectionTitle");
        String sectionContent = MailUtils.convertToString(queueData.get("sectionContent"));
        String mailSource = (String) queueData.get("mailSource");

        // 테이블 데이터를 String으로 변환
        List<Map<String, String>> tableDataString = convertToStringMap(tableData);

        // 건수 계산
        int count = (tableDataString != null && !tableDataString.isEmpty()) ? tableDataString.size() : 0;

        // MailRequest 생성
        MailRequest.Builder builder = MailRequest.builder()
                .subject(MailRequest.alarmSubject(sectionTitle, severity, count))
                .addTextSection(MailRequest.alarmTitle(sectionTitle, severity), sectionContent)
                .recipients(recipients)
                .mailType("ALARM")
                .mailSource(mailSource);

        // 테이블 데이터가 있으면 추가
        if (tableDataString != null && !tableDataString.isEmpty()) {
            builder.addTableSection(tableDataString);
        }

        return builder.build();
    }

    /**
     * 수신인 USER_ID 파싱 (Pure Function)
     *
     * 콤마로 구분된 사용자 ID 문자열을 List로 변환합니다.
     * trim + 빈 문자열 제거 처리를 수행합니다.
     *
     * Example:
     *   Input:  " admin1 , user1 , "
     *   Output: ["admin1", "user1"]
     *
     * @param recipientUserIds 콤마 구분 문자열 (NULL 가능)
     * @return trim된 사용자 ID 리스트 (대소문자 정규화는 하지 않음)
     * @since v2.4.0 (Pure Function 분리)
     */
    public List<String> parseRecipientIds(String recipientUserIds) {
        List<String> result = new ArrayList<>();
        if (recipientUserIds == null || recipientUserIds.trim().isEmpty()) {
            return result;
        }

        String[] tokens = recipientUserIds.split(",");
        for (String token : tokens) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 수신인 그룹 파싱 (Pure Function)
     *
     * 콤마로 구분된 그룹 문자열을 List로 변환합니다.
     * trim + 빈 문자열 제거 처리를 수행합니다.
     *
     * Example:
     *   Input:  " ADM , SALES , "
     *   Output: ["ADM", "SALES"]
     *
     * @param recipientGroups 콤마 구분 문자열 (NULL 가능)
     * @return trim된 그룹 리스트 (대소문자 정규화는 하지 않음)
     * @since v2.4.0 (Pure Function 분리)
     */
    public List<String> parseRecipientGroups(String recipientGroups) {
        List<String> result = new ArrayList<>();
        if (recipientGroups == null || recipientGroups.trim().isEmpty()) {
            return result;
        }

        String[] tokens = recipientGroups.split(",");
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
     * Why LinkedHashMap:
     * - MailBodyRenderer가 map.keySet()을 순회하며 테이블 헤더 생성
     * - HashMap은 순서 미보장 → 컬럼 순서가 매번 변경될 수 있음
     * - LinkedHashMap은 삽입 순서 유지 → DB 쿼리 결과 순서 그대로 반영
     *
     * Example:
     *   Input:  [{orderId=1, customerName="홍길동", status=10}]  (Integer status)
     *   Output: [{orderId="1", customerName="홍길동", status="10"}]  (All String)
     *
     * Spring 3.2 ASM 호환 (v2.1.3):
     * - Before: maps.stream().map(m -> {...}).collect(Collectors.toList())
     * - After: 중첩 for-loop (Lambda 제거)
     *
     * @param source MyBatis 조회 결과 (List<Map<String, Object>>, NULL 가능)
     * @return String으로 변환된 Map 리스트 (LinkedHashMap으로 순서 보장)
     * @since v2.1.3 (Spring 3.2 호환 for-loop 전환)
     * @since v2.4.0 (public으로 변경, Pure Function)
     */
    public List<Map<String, String>> convertToStringMap(List<Map<String, Object>> source) {
        List<Map<String, String>> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (Map<String, Object> map : source) {
            Map<String, String> stringMap = new LinkedHashMap<>();  // 순서 보장
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                stringMap.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
            }
            result.add(stringMap);
        }
        return result;
    }

    // ===== Orchestration (통합 테스트 대상) =====

    /**
     * 동적 수신인 조회 (사용자 ID + 그룹 통합)
     *
     * RECIPIENT_USER_IDS와 RECIPIENT_GROUPS를 동적으로 조회하여 실제 Recipient 목록을 생성합니다.
     *
     * Features (v2.1.0+):
     * - 유연한 조합: 사용자 ID / 그룹 / 조합 모두 가능
     * - NULL 기본값: 둘 다 NULL이면 ADM 그룹 자동 발송
     * - 대소문자 정규화: Recipient.fromMap()에서 일원화 (v2.1.1)
     * - 중복 제거: 이메일 기준 (fromMapList 내부 처리)
     *
     * Logic Flow:
     * 1. NULL 체크 → 둘 다 NULL이면 ADM 그룹 기본 설정
     * 2. 콤마 split → trim (parseRecipientIds/Groups 사용)
     * 3. MyBatis 통합 쿼리 호출 (alarm.selectRecipientsByConditions)
     * 4. Recipient.fromMapList()로 변환 + 중복 제거
     * 5. 빈 결과 → ValueChainException 발생
     *
     * Spring 3.2 ASM 호환 (v2.1.3):
     * - Arrays.stream().map().filter().collect() 제거
     * - for-loop + 수동 필터링으로 전환
     *
     * Example QUEUE Data:
     *   RECIPIENT_USER_IDS: "ADMIN1,sales001" (대소문자 혼용)
     *   RECIPIENT_GROUPS: "ADM,LOGISTICS"
     *   → DB 조회: ["admin@test.com", "sales@test.com", "logistics@test.com"]
     *   → Recipient: USER_ID 대문자, EMAIL 소문자, 중복 제거
     *
     * @param recipientUserIds 콤마 구분 사용자 ID (NULL 가능, 대소문자 혼용 가능)
     * @param recipientGroups 콤마 구분 그룹명 (NULL 가능, 대소문자 혼용 가능)
     * @return 중복 제거된 Recipient 목록 (이메일 기준)
     * @throws ValueChainException 수신인 조회 결과가 없는 경우
     * @since v2.1.0 (동적 수신인 조회 도입)
     * @since v2.1.1 (대소문자 정규화 Recipient 일원화)
     * @since v2.1.3 (Spring 3.2 호환 for-loop 전환)
     * @since v2.4.0 (parseRecipientIds/Groups Pure Function 사용)
     */
    private List<Recipient> resolveRecipients(String recipientUserIds, String recipientGroups) {
        // 1. NULL 체크 및 기본값 설정 (알람 메일 전용)
        boolean hasUserIds = recipientUserIds != null && !recipientUserIds.trim().isEmpty();
        boolean hasGroups = recipientGroups != null && !recipientGroups.trim().isEmpty();

        if (!hasUserIds && !hasGroups) {
            // 둘 다 NULL이면 ADM 그룹을 기본값으로 설정
            System.out.println("⚠️ 수신인 미지정 → ADM 그룹 기본 발송");
            recipientGroups = "ADM";
            hasGroups = true;
        }

        // 2. 콤마 구분 문자열을 List로 변환 (Pure Function 사용)
        List<String> userIdList = hasUserIds ? parseRecipientIds(recipientUserIds) : new ArrayList<String>();
        List<String> groupList = hasGroups ? parseRecipientGroups(recipientGroups) : new ArrayList<String>();

        // 3. MyBatis 파라미터 생성
        Map<String, Object> params = new HashMap<>();
        if (!userIdList.isEmpty()) {
            params.put("userIds", userIdList);
        }
        if (!groupList.isEmpty()) {
            params.put("groups", groupList);
        }

        // 4. 통합 쿼리 호출 (DISTINCT + IN 절)
        List<Map<String, Object>> recipientMaps = mailDao.selectList("alarm.selectRecipientsByConditions", params);

        // 5. Recipient 변환 및 이메일 기준 중복 제거 (fromMapList 사용)
        List<Recipient> recipients = Recipient.fromMapList(recipientMaps);

        // 6. 유효성 검증
        if (recipients.isEmpty()) {
            throw new ValueChainException(
                    "수신인 조회 결과가 없습니다. " +
                            "userIds=" + userIdList + ", groups=" + groupList
            );
        }

        System.out.println("📧 수신인 조회 완료: " + recipients.size() + "명 " +
                "(userIds=" + userIdList.size() + ", groups=" + groupList.size() + ")");

        return recipients;
    }

    private Long getLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    private Integer getInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }
}