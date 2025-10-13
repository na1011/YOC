package com.yoc.wms.mail.service;

import com.yoc.wms.mail.config.MailConfig;
import com.yoc.wms.mail.dao.MailDao;
import com.yoc.wms.mail.domain.MailRequest;
import com.yoc.wms.mail.domain.Recipient;
import com.yoc.wms.mail.domain.MailSection;
import com.yoc.wms.mail.enums.SendStatus;
import com.yoc.wms.mail.exception.ValueChainException;
import com.yoc.wms.mail.renderer.MailBodyRenderer;
import com.yoc.wms.mail.util.MailUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.internet.MimeMessage;

import java.net.InetAddress;
import java.util.*;

/**
 * 메일 발송 서비스 (공통화 및 SRP 원칙)
 *
 *  @author 김찬기
 *  @since 1.0
 */
@Service
public class MailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private MailDao mailDao;

    @Autowired
    private MailBodyRenderer renderer;

    @Autowired
    private MailConfig mailConfig;

    private static final int MAX_RETRY_COUNT = 3;
    private static final long[] RETRY_DELAYS = {5000L, 10000L, 20000L};

    /**
     * 메일 발송 (공통 진입점)
     *
     * @param request 메일 요청 DTO
     */
    @Transactional
    public void sendMail(MailRequest request) {
        try {
            // 1. 수신인 검증
            MailUtils.validateRecipients(request.getRecipients());

            // 2. 섹션에 연락처 추가
            List<MailSection> sections = new ArrayList<>(request.getSections());
            sections.addAll(MailSection.forContact(mailConfig.getContactInfo()));

            // 3. HTML 생성 (완전한 문서 구조)
            String htmlBody = renderer.renderWithStructure(
                    sections,
                    mailConfig.getSystemTitle(),
                    mailConfig.getFooterMessage()
            );

            // 4. 로그 생성
            Long logId = createLog(
                    request.getMailType(),
                    request.getMailSource(),
                    request.getSubject(),
                    request.getRecipients(),
                    request.getCcRecipients(),
                    htmlBody
            );

            // 5. 메일 발송 (재시도 포함)
            sendWithRetry(
                    request.getRecipients(),
                    request.getCcRecipients(),
                    request.getSubject(),
                    htmlBody,
                    logId
            );

        } catch (Exception e) {
            throw new ValueChainException("메일 발송 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 발송 로그 생성 (통합)
     */
    private Long createLog(String mailType, String mailSource, String subject,
                           List<Recipient> recipients, List<Recipient> ccRecipients,
                           String bodyHtml) {
        Map<String, Object> logParams = new HashMap<>();
        logParams.put("mailType", mailType);
        logParams.put("mailSource", mailSource);
        logParams.put("subject", subject);
        logParams.put("recipients", MailUtils.formatRecipientsToString(recipients));
        logParams.put("ccRecipients", MailUtils.formatRecipientsToString(ccRecipients));
        logParams.put("bodyHtml", bodyHtml);
        logParams.put("sendStatus", "PENDING");
        logParams.put("errorMessage", null);
        logParams.put("retryCount", 0);
        logParams.put("sendDate", null);
        logParams.put("senderEmail", "wms-noreply@youngone.co.kr");
        logParams.put("ipAddress", getLocalIpAddress());

        mailDao.insert("mail.insertMailSendLog", logParams);
        return (Long) logParams.get("logId");
    }

    /**
     * 로컬 IP 주소 조회
     */
    private String getLocalIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 재시도 포함 발송
     */
    private void sendWithRetry(List<Recipient> recipients, List<Recipient> ccRecipients,
                               String subject, String htmlBody, Long logId) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRY_COUNT) {
            try {
                doSendMail(recipients, ccRecipients, subject, htmlBody);

                // 성공
                updateLogStatus(logId, SendStatus.SUCCESS, null);
                return;

            } catch (Exception e) {
                lastException = e;
                attempt++;

                if (attempt < MAX_RETRY_COUNT) {
                    try {
                        Thread.sleep(RETRY_DELAYS[attempt - 1]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        // 실패
        updateLogStatus(logId, SendStatus.FAILURE, lastException.getMessage());
        throw new ValueChainException(MAX_RETRY_COUNT + "회 재시도 후 메일 발송 실패", lastException);
    }

    /**
     * 실제 메일 발송 (일괄)
     * Spring 3.2 ASM 호환성을 위해 for-loop 사용 (lambda/method reference 제거)
     */
    private void doSendMail(List<Recipient> recipients, List<Recipient> ccRecipients,
                            String subject, String htmlBody) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom("wms-noreply@youngone.co.kr", "WMS 시스템");

        // TO 수신인 변환
        String[] toEmails = new String[recipients.size()];
        for (int i = 0; i < recipients.size(); i++) {
            toEmails[i] = recipients.get(i).getEmail();
        }
        helper.setTo(toEmails);

        // CC 수신인 변환
        if (ccRecipients != null && !ccRecipients.isEmpty()) {
            String[] ccEmails = new String[ccRecipients.size()];
            for (int i = 0; i < ccRecipients.size(); i++) {
                ccEmails[i] = ccRecipients.get(i).getEmail();
            }
            helper.setCc(ccEmails);
        }

        helper.setSubject(subject);
        helper.setText(htmlBody, true);

        mailSender.send(message);
    }

    /**
     * 로그 상태 업데이트
     */
    private void updateLogStatus(Long logId, SendStatus status, String errorMessage) {
        Map<String, Object> params = new HashMap<>();
        params.put("logId", logId);
        params.put("sendStatus", status.name());
        params.put("errorMessage", errorMessage);
        params.put("sendDate", new Date());

        mailDao.update("mail.updateMailSendLogStatus", params);
    }
}