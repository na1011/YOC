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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.internet.MimeMessage;

import java.net.InetAddress;
import java.util.*;

/**
 * 메일 발송 서비스 (단순화됨)
 *
 * <h3>설계 원칙</h3>
 * <ul>
 *   <li>템플릿 의존성 제거 - MailSection 기반으로 통일</li>
 *   <li>단일 책임 - HTML 생성, 발송, 로깅만 수행</li>
 *   <li>Factory Pattern - MailSection.forAlarm() 등 활용</li>
 * </ul>
 *
 * <h3>호출 방법</h3>
 * <pre>
 * // MailRequest 사용
 * MailRequest request = MailRequest.builder()
 *     .subject("제목")
 *     .addAllSections(MailSection.forAlarm(...))
 *     .recipients(recipients)
 *     .mailType("ALARM")
 *     .build();
 * mailService.sendMail(request);
 * </pre>
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
    public void sendMail(MailRequest request) {
        sendMailAsync(request);
    }

    /**
     * 비동기 메일 발송
     */
    @Async
    @Transactional
    protected void sendMailAsync(MailRequest request) {
        try {
            // 1. 수신인 검증
            MailUtils.validateRecipients(request.getRecipients());

            // 2. 섹션에 연락처 추가
            List<MailSection> sections = new ArrayList<>(request.getSections());
            sections.addAll(MailSection.forContact(mailConfig.getContactInfo()));

            // 3. HTML 생성
            String htmlBody = wrapWithHtmlStructure(renderer.render(sections));

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
     * HTML 전체 구조로 감싸기
     */
    private String wrapWithHtmlStructure(String body) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html>");
        html.append("<head><meta charset='UTF-8'></head>");
        html.append("<body style='font-family: Arial, sans-serif; padding: 20px;'>");
        html.append("<div style='max-width: 800px; margin: 0 auto;'>");
        html.append("<h2 style='color: #333; border-bottom: 3px solid #4CAF50; padding-bottom: 10px;'>WMS 시스템 알림</h2>");
        html.append(body);
        html.append("<div style='margin-top: 30px; padding-top: 20px; border-top: 1px solid #ddd; color: #999; font-size: 12px;'>");
        html.append("본 메일은 WMS 시스템에서 자동 발송되었습니다.");
        html.append("</div>");
        html.append("</div>");
        html.append("</body>");
        html.append("</html>");
        return html.toString();
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
     */
    private void doSendMail(List<Recipient> recipients, List<Recipient> ccRecipients,
                            String subject, String htmlBody) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom("wms-noreply@youngone.co.kr", "WMS 시스템");

        String[] toEmails = recipients.stream()
                .map(Recipient::getEmail)
                .toArray(String[]::new);
        helper.setTo(toEmails);

        if (ccRecipients != null && !ccRecipients.isEmpty()) {
            String[] ccEmails = ccRecipients.stream()
                    .map(Recipient::getEmail)
                    .toArray(String[]::new);
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