/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2024 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.queue.discovery.event;

import lombok.Getter;
import lombok.ToString;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.queue.discovery.QueueKey;

import java.io.Serial;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ToString(callSuper = true)
public class PartitionChangeEvent extends TbApplicationEvent {

    @Serial
    private static final long serialVersionUID = -8731788167026510559L;

    @Getter
    private final ServiceType serviceType;
    @Getter
    private final Map<QueueKey, Set<TopicPartitionInfo>> partitionsMap;

    public PartitionChangeEvent(Object source, ServiceType serviceType, Map<QueueKey, Set<TopicPartitionInfo>> partitionsMap) {
        super(source);
        this.serviceType = serviceType;
        this.partitionsMap = partitionsMap;
    }

    public Set<TopicPartitionInfo> getCorePartitions() {
        return getPartitionsByServiceTypeAndQueueName(ServiceType.TB_CORE, DataConstants.MAIN_QUEUE_NAME);
    }

    public Set<TopicPartitionInfo> getEdgePartitions() {
        return getPartitionsByServiceTypeAndQueueName(ServiceType.TB_CORE, DataConstants.EDGE_QUEUE_NAME);
    }

    public Set<TopicPartitionInfo> getCfPartitions() {
        return partitionsMap.getOrDefault(QueueKey.CF, Collections.emptySet());
    }

    private Set<TopicPartitionInfo> getPartitionsByServiceTypeAndQueueName(ServiceType serviceType, String queueName) {
        return partitionsMap.entrySet()
                .stream()
                .filter(entry -> serviceType.equals(entry.getKey().getType()) && queueName.equals(entry.getKey().getQueueName()))
                .flatMap(entry -> entry.getValue().stream())
                .collect(Collectors.toSet());
    }
}
