package com.yoc.wms.mail.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MailConfig 단위 테스트
 *
 * 테스트 전략:
 * - 6개 시나리오: ReflectionTestUtils로 필드 주입 (단위 테스트, 초고속)
 * - 1개 통합 테스트: 실제 mail-config.properties 파일 로딩 검증
 */
class MailConfigTest {

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
    @DisplayName("getContactInfo: properties 없을 때 - contact1 기본값만 출력")
    void getContactInfo_defaultValues() {
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
    @DisplayName("getContactInfo: contact 1~3 모두 있을 때 - 3줄 출력")
    void getContactInfo_allContacts() {
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
        long newlineCount = contactInfo.chars().filter(ch -> ch == '\n').count();
        assertEquals(2, newlineCount);

        // 순서 확인
        int itIndex = contactInfo.indexOf("IT");
        int hrIndex = contactInfo.indexOf("HR");
        int legalIndex = contactInfo.indexOf("법무");
        assertTrue(itIndex < hrIndex);
        assertTrue(hrIndex < legalIndex);
    }

    @Test
    @DisplayName("getContactInfo: contact 1과 3만 있을 때 - 2줄 출력 (contact2 건너뜀)")
    void getContactInfo_contact1And3Only() {
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
        long newlineCount = contactInfo.chars().filter(ch -> ch == '\n').count();
        assertEquals(1, newlineCount);
    }

    @Test
    @DisplayName("getContactInfo: contact 1만 있을 때 - 1줄 출력 (contact2~3 건너뜀)")
    void getContactInfo_contact1Only() {
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
    @DisplayName("getContactInfo: contact2의 name이 빈 문자열일 때 - 건너뜀")
    void getContactInfo_emptyName() {
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
    @DisplayName("getContactInfo: contact2의 email이 빈 문자열일 때 - 건너뜀")
    void getContactInfo_emptyEmail() {
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

    // ==================== 통합 테스트 (실제 파일 로딩) ====================

    @Nested
    @SpringBootTest
    @DisplayName("mail-config.properties 파일 로딩 통합 테스트")
    class PropertiesFileLoadingTest {
        @Autowired
        private MailConfig mailConfig;

        @Test
        @DisplayName("mail-config.properties에서 contact 정보 제대로 로딩되는지 검증")
        void getContactInfo_loadFromPropertiesFile() {
            // When
            String contactInfo = mailConfig.getContactInfo();

            // Then
            assertNotNull(contactInfo);

            // mail-config.properties 파일에 설정된 실제 값 검증
            assertTrue(contactInfo.contains("김찬기"),
                    "mail-config.properties의 contact name이 로딩되지 않음");
            assertTrue(contactInfo.contains("chanki_kim@youngone.co.kr"),
                    "mail-config.properties의 contact email이 로딩되지 않음");

            // contact.2는 없고, contact.1과 contact.3만 있으므로 줄바꿈 1개
            long newlineCount = contactInfo.chars().filter(ch -> ch == '\n').count();
            assertEquals(1, newlineCount);

            // 기본값이 아닌 실제 파일 값이 로딩되었는지 확인
            assertFalse(contactInfo.contains("IT"),
                    "기본값(IT)이 사용되어서는 안 됨. @PropertySource가 제대로 동작하지 않음");
            assertFalse(contactInfo.contains("C20002_3000@test.co.kr"),
                    "기본값 이메일이 사용되어서는 안 됨. properties 파일 로딩 실패");
        }
    }
}
