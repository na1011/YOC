package com.yoc.wms.mail.util;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 테스트용 Fake JavaMailSender
 *
 * 실제 SMTP 발송 없이 메일 객체만 저장합니다.
 * 통합 테스트에서 Real MailService와 함께 사용됩니다.
 *
 * Features:
 * - 순수 Java (Mockito 불필요)
 * - 운영 환경 100% 호환 (Spring 3.1.2)
 * - 실패 시뮬레이션 지원
 * - 발송 이력 저장
 *
 * Usage:
 *   FakeMailSender fake = (FakeMailSender) mailSender;
 *   fake.setShouldFail(true);  // 실패 시뮬레이션
 *   assertEquals(1, fake.getSentCount());
 *
 * @author 김찬기
 * @since v2.4.0 (Chicago School 테스트 아키텍처)
 */
public class FakeMailSender implements JavaMailSender {

    private final List<MimeMessage> sentMessages = new ArrayList<>();
    private boolean shouldFail = false;
    private int sendCallCount = 0;

    @Override
    public MimeMessage createMimeMessage() {
        // Mock Session 사용 (실제 SMTP 연결 없음)
        Properties props = new Properties();
        Session session = Session.getInstance(props);
        return new MimeMessage(session);
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
        return createMimeMessage();
    }

    @Override
    public void send(MimeMessage mimeMessage) throws MailException {
        sendCallCount++;

        if (shouldFail) {
            throw new RuntimeException("Fake SMTP Error (Simulated)");
        }

        sentMessages.add(mimeMessage);
    }

    @Override
    public void send(MimeMessage... mimeMessages) throws MailException {
        for (MimeMessage msg : mimeMessages) {
            send(msg);
        }
    }

    @Override
    public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
        try {
            MimeMessage mimeMessage = createMimeMessage();
            mimeMessagePreparator.prepare(mimeMessage);
            send(mimeMessage);
        } catch (Exception e) {
            throw new RuntimeException("Failed to prepare message", e);
        }
    }

    @Override
    public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
        for (MimeMessagePreparator preparator : mimeMessagePreparators) {
            send(preparator);
        }
    }

    @Override
    public void send(SimpleMailMessage simpleMessage) throws MailException {
        sendCallCount++;
        if (shouldFail) {
            throw new RuntimeException("Fake SMTP Error (Simulated)");
        }
        // SimpleMailMessage는 사용 안 함 (MimeMessage만 사용)
    }

    @Override
    public void send(SimpleMailMessage... simpleMessages) throws MailException {
        for (SimpleMailMessage msg : simpleMessages) {
            send(msg);
        }
    }

    // ===== 테스트 헬퍼 메서드 =====

    /**
     * 발송된 메시지 개수 반환
     */
    public int getSentCount() {
        return sentMessages.size();
    }

    /**
     * send() 호출 횟수 반환 (실패 포함)
     */
    public int getSendCallCount() {
        return sendCallCount;
    }

    /**
     * 발송된 메시지 목록 반환
     */
    public List<MimeMessage> getSentMessages() {
        return new ArrayList<>(sentMessages);
    }

    /**
     * 실패 시뮬레이션 설정
     *
     * @param shouldFail true이면 send() 호출 시 예외 발생
     */
    public void setShouldFail(boolean shouldFail) {
        this.shouldFail = shouldFail;
    }

    /**
     * 상태 초기화 (다음 테스트를 위해)
     */
    public void reset() {
        sentMessages.clear();
        shouldFail = false;
        sendCallCount = 0;
    }

    /**
     * 특정 인덱스의 메시지 반환
     */
    public MimeMessage getMessage(int index) {
        if (index < 0 || index >= sentMessages.size()) {
            return null;
        }
        return sentMessages.get(index);
    }

    /**
     * 마지막 발송 메시지 반환
     */
    public MimeMessage getLastMessage() {
        if (sentMessages.isEmpty()) {
            return null;
        }
        return sentMessages.get(sentMessages.size() - 1);
    }
}
