/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2025 ThingsBoard, Inc. All Rights Reserved.
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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.thingsboard.server.common.data.device.profile.AlarmConditionFilterKey;
import org.thingsboard.server.common.data.device.profile.AlarmConditionKeyType;
import org.thingsboard.server.common.data.device.profile.SpecificTimeSchedule;
import org.thingsboard.server.common.data.query.ComplexFilterPredicate;
import org.thingsboard.server.common.data.query.DynamicValue;
import org.thingsboard.server.common.data.query.DynamicValueSourceType;
import org.thingsboard.server.common.data.query.FilterPredicateType;
import org.thingsboard.server.common.data.query.SimpleKeyFilterPredicate;

import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileStateTest {

    ProfileState profileState;
    Set<AlarmConditionFilterKey> entityKeys = new HashSet<>();
    Set<AlarmConditionFilterKey> ruleKeys = new HashSet<>();

    @BeforeEach
    void setUp() {
        profileState = mock(ProfileState.class);
    }

    @ParameterizedTest()
    @EnumSource(DynamicValueSourceType.class)
    @NullSource
    void addScheduleDynamicValuesSourceAttribute(DynamicValueSourceType sourceType) {
        willCallRealMethod().given(profileState).addScheduleDynamicValues(any(), any());
        final DynamicValue<String> dynamicValue = new DynamicValue<>(sourceType, "myKey");
        SpecificTimeSchedule schedule = new SpecificTimeSchedule();
        schedule.setDynamicValue(dynamicValue);

        Assertions.assertThat(entityKeys.isEmpty()).isTrue();
        Assertions.assertThat(schedule.getDynamicValue().getSourceAttribute()).isNotNull();

        profileState.addScheduleDynamicValues(schedule, entityKeys);

        Assertions.assertThat(entityKeys).isEqualTo(Set.of(
                new AlarmConditionFilterKey(AlarmConditionKeyType.ATTRIBUTE, "myKey")));
    }

    @ParameterizedTest()
    @EnumSource(DynamicValueSourceType.class)
    @NullSource
    void addScheduleDynamicValuesSourceAttributeIsNull(DynamicValueSourceType sourceType) {
        willCallRealMethod().given(profileState).addScheduleDynamicValues(any(), any());
        DynamicValue<String> dynamicValue = new DynamicValue<>(sourceType, null);
        SpecificTimeSchedule schedule = new SpecificTimeSchedule();
        schedule.setDynamicValue(dynamicValue);

        Assertions.assertThat(entityKeys.isEmpty()).isTrue();
        Assertions.assertThat(schedule.getDynamicValue().getSourceAttribute()).isNull();

        profileState.addScheduleDynamicValues(schedule, entityKeys);

        Assertions.assertThat(entityKeys.isEmpty()).isTrue();
    }

    @ParameterizedTest()
    @EnumSource(value = FilterPredicateType.class,  names = {"COMPLEX"})
    void addDynamicValuesRecursivelySourceAttributeComplexKeyFilter(FilterPredicateType predicateType) {
        willCallRealMethod().given(profileState).addDynamicValuesRecursively(any(), any(), any());
        ComplexFilterPredicate predicate = mock(ComplexFilterPredicate.class, RETURNS_DEEP_STUBS);
        willReturn(predicateType).given(predicate).getType();
        profileState.addDynamicValuesRecursively(predicate, entityKeys, ruleKeys);
        Assertions.assertThat(entityKeys.isEmpty()).isTrue();
        Assertions.assertThat(ruleKeys.isEmpty()).isTrue();
    }

    @ParameterizedTest()
    @EnumSource(value = FilterPredicateType.class,  names = {"STRING", "NUMERIC", "BOOLEAN"})
    void addDynamicValuesRecursivelySourceAttributeIsNull(FilterPredicateType predicateType) {
        willCallRealMethod().given(profileState).addDynamicValuesRecursively(any(), any(), any());
        SimpleKeyFilterPredicate<String> predicate = mock(SimpleKeyFilterPredicate.class, RETURNS_DEEP_STUBS);
        willReturn(predicateType).given(predicate).getType();
        when(predicate.getValue().getDynamicValue().getSourceType()).thenReturn(DynamicValueSourceType.CURRENT_DEVICE);
        when(predicate.getValue().getDynamicValue().getSourceAttribute()).thenReturn(null);
        profileState.addDynamicValuesRecursively(predicate, entityKeys, ruleKeys);
        Assertions.assertThat(entityKeys.isEmpty()).isTrue();
        Assertions.assertThat(ruleKeys.isEmpty()).isTrue();
    }

    @ParameterizedTest()
    @EnumSource(value = FilterPredicateType.class,  names = {"STRING", "NUMERIC", "BOOLEAN"})
    void addDynamicValuesRecursivelySourceAttributeAdded(FilterPredicateType predicateType) {
        willCallRealMethod().given(profileState).addDynamicValuesRecursively(any(), any(), any());
        SimpleKeyFilterPredicate<String> predicate = mock(SimpleKeyFilterPredicate.class, RETURNS_DEEP_STUBS);
        willReturn(predicateType).given(predicate).getType();
        when(predicate.getValue().getDynamicValue().getSourceType()).thenReturn(DynamicValueSourceType.CURRENT_DEVICE);
        when(predicate.getValue().getDynamicValue().getSourceAttribute()).thenReturn("myKey");
        profileState.addDynamicValuesRecursively(predicate, entityKeys, ruleKeys);
        Assertions.assertThat(entityKeys).isEqualTo(Set.of(
                new AlarmConditionFilterKey(AlarmConditionKeyType.ATTRIBUTE, "myKey")));
        Assertions.assertThat(ruleKeys).isEqualTo(Set.of(
                new AlarmConditionFilterKey(AlarmConditionKeyType.ATTRIBUTE, "myKey")));
    }

}
