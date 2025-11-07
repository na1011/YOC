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

    @Autowired
    private RecipientResolver recipientResolver;

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
            params.put("LIMIT", 10);
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
     *  - QUEUE_ID: QUEUE_ID
     *  - MAIL_SOURCE: MAIL_SOURCE (예: OVERDUE_ORDERS)
     *  - ALARM_NAME: ALARM_NAME (예: 지연 주문 알림)
     *  - SEVERITY: SEVERITY (INFO/WARNING/CRITICAL)
     *  - SQL_ID: SQL_ID (예: alarm.selectOverdueOrdersDetail)
     *  - SECTION_TITLE: SECTION_TITLE (Procedure가 작성한 소제목)
     *  - SECTION_CONTENT: SECTION_CONTENT (Procedure가 작성한 본문)
     *  - RECIPIENT_USER_IDS: RECIPIENT_USER_IDS (콤마 구분 사용자 ID, NULL 가능)
     *  - RECIPIENT_GROUPS: RECIPIENT_GROUPS (콤마 구분 그룹, NULL 가능)
     *  - COLUMN_ORDER: COLUMN_ORDER (콤마 구분 컬럼 순서, NULL 가능)
     *  - EXCEL_SQL_ID: EXCEL_SQL_ID (Excel 데이터 조회 SQL ID, NULL 가능, v3.0.0)
     *  - EXCEL_COLUMN_ORDER: EXCEL_COLUMN_ORDER (Excel 컬럼 순서, NULL 가능, v3.0.0)
     *  - EXCEL_FILE_NAME: EXCEL_FILE_NAME (Excel 파일명, NULL 가능, v3.0.0)
     */
    private void processMessage(Map<String, Object> msg) {
        Long queueId = getLong(msg.get("QUEUE_ID"));
        String mailSource = (String) msg.get("MAIL_SOURCE");
        String severity = (String) msg.get("SEVERITY");
        String sqlId = (String) msg.get("SQL_ID");
        String sectionTitle = (String) msg.get("SECTION_TITLE");
        String sectionContent = MailUtils.convertToString(msg.get("SECTION_CONTENT"));
        String columnOrder = (String) msg.get("COLUMN_ORDER");
        Integer retryCount = getInteger(msg.get("RETRY_COUNT"));

        // Excel 관련 정보 읽기 (v3.0.0)
        String excelSqlId = (String) msg.get("EXCEL_SQL_ID");
        String excelColumnOrder = (String) msg.get("EXCEL_COLUMN_ORDER");
        String excelFileName = (String) msg.get("EXCEL_FILE_NAME");

        // 수신인 정보 읽기
        String recipientUserIds = MailUtils.convertToString(msg.get("RECIPIENT_USER_IDS"));
        String recipientGroups = (String) msg.get("RECIPIENT_GROUPS");

        try {
            // 1. SQL_ID로 HTML 테이블 데이터 조회
            List<Map<String, Object>> tableData = mailDao.selectList(sqlId, null);

            // 2. Excel 데이터 조회 (SKIP on error, v3.0.0)
            List<Map<String, Object>> excelData = null;
            if (excelSqlId != null && !excelSqlId.trim().isEmpty()) {
                try {
                    excelData = mailDao.selectList(excelSqlId, null);
                    if (excelData == null || excelData.isEmpty()) {
                        System.out.println("⚠️ Excel 데이터 없음, 첨부 건너뜀: " + excelSqlId);
                        excelData = null; // Skip
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ Excel 조회 실패, 첨부 건너뜀: " + e.getMessage());
                    excelData = null; // Skip
                }
            }

            // 3. 수신인 목록 동적 조회 (RecipientResolver 사용)
            List<Recipient> recipients = recipientResolver.resolveByConditions(recipientUserIds, recipientGroups, true);

            // 4. MailRequest 생성 (Pure Function 사용, Excel 포함)
            MailRequest request = buildAlarmMailRequest(
                    msg,
                    tableData,
                    recipients,
                    columnOrder,
                    excelData,
                    excelColumnOrder,
                    excelFileName
            );

            // 5. MailService 호출 (boolean 반환)
            boolean success = mailService.sendMail(request);

            // 6. 성공/실패 처리
            if (success) {
                Map<String, Object> updateParams = new HashMap<>();
                updateParams.put("QUEUE_ID", queueId);
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
        params.put("QUEUE_ID", queueId);
        params.put("ERROR_MESSAGE", errorMessage);

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
     * @param queueData 큐에서 읽은 데이터 (SEVERITY, SECTION_TITLE, SECTION_CONTENT, MAIL_SOURCE 포함)
     * @param tableData SQL_ID 실행 결과 (NULL 가능)
     * @param recipients 조회된 수신인 목록
     * @param columnOrder 테이블 컬럼 순서 (쉼표 구분, NULL 가능)
     * @param excelData Excel 데이터 (NULL이면 첨부 없음, v3.0.0)
     * @param excelColumnOrder Excel 컬럼 순서 (쉼표 구분, NULL 가능, v3.0.0)
     * @param excelFileName Excel 파일명 (NULL이면 sectionTitle 기반, v3.0.0)
     * @return MailRequest 객체
     * @since v2.4.0 (Pure Function 분리)
     * @since v2.5.0 (columnOrder 파라미터 추가)
     * @since v3.0.0 (Excel 파라미터 추가)
     */
    public MailRequest buildAlarmMailRequest(
            Map<String, Object> queueData,
            List<Map<String, Object>> tableData,
            List<Recipient> recipients,
            String columnOrder,
            List<Map<String, Object>> excelData,
            String excelColumnOrder,
            String excelFileName
    ) {
        String severity = (String) queueData.get("SEVERITY");
        String sectionTitle = (String) queueData.get("SECTION_TITLE");
        String sectionContent = MailUtils.convertToString(queueData.get("SECTION_CONTENT"));
        String mailSource = (String) queueData.get("MAIL_SOURCE");

        // 테이블 데이터를 String으로 변환 (MailUtils 사용)
        List<Map<String, String>> tableDataString = MailUtils.convertToStringMap(tableData);

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
            // columnOrder가 있으면 메타데이터로 전달
            if (columnOrder != null && !columnOrder.trim().isEmpty()) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("columnOrder", columnOrder.trim());
                builder.addTableSection(null, tableDataString, metadata);
            } else {
                builder.addTableSection(tableDataString);
            }
        }

        // Excel 첨부 추가 (v3.0.0)
        if (excelData != null && !excelData.isEmpty()) {
            List<Map<String, String>> excelDataString = MailUtils.convertToStringMap(excelData);

            // 파일명 결정 (NULL이면 sectionTitle 기반)
            String title = (excelFileName != null && !excelFileName.trim().isEmpty())
                    ? excelFileName.trim()
                    : sectionTitle;

            builder.addExcelAttachment(title, excelDataString, excelColumnOrder);
        }

        return builder.build();
    }

    /**
     * 큐 데이터로부터 MailRequest 생성 (Pure Function - Excel 없음)
     *
     * 하위 호환성을 위해 Excel 파라미터가 없는 오버로드 메서드를 유지합니다.
     *
     * @deprecated v3.0.0 이후 Excel 파라미터가 있는 메서드 사용 권장
     */
    public MailRequest buildAlarmMailRequest(
            Map<String, Object> queueData,
            List<Map<String, Object>> tableData,
            List<Recipient> recipients,
            String columnOrder
    ) {
        return buildAlarmMailRequest(queueData, tableData, recipients, columnOrder, null, null, null);
    }

    // ===== Orchestration (통합 테스트 대상) =====

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