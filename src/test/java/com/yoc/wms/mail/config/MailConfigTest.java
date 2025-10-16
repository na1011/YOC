package com.yoc.wms.mail.config;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.*;

/**
 * MailConfig 단위 테스트
 *
 * 테스트 전략:
 * - 6개 시나리오: ReflectionTestUtils로 필드 주입 (단위 테스트, 초고속)
 * - 1개 통합 테스트: 별도 파일 (MailConfigPropertiesFileLoadingTest.java)
 */
public class MailConfigTest {

    // ==================== 헬퍼 메서드 ====================

    /**
     * MailConfig 인스턴스 생성 및 연락처 설정
     */
    private MailConfig createMailConfig(
            String name1, String email1,
            String name2, String email2,
            String name3, String email3) {
        MailConfig config = new MailConfig();
        ReflectionTestUtils.setField(config, "contactName1", name1);
        ReflectionTestUtils.setField(config, "contactEmail1", email1);
        ReflectionTestUtils.setField(config, "contactName2", name2);
        ReflectionTestUtils.setField(config, "contactEmail2", email2);
        ReflectionTestUtils.setField(config, "contactName3", name3);
        ReflectionTestUtils.setField(config, "contactEmail3", email3);
        return config;
    }

    // ==================== 단위 테스트 (ReflectionTestUtils) ====================

    @Test
    public void getContactInfo_defaultValues() {
        // Given: @Value 기본값 시뮬레이션
        MailConfig config = createMailConfig(
                "IT", "C20002_3000@test.co.kr",
                null, null,
                null, null
        );

        // When
        String contactInfo = config.getContactInfo();

        // Then
        assertNotNull(contactInfo);
        assertTrue(contactInfo.contains("IT"));
        assertTrue(contactInfo.contains("C20002_3000@test.co.kr"));
        assertFalse(contactInfo.contains("\n")); // 줄바꿈 없음 (1개만)
        assertEquals("IT: C20002_3000@test.co.kr", contactInfo);
    }

    @Test
    public void getContactInfo_allContacts() {
        // Given
        MailConfig config = createMailConfig(
                "IT", "it@company.com",
                "HR", "hr@company.com",
                "법무", "legal@company.com"
        );

        // When
        String contactInfo = config.getContactInfo();

        // Then
        assertNotNull(contactInfo);
        assertTrue(contactInfo.contains("IT: it@company.com"));
        assertTrue(contactInfo.contains("HR: hr@company.com"));
        assertTrue(contactInfo.contains("법무: legal@company.com"));

        // 줄바꿈 2개 확인
        int newlineCount = 0;
        for (int i = 0; i < contactInfo.length(); i++) {
            if (contactInfo.charAt(i) == '\n') {
                newlineCount++;
            }
        }
        assertEquals(2, newlineCount);

        // 순서 확인
        int itIndex = contactInfo.indexOf("IT");
        int hrIndex = contactInfo.indexOf("HR");
        int legalIndex = contactInfo.indexOf("법무");
        assertTrue(itIndex < hrIndex);
        assertTrue(hrIndex < legalIndex);
    }

    @Test
    public void getContactInfo_contact1And3Only() {
        // Given
        MailConfig config = createMailConfig(
                "IT", "it@company.com",
                null, null,  // contact2 없음
                "법무", "legal@company.com"
        );

        // When
        String contactInfo = config.getContactInfo();

        // Then
        assertNotNull(contactInfo);
        assertTrue(contactInfo.contains("IT: it@company.com"));
        assertTrue(contactInfo.contains("법무: legal@company.com"));
        assertFalse(contactInfo.contains("HR")); // contact2 없음

        // 줄바꿈 1개 확인
        int newlineCount = 0;
        for (int i = 0; i < contactInfo.length(); i++) {
            if (contactInfo.charAt(i) == '\n') {
                newlineCount++;
            }
        }
        assertEquals(1, newlineCount);
    }

    @Test
    public void getContactInfo_contact1Only() {
        // Given
        MailConfig config = createMailConfig(
                "IT Support", "support@company.com",
                null, null,  // contact2 없음
                null, null   // contact3 없음
        );

        // When
        String contactInfo = config.getContactInfo();

        // Then
        assertNotNull(contactInfo);
        assertEquals("IT Support: support@company.com", contactInfo);
        assertFalse(contactInfo.contains("\n")); // 줄바꿈 없음
    }

    @Test
    public void getContactInfo_emptyName() {
        // Given
        MailConfig config = createMailConfig(
                "IT", "it@company.com",
                "", "hr@company.com",  // name2가 빈 문자열
                null, null
        );

        // When
        String contactInfo = config.getContactInfo();

        // Then
        assertNotNull(contactInfo);
        assertEquals("IT: it@company.com", contactInfo);
        assertFalse(contactInfo.contains("hr@company.com")); // contact2 건너뜀
        assertFalse(contactInfo.contains("\n")); // 줄바꿈 없음
    }

    @Test
    public void getContactInfo_emptyEmail() {
        // Given
        MailConfig config = createMailConfig(
                "IT", "it@company.com",
                "HR", "",  // email2가 빈 문자열
                null, null
        );

        // When
        String contactInfo = config.getContactInfo();

        // Then
        assertNotNull(contactInfo);
        assertEquals("IT: it@company.com", contactInfo);
        assertFalse(contactInfo.contains("HR")); // contact2 건너뜀
        assertFalse(contactInfo.contains("\n")); // 줄바꿈 없음
    }
}
