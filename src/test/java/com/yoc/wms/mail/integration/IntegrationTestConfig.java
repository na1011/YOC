package com.yoc.wms.mail.integration;

import com.yoc.wms.mail.util.FakeMailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 통합 테스트용 설정
 *
 * Real Components를 사용하되, JavaMailSender만 Fake로 교체합니다.
 * 이를 통해 실제 SMTP 발송 없이 통합 테스트를 수행합니다.
 *
 * Architecture:
 * - AlarmMailService: Real (실제 로직 테스트)
 * - MailService: Real (실제 발송 로직 테스트)
 * - MailDao: Real (H2 In-Memory)
 * - JavaMailSender: Fake (SMTP 발송 방지)
 *
 * Usage:
 *   @RunWith(SpringJUnit4ClassRunner.class)
 *   @SpringBootTest
 *   @Import(IntegrationTestConfig.class)
 *   public class IntegrationTest {
 *       @Autowired
 *       private JavaMailSender mailSender;  // FakeMailSender 주입됨
 *   }
 *
 * 운영 환경 호환성:
 * - @TestConfiguration: Spring 3.1.2 호환 (Spring Boot 기능)
 * - FakeMailSender: 순수 Java (Mockito 불필요)
 *
 * @author 김찬기
 * @since v2.4.0 (Chicago School 테스트 아키텍처)
 */
@TestConfiguration
public class IntegrationTestConfig {

    /**
     * Fake JavaMailSender 빈 생성
     *
     * @Primary를 사용하여 실제 JavaMailSender 대신 주입됩니다.
     * 통합 테스트에서 실제 SMTP 발송을 방지합니다.
     *
     * @return FakeMailSender 인스턴스
     */
    @Bean
    @Primary
    public JavaMailSender fakeMailSender() {
        return new FakeMailSender();
    }
}
