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
package org.thingsboard.server.queue.discovery;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.gen.transport.TransportProtos.ServiceInfo;
import org.thingsboard.server.queue.settings.TbQueueRuleEngineSettings;
import org.thingsboard.server.queue.settings.TbRuleEngineQueueConfiguration;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DefaultTbServiceInfoProvider implements TbServiceInfoProvider {

    @Getter
    @Value("${service.id:#{null}}")
    private String serviceId;

    @Getter
    @Value("${service.type:monolith}")
    private String serviceType;

    @Getter
    @Value("${service.tenant_id:}")
    private String tenantIdStr;

    @Autowired(required = false)
    private TbQueueRuleEngineSettings ruleEngineSettings;

    private List<ServiceType> serviceTypes;
    private ServiceInfo serviceInfo;
    private TenantId isolatedTenant;

    @PostConstruct
    public void init() {
        if (StringUtils.isEmpty(serviceId)) {
            try {
                serviceId = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                serviceId = org.apache.commons.lang3.RandomStringUtils.randomAlphabetic(10);
            }
        }
        log.info("Current Service ID: {}", serviceId);
        if (serviceType.equalsIgnoreCase("monolith")) {
            serviceTypes = Collections.unmodifiableList(Arrays.asList(ServiceType.values()));
        } else {
            serviceTypes = Collections.singletonList(ServiceType.of(serviceType));
        }
        ServiceInfo.Builder builder = ServiceInfo.newBuilder()
                .setServiceId(serviceId)
                .addAllServiceTypes(serviceTypes.stream().map(ServiceType::name).collect(Collectors.toList()));
        UUID tenantId;
        if (!StringUtils.isEmpty(tenantIdStr)) {
            tenantId = UUID.fromString(tenantIdStr);
            isolatedTenant = new TenantId(tenantId);
        } else {
            tenantId = TenantId.NULL_UUID;
        }
        builder.setTenantIdMSB(tenantId.getMostSignificantBits());
        builder.setTenantIdLSB(tenantId.getLeastSignificantBits());

        if (serviceTypes.contains(ServiceType.TB_RULE_ENGINE) && ruleEngineSettings != null) {
            for (TbRuleEngineQueueConfiguration queue : ruleEngineSettings.getQueues()) {
                TransportProtos.QueueInfo queueInfo = TransportProtos.QueueInfo.newBuilder()
                        .setName(queue.getName())
                        .setTopic(queue.getTopic())
                        .setPartitions(queue.getPartitions()).build();
                builder.addRuleEngineQueues(queueInfo);
            }
        }

        serviceInfo = builder.build();
    }

    @Override
    public ServiceInfo getServiceInfo() {
        return serviceInfo;
    }

    @Override
    public boolean isService(ServiceType serviceType) {
        return serviceTypes.contains(serviceType);
    }

    @Override
    public Optional<TenantId> getIsolatedTenant() {
        return Optional.ofNullable(isolatedTenant);
    }
}
