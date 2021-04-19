/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.rule.engine.mail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.thingsboard.rule.engine.api.RuleNode;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.server.common.data.id.BlobEntityId;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.msg.TbMsg;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.thingsboard.rule.engine.api.TbRelationTypes.SUCCESS;
import static org.thingsboard.rule.engine.mail.TbSendEmailNode.SEND_EMAIL_TYPE;

@Slf4j
@RuleNode(
        type = ComponentType.TRANSFORMATION,
        name = "to email",
        configClazz = TbMsgToEmailNodeConfiguration.class,
        nodeDescription = "Transforms message to email message",
        nodeDetails = "Transforms message to email message by populating email fields using values derived from message metadata. " +
                      "Set 'SEND_EMAIL' output message type.",
        uiResources = {"static/rulenode/rulenode-core-config.js"},
        configDirective = "tbTransformationNodeToEmailConfig",
        icon = "email"
)
public class TbMsgToEmailNode implements TbNode {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ATTACHMENTS = "attachments";
    private static final String EMAIL_TIMEZONE = "emailTimezone";

    private static final Pattern dateVarPattern = Pattern.compile("%d\\{([^\\}]*)\\}");

    private TbMsgToEmailNodeConfiguration config;

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        this.config = TbNodeUtils.convert(configuration, TbMsgToEmailNodeConfiguration.class);
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) {
        try {
            EmailPojo email = convert(msg);
            TbMsg emailMsg = buildEmailMsg(ctx, msg, email);
            ctx.tellNext(emailMsg, SUCCESS);
        } catch (Exception ex) {
            log.warn("Can not convert message to email " + ex.getMessage());
            ctx.tellFailure(msg, ex);
        }
    }

    private TbMsg buildEmailMsg(TbContext ctx, TbMsg msg, EmailPojo email) throws JsonProcessingException {
        String emailJson = MAPPER.writeValueAsString(email);
        return ctx.transformMsg(msg, SEND_EMAIL_TYPE, msg.getOriginator(), msg.getMetaData().copy(), emailJson);
    }

    private EmailPojo convert(TbMsg msg) throws IOException {
        TimeZone tz = null;
        String emailTimezone = msg.getMetaData().getValue(EMAIL_TIMEZONE);
        if (!StringUtils.isEmpty(emailTimezone)) {
            tz = TimeZone.getTimeZone(emailTimezone);
        }
        Date currentDate = new Date();
        EmailPojo.EmailPojoBuilder builder = EmailPojo.builder();
        builder.from(fromTemplate(this.config.getFromTemplate(), msg));
        builder.to(fromTemplate(this.config.getToTemplate(), msg));
        builder.cc(fromTemplate(this.config.getCcTemplate(), msg));
        builder.bcc(fromTemplate(this.config.getBccTemplate(), msg));
        builder.subject(fromTemplateWithDate(this.config.getSubjectTemplate(), msg, currentDate, tz));
        builder.body(fromTemplateWithDate(this.config.getBodyTemplate(), msg, currentDate, tz));
        List<BlobEntityId> attachments = new ArrayList<>();
        String attachmentsStr = msg.getMetaData().getValue(ATTACHMENTS);
        if (!StringUtils.isEmpty(attachmentsStr)) {
            String[] attachmentsStrArray = attachmentsStr.split(",");
            for (String attachmentStr : attachmentsStrArray) {
                attachments.add(new BlobEntityId(UUID.fromString(attachmentStr)));
            }
        }
        builder.attachments(attachments);
        return builder.build();
    }

    private String fromTemplate(String template, TbMsg msg) {
        if (!StringUtils.isEmpty(template)) {
            return TbNodeUtils.processPattern(template, msg);
        } else {
            return null;
        }
    }

    private String fromTemplateWithDate(String template, TbMsg msg, Date currentDate, TimeZone tz) {
        if (!StringUtils.isEmpty(template)) {
            return processDatePatterns(TbNodeUtils.processPattern(template, msg), currentDate, tz);
        } else {
            return null;
        }
    }

    private String processDatePatterns(String datePattern, Date currentDate, TimeZone tz) {
        String result = datePattern;
        Matcher matcher = dateVarPattern.matcher(datePattern);
        while (matcher.find()) {
            String toReplace = matcher.group(0);
            SimpleDateFormat dateFormat = new SimpleDateFormat(matcher.group(1));
            if (tz != null) {
                dateFormat.setTimeZone(tz);
            }
            String replacement = dateFormat.format(currentDate);
            result = result.replace(toReplace, replacement);
        }
        return result;
    }

    @Override
    public void destroy() {

    }
}
