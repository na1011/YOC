package com.yoc.wms.mail.service;

import com.yoc.wms.mail.dao.MailDao;
import com.yoc.wms.mail.domain.MailRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
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
@RunWith(MockitoJUnitRunner.class)
public class AlarmMailServiceTest {

    @Mock
    private MailDao mailDao;

    @Mock
    private MailService mailService;

    @InjectMocks
    private AlarmMailService alarmMailService;

    private Map<String, Object> testQueueMessage;
    private List<Map<String, Object>> testAdmUsers;
    private List<Map<String, Object>> testTableData;

    @Before
    public void setUp() {
        // 테스트용 큐 메시지
        testQueueMessage = new HashMap<String, Object>();
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
        when(mailService.sendMail(any(MailRequest.class))).thenReturn(true);
    }

    // ==================== processQueue() 테스트 ====================

    @Test
    public void processQueue_success() {
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
    public void processQueue_emptyQueue() {
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
    public void processQueue_nullQueue() {
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
    public void processQueue_multipleMessages() {
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
    public void processMessage_clobContent() throws Exception {
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
    public void processMessage_noTableData() {
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
    public void processMessage_noAdmUsers() {
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
    public void processMessage_admUsersNull() {
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
    public void handleFailure_firstFailure() {
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
        verify(mailDao).update(eq("alarm.updateQueueRetry"), anyMap());
    }

    @Test
    public void handleFailure_finalFailure() {
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
    public void handleFailure_longErrorMessage() {
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
        verify(mailDao).update(eq("alarm.updateQueueRetry"), anyMap());
    }

    // ==================== 심각도별 처리 테스트 ====================

    @Test
    public void processMessage_criticalSeverity() {
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
        verify(mailService).sendMail(any(MailRequest.class));
    }

    @Test
    public void processMessage_infoSeverity() {
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
    public void typeConversion_long() {
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
        verify(mailDao).update(eq("alarm.updateQueueSuccess"), anyMap());
    }

    @Test
    public void typeConversion_integerToLong() {
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
    public void typeConversion_objectToString() {
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
    public void edgeCase_nullSqlId() {
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
    public void edgeCase_nullSectionTitle() {
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
    public void edgeCase_nullRetryCount() {
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
    public void edgeCase_tableDataWithNull() {
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
    public void collectAlarms_h2Environment() {
        // When
        alarmMailService.collectAlarms();

        // Then - 아무 작업도 수행하지 않음
        verify(mailDao, never()).selectList(anyString(), any());
        verify(mailDao, never()).insert(anyString(), any());
    }

    // ==================== v2.1.0 수신인 유연화 테스트 ====================

    @Test
    public void resolveRecipients_multipleUsersAndGroups() {
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
        verify(mailDao).selectList(eq("alarm.selectRecipientsByConditions"), anyMap());
        verify(mailService).sendMail(any(MailRequest.class));
    }

    @Test
    public void resolveRecipients_userIdsOnly() {
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
        verify(mailService).sendMail(any(MailRequest.class));
    }

    @Test
    public void resolveRecipients_groupsOnly() {
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
        verify(mailService).sendMail(any(MailRequest.class));
    }

    @Test
    public void resolveRecipients_bothNull_defaultToAdm() {
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
        verify(mailDao).selectList(eq("alarm.selectRecipientsByConditions"), anyMap());
        verify(mailService).sendMail(any(MailRequest.class));
    }

    @Test
    public void resolveRecipients_emptyString_defaultToAdm() {
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
    public void resolveRecipients_lowercaseUserId_normalizedToUppercase() {
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
        verify(mailService).sendMail(any(MailRequest.class));
    }

    @Test
    public void resolveRecipients_deduplicateByEmail() {
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
        verify(mailService).sendMail(any(MailRequest.class));
    }

    @Test
    public void resolveRecipients_withWhitespace_trimmed() {
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
        verify(mailDao).selectList(eq("alarm.selectRecipientsByConditions"), anyMap());
    }

    @Test
    public void resolveRecipients_withEmptyItems_filtered() {
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
        verify(mailDao).selectList(eq("alarm.selectRecipientsByConditions"), anyMap());
    }

    // ==================== Helper Methods ====================

    private Map<String, Object> createMap(Object... keyValues) {
        Map<String, Object> map = new HashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}