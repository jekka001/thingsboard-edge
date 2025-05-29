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
package org.thingsboard.server.queue.discovery;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.TbTransportService;
import org.thingsboard.server.common.data.integration.IntegrationType;
import org.thingsboard.server.common.data.job.JobType;
import org.thingsboard.server.common.data.util.CollectionsUtil;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.gen.transport.TransportProtos.ServiceInfo;
import org.thingsboard.server.queue.edqs.EdqsConfig;
import org.thingsboard.server.queue.task.TaskProcessor;
import org.thingsboard.server.queue.util.AfterContextReady;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.thingsboard.common.util.SystemUtil.getCpuCount;
import static org.thingsboard.common.util.SystemUtil.getCpuUsage;
import static org.thingsboard.common.util.SystemUtil.getDiscSpaceUsage;
import static org.thingsboard.common.util.SystemUtil.getMemoryUsage;
import static org.thingsboard.common.util.SystemUtil.getTotalDiscSpace;
import static org.thingsboard.common.util.SystemUtil.getTotalMemory;

@Component
@Slf4j
public class DefaultTbServiceInfoProvider implements TbServiceInfoProvider {

    private static final String INTEGRATIONS_NONE = "NONE";
    private static final String INTEGRATIONS_ALL = "ALL";
    @Getter
    @Value("${service.id:#{null}}")
    private String serviceId;

    @Getter
    @Value("${service.type:monolith}")
    private String serviceType;

    @Value("${service.integrations.supported:ALL}")
    private String supportedIntegrationsStr;

    @Value("${service.integrations.excluded:NONE}")
    private String excludedIntegrationsStr;

    @Getter
    @Value("${service.rule_engine.assigned_tenant_profiles:}")
    private Set<UUID> assignedTenantProfiles;

    @Autowired(required = false)
    private EdqsConfig edqsConfig;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private List<TaskProcessor<?, ?>> availableTaskProcessors;

    private List<ServiceType> serviceTypes;
    private List<JobType> taskTypes;
    private ServiceInfo serviceInfo;

    private boolean ready = true;

    @PostConstruct
    public void init() {
        if (StringUtils.isEmpty(serviceId)) {
            try {
                serviceId = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                serviceId = StringUtils.randomAlphabetic(10);
            }
        }
        log.info("Current Service ID: {}", serviceId);
        if (serviceType.equalsIgnoreCase("monolith")) {
            serviceTypes = List.of(ServiceType.values());
        } else {
            serviceTypes = Collections.singletonList(ServiceType.of(serviceType));
        }
        if (!serviceTypes.contains(ServiceType.TB_RULE_ENGINE) || assignedTenantProfiles == null) {
            assignedTenantProfiles = Collections.emptySet();
        }
        if (serviceTypes.contains(ServiceType.EDQS)) {
            ready = false;
            if (StringUtils.isBlank(edqsConfig.getLabel())) {
                edqsConfig.setLabel(serviceId);
            }
        }
        if (CollectionsUtil.isNotEmpty(availableTaskProcessors)) {
            taskTypes = availableTaskProcessors.stream()
                    .map(TaskProcessor::getJobType)
                    .toList();
        } else {
            taskTypes = Collections.emptyList();
        }

        generateNewServiceInfoWithCurrentSystemInfo();
    }

    @Override
    public List<IntegrationType> getSupportedIntegrationTypes() {
        List<IntegrationType> supportedIntegrationTypes;
        if (serviceTypes.contains(ServiceType.TB_INTEGRATION_EXECUTOR)) {
            if (StringUtils.isEmpty(supportedIntegrationsStr) || supportedIntegrationsStr.equalsIgnoreCase(INTEGRATIONS_NONE)) {
                supportedIntegrationTypes = Collections.emptyList();
            } else if (supportedIntegrationsStr.equalsIgnoreCase(INTEGRATIONS_ALL)) {
                supportedIntegrationTypes = Arrays.asList(IntegrationType.values());
            } else {
                try {
                    supportedIntegrationTypes = Arrays.stream(supportedIntegrationsStr.split(",")).map(String::trim).map(IntegrationType::valueOf).collect(Collectors.toList());
                } catch (RuntimeException e) {
                    log.warn("Failed to parse supplied integration types: {}", supportedIntegrationsStr);
                    throw e;
                }
            }

            List<IntegrationType> excludedIntegrationTypes;
            if (StringUtils.isEmpty(excludedIntegrationsStr) || excludedIntegrationsStr.equalsIgnoreCase(INTEGRATIONS_NONE)) {
                excludedIntegrationTypes = Collections.emptyList();
            } else if (excludedIntegrationsStr.equalsIgnoreCase(INTEGRATIONS_ALL)) {
                excludedIntegrationTypes = Arrays.asList(IntegrationType.values());
            } else {
                try {
                    excludedIntegrationTypes = Arrays.stream(excludedIntegrationsStr.split(",")).map(String::trim).map(IntegrationType::valueOf).collect(Collectors.toList());
                } catch (RuntimeException e) {
                    log.warn("Failed to parse excluded integration types: {}", excludedIntegrationsStr);
                    throw e;
                }
            }

            supportedIntegrationTypes = supportedIntegrationTypes.stream().filter(it -> !it.isRemoteOnly()).filter(it -> !excludedIntegrationTypes.contains(it)).collect(Collectors.toList());
        } else {
            supportedIntegrationTypes = Collections.emptyList();
        }
        return supportedIntegrationTypes;
    }

    @AfterContextReady
    public void setTransports() {
        serviceInfo = ServiceInfo.newBuilder(serviceInfo)
                .addAllTransports(getTransportServices().stream()
                        .map(TbTransportService::getName)
                        .collect(Collectors.toSet()))
                .build();
    }

    private Collection<TbTransportService> getTransportServices() {
        return applicationContext.getBeansOfType(TbTransportService.class).values();
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
    public ServiceInfo generateNewServiceInfoWithCurrentSystemInfo() {
        ServiceInfo.Builder builder = ServiceInfo.newBuilder()
                .setServiceId(serviceId)
                .addAllServiceTypes(serviceTypes.stream().map(ServiceType::name).collect(Collectors.toList()))
                .setSystemInfo(getCurrentSystemInfoProto());
        List<IntegrationType> supportedIntegrationTypes = getSupportedIntegrationTypes();
        supportedIntegrationTypes.forEach(integrationType -> builder.addIntegrationTypes(integrationType.name()));

        if (CollectionsUtil.isNotEmpty(assignedTenantProfiles)) {
            builder.addAllAssignedTenantProfiles(assignedTenantProfiles.stream().map(UUID::toString).collect(Collectors.toList()));
        }
        if (edqsConfig != null) {
            builder.setLabel(edqsConfig.getLabel());
        }
        builder.setReady(ready);
        builder.addAllTaskTypes(taskTypes.stream().map(JobType::name).toList());
        return serviceInfo = builder.build();
    }

    @Override
    public boolean setReady(boolean ready) {
        boolean changed = this.ready != ready;
        this.ready = ready;
        return changed;
    }

    private TransportProtos.SystemInfoProto getCurrentSystemInfoProto() {
        TransportProtos.SystemInfoProto.Builder builder = TransportProtos.SystemInfoProto.newBuilder();

        getCpuUsage().ifPresent(builder::setCpuUsage);
        getMemoryUsage().ifPresent(builder::setMemoryUsage);
        getDiscSpaceUsage().ifPresent(builder::setDiskUsage);

        getCpuCount().ifPresent(builder::setCpuCount);
        getTotalMemory().ifPresent(builder::setTotalMemory);
        getTotalDiscSpace().ifPresent(builder::setTotalDiscSpace);

        return builder.build();
    }

}
