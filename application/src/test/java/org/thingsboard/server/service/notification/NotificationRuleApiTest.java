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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.util.Pair;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.TenantProfile;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmSearchStatus;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.alarm.AlarmStatus;
import org.thingsboard.server.common.data.device.profile.AlarmCondition;
import org.thingsboard.server.common.data.device.profile.AlarmConditionFilter;
import org.thingsboard.server.common.data.device.profile.AlarmConditionFilterKey;
import org.thingsboard.server.common.data.device.profile.AlarmConditionKeyType;
import org.thingsboard.server.common.data.device.profile.AlarmRule;
import org.thingsboard.server.common.data.device.profile.DeviceProfileAlarm;
import org.thingsboard.server.common.data.device.profile.SimpleAlarmConditionSpec;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.notification.Notification;
import org.thingsboard.server.common.data.notification.NotificationDeliveryMethod;
import org.thingsboard.server.common.data.notification.NotificationRequest;
import org.thingsboard.server.common.data.notification.NotificationRequestInfo;
import org.thingsboard.server.common.data.notification.NotificationType;
import org.thingsboard.server.common.data.notification.info.AlarmNotificationInfo;
import org.thingsboard.server.common.data.notification.rule.DefaultNotificationRuleRecipientsConfig;
import org.thingsboard.server.common.data.notification.rule.EscalatedNotificationRuleRecipientsConfig;
import org.thingsboard.server.common.data.notification.rule.NotificationRule;
import org.thingsboard.server.common.data.notification.rule.NotificationRuleInfo;
import org.thingsboard.server.common.data.notification.rule.trigger.AlarmNotificationRuleTriggerConfig;
import org.thingsboard.server.common.data.notification.rule.trigger.AlarmNotificationRuleTriggerConfig.AlarmAction;
import org.thingsboard.server.common.data.notification.rule.trigger.EntityActionNotificationRuleTriggerConfig;
import org.thingsboard.server.common.data.notification.rule.trigger.NotificationRuleTriggerType;
import org.thingsboard.server.common.data.notification.targets.NotificationTarget;
import org.thingsboard.server.common.data.notification.template.NotificationTemplate;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.query.BooleanFilterPredicate;
import org.thingsboard.server.common.data.query.EntityKeyValueType;
import org.thingsboard.server.common.data.query.FilterPredicateValue;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.tenant.profile.DefaultTenantProfileConfiguration;
import org.thingsboard.server.common.data.tenant.profile.TenantProfileData;
import org.thingsboard.server.dao.notification.NotificationRequestService;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.dao.tenant.TenantProfileService;
import org.thingsboard.server.service.apiusage.limits.LimitedApi;
import org.thingsboard.server.service.apiusage.limits.RateLimitService;
import org.thingsboard.server.service.telemetry.AlarmSubscriptionService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DaoSqlTest
public class NotificationRuleApiTest extends AbstractNotificationApiTest {

    @SpyBean
    private AlarmSubscriptionService alarmSubscriptionService;
    @Autowired
    private NotificationRequestService notificationRequestService;
    @Autowired
    private TenantProfileService tenantProfileService;
    @Autowired
    private RateLimitService rateLimitService;

    @Before
    public void beforeEach() throws Exception {
        loginTenantAdmin();
    }

    @Test
    public void testNotificationRuleProcessing_entityActionTrigger() throws Exception {
        String notificationSubject = "${actionType}: ${entityType} [${entityId}]";
        String notificationText = "User: ${userEmail}";
        NotificationTemplate notificationTemplate = createNotificationTemplate(NotificationType.GENERAL, notificationSubject, notificationText, NotificationDeliveryMethod.WEB);

        NotificationRule notificationRule = new NotificationRule();
        notificationRule.setName("Web notification when any device is created, updated or deleted");
        notificationRule.setTemplateId(notificationTemplate.getId());
        notificationRule.setTriggerType(NotificationRuleTriggerType.ENTITY_ACTION);

        EntityActionNotificationRuleTriggerConfig triggerConfig = new EntityActionNotificationRuleTriggerConfig();
        triggerConfig.setEntityTypes(Set.of(EntityType.DEVICE));
        triggerConfig.setCreated(true);
        triggerConfig.setUpdated(true);
        triggerConfig.setDeleted(true);

        DefaultNotificationRuleRecipientsConfig recipientsConfig = new DefaultNotificationRuleRecipientsConfig();
        recipientsConfig.setTriggerType(NotificationRuleTriggerType.ENTITY_ACTION);
        recipientsConfig.setTargets(List.of(createNotificationTarget(tenantAdminUserId).getUuidId()));

        notificationRule.setTriggerConfig(triggerConfig);
        notificationRule.setRecipientsConfig(recipientsConfig);
        notificationRule = saveNotificationRule(notificationRule);

        getWsClient().subscribeForUnreadNotifications(10).waitForReply(true);


        getWsClient().registerWaitForUpdate();
        Device device = createDevice("DEVICE!!!", "default", "12345");
        getWsClient().waitForUpdate(true);

        Notification notification = getWsClient().getLastDataUpdate().getUpdate();
        assertThat(notification.getSubject()).isEqualTo("added: Device [" + device.getId() + "]");
        assertThat(notification.getText()).isEqualTo("User: " + TENANT_ADMIN_EMAIL);


        getWsClient().registerWaitForUpdate();
        device.setName("Updated name");
        device = doPost("/api/device", device, Device.class);
        getWsClient().waitForUpdate(true);

        notification = getWsClient().getLastDataUpdate().getUpdate();
        assertThat(notification.getSubject()).isEqualTo("updated: Device [" + device.getId() + "]");


        getWsClient().registerWaitForUpdate();
        doDelete("/api/device/" + device.getId()).andExpect(status().isOk());
        getWsClient().waitForUpdate(true);

        notification = getWsClient().getLastDataUpdate().getUpdate();
        assertThat(notification.getSubject()).isEqualTo("deleted: Device [" + device.getId() + "]");
    }

    @Test
    public void testNotificationRuleProcessing_alarmTrigger() throws Exception {
        String notificationSubject = "Alarm type: ${alarmType}, status: ${alarmStatus}, " +
                "severity: ${alarmSeverity}, deviceId: ${alarmOriginatorId}";
        String notificationText = "Status: ${alarmStatus}, severity: ${alarmSeverity}";
        NotificationTemplate notificationTemplate = createNotificationTemplate(NotificationType.ALARM, notificationSubject, notificationText, NotificationDeliveryMethod.WEB);

        NotificationRule notificationRule = new NotificationRule();
        notificationRule.setName("Web notification on any alarm");
        notificationRule.setTemplateId(notificationTemplate.getId());
        notificationRule.setTriggerType(NotificationRuleTriggerType.ALARM);

        AlarmNotificationRuleTriggerConfig triggerConfig = new AlarmNotificationRuleTriggerConfig();
        triggerConfig.setAlarmTypes(null);
        triggerConfig.setAlarmSeverities(null);
        triggerConfig.setNotifyOn(Set.of(AlarmAction.CREATED, AlarmAction.SEVERITY_CHANGED, AlarmAction.ACKNOWLEDGED, AlarmAction.CLEARED));
        notificationRule.setTriggerConfig(triggerConfig);

        EscalatedNotificationRuleRecipientsConfig recipientsConfig = new EscalatedNotificationRuleRecipientsConfig();
        recipientsConfig.setTriggerType(NotificationRuleTriggerType.ALARM);
        Map<Integer, List<UUID>> escalationTable = new HashMap<>();
        recipientsConfig.setEscalationTable(escalationTable);
        Map<Integer, NotificationApiWsClient> clients = new HashMap<>();
        for (int delay = 0; delay <= 5; delay++) {
            Pair<User, NotificationApiWsClient> userAndClient = createUserAndConnectWsClient(Authority.TENANT_ADMIN);
            loginTenantAdmin();
            NotificationTarget notificationTarget = createNotificationTarget(userAndClient.getFirst().getId());
            escalationTable.put(delay, List.of(notificationTarget.getUuidId()));
            clients.put(delay, userAndClient.getSecond());
        }
        notificationRule.setRecipientsConfig(recipientsConfig);
        notificationRule = saveNotificationRule(notificationRule);


        String alarmType = "myBoolIsTrue";
        DeviceProfile deviceProfile = createDeviceProfileWithAlarmRules(alarmType);
        Device device = createDevice("Device 1", deviceProfile.getName(), "1234");

        clients.values().forEach(wsClient -> {
            wsClient.subscribeForUnreadNotifications(10).waitForReply(true);
            wsClient.registerWaitForUpdate();
        });

        JsonNode attr = JacksonUtil.newObjectNode()
                .set("bool", BooleanNode.TRUE);
        doPost("/api/plugins/telemetry/" + device.getId() + "/" + DataConstants.SHARED_SCOPE, attr);

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> alarmSubscriptionService.findLatestByOriginatorAndType(tenantId, device.getId(), alarmType).get() != null);
        Alarm alarm = alarmSubscriptionService.findLatestByOriginatorAndType(tenantId, device.getId(), alarmType).get();

        long ts = System.currentTimeMillis();
        await().atMost(7, TimeUnit.SECONDS)
                .until(() -> clients.values().stream().allMatch(client -> client.getLastDataUpdate() != null));
        clients.forEach((expectedDelay, wsClient) -> {
            Notification notification = wsClient.getLastDataUpdate().getUpdate();
            double actualDelay = (double) (notification.getCreatedTime() - ts) / 1000;
            assertThat(actualDelay).isCloseTo(expectedDelay, offset(0.5));

            assertThat(notification.getSubject()).isEqualTo("Alarm type: " + alarmType + ", status: " + AlarmStatus.ACTIVE_UNACK + ", " +
                    "severity: " + AlarmSeverity.CRITICAL.toString().toLowerCase() + ", deviceId: " + device.getId());
            assertThat(notification.getText()).isEqualTo("Status: " + AlarmStatus.ACTIVE_UNACK + ", severity: " + AlarmSeverity.CRITICAL.toString().toLowerCase());

            assertThat(notification.getType()).isEqualTo(NotificationType.ALARM);
            assertThat(notification.getInfo()).isInstanceOf(AlarmNotificationInfo.class);
            AlarmNotificationInfo info = (AlarmNotificationInfo) notification.getInfo();
            assertThat(info.getAlarmId()).isEqualTo(alarm.getUuidId());
            assertThat(info.getAlarmType()).isEqualTo(alarmType);
            assertThat(info.getAlarmSeverity()).isEqualTo(AlarmSeverity.CRITICAL);
            assertThat(info.getAlarmStatus()).isEqualTo(AlarmStatus.ACTIVE_UNACK);
        });

        clients.values().forEach(wsClient -> wsClient.registerWaitForUpdate());
        alarmSubscriptionService.acknowledgeAlarm(tenantId, alarm.getId(), System.currentTimeMillis());
        AlarmStatus expectedStatus = AlarmStatus.ACTIVE_ACK;
        AlarmSeverity expectedSeverity = AlarmSeverity.CRITICAL;
        clients.values().forEach(wsClient -> {
            wsClient.waitForUpdate(true);
            Notification updatedNotification = wsClient.getLastDataUpdate().getUpdate();
            assertThat(updatedNotification.getSubject()).isEqualTo("Alarm type: " + alarmType + ", status: " + expectedStatus + ", " +
                    "severity: " + expectedSeverity.toString().toLowerCase() + ", deviceId: " + device.getId());
            assertThat(updatedNotification.getText()).isEqualTo("Status: " + expectedStatus + ", severity: " + expectedSeverity.toString().toLowerCase());

            wsClient.close();
        });
    }

    @Test
    public void testNotificationRuleProcessing_alarmTrigger_clearRule() throws Exception {
        String notificationSubject = "${alarmSeverity} alarm '${alarmType}' is ${alarmStatus}";
        String notificationText = "${alarmId}";
        NotificationTemplate notificationTemplate = createNotificationTemplate(NotificationType.ALARM, notificationSubject, notificationText, NotificationDeliveryMethod.WEB);

        NotificationRule notificationRule = new NotificationRule();
        notificationRule.setName("Web notification on any alarm");
        notificationRule.setTemplateId(notificationTemplate.getId());
        notificationRule.setTriggerType(NotificationRuleTriggerType.ALARM);

        String alarmType = "myBoolIsTrue";
        DeviceProfile deviceProfile = createDeviceProfileWithAlarmRules(alarmType);
        Device device = createDevice("Device 1", deviceProfile.getName(), "1234");

        AlarmNotificationRuleTriggerConfig triggerConfig = new AlarmNotificationRuleTriggerConfig();
        triggerConfig.setAlarmTypes(Set.of(alarmType));
        triggerConfig.setAlarmSeverities(null);
        triggerConfig.setNotifyOn(Set.of(AlarmAction.CREATED, AlarmAction.SEVERITY_CHANGED, AlarmAction.ACKNOWLEDGED));

        AlarmNotificationRuleTriggerConfig.ClearRule clearRule = new AlarmNotificationRuleTriggerConfig.ClearRule();
        clearRule.setAlarmStatuses(Set.of(AlarmSearchStatus.CLEARED, AlarmSearchStatus.UNACK));
        triggerConfig.setClearRule(clearRule);
        notificationRule.setTriggerConfig(triggerConfig);

        EscalatedNotificationRuleRecipientsConfig recipientsConfig = new EscalatedNotificationRuleRecipientsConfig();
        recipientsConfig.setTriggerType(NotificationRuleTriggerType.ALARM);
        Map<Integer, List<UUID>> escalationTable = new HashMap<>();
        recipientsConfig.setEscalationTable(escalationTable);

        escalationTable.put(0, List.of(createNotificationTarget(tenantAdminUserId).getUuidId()));
        escalationTable.put(1000, List.of(createNotificationTarget(customerUserId).getUuidId()));

        notificationRule.setRecipientsConfig(recipientsConfig);
        notificationRule = saveNotificationRule(notificationRule);

        getWsClient().subscribeForUnreadNotifications(10).waitForReply(true);
        getWsClient().registerWaitForUpdate();
        JsonNode attr = JacksonUtil.newObjectNode()
                .set("bool", BooleanNode.TRUE);
        doPost("/api/plugins/telemetry/" + device.getId() + "/" + DataConstants.SHARED_SCOPE, attr);

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> alarmSubscriptionService.findLatestByOriginatorAndType(tenantId, device.getId(), alarmType).get() != null);
        Alarm alarm = alarmSubscriptionService.findLatestByOriginatorAndType(tenantId, device.getId(), alarmType).get();
        getWsClient().waitForUpdate(true);

        Notification notification = getWsClient().getLastDataUpdate().getUpdate();
        assertThat(notification.getSubject()).isEqualTo("critical alarm '" + alarmType + "' is ACTIVE_UNACK");
        assertThat(notification.getInfo()).asInstanceOf(type(AlarmNotificationInfo.class))
                .extracting(AlarmNotificationInfo::getAlarmId).isEqualTo(alarm.getUuidId());

        await().atMost(2, TimeUnit.SECONDS).until(() -> findNotificationRequests(EntityType.ALARM).getTotalElements() == escalationTable.size());
        NotificationRequestInfo scheduledNotificationRequest = findNotificationRequests(EntityType.ALARM).getData().stream()
                .filter(NotificationRequest::isScheduled)
                .findFirst().orElse(null);
        assertThat(scheduledNotificationRequest).extracting(NotificationRequest::getInfo).isEqualTo(notification.getInfo());

        getWsClient().registerWaitForUpdate();
        alarmSubscriptionService.clearAlarm(tenantId, alarm.getId(), System.currentTimeMillis(), null);
        getWsClient().waitForUpdate(true);
        notification = getWsClient().getLastDataUpdate().getUpdate();
        assertThat(notification.getSubject()).isEqualTo("critical alarm '" + alarmType + "' is CLEARED_UNACK");

        assertThat(findNotificationRequests(EntityType.ALARM).getData()).filteredOn(NotificationRequest::isScheduled).isEmpty();
    }

    @Test
    public void testNotificationRuleInfo() throws Exception {
        NotificationDeliveryMethod[] deliveryMethods = {NotificationDeliveryMethod.WEB, NotificationDeliveryMethod.EMAIL};
        NotificationTemplate template = createNotificationTemplate(NotificationType.ENTITY_ACTION, "Subject", "Text", deliveryMethods);

        NotificationRule rule = new NotificationRule();
        rule.setName("Test");
        rule.setTemplateId(template.getId());

        rule.setTriggerType(NotificationRuleTriggerType.ENTITY_ACTION);
        EntityActionNotificationRuleTriggerConfig triggerConfig = new EntityActionNotificationRuleTriggerConfig();
        rule.setTriggerConfig(triggerConfig);

        DefaultNotificationRuleRecipientsConfig recipientsConfig = new DefaultNotificationRuleRecipientsConfig();
        recipientsConfig.setTriggerType(NotificationRuleTriggerType.ENTITY_ACTION);
        recipientsConfig.setTargets(List.of(createNotificationTarget(tenantAdminUserId).getUuidId()));
        rule.setRecipientsConfig(recipientsConfig);
        rule = saveNotificationRule(rule);

        NotificationRuleInfo ruleInfo = findNotificationRules().getData().get(0);
        assertThat(ruleInfo.getId()).isEqualTo(ruleInfo.getId());
        assertThat(ruleInfo.getTemplateName()).isEqualTo(template.getName());
        assertThat(ruleInfo.getDeliveryMethods()).containsOnly(deliveryMethods);
    }

    @Test
    public void testNotificationRequestsPerRuleRateLimits() throws Exception {
        int notificationRequestsLimit = 10;
        TenantProfile tenantProfile = tenantProfileService.findDefaultTenantProfile(TenantId.SYS_TENANT_ID);
        TenantProfileData profileData = tenantProfile.getProfileData();
        DefaultTenantProfileConfiguration profileConfiguration = (DefaultTenantProfileConfiguration) profileData.getConfiguration();
        profileConfiguration.setTenantNotificationRequestsPerRuleRateLimit(notificationRequestsLimit + ":300");
        tenantProfile.setProfileData(profileData);
        loginSysAdmin();
        doPost("/api/tenantProfile", tenantProfile).andExpect(status().isOk());
        loginTenantAdmin();

        NotificationRule rule = new NotificationRule();
        rule.setName("Device created");
        rule.setTriggerType(NotificationRuleTriggerType.ENTITY_ACTION);
        NotificationTemplate template = createNotificationTemplate(NotificationType.ENTITY_ACTION, "Device created", "Device created",
                NotificationDeliveryMethod.WEB, NotificationDeliveryMethod.SMS);
        rule.setTemplateId(template.getId());
        EntityActionNotificationRuleTriggerConfig triggerConfig = new EntityActionNotificationRuleTriggerConfig();
        triggerConfig.setEntityTypes(Set.of(EntityType.DEVICE));
        triggerConfig.setCreated(true);
        rule.setTriggerConfig(triggerConfig);
        NotificationTarget target = createNotificationTarget(tenantAdminUserId);
        DefaultNotificationRuleRecipientsConfig recipientsConfig = new DefaultNotificationRuleRecipientsConfig();
        recipientsConfig.setTriggerType(NotificationRuleTriggerType.ENTITY_ACTION);
        recipientsConfig.setTargets(List.of(target.getUuidId()));
        rule.setRecipientsConfig(recipientsConfig);
        rule = saveNotificationRule(rule);

        for (int i = 0; i < notificationRequestsLimit; i++) {
            String name = "device " + i;
            createDevice(name, name);
        }
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(getMyNotifications(false, 100)).size().isEqualTo(notificationRequestsLimit);
                });
        for (int i = 0; i < 5; i++) {
            String name = "device " + (notificationRequestsLimit + i);
            createDevice(name, name);
        }

        boolean rateLimitExceeded = !rateLimitService.checkRateLimit(LimitedApi.NOTIFICATION_REQUESTS_PER_RULE, tenantId, rule.getId());
        assertThat(rateLimitExceeded).isTrue();

        TimeUnit.SECONDS.sleep(3);
        assertThat(getMyNotifications(false, 100)).size().isEqualTo(notificationRequestsLimit);
    }

    private DeviceProfile createDeviceProfileWithAlarmRules(String alarmType) {
        DeviceProfile deviceProfile = createDeviceProfile("For notification rule test");
        deviceProfile.setTenantId(tenantId);

        List<DeviceProfileAlarm> alarms = new ArrayList<>();
        DeviceProfileAlarm alarm = new DeviceProfileAlarm();
        alarm.setAlarmType(alarmType);
        alarm.setId(alarmType);
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmDetails("Details");
        AlarmCondition alarmCondition = new AlarmCondition();
        alarmCondition.setSpec(new SimpleAlarmConditionSpec());
        List<AlarmConditionFilter> condition = new ArrayList<>();

        AlarmConditionFilter alarmConditionFilter = new AlarmConditionFilter();
        alarmConditionFilter.setKey(new AlarmConditionFilterKey(AlarmConditionKeyType.ATTRIBUTE, "bool"));
        BooleanFilterPredicate predicate = new BooleanFilterPredicate();
        predicate.setOperation(BooleanFilterPredicate.BooleanOperation.EQUAL);
        predicate.setValue(new FilterPredicateValue<>(true));

        alarmConditionFilter.setPredicate(predicate);
        alarmConditionFilter.setValueType(EntityKeyValueType.BOOLEAN);
        condition.add(alarmConditionFilter);
        alarmCondition.setCondition(condition);
        alarmRule.setCondition(alarmCondition);
        TreeMap<AlarmSeverity, AlarmRule> createRules = new TreeMap<>();
        createRules.put(AlarmSeverity.CRITICAL, alarmRule);
        alarm.setCreateRules(createRules);
        alarms.add(alarm);

        deviceProfile.getProfileData().setAlarms(alarms);
        deviceProfile = doPost("/api/deviceProfile", deviceProfile, DeviceProfile.class);
        return deviceProfile;
    }

    private NotificationRule saveNotificationRule(NotificationRule notificationRule) {
        return doPost("/api/notification/rule", notificationRule, NotificationRule.class);
    }

    private PageData<NotificationRuleInfo> findNotificationRules() throws Exception {
        PageLink pageLink = new PageLink(10);
        return doGetTypedWithPageLink("/api/notification/rules?", new TypeReference<PageData<NotificationRuleInfo>>() {}, pageLink);
    }

    private PageData<NotificationRequestInfo> findNotificationRequests(EntityType originatorType) {
        return notificationRequestService.findNotificationRequestsInfosByTenantIdAndOriginatorType(tenantId, originatorType, new PageLink(100));
    }

}
