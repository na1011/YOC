package com.yoc.wms.mail.config;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;

/**
 * MailConfig mail-config.properties 파일 로딩 통합 테스트
 *
 * MailConfigTest에서 분리된 통합 테스트
 * 실제 mail-config.properties 파일이 제대로 로딩되는지 검증
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
public class MailConfigPropertiesFileLoadingTest {

    @Autowired
    private MailConfig mailConfig;

    @Test
    public void getContactInfo_loadFromPropertiesFile() {
        // When
        String contactInfo = mailConfig.getContactInfo();

        // Then
        assertNotNull(contactInfo);

        // mail-config.properties 파일에 설정된 실제 값 검증
        assertTrue("mail-config.properties의 contact name이 로딩되지 않음",
                contactInfo.contains("김찬기"));
        assertTrue("mail-config.properties의 contact email이 로딩되지 않음",
                contactInfo.contains("chanki_kim@youngone.co.kr"));

        // contact.2는 없고, contact.1과 contact.3만 있으므로 줄바꿈 1개
        long newlineCount = 0;
        for (int i = 0; i < contactInfo.length(); i++) {
            if (contactInfo.charAt(i) == '\n') {
                newlineCount++;
            }
        }
        assertEquals(1, newlineCount);

        // 기본값이 아닌 실제 파일 값이 로딩되었는지 확인
        assertFalse("기본값(IT)이 사용되어서는 안 됨. @PropertySource가 제대로 동작하지 않음",
                contactInfo.contains("IT"));
        assertFalse("기본값 이메일이 사용되어서는 안 됨. properties 파일 로딩 실패",
                contactInfo.contains("C20002_3000@test.co.kr"));
    }
}
