package com.yoc.wms.mail.service;

import com.yoc.wms.mail.dao.MailDao;
import com.yoc.wms.mail.domain.MailRequest;
import com.yoc.wms.mail.domain.Recipient;
import com.yoc.wms.mail.util.MailUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 알람 메일 발송 서비스 (Consumer Only)
 *
 * <h3>아키텍처</h3>
 * <ul>
 *   <li>Producer: Oracle Procedure가 MAIL_QUEUE에 INSERT (운영환경)</li>
 *   <li>Consumer: Spring이 QUEUE를 읽어 SQL_ID 호출 후 메일 발송 (이 클래스)</li>
 * </ul>
 *
 * <h3>H2 환경 시뮬레이션</h3>
 * <ul>
 *   <li>Producer: 비활성화 (@Scheduled collectAlarms 동작 안 함)</li>
 *   <li>QUEUE 초기 데이터: schema.sql에 INSERT</li>
 *   <li>Consumer: 정상 동작 (processQueue)</li>
 * </ul>
 */
@Service
public class AlarmMailService {

    @Autowired
    private MailDao mailDao;

    @Autowired
    private MailService mailService;

    private static final int MAX_RETRY_COUNT = 3;

    /**
     * Producer: H2 환경에서는 비활성화
     *
     * <p>운영환경에서는 Oracle Scheduler가 Procedure를 호출하여
     * MAIL_QUEUE에 데이터를 INSERT 합니다.
     *
     * <p>H2 환경에서는 data.sql에 테스트 데이터가 미리 삽입되어 있어
     * 이 메서드는 실행되지 않습니다.
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
            List<Map<String, Object>> messages = mailDao.selectList("alarm.selectPendingQueue", null);

            if (messages == null || messages.isEmpty()) {
                return;
            }

            System.out.println("=== 큐 처리 시작: " + messages.size() + "건 ===");

            for (Map<String, Object> msg : messages) {
                processMessage(msg);
            }

        } catch (Exception e) {
            System.err.println("큐 처리 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 개별 메시지 처리
     *
     * <p>QUEUE에서 읽은 데이터 구조:
     * <ul>
     *   <li>queueId: QUEUE_ID</li>
     *   <li>mailSource: MAIL_SOURCE (예: OVERDUE_ORDERS)</li>
     *   <li>alarmName: ALARM_NAME (예: 지연 주문 알림)</li>
     *   <li>severity: SEVERITY (INFO/WARNING/CRITICAL)</li>
     *   <li>sqlId: SQL_ID (예: alarm.selectOverdueOrdersDetail)</li>
     *   <li>sectionTitle: SECTION_TITLE (Procedure가 작성한 소제목)</li>
     *   <li>sectionContent: SECTION_CONTENT (Procedure가 작성한 본문)</li>
     * </ul>
     */
    private void processMessage(Map<String, Object> msg) {
        Long queueId = getLong(msg.get("queueId"));
        String mailSource = (String) msg.get("mailSource");
        String severity = (String) msg.get("severity");
        String sqlId = (String) msg.get("sqlId");
        String sectionTitle = (String) msg.get("sectionTitle");
        String sectionContent = MailUtils.convertToString(msg.get("sectionContent"));
        Integer retryCount = getInteger(msg.get("retryCount"));

        try {
            // 1. SQL_ID로 테이블 데이터 조회
            List<Map<String, Object>> tableData = mailDao.selectList(sqlId, null);

            // 2. ADM 그룹 조회 및 Recipient 변환
            List<Map<String, Object>> admUserMaps = mailDao.selectList("alarm.selectAdmGroup", null);

            if (admUserMaps == null || admUserMaps.isEmpty()) {
                throw new IllegalStateException("ADM 그룹 사용자가 없습니다");
            }

            List<Recipient> admUsers = admUserMaps.stream()
                    .map(Recipient::fromMap)
                    .collect(Collectors.toList());

            // 3. MailRequest 생성 (Builder + Helper Methods 사용)
            List<Map<String, String>> tableDataString = convertToStringMap(tableData);

            MailRequest.Builder builder = MailRequest.builder()
                    .subject(MailRequest.alarmSubject(sectionTitle, severity, tableDataString.size()))
                    .addTextSection(MailRequest.alarmTitle(sectionTitle, severity), sectionContent)
                    .recipients(admUsers)
                    .mailType("ALARM")
                    .mailSource(mailSource);

            // 테이블 데이터가 있으면 추가
            if (tableDataString != null && !tableDataString.isEmpty()) {
                builder.addTableSection(tableDataString);
            }

            MailRequest request = builder.build();

            // 4. MailService 호출 (발송 + 로그 자동 처리)
            mailService.sendMail(request);

            // 6. 큐 성공 처리
            Map<String, Object> updateParams = new HashMap<>();
            updateParams.put("queueId", queueId);
            mailDao.update("alarm.updateQueueSuccess", updateParams);

            System.out.println("✅ 알람 발송 성공: " + mailSource);

        } catch (Exception e) {
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

    /**
     * Map<String, Object> → Map<String, String> 변환
     */
    private List<Map<String, String>> convertToStringMap(List<Map<String, Object>> source) {
        return source.stream()
                .map(map -> {
                    Map<String, String> stringMap = new LinkedHashMap<>();
                    map.forEach((k, v) -> stringMap.put(k, v != null ? v.toString() : ""));
                    return stringMap;
                })
                .collect(Collectors.toList());
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