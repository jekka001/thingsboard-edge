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
package org.thingsboard.rule.engine.profile;

import lombok.AccessLevel;
import lombok.Getter;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.device.profile.AlarmRule;
import org.thingsboard.server.common.data.device.profile.DeviceProfileAlarm;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.query.ComplexFilterPredicate;
import org.thingsboard.server.common.data.query.DynamicValue;
import org.thingsboard.server.common.data.query.DynamicValueSourceType;
import org.thingsboard.server.common.data.query.EntityKey;
import org.thingsboard.server.common.data.query.EntityKeyType;
import org.thingsboard.server.common.data.query.FilterPredicateValue;
import org.thingsboard.server.common.data.query.KeyFilter;
import org.thingsboard.server.common.data.query.KeyFilterPredicate;
import org.thingsboard.server.common.data.query.SimpleKeyFilterPredicate;
import org.thingsboard.server.common.data.query.StringFilterPredicate;

import javax.print.attribute.standard.Severity;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


class ProfileState {

    private DeviceProfile deviceProfile;
    @Getter(AccessLevel.PACKAGE)
    private final List<DeviceProfileAlarm> alarmSettings = new CopyOnWriteArrayList<>();
    @Getter(AccessLevel.PACKAGE)
    private final Set<EntityKey> entityKeys = ConcurrentHashMap.newKeySet();

    private final Map<String, Map<AlarmSeverity, Set<EntityKey>>> alarmCreateKeys = new HashMap<>();
    private final Map<String, Set<EntityKey>> alarmClearKeys = new HashMap<>();

    ProfileState(DeviceProfile deviceProfile) {
        updateDeviceProfile(deviceProfile);
    }

    void updateDeviceProfile(DeviceProfile deviceProfile) {
        this.deviceProfile = deviceProfile;
        alarmSettings.clear();
        alarmCreateKeys.clear();
        alarmClearKeys.clear();
        if (deviceProfile.getProfileData().getAlarms() != null) {
            alarmSettings.addAll(deviceProfile.getProfileData().getAlarms());
            for (DeviceProfileAlarm alarm : deviceProfile.getProfileData().getAlarms()) {
                Map<AlarmSeverity, Set<EntityKey>> createAlarmKeys = alarmCreateKeys.computeIfAbsent(alarm.getId(), id -> new HashMap<>());
                alarm.getCreateRules().forEach(((severity, alarmRule) -> {
                    Set<EntityKey> ruleKeys = createAlarmKeys.computeIfAbsent(severity, id -> new HashSet<>());
                    for (KeyFilter keyFilter : alarmRule.getCondition().getCondition()) {
                        entityKeys.add(keyFilter.getKey());
                        ruleKeys.add(keyFilter.getKey());
                        addDynamicValuesRecursively(keyFilter.getPredicate(), entityKeys, ruleKeys);
                    }
                }));
                if (alarm.getClearRule() != null) {
                    Set<EntityKey> clearAlarmKeys = alarmClearKeys.computeIfAbsent(alarm.getId(), id -> new HashSet<>());
                    for (KeyFilter keyFilter : alarm.getClearRule().getCondition().getCondition()) {
                        entityKeys.add(keyFilter.getKey());
                        clearAlarmKeys.add(keyFilter.getKey());
                        addDynamicValuesRecursively(keyFilter.getPredicate(), entityKeys, clearAlarmKeys);
                    }
                }
            }
        }
    }

    private void addDynamicValuesRecursively(KeyFilterPredicate predicate, Set<EntityKey> entityKeys, Set<EntityKey> ruleKeys) {
        switch (predicate.getType()) {
            case STRING:
            case NUMERIC:
            case BOOLEAN:
                DynamicValue value = ((SimpleKeyFilterPredicate) predicate).getValue().getDynamicValue();
                if (value != null && value.getSourceType() == DynamicValueSourceType.CURRENT_DEVICE) {
                    EntityKey entityKey = new EntityKey(EntityKeyType.ATTRIBUTE, value.getSourceAttribute());
                    entityKeys.add(entityKey);
                    ruleKeys.add(entityKey);
                }
                break;
            case COMPLEX:
                for (KeyFilterPredicate child : ((ComplexFilterPredicate) predicate).getPredicates()) {
                    addDynamicValuesRecursively(child, entityKeys, ruleKeys);
                }
                break;
        }
    }

    DeviceProfileId getProfileId() {
        return deviceProfile.getId();
    }

    Set<EntityKey> getCreateAlarmKeys(String id, AlarmSeverity severity) {
        Map<AlarmSeverity, Set<EntityKey>> sKeys = alarmCreateKeys.get(id);
        if (sKeys == null) {
            return Collections.emptySet();
        } else {
            Set<EntityKey> keys = sKeys.get(severity);
            if (keys == null) {
                return Collections.emptySet();
            } else {
                return keys;
            }
        }
    }

    Set<EntityKey> getClearAlarmKeys(String id) {
        Set<EntityKey> keys = alarmClearKeys.get(id);
        if (keys == null) {
            return Collections.emptySet();
        } else {
            return keys;
        }
    }
}
