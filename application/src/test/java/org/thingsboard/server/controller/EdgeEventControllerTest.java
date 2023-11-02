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
package org.thingsboard.server.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.dao.edge.EdgeEventDao;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.dao.sqlts.insert.sql.SqlPartitioningRepository;
import org.thingsboard.server.service.ttl.EdgeEventsCleanUpService;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@TestPropertySource(properties = {
        "edges.enabled=true",
        "queue.rule-engine.stats.enabled=false"
})
@Slf4j
@DaoSqlTest
public class EdgeEventControllerTest extends AbstractControllerTest {

    @Autowired
    private EdgeEventDao edgeEventDao;
    @SpyBean
    private SqlPartitioningRepository partitioningRepository;
    @Autowired
    private EdgeEventsCleanUpService edgeEventsCleanUpService;

    @Value("#{${sql.edge_events.partition_size} * 60 * 60 * 1000}")
    private long partitionDurationInMs;
    @Value("${sql.ttl.edge_events.edge_event_ttl}")
    private long edgeEventTtlInSec;

    @Before
    public void beforeTest() throws Exception {
        loginTenantAdmin();
    }

    @After
    public void afterTest() throws Exception {
    }

    // @voba - merge comment
    // edge entities support available in CE/PE
    @Ignore
    @Test
    public void testGetEdgeEvents() throws Exception {
        Edge edge = constructEdge("TestEdge", "default");
        edge = doPost("/api/edge", edge, Edge.class);

        // simulate edge activation
        ObjectNode attributes = JacksonUtil.newObjectNode();
        attributes.put("active", true);
        doPost("/api/plugins/telemetry/EDGE/" + edge.getId() + "/attributes/" + DataConstants.SERVER_SCOPE, attributes);

        final EdgeId edgeId = edge.getId();

        EntityGroup deviceEntityGroup = constructEntityGroup("TestDeviceGroup", EntityType.DEVICE);
        EntityGroup savedDeviceEntityGroup = doPost("/api/entityGroup", deviceEntityGroup, EntityGroup.class);
        doPost("/api/edge/" + edgeId.toString() + "/entityGroup/" + savedDeviceEntityGroup.getId().toString() + "/DEVICE", EntityGroup.class);
        awaitForNumberOfEdgeEvents(edgeId, 1);

        Device device = constructDevice("TestDevice", "default");
        Device savedDevice =
                doPost("/api/device?entityGroupId=" + savedDeviceEntityGroup.getId().getId().toString(), device, Device.class);
        awaitForNumberOfEdgeEvents(edgeId, 2);

        Device device2 = constructDevice("TestDevice2", "default");
        doPost("/api/device?entityGroupId=" + savedDeviceEntityGroup.getId().getId().toString(), device2, Device.class);
        awaitForNumberOfEdgeEvents(edgeId, 3);

        EntityGroup assetEntityGroup = constructEntityGroup("TestAssetGroup", EntityType.ASSET);
        EntityGroup savedAssetEntityGroup = doPost("/api/entityGroup", assetEntityGroup, EntityGroup.class);
        doPost("/api/edge/" + edgeId.toString() + "/entityGroup/" + savedAssetEntityGroup.getId().toString()+ "/ASSET", EntityGroup.class);
        awaitForNumberOfEdgeEvents(edgeId, 4);

        Asset asset = constructAsset("TestAsset", "default");
        Asset savedAsset =
                doPost("/api/asset?entityGroupId=" + savedAssetEntityGroup.getId().getId().toString(), asset, Asset.class);
        awaitForNumberOfEdgeEvents(edgeId, 5);

        Asset asset2 = constructAsset("TestAsset2", "default");
        doPost("/api/asset?entityGroupId=" + savedAssetEntityGroup.getId().getId().toString(), asset2, Asset.class);
        awaitForNumberOfEdgeEvents(edgeId, 6);

        EntityRelation relation = new EntityRelation(savedAsset.getId(), savedDevice.getId(), EntityRelation.CONTAINS_TYPE);
        doPost("/api/relation", relation);
        awaitForNumberOfEdgeEvents(edgeId, 7);

        List<EdgeEvent> edgeEvents = findEdgeEvents(edgeId);

        Assert.assertTrue(popEdgeEvent(edgeEvents, EdgeEventType.ENTITY_GROUP, EdgeEventActionType.ASSIGNED_TO_EDGE)); // TestDeviceGroup

        Assert.assertTrue(popEdgeEvent(edgeEvents, EdgeEventType.DEVICE, EdgeEventActionType.ADDED_TO_ENTITY_GROUP)); // TestDevice
        Assert.assertTrue(popEdgeEvent(edgeEvents, EdgeEventType.DEVICE, EdgeEventActionType.ADDED_TO_ENTITY_GROUP)); // TestDevice2

        Assert.assertTrue(popEdgeEvent(edgeEvents, EdgeEventType.ENTITY_GROUP, EdgeEventActionType.ASSIGNED_TO_EDGE)); // TestAssetGroup

        Assert.assertTrue(popEdgeEvent(edgeEvents, EdgeEventType.ASSET, EdgeEventActionType.ADDED_TO_ENTITY_GROUP)); // TestAsset
        Assert.assertTrue(popEdgeEvent(edgeEvents, EdgeEventType.ASSET, EdgeEventActionType.ADDED_TO_ENTITY_GROUP)); // TestAsset2

        Assert.assertTrue(popEdgeEvent(edgeEvents, EdgeEventType.RELATION, EdgeEventActionType.RELATION_ADD_OR_UPDATE));
        Assert.assertTrue(edgeEvents.isEmpty());
    }

    private boolean popEdgeEvent(List<EdgeEvent> edgeEvents, EdgeEventType edgeEventType, EdgeEventActionType actionType) {
        for (EdgeEvent edgeEvent : edgeEvents) {
            if (edgeEventType.equals(edgeEvent.getType())) {
                if (actionType != null && !actionType.equals(edgeEvent.getAction())) {
                    continue;
                }
                edgeEvents.remove(edgeEvent);
                return true;
            }
        }
        return false;
    }

    private void awaitForNumberOfEdgeEvents(EdgeId edgeId, int expectedNumber) {
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> {
                    List<EdgeEvent> edgeEvents = findEdgeEvents(edgeId);
                    return edgeEvents.size() == expectedNumber;
                });
    }

    @Test
    public void saveEdgeEvent_thenCreatePartitionIfNotExist() {
        reset(partitioningRepository);
        EdgeEvent edgeEvent = createEdgeEvent();
        verify(partitioningRepository).createPartitionIfNotExists(eq("edge_event"), eq(edgeEvent.getCreatedTime()), eq(partitionDurationInMs));
        List<Long> partitions = partitioningRepository.fetchPartitions("edge_event");
        assertThat(partitions).singleElement().satisfies(partitionStartTs -> {
            assertThat(partitionStartTs).isEqualTo(partitioningRepository.calculatePartitionStartTime(edgeEvent.getCreatedTime(), partitionDurationInMs));
        });
    }

    @Test
    public void cleanUpEdgeEventByTtl_dropOldPartitions() {
        long oldEdgeEventTs = LocalDate.of(2020, 10, 1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        long partitionStartTs = partitioningRepository.calculatePartitionStartTime(oldEdgeEventTs, partitionDurationInMs);
        partitioningRepository.createPartitionIfNotExists("edge_event", oldEdgeEventTs, partitionDurationInMs);
        List<Long> partitions = partitioningRepository.fetchPartitions("edge_event");
        assertThat(partitions).contains(partitionStartTs);

        edgeEventsCleanUpService.cleanUp();
        partitions = partitioningRepository.fetchPartitions("edge_event");
        assertThat(partitions).doesNotContain(partitionStartTs);
        assertThat(partitions).allSatisfy(partitionsStart -> {
            long partitionEndTs = partitionsStart + partitionDurationInMs;
            assertThat(partitionEndTs).isGreaterThan(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(edgeEventTtlInSec));
        });
    }

    private List<EdgeEvent> findEdgeEvents(EdgeId edgeId) throws Exception {
        return doGetTypedWithTimePageLink("/api/edge/" + edgeId.toString() + "/events?",
                new TypeReference<PageData<EdgeEvent>>() {
                }, new TimePageLink(10)).getData();
    }

    private EntityGroup constructEntityGroup(String name, EntityType type) {
        EntityGroup result = new EntityGroup();
        result.setName(name);
        result.setType(type);
        return result;
    }

    private Device constructDevice(String name, String type) {
        Device device = new Device();
        device.setName(name);
        device.setType(type);
        return device;
    }

    private Asset constructAsset(String name, String type) {
        Asset asset = new Asset();
        asset.setName(name);
        asset.setType(type);
        return asset;
    }

    private EdgeEvent createEdgeEvent() {
        EdgeEvent edgeEvent = new EdgeEvent();
        edgeEvent.setCreatedTime(System.currentTimeMillis());
        edgeEvent.setTenantId(tenantId);
        edgeEvent.setAction(EdgeEventActionType.ADDED);
        edgeEvent.setEntityId(tenantAdminUser.getUuidId());
        edgeEvent.setType(EdgeEventType.ALARM);
        try {
            edgeEventDao.saveAsync(edgeEvent).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        return edgeEvent;
    }
}
