/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package org.thingsboard.server.service.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.NestedRuntimeException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thingsboard.rule.engine.api.MailService;
import org.thingsboard.server.common.data.AdminSettings;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.blob.BlobEntity;
import org.thingsboard.server.common.data.ApiFeature;
import org.thingsboard.server.common.data.ApiUsageRecordKey;
import org.thingsboard.server.common.data.ApiUsageStateMailMessage;
import org.thingsboard.server.common.data.ApiUsageStateValue;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.BlobEntityId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.blob.BlobEntityService;
import org.thingsboard.server.dao.exception.IncorrectParameterException;
import org.thingsboard.server.dao.settings.AdminSettingsService;
import org.thingsboard.server.common.stats.TbApiUsageReportClient;
import org.thingsboard.server.service.apiusage.TbApiUsageStateService;

import javax.activation.DataSource;
import javax.mail.internet.MimeMessage;
import javax.mail.util.ByteArrayDataSource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service
@Slf4j
public class DefaultMailService implements MailService {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static final String MAIL_PROP = "mail.";
    public static final String TARGET_EMAIL = "targetEmail";
    public static final String UTF_8 = "UTF-8";

    public static final int _10K = 10000;
    public static final int _1M = 1000000;

    private final AdminSettingsService adminSettingsService;
    private final AttributesService attributesService;
    private final BlobEntityService blobEntityService;
    private final TbApiUsageReportClient apiUsageClient;

    @Lazy
    @Autowired
    private TbApiUsageStateService apiUsageStateService;

    @Value("${actors.rule.allow_system_mail_service}")
    private boolean allowSystemMailService;

    public DefaultMailService(AdminSettingsService adminSettingsService, AttributesService attributesService, BlobEntityService blobEntityService, TbApiUsageReportClient apiUsageClient) {
        this.adminSettingsService = adminSettingsService;
        this.attributesService = attributesService;
        this.blobEntityService = blobEntityService;
        this.apiUsageClient = apiUsageClient;
    }

    @Override
    public void sendEmail(TenantId tenantId, String email, String subject, String message) throws ThingsboardException {
        sendMail(tenantId, email, subject, message);
    }

    @Override
    public void sendTestMail(TenantId tenantId, JsonNode jsonConfig, String email) throws ThingsboardException {
        JavaMailSenderImpl testMailSender = createMailSender(jsonConfig);
        String mailFrom = getStringValue(jsonConfig, "mailFrom");

        JsonNode mailTemplates = getConfig(tenantId, "mailTemplates");
        String subject = MailTemplates.subject(mailTemplates, MailTemplates.TEST);

        Map<String, Object> model = new HashMap<>();
        model.put(TARGET_EMAIL, email);

        String message = body(mailTemplates, MailTemplates.TEST, model);

        sendMail(testMailSender, mailFrom, email, subject, message);
    }

    @Override
    public void sendActivationEmail(TenantId tenantId, String activationLink, String email) throws ThingsboardException {

        JsonNode mailTemplates = getConfig(tenantId, "mailTemplates");
        String subject = MailTemplates.subject(mailTemplates, MailTemplates.ACTIVATION);

        Map<String, Object> model = new HashMap<>();
        model.put("activationLink", activationLink);
        model.put(TARGET_EMAIL, email);

        String message = body(mailTemplates, MailTemplates.ACTIVATION, model);

        sendMail(tenantId, email, subject, message);
    }

    @Override
    public void sendAccountActivatedEmail(TenantId tenantId, String loginLink, String email) throws ThingsboardException {

        JsonNode mailTemplates = getConfig(tenantId, "mailTemplates");
        String subject = MailTemplates.subject(mailTemplates, MailTemplates.ACCOUNT_ACTIVATED);

        Map<String, Object> model = new HashMap<>();
        model.put("loginLink", loginLink);
        model.put(TARGET_EMAIL, email);

        String message = body(mailTemplates, MailTemplates.ACCOUNT_ACTIVATED, model);

        sendMail(tenantId, email, subject, message);
    }

    @Override
    public void sendResetPasswordEmail(TenantId tenantId, String passwordResetLink, String email) throws ThingsboardException {

        JsonNode mailTemplates = getConfig(tenantId, "mailTemplates");
        String subject = MailTemplates.subject(mailTemplates, MailTemplates.RESET_PASSWORD);

        Map<String, Object> model = new HashMap<>();
        model.put("passwordResetLink", passwordResetLink);
        model.put(TARGET_EMAIL, email);

        String message = body(mailTemplates, MailTemplates.RESET_PASSWORD, model);

        sendMail(tenantId, email, subject, message);
    }

    @Override
    public void sendPasswordWasResetEmail(TenantId tenantId, String loginLink, String email) throws ThingsboardException {

        JsonNode mailTemplates = getConfig(tenantId, "mailTemplates");
        String subject = MailTemplates.subject(mailTemplates, MailTemplates.PASSWORD_WAS_RESET);

        Map<String, Object> model = new HashMap<>();
        model.put("loginLink", loginLink);
        model.put(TARGET_EMAIL, email);

        String message = body(mailTemplates, MailTemplates.PASSWORD_WAS_RESET, model);

        sendMail(tenantId, email, subject, message);
    }

    @Override
    public void sendUserActivatedEmail(TenantId tenantId, String userFullName, String userEmail, String targetEmail) throws ThingsboardException {
        JsonNode mailTemplates = getConfig(tenantId, "mailTemplates");
        String subject = MailTemplates.subject(mailTemplates, MailTemplates.USER_ACTIVATED);

        Map<String, Object> model = new HashMap<>();
        model.put("userFullName", userFullName);
        model.put("userEmail", userEmail);
        model.put(TARGET_EMAIL, targetEmail);

        String message = body(mailTemplates, MailTemplates.USER_ACTIVATED, model);

        sendMail(tenantId, targetEmail, subject, message);
    }

    @Override
    public void sendUserRegisteredEmail(TenantId tenantId, String userFullName, String userEmail, String targetEmail) throws ThingsboardException {
        JsonNode mailTemplates = getConfig(tenantId, "mailTemplates");
        String subject = MailTemplates.subject(mailTemplates, MailTemplates.USER_REGISTERED);

        Map<String, Object> model = new HashMap<>();
        model.put("userFullName", userFullName);
        model.put("userEmail", userEmail);
        model.put(TARGET_EMAIL, targetEmail);

        String message = body(mailTemplates, MailTemplates.USER_REGISTERED, model);

        sendMail(tenantId, targetEmail, subject, message);
    }

    private void sendMail(TenantId tenantId, String email,
                          String subject, String message) throws ThingsboardException {
        JsonNode jsonConfig = getConfig(tenantId, "mail");
        JavaMailSenderImpl mailSender = createMailSender(jsonConfig);
        String mailFrom = getStringValue(jsonConfig, "mailFrom");
        sendMail(mailSender, mailFrom, email, subject, message);
    }

    @Override
    public void send(TenantId tenantId, String from, String to, String cc, String bcc, String subject, String body, List<BlobEntityId> attachments) throws ThingsboardException {
        ConfigEntry configEntry = getConfig(tenantId, "mail", allowSystemMailService);
        JsonNode jsonConfig = configEntry.jsonConfig;
        if (!configEntry.isSystem || apiUsageStateService.getApiUsageState(tenantId).isEmailSendEnabled()) {
            JavaMailSenderImpl mailSender = createMailSender(jsonConfig);
            String mailFrom = getStringValue(jsonConfig, "mailFrom");
            try {
                MimeMessage mailMsg = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mailMsg, attachments != null && !attachments.isEmpty(), "UTF-8");
                helper.setFrom(StringUtils.isBlank(from) ? mailFrom : from);
                helper.setTo(to.split("\\s*,\\s*"));
                if (!StringUtils.isBlank(cc)) {
                    helper.setCc(cc.split("\\s*,\\s*"));
                }
                if (!StringUtils.isBlank(bcc)) {
                    helper.setBcc(bcc.split("\\s*,\\s*"));
                }
                helper.setSubject(subject);
                helper.setText(body);
                if (attachments != null) {
                    for (BlobEntityId blobEntityId : attachments) {
                        BlobEntity blobEntity = blobEntityService.findBlobEntityById(tenantId, blobEntityId);
                        if (blobEntity != null) {
                            DataSource dataSource = new ByteArrayDataSource(blobEntity.getData().array(), blobEntity.getContentType());
                            helper.addAttachment(blobEntity.getName(), dataSource);
                        }
                    }
                }
                mailSender.send(helper.getMimeMessage());
                if (configEntry.isSystem) {
                    apiUsageClient.report(tenantId, ApiUsageRecordKey.EMAIL_EXEC_COUNT, 1);
                }
            } catch (Exception e) {
                throw handleException(e);
            }
        } else  {
            throw new RuntimeException("Email sending is disabled due to API limits!");
        }
    }

    @Override
    public void sendAccountLockoutEmail(TenantId tenantId, String lockoutEmail, String email, Integer maxFailedLoginAttempts) throws ThingsboardException {
        JsonNode mailTemplates = getConfig(tenantId, "mailTemplates");
        String subject = MailTemplates.subject(mailTemplates, MailTemplates.ACCOUNT_LOCKOUT);

        Map<String, Object> model = new HashMap<>();
        model.put("lockoutAccount", lockoutEmail);
        model.put("maxFailedLoginAttempts", maxFailedLoginAttempts);
        model.put(TARGET_EMAIL, email);

        String message = body(mailTemplates, MailTemplates.ACCOUNT_LOCKOUT, model);

        sendMail(tenantId, email, subject, message);
    }

    @Override
    public void sendApiFeatureStateEmail(TenantId tenantId, ApiFeature apiFeature, ApiUsageStateValue stateValue, String email, ApiUsageStateMailMessage msg) throws ThingsboardException {
        JsonNode mailTemplates = getConfig(null, "mailTemplates");
        String subject = null;

        Map<String, Object> model = new HashMap<>();
        model.put("apiFeature", apiFeature.getLabel());
        model.put(TARGET_EMAIL, email);

        String message = null;

        switch (stateValue) {
            case ENABLED:
                model.put("apiLabel", toEnabledValueLabel(apiFeature));
                message = body(mailTemplates, MailTemplates.API_USAGE_STATE_ENABLED, model);
                subject = MailTemplates.subject(mailTemplates, MailTemplates.API_USAGE_STATE_ENABLED);
                break;
            case WARNING:
                model.put("apiValueLabel", toDisabledValueLabel(apiFeature) + " " + toWarningValueLabel(msg.getKey(), msg.getValue(), msg.getThreshold()));
                message = body(mailTemplates, MailTemplates.API_USAGE_STATE_WARNING, model);
                subject = MailTemplates.subject(mailTemplates, MailTemplates.API_USAGE_STATE_WARNING);
                break;
            case DISABLED:
                model.put("apiLimitValueLabel", toDisabledValueLabel(apiFeature) + " " + toDisabledValueLabel(msg.getKey(), msg.getThreshold()));
                message = body(mailTemplates, MailTemplates.API_USAGE_STATE_DISABLED, model);
                subject = MailTemplates.subject(mailTemplates, MailTemplates.API_USAGE_STATE_DISABLED);
                break;
        }
        sendMail(tenantId, email, subject, message);
    }

    private String toEnabledValueLabel(ApiFeature apiFeature) {
        switch (apiFeature) {
            case DB:
                return "save";
            case TRANSPORT:
                return "receive";
            case JS:
                return "invoke";
            case RE:
                return "process";
            case EMAIL:
            case SMS:
                return "send";
            default:
                throw new RuntimeException("Not implemented!");
        }
    }

    private String toDisabledValueLabel(ApiFeature apiFeature) {
        switch (apiFeature) {
            case DB:
                return "saved";
            case TRANSPORT:
                return "received";
            case JS:
                return "invoked";
            case RE:
                return "processed";
            case EMAIL:
            case SMS:
                return "sent";
            default:
                throw new RuntimeException("Not implemented!");
        }
    }

    private String toWarningValueLabel(ApiUsageRecordKey key, long value, long threshold) {
        String valueInM = getValueAsString(value);
        String thresholdInM = getValueAsString(threshold);
        switch (key) {
            case STORAGE_DP_COUNT:
            case TRANSPORT_DP_COUNT:
                return valueInM + " out of " + thresholdInM + " allowed data points";
            case TRANSPORT_MSG_COUNT:
                return valueInM + " out of " + thresholdInM + " allowed messages";
            case JS_EXEC_COUNT:
                return valueInM + " out of " + thresholdInM + " allowed JavaScript functions";
            case RE_EXEC_COUNT:
                return valueInM + " out of " + thresholdInM + " allowed Rule Engine messages";
            case EMAIL_EXEC_COUNT:
                return valueInM + " out of " + thresholdInM + " allowed Email messages";
            case SMS_EXEC_COUNT:
                return valueInM + " out of " + thresholdInM + " allowed SMS messages";
            default:
                throw new RuntimeException("Not implemented!");
        }
    }

    private String toDisabledValueLabel(ApiUsageRecordKey key, long value) {
        switch (key) {
            case STORAGE_DP_COUNT:
            case TRANSPORT_DP_COUNT:
                return getValueAsString(value) + " data points";
            case TRANSPORT_MSG_COUNT:
                return getValueAsString(value) + " messages";
            case JS_EXEC_COUNT:
                return "JavaScript functions " + getValueAsString(value) + " times";
            case RE_EXEC_COUNT:
                return getValueAsString(value) + " Rule Engine messages";
            case EMAIL_EXEC_COUNT:
                return getValueAsString(value) + " Email messages";
            case SMS_EXEC_COUNT:
                return getValueAsString(value) + " SMS messages";
            default:
                throw new RuntimeException("Not implemented!");
        }
    }

    @NotNull
    private String getValueAsString(long value) {
        if (value > _1M && value % _1M < _10K) {
            return value / _1M + "M";
        } else if (value > _10K) {
            return String.format("%.2fM", ((double) value) / 1000000);
        } else {
            return value + "";
        }
    }

    private void sendMail(JavaMailSenderImpl mailSender,
                          String mailFrom, String email,
                          String subject, String message) throws ThingsboardException {
        try {
            MimeMessage mimeMsg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMsg, UTF_8);
            helper.setFrom(mailFrom);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(message, true);
            mailSender.send(helper.getMimeMessage());
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private JavaMailSenderImpl createMailSender(JsonNode jsonConfig) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(getStringValue(jsonConfig, "smtpHost"));
        mailSender.setPort(parsePort(getStringValue(jsonConfig, "smtpPort")));
        mailSender.setUsername(getStringValue(jsonConfig, "username"));
        mailSender.setPassword(getStringValue(jsonConfig, "password"));
        mailSender.setJavaMailProperties(createJavaMailProperties(jsonConfig));
        return mailSender;
    }

    private Properties createJavaMailProperties(JsonNode jsonConfig) {
        Properties javaMailProperties = new Properties();
        String protocol = getStringValue(jsonConfig, "smtpProtocol");
        javaMailProperties.put("mail.transport.protocol", protocol);
        javaMailProperties.put(MAIL_PROP + protocol + ".host", getStringValue(jsonConfig, "smtpHost"));
        javaMailProperties.put(MAIL_PROP + protocol + ".port", getStringValue(jsonConfig, "smtpPort"));
        javaMailProperties.put(MAIL_PROP + protocol + ".timeout", getStringValue(jsonConfig, "timeout"));
        javaMailProperties.put(MAIL_PROP + protocol + ".auth", String.valueOf(StringUtils.isNotEmpty(getStringValue(jsonConfig, "username"))));
        boolean enableTls = false;
        if (jsonConfig.has("enableTls")) {
            if (jsonConfig.get("enableTls").isBoolean() && jsonConfig.get("enableTls").booleanValue()) {
                enableTls = true;
            } else if (jsonConfig.get("enableTls").isTextual()) {
                enableTls = "true".equalsIgnoreCase(jsonConfig.get("enableTls").asText());
            }
        }
        javaMailProperties.put(MAIL_PROP + protocol + ".starttls.enable", enableTls);
        if (enableTls && jsonConfig.has("tlsVersion") && !jsonConfig.get("tlsVersion").isNull()) {
            String tlsVersion = jsonConfig.get("tlsVersion").asText();
            if (StringUtils.isNoneEmpty(tlsVersion)) {
                javaMailProperties.put(MAIL_PROP + protocol + ".ssl.protocols", tlsVersion);
            }
        }
        
        boolean enableProxy = jsonConfig.has("enableProxy") && jsonConfig.get("enableProxy").asBoolean();

        if (enableProxy) {
            javaMailProperties.put(MAIL_PROP + protocol + ".proxy.host", jsonConfig.get("proxyHost").asText());
            javaMailProperties.put(MAIL_PROP + protocol + ".proxy.port", jsonConfig.get("proxyPort").asText());
            String proxyUser = jsonConfig.get("proxyUser").asText();
            if (StringUtils.isNoneEmpty(proxyUser)) {
                javaMailProperties.put(MAIL_PROP + protocol + ".proxy.user", proxyUser);
            }
            String proxyPassword = jsonConfig.get("proxyPassword").asText();
            if (StringUtils.isNoneEmpty(proxyPassword)) {
                javaMailProperties.put(MAIL_PROP + protocol + ".proxy.password", proxyPassword);
            }
        }

        return javaMailProperties;
    }

    private int parsePort(String strPort) {
        try {
            return Integer.valueOf(strPort);
        } catch (NumberFormatException e) {
            throw new IncorrectParameterException(String.format("Invalid smtp port value: %s", strPort));
        }
    }

    private String getStringValue(JsonNode jsonNode, String key) {
        if (jsonNode.has(key)) {
            return jsonNode.get(key).asText();
        } else {
            return "";
        }
    }

    private JsonNode getConfig(TenantId tenantId, String key) throws ThingsboardException {
        return getConfig(tenantId, key, true).jsonConfig;
    }

    private ConfigEntry getConfig(TenantId tenantId, String key, boolean allowSystemMailService) throws ThingsboardException {
        try {
            JsonNode jsonConfig = null;
            boolean isSystem = false;
            if (tenantId != null && !tenantId.isNullUid()) {
                String jsonString = getEntityAttributeValue(tenantId, tenantId, key);
                if (!StringUtils.isEmpty(jsonString)) {
                    try {
                        jsonConfig = objectMapper.readTree(jsonString);
                    } catch (Exception e) {
                    }
                }
                if (jsonConfig != null) {
                    JsonNode useSystemMailSettingsNode = jsonConfig.get("useSystemMailSettings");
                    if (useSystemMailSettingsNode == null || useSystemMailSettingsNode.asBoolean()) {
                        jsonConfig = null;
                    }
                }
            }
            if (jsonConfig == null) {
                if (!allowSystemMailService) {
                    throw new RuntimeException("Access to System Mail Service is forbidden!");
                }
                AdminSettings settings = adminSettingsService.findAdminSettingsByKey(tenantId, key);
                if (settings != null) {
                    jsonConfig = settings.getJsonValue();
                    isSystem = true;
                }
            }
            if (jsonConfig == null) {
                throw new IncorrectParameterException("Failed to get mail configuration. Settings not found!");
            }
            return new ConfigEntry(jsonConfig, isSystem);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private String getEntityAttributeValue(TenantId tenantId, EntityId entityId, String key) throws Exception {
        List<AttributeKvEntry> attributeKvEntries =
                attributesService.find(tenantId, entityId, DataConstants.SERVER_SCOPE, Arrays.asList(key)).get();
        if (attributeKvEntries != null && !attributeKvEntries.isEmpty()) {
            AttributeKvEntry kvEntry = attributeKvEntries.get(0);
            return kvEntry.getValueAsString();
        } else {
            return "";
        }
    }

    class ConfigEntry {

        JsonNode jsonConfig;
        boolean isSystem;

        ConfigEntry(JsonNode jsonConfig, boolean isSystem) {
            this.jsonConfig = jsonConfig;
            this.isSystem = isSystem;
        }

    }

    private String body(JsonNode mailTemplates, String template, Map<String, Object> model) throws ThingsboardException {
        try {
            return MailTemplates.body(mailTemplates, template, model);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    protected ThingsboardException handleException(Exception exception) {
        String message;
        if (exception instanceof NestedRuntimeException) {
            message = ((NestedRuntimeException) exception).getMostSpecificCause().getMessage();
        } else {
            message = exception.getMessage();
        }
        log.warn("Unable to send mail: {}", message);
        return new ThingsboardException(String.format("Unable to send mail: %s", message),
                ThingsboardErrorCode.GENERAL);
    }

}
