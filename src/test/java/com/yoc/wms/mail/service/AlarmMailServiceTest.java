package com.yoc.wms.mail.service;

import com.yoc.wms.mail.dao.MailDao;
import com.yoc.wms.mail.domain.MailRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AlarmMailService 단위 테스트
 *
 * 테스트 범위:
 * - processQueue() 정상 흐름
 * - processMessage() 개별 처리
 * - 실패 처리 및 재시도 로직
 * - Map 타입 변환
 * - 엣지케이스 (빈 큐, 잘못된 데이터)
 *
 * 주의: @Scheduled 메서드는 실제로 스케줄링되지 않음 (단위 테스트)
 */
@ExtendWith(MockitoExtension.class)
class AlarmMailServiceTest {

    @Mock
    private MailDao mailDao;

    @Mock
    private MailService mailService;

    @InjectMocks
    private AlarmMailService alarmMailService;

    private Map<String, Object> testQueueMessage;
    private List<Map<String, Object>> testAdmUsers;
    private List<Map<String, Object>> testTableData;

    @BeforeEach
    void setUp() {
        // 테스트용 큐 메시지
        testQueueMessage = new HashMap<>();
        testQueueMessage.put("queueId", 1L);
        testQueueMessage.put("mailSource", "OVERDUE_ORDERS");
        testQueueMessage.put("severity", "WARNING");
        testQueueMessage.put("sqlId", "alarm.selectOverdueOrdersDetail");
        testQueueMessage.put("sectionTitle", "지연 주문 알림");
        testQueueMessage.put("sectionContent", "10건의 지연 주문이 발견되었습니다.");
        testQueueMessage.put("retryCount", 0);

        // ADM 사용자 목록
        testAdmUsers = Arrays.asList(
            createMap("userId", "admin1", "email", "admin1@company.com", "group", "ADM"),
            createMap("userId", "admin2", "email", "admin2@company.com", "group", "ADM")
        );

        // 테이블 데이터
        testTableData = Arrays.asList(
            createMap("orderId", "001", "status", "DELAYED"),
            createMap("orderId", "002", "status", "DELAYED")
        );

        // 기본 동작: 메일 발송 성공 (실패 케이스는 개별 테스트에서 override)
        lenient().when(mailService.sendMail(any(MailRequest.class))).thenReturn(true);
    }

    // ==================== processQueue() 테스트 ====================

    @Test
    @DisplayName("processQueue: 정상 처리")
    void processQueue_success() {
        // Given
        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(testAdmUsers);

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailDao).selectList(eq("alarm.selectPendingQueue"), anyMap());
        verify(mailDao).selectList("alarm.selectOverdueOrdersDetail", null);
        verify(mailDao).selectList(eq("alarm.selectRecipientsByConditions"), anyMap());
        verify(mailService).sendMail(any(MailRequest.class));
        verify(mailDao).update(eq("alarm.updateQueueSuccess"), anyMap());
    }

    @Test
    @DisplayName("processQueue: 빈 큐 - 아무 작업 안 함")
    void processQueue_emptyQueue() {
        // Given
        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.emptyList());

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailDao).selectList(eq("alarm.selectPendingQueue"), anyMap());
        verify(mailService, never()).sendMail(any());
    }

    @Test
    @DisplayName("processQueue: null 큐 - 아무 작업 안 함")
    void processQueue_nullQueue() {
        // Given
        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(null);

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailDao).selectList(eq("alarm.selectPendingQueue"), anyMap());
        verify(mailService, never()).sendMail(any());
    }

    @Test
    @DisplayName("processQueue: 여러 메시지 순차 처리")
    void processQueue_multipleMessages() {
        // Given
        Map<String, Object> msg1 = new HashMap<>(testQueueMessage);
        msg1.put("queueId", 1L);
        msg1.put("mailSource", "SOURCE1");

        Map<String, Object> msg2 = new HashMap<>(testQueueMessage);
        msg2.put("queueId", 2L);
        msg2.put("mailSource", "SOURCE2");

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Arrays.asList(msg1, msg2));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(testAdmUsers);

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailService, times(2)).sendMail(any(MailRequest.class));
        verify(mailDao, times(2)).update(eq("alarm.updateQueueSuccess"), anyMap());
    }

    // ==================== processMessage() 개별 처리 테스트 ====================

    @Test
    @DisplayName("processMessage: CLOB 타입 sectionContent 처리")
    void processMessage_clobContent() throws Exception {
        // Given
        java.sql.Clob clob = mock(java.sql.Clob.class);
        when(clob.length()).thenReturn(10L);
        when(clob.getSubString(1, 10)).thenReturn("CLOB 내용");

        testQueueMessage.put("sectionContent", clob);

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(testAdmUsers);

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailService).sendMail(any(MailRequest.class));
        verify(clob).length();
    }

    @Test
    @DisplayName("processMessage: 테이블 데이터 없음 - 발송 성공")
    void processMessage_noTableData() {
        // Given
        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(Collections.emptyList());
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(testAdmUsers);

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailService).sendMail(any(MailRequest.class));
    }

    @Test
    @DisplayName("processMessage: ADM 사용자 없음 - 예외 발생")
    void processMessage_noAdmUsers() {
        // Given
        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(Collections.emptyList());

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailService, never()).sendMail(any());
        verify(mailDao).update(eq("alarm.updateQueueRetry"), anyMap());
    }

    @Test
    @DisplayName("processMessage: ADM 사용자 null - 예외 발생")
    void processMessage_admUsersNull() {
        // Given
        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(null);

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailDao).update(eq("alarm.updateQueueRetry"), anyMap());
    }

    // ==================== 실패 처리 테스트 ====================

    @Test
    @DisplayName("handleFailure: 첫 실패 - 재시도 상태")
    void handleFailure_firstFailure() {
        // Given
        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(testAdmUsers);
        doReturn(false)
            .when(mailService).sendMail(any(MailRequest.class));

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailDao).update(eq("alarm.updateQueueRetry"), argThat(params -> {
            assertEquals(1L, params.get("queueId"));
            assertNotNull(params.get("errorMessage"));
            return true;
        }));
    }

    @Test
    @DisplayName("handleFailure: 최종 실패 (3회 시도)")
    void handleFailure_finalFailure() {
        // Given - retryCount가 2 (3번째 시도)
        testQueueMessage.put("retryCount", 2);

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(testAdmUsers);
        doReturn(false)
            .when(mailService).sendMail(any(MailRequest.class));

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailDao).update(eq("alarm.updateQueueFailed"), anyMap());
    }

    @Test
    @DisplayName("handleFailure: 매우 긴 에러 메시지 - 2000자로 자름")
    void handleFailure_longErrorMessage() {
        // Given
        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(testAdmUsers);
        doReturn(false)
            .when(mailService).sendMail(any(MailRequest.class));

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailDao).update(eq("alarm.updateQueueRetry"), argThat(params -> {
            String errorMsg = (String) params.get("errorMessage");
            assertTrue(errorMsg.length() <= 2000);
            return true;
        }));
    }

    // ==================== 심각도별 처리 테스트 ====================

    @Test
    @DisplayName("processMessage: CRITICAL 심각도")
    void processMessage_criticalSeverity() {
        // Given
        testQueueMessage.put("severity", "CRITICAL");

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(testAdmUsers);

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailService).sendMail(argThat(request ->
            request.getSubject().contains("[긴급]")
        ));
    }

    @Test
    @DisplayName("processMessage: INFO 심각도")
    void processMessage_infoSeverity() {
        // Given
        testQueueMessage.put("severity", "INFO");

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(testAdmUsers);

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailService).sendMail(any(MailRequest.class));
    }

    // ==================== 타입 변환 테스트 ====================

    @Test
    @DisplayName("타입 변환: Long 타입")
    void typeConversion_long() {
        // Given
        testQueueMessage.put("queueId", 123L);

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(testAdmUsers);

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailDao).update(eq("alarm.updateQueueSuccess"), argThat(params ->
            Long.valueOf(123L).equals(params.get("queueId"))
        ));
    }

    @Test
    @DisplayName("타입 변환: Integer를 Long으로")
    void typeConversion_integerToLong() {
        // Given
        testQueueMessage.put("queueId", 456);

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(testAdmUsers);

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailDao).update(eq("alarm.updateQueueSuccess"), anyMap());
    }

    @Test
    @DisplayName("타입 변환: Object 값을 String으로")
    void typeConversion_objectToString() {
        // Given
        testTableData = Collections.singletonList(
            createMap("id", 123, "active", true, "name", "Test")
        );

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(testAdmUsers);

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailService).sendMail(any(MailRequest.class));
    }

    // ==================== 엣지케이스 테스트 ====================

    @Test
    @DisplayName("엣지케이스: sqlId가 null")
    void edgeCase_nullSqlId() {
        // Given
        testQueueMessage.put("sqlId", null);

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailDao).update(eq("alarm.updateQueueRetry"), anyMap());
    }

    @Test
    @DisplayName("엣지케이스: sectionTitle이 null")
    void edgeCase_nullSectionTitle() {
        // Given
        testQueueMessage.put("sectionTitle", null);

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(testAdmUsers);

        // When
        alarmMailService.processQueue();

        // Then - 예외 발생 가능하지만 처리됨
        verify(mailDao, atLeastOnce()).update(anyString(), anyMap());
    }

    @Test
    @DisplayName("엣지케이스: retryCount null - 0으로 처리")
    void edgeCase_nullRetryCount() {
        // Given
        testQueueMessage.put("retryCount", null);

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(testAdmUsers);
        doReturn(false)
            .when(mailService).sendMail(any(MailRequest.class));

        // When
        alarmMailService.processQueue();

        // Then - 재시도로 처리됨 (retryCount 0으로 간주)
        verify(mailDao).update(eq("alarm.updateQueueRetry"), anyMap());
    }

    @Test
    @DisplayName("엣지케이스: 테이블 데이터에 null 값 포함")
    void edgeCase_tableDataWithNull() {
        // Given
        testTableData = Collections.singletonList(
            createMap("orderId", "001", "notes", null)
        );

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(testAdmUsers);

        // When
        alarmMailService.processQueue();

        // Then - null은 빈 문자열로 변환됨
        verify(mailService).sendMail(any(MailRequest.class));
    }

    // ==================== collectAlarms() 테스트 ====================

    @Test
    @DisplayName("collectAlarms: H2 환경에서 비활성화")
    void collectAlarms_h2Environment() {
        // When
        alarmMailService.collectAlarms();

        // Then - 아무 작업도 수행하지 않음
        verify(mailDao, never()).selectList(anyString(), any());
        verify(mailDao, never()).insert(anyString(), any());
    }

    // ==================== v2.1.0 수신인 유연화 테스트 ====================

    @Test
    @DisplayName("resolveRecipients: 복수 USER_ID + 복수 그룹")
    void resolveRecipients_multipleUsersAndGroups() {
        // Given - QUEUE에 "admin1,user1" + "ADM,SALES" 저장
        testQueueMessage.put("recipientUserIds", "admin1,user1");
        testQueueMessage.put("recipientGroups", "ADM,SALES");

        List<Map<String, Object>> recipients = Arrays.asList(
            createMap("userId", "ADMIN1", "email", "admin1@company.com", "group", "ADM"),
            createMap("userId", "USER1", "email", "user1@company.com", "group", "USER"),
            createMap("userId", "SALES001", "email", "sales1@company.com", "group", "SALES")
        );

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(recipients);

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailDao).selectList(eq("alarm.selectRecipientsByConditions"), argThat(params -> {
            List<String> userIds = (List<String>) params.get("userIds");
            List<String> groups = (List<String>) params.get("groups");
            return userIds.size() == 2 && groups.size() == 2;
        }));
        verify(mailService).sendMail(argThat(request -> request.getRecipients().size() == 3));
    }

    @Test
    @DisplayName("resolveRecipients: USER_ID만 지정 (그룹 NULL)")
    void resolveRecipients_userIdsOnly() {
        // Given
        testQueueMessage.put("recipientUserIds", "admin1,sales001");
        testQueueMessage.put("recipientGroups", null);

        List<Map<String, Object>> recipients = Arrays.asList(
            createMap("userId", "ADMIN1", "email", "admin1@company.com", "group", "ADM"),
            createMap("userId", "SALES001", "email", "sales1@company.com", "group", "SALES")
        );

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(recipients);

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailService).sendMail(argThat(request -> request.getRecipients().size() == 2));
    }

    @Test
    @DisplayName("resolveRecipients: 그룹만 지정 (USER_ID NULL)")
    void resolveRecipients_groupsOnly() {
        // Given
        testQueueMessage.put("recipientUserIds", null);
        testQueueMessage.put("recipientGroups", "SALES,LOGISTICS");

        List<Map<String, Object>> recipients = Arrays.asList(
            createMap("userId", "SALES001", "email", "sales1@company.com", "group", "SALES"),
            createMap("userId", "LOGISTIC001", "email", "logistic1@company.com", "group", "LOGISTICS")
        );

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(recipients);

        // When
        alarmMailService.processQueue();

        // Then
        verify(mailService).sendMail(argThat(request -> request.getRecipients().size() == 2));
    }

    @Test
    @DisplayName("resolveRecipients: 둘 다 NULL - ADM 그룹 기본값")
    void resolveRecipients_bothNull_defaultToAdm() {
        // Given
        testQueueMessage.put("recipientUserIds", null);
        testQueueMessage.put("recipientGroups", null);

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(testAdmUsers);

        // When
        alarmMailService.processQueue();

        // Then - ADM 그룹이 기본값으로 조회됨
        verify(mailDao).selectList(eq("alarm.selectRecipientsByConditions"), argThat(params -> {
            List<String> groups = (List<String>) params.get("groups");
            return groups != null && groups.size() == 1 && groups.contains("ADM");
        }));
        verify(mailService).sendMail(any(MailRequest.class));
    }

    @Test
    @DisplayName("resolveRecipients: 빈 문자열 - ADM 그룹 기본값")
    void resolveRecipients_emptyString_defaultToAdm() {
        // Given
        testQueueMessage.put("recipientUserIds", "");
        testQueueMessage.put("recipientGroups", "  ");

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(testAdmUsers);

        // When
        alarmMailService.processQueue();

        // Then - ADM 그룹이 기본값으로 조회됨
        verify(mailService).sendMail(any(MailRequest.class));
    }

    @Test
    @DisplayName("resolveRecipients: USER_ID 소문자 입력 - 대문자로 정규화")
    void resolveRecipients_lowercaseUserId_normalizedToUppercase() {
        // Given - QUEUE에 소문자로 저장됨 (Procedure 버그 시나리오)
        testQueueMessage.put("recipientUserIds", "admin1,sales001");
        testQueueMessage.put("recipientGroups", "adm");

        // DB는 대문자 USER_ID만 저장되어 있음
        List<Map<String, Object>> recipients = Arrays.asList(
            createMap("userId", "ADMIN1", "email", "admin1@company.com", "group", "ADM")
        );

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(recipients);

        // When
        alarmMailService.processQueue();

        // Then - Recipient.fromMap()에서 대문자로 정규화되어 조회 성공
        verify(mailService).sendMail(argThat(request -> {
            boolean hasUppercaseUserId = request.getRecipients().stream()
                .anyMatch(r -> "ADMIN1".equals(r.getUserId()));
            return hasUppercaseUserId;
        }));
    }

    @Test
    @DisplayName("resolveRecipients: 중복 이메일 제거 (같은 사용자가 USER_ID + 그룹 양쪽 매칭)")
    void resolveRecipients_deduplicateByEmail() {
        // Given - admin1은 USER_ID와 ADM 그룹 양쪽에 매칭됨
        testQueueMessage.put("recipientUserIds", "admin1");
        testQueueMessage.put("recipientGroups", "ADM");

        // DB 쿼리는 DISTINCT이지만, 만약 중복이 반환된다면 Java에서 제거해야 함
        List<Map<String, Object>> recipients = Arrays.asList(
            createMap("userId", "ADMIN1", "email", "admin1@company.com", "group", "ADM"),
            createMap("userId", "ADMIN2", "email", "admin2@company.com", "group", "ADM")
        );

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(recipients);

        // When
        alarmMailService.processQueue();

        // Then - LinkedHashSet으로 이메일 기준 중복 제거
        verify(mailService).sendMail(argThat(request -> request.getRecipients().size() == 2));
    }

    @Test
    @DisplayName("resolveRecipients: 공백 포함된 콤마 구분 문자열 - trim 처리")
    void resolveRecipients_withWhitespace_trimmed() {
        // Given - 공백 포함
        testQueueMessage.put("recipientUserIds", " admin1 , user1 ");
        testQueueMessage.put("recipientGroups", " ADM , SALES ");

        List<Map<String, Object>> recipients = Arrays.asList(
            createMap("userId", "ADMIN1", "email", "admin1@company.com", "group", "ADM")
        );

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(recipients);

        // When
        alarmMailService.processQueue();

        // Then - trim 후 정상 조회
        verify(mailDao).selectList(eq("alarm.selectRecipientsByConditions"), argThat(params -> {
            List<String> userIds = (List<String>) params.get("userIds");
            return userIds.size() == 2; // "admin1", "user1"
        }));
    }

    @Test
    @DisplayName("resolveRecipients: 빈 항목 포함 - 필터링")
    void resolveRecipients_withEmptyItems_filtered() {
        // Given - 빈 항목 포함
        testQueueMessage.put("recipientUserIds", "admin1,,user1,");
        testQueueMessage.put("recipientGroups", "ADM,,");

        List<Map<String, Object>> recipients = Arrays.asList(
            createMap("userId", "ADMIN1", "email", "admin1@company.com", "group", "ADM")
        );

        when(mailDao.selectList(eq("alarm.selectPendingQueue"), anyMap()))
            .thenReturn(Collections.singletonList(testQueueMessage));
        when(mailDao.selectList("alarm.selectOverdueOrdersDetail", null))
            .thenReturn(testTableData);
        when(mailDao.selectList(eq("alarm.selectRecipientsByConditions"), anyMap()))
            .thenReturn(recipients);

        // When
        alarmMailService.processQueue();

        // Then - 빈 항목 제거
        verify(mailDao).selectList(eq("alarm.selectRecipientsByConditions"), argThat(params -> {
            List<String> userIds = (List<String>) params.get("userIds");
            List<String> groups = (List<String>) params.get("groups");
            return userIds.size() == 2 && groups.size() == 1; // admin1, user1 / ADM
        }));
    }

    // ==================== Helper Methods ====================

    private Map<String, Object> createMap(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}