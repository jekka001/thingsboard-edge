/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2023 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.service.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.thingsboard.server.common.data.id.IdBased;
import org.thingsboard.server.common.data.notification.NotificationDeliveryMethod;
import org.thingsboard.server.common.data.notification.NotificationType;
import org.thingsboard.server.common.data.notification.template.EmailDeliveryMethodNotificationTemplate;
import org.thingsboard.server.common.data.notification.template.NotificationTemplate;
import org.thingsboard.server.common.data.notification.template.NotificationTemplateConfig;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.service.DaoSqlTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DaoSqlTest
public class NotificationTemplateApiTest extends AbstractNotificationApiTest {

    @Before
    public void beforeEach() throws Exception {
        loginTenantAdmin();
    }

    @Test
    public void givenInvalidNotificationTemplate_whenSaving_returnValidationError() throws Exception {
        NotificationTemplate notificationTemplate = new NotificationTemplate();
        notificationTemplate.setName(null);
        notificationTemplate.setNotificationType(null);
        notificationTemplate.setConfiguration(null);

        String validationError = saveAndGetError(notificationTemplate, status().isBadRequest());
        assertThat(validationError)
                .contains("name must not be")
                .contains("notificationType must not be")
                .contains("configuration must not be");

        NotificationTemplateConfig config = new NotificationTemplateConfig();
        notificationTemplate.setConfiguration(config);
        config.setDefaultTextTemplate("Default text");
        config.setNotificationSubject(null);
        EmailDeliveryMethodNotificationTemplate emailTemplate = new EmailDeliveryMethodNotificationTemplate();
        emailTemplate.setEnabled(true);
        emailTemplate.setBody(null);
        emailTemplate.setSubject(null);
        config.setDeliveryMethodsTemplates(Map.of(
                NotificationDeliveryMethod.EMAIL, emailTemplate
        ));
        notificationTemplate.setName("<script/>");

        validationError = saveAndGetError(notificationTemplate, status().isBadRequest());
        assertThat(validationError)
                .contains("notificationSubject must be")
                .contains("name is malformed");

        config.setDefaultTextTemplate(null);

        validationError = saveAndGetError(notificationTemplate, status().isBadRequest());
        assertThat(validationError)
                .contains("defaultTextTemplate").contains("must be specified");
    }

    @Test
    public void testTemplatesSearch() throws Exception {
        NotificationTemplate alarmNotificationTemplate = createNotificationTemplate(NotificationType.ALARM, "Alarm", "Alarm", NotificationDeliveryMethod.WEB);
        NotificationTemplate generalNotificationTemplate = createNotificationTemplate(NotificationType.GENERAL, "General", "General", NotificationDeliveryMethod.WEB);
        NotificationTemplate entityActionNotificationTemplate = createNotificationTemplate(NotificationType.ENTITY_ACTION, "Entity action", "Entity action", NotificationDeliveryMethod.WEB);

        assertThat(findTemplates(NotificationType.ALARM)).extracting(IdBased::getId)
                .containsOnly(alarmNotificationTemplate.getId());
        assertThat(findTemplates(NotificationType.ENTITY_ACTION)).extracting(IdBased::getId)
                .containsOnly(entityActionNotificationTemplate.getId());
        assertThat(findTemplates(NotificationType.GENERAL)).extracting(IdBased::getId)
                .containsOnly(generalNotificationTemplate.getId());

        assertThat(findTemplates(NotificationType.GENERAL, NotificationType.ALARM)).extracting(IdBased::getId)
                .containsOnly(generalNotificationTemplate.getId(), alarmNotificationTemplate.getId());
        assertThat(findTemplates(NotificationType.GENERAL, NotificationType.ENTITY_ACTION)).extracting(IdBased::getId)
                .containsOnly(generalNotificationTemplate.getId(), entityActionNotificationTemplate.getId());

        assertThat(findTemplates()).extracting(IdBased::getId)
                .containsOnly(generalNotificationTemplate.getId(), alarmNotificationTemplate.getId(), entityActionNotificationTemplate.getId());
    }

    private String saveAndGetError(NotificationTemplate notificationTemplate, ResultMatcher statusMatcher) throws Exception {
        return getErrorMessage(save(notificationTemplate, statusMatcher));
    }

    private ResultActions save(NotificationTemplate notificationTemplate, ResultMatcher statusMatcher) throws Exception {
        return doPost("/api/notification/template", notificationTemplate)
                .andExpect(statusMatcher);
    }

    private List<NotificationTemplate> findTemplates(NotificationType... notificationTypes) throws Exception {
        PageLink pageLink = new PageLink(100, 0);
        return doGetTypedWithPageLink("/api/notification/templates?notificationTypes=" + StringUtils.join(notificationTypes, ",") + "&",
                new TypeReference<PageData<NotificationTemplate>>() {}, pageLink).getData();
    }

}
