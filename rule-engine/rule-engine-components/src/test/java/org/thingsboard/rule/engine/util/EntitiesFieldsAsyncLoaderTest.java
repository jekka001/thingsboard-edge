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
package org.thingsboard.rule.engine.util;

import com.google.common.util.concurrent.Futures;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.common.util.ListeningExecutor;
import org.thingsboard.rule.engine.TestDbCallbackExecutor;
import org.thingsboard.rule.engine.api.RuleEngineAlarmService;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.server.common.data.BaseData;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityFieldsData;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.id.AlarmId;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UUIDBased;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.edge.EdgeService;
import org.thingsboard.server.dao.entityview.EntityViewService;
import org.thingsboard.server.dao.rule.RuleChainService;
import org.thingsboard.server.dao.tenant.TenantService;
import org.thingsboard.server.dao.user.UserService;

import java.util.EnumSet;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EntitiesFieldsAsyncLoaderTest {

    private static final ListeningExecutor DB_EXECUTOR = new TestDbCallbackExecutor();
    private static EnumSet<EntityType> SUPPORTED_ENTITY_TYPES;
    private static UUID RANDOM_UUID;
    private static TenantId TENANT_ID;
    @Mock
    private TbContext ctxMock;
    @Mock
    private TenantService tenantServiceMock;
    @Mock
    private CustomerService customerServiceMock;
    @Mock
    private UserService userServiceMock;
    @Mock
    private AssetService assetServiceMock;
    @Mock
    private DeviceService deviceServiceMock;
    @Mock
    private RuleEngineAlarmService ruleEngineAlarmServiceMock;
    @Mock
    private RuleChainService ruleChainServiceMock;
    @Mock
    private EntityViewService entityViewServiceMock;
    @Mock
    private EdgeService edgeServiceMock;

    @BeforeAll
    public static void setup() {
        RANDOM_UUID = UUID.randomUUID();
        TENANT_ID = new TenantId(UUID.randomUUID());
        SUPPORTED_ENTITY_TYPES = EnumSet.of(
                EntityType.TENANT,
                EntityType.CUSTOMER,
                EntityType.USER,
                EntityType.ASSET,
                EntityType.DEVICE,
                EntityType.ALARM,
                EntityType.RULE_CHAIN,
                EntityType.ENTITY_VIEW,
                EntityType.EDGE
        );
    }

    @Test
    public void givenSupportedEntityTypes_whenFindAsync_thenOK() throws ExecutionException, InterruptedException {
        for (var entityType : SUPPORTED_ENTITY_TYPES) {
            var entityId = EntityIdFactory.getByTypeAndUuid(entityType, RANDOM_UUID);

            initMocks(entityType, false);

            when(ctxMock.getTenantId()).thenReturn(TENANT_ID);

            var actualEntityFieldsData = EntitiesFieldsAsyncLoader.findAsync(ctxMock, entityId).get();
            var expectedEntityFieldsData = new EntityFieldsData(getEntityFromEntityId(entityId));

            Assertions.assertEquals(expectedEntityFieldsData, actualEntityFieldsData);
        }
    }

    @Test
    public void givenUnsupportedEntityTypes_whenFindAsync_thenException() {
        for (var entityType : EntityType.values()) {
            if (!SUPPORTED_ENTITY_TYPES.contains(entityType)) {
                var entityId = EntityIdFactory.getByTypeAndUuid(entityType, RANDOM_UUID);

                var expectedExceptionMsg = "org.thingsboard.rule.engine.api.TbNodeException: Unexpected originator EntityType: " + entityType;

                var exception = assertThrows(ExecutionException.class,
                        () -> EntitiesFieldsAsyncLoader.findAsync(ctxMock, entityId).get());

                assertInstanceOf(TbNodeException.class, exception.getCause());
                assertThat(exception.getMessage()).isEqualTo(expectedExceptionMsg);
            }
        }
    }

    @Test
    public void givenSupportedTypeButEntityDoesNotExist_whenFindAsync_thenException() {
        for (var entityType : SUPPORTED_ENTITY_TYPES) {
            var entityId = EntityIdFactory.getByTypeAndUuid(entityType, RANDOM_UUID);

            initMocks(entityType, true);
            when(ctxMock.getTenantId()).thenReturn(TENANT_ID);

            var expectedExceptionMsg = "java.util.NoSuchElementException: Entity not found!";

            var exception = assertThrows(ExecutionException.class,
                    () -> EntitiesFieldsAsyncLoader.findAsync(ctxMock, entityId).get());

            assertInstanceOf(NoSuchElementException.class, exception.getCause());
            assertThat(exception.getMessage()).isEqualTo(expectedExceptionMsg);
        }
    }

    private void initMocks(EntityType entityType, boolean entityDoesNotExist) {
        switch (entityType) {
            case TENANT:
                var tenant = Futures.immediateFuture(entityDoesNotExist ? null : new Tenant(new TenantId(RANDOM_UUID)));

                when(ctxMock.getDbCallbackExecutor()).thenReturn(DB_EXECUTOR);
                when(ctxMock.getTenantService()).thenReturn(tenantServiceMock);
                doReturn(tenant).when(tenantServiceMock).findTenantByIdAsync(eq(TENANT_ID), any());

                break;
            case CUSTOMER:
                var customer = Futures.immediateFuture(entityDoesNotExist ? null : new Customer(new CustomerId(RANDOM_UUID)));

                when(ctxMock.getDbCallbackExecutor()).thenReturn(DB_EXECUTOR);
                when(ctxMock.getCustomerService()).thenReturn(customerServiceMock);
                doReturn(customer).when(customerServiceMock).findCustomerByIdAsync(eq(TENANT_ID), any());

                break;
            case USER:
                var user = Futures.immediateFuture(entityDoesNotExist ? null : new User(new UserId(RANDOM_UUID)));

                when(ctxMock.getDbCallbackExecutor()).thenReturn(DB_EXECUTOR);
                when(ctxMock.getUserService()).thenReturn(userServiceMock);
                doReturn(user).when(userServiceMock).findUserByIdAsync(eq(TENANT_ID), any());

                break;
            case ASSET:
                var asset = Futures.immediateFuture(entityDoesNotExist ? null : new Asset(new AssetId(RANDOM_UUID)));

                when(ctxMock.getDbCallbackExecutor()).thenReturn(DB_EXECUTOR);
                when(ctxMock.getAssetService()).thenReturn(assetServiceMock);
                doReturn(asset).when(assetServiceMock).findAssetByIdAsync(eq(TENANT_ID), any());

                break;
            case DEVICE:
                var device = entityDoesNotExist ? null : new Device(new DeviceId(RANDOM_UUID));

                when(ctxMock.getDbCallbackExecutor()).thenReturn(DB_EXECUTOR);
                when(ctxMock.getDeviceService()).thenReturn(deviceServiceMock);
                doReturn(device).when(deviceServiceMock).findDeviceById(eq(TENANT_ID), any());

                break;
            case ALARM:
                var alarm = Futures.immediateFuture(entityDoesNotExist ? null : new Alarm(new AlarmId(RANDOM_UUID)));

                when(ctxMock.getDbCallbackExecutor()).thenReturn(DB_EXECUTOR);
                when(ctxMock.getAlarmService()).thenReturn(ruleEngineAlarmServiceMock);
                doReturn(alarm).when(ruleEngineAlarmServiceMock).findAlarmByIdAsync(eq(TENANT_ID), any());

                break;
            case RULE_CHAIN:
                var ruleChain = Futures.immediateFuture(entityDoesNotExist ? null : new RuleChain(new RuleChainId(RANDOM_UUID)));

                when(ctxMock.getDbCallbackExecutor()).thenReturn(DB_EXECUTOR);
                when(ctxMock.getRuleChainService()).thenReturn(ruleChainServiceMock);
                doReturn(ruleChain).when(ruleChainServiceMock).findRuleChainByIdAsync(eq(TENANT_ID), any());

                break;
            case ENTITY_VIEW:
                var entityView = Futures.immediateFuture(entityDoesNotExist ? null : new EntityView(new EntityViewId(RANDOM_UUID)));

                when(ctxMock.getDbCallbackExecutor()).thenReturn(DB_EXECUTOR);
                when(ctxMock.getEntityViewService()).thenReturn(entityViewServiceMock);
                doReturn(entityView).when(entityViewServiceMock).findEntityViewByIdAsync(eq(TENANT_ID), any());

                break;
            case EDGE:
                var edge = Futures.immediateFuture(entityDoesNotExist ? null : new Edge(new EdgeId(RANDOM_UUID)));

                when(ctxMock.getDbCallbackExecutor()).thenReturn(DB_EXECUTOR);
                when(ctxMock.getEdgeService()).thenReturn(edgeServiceMock);
                doReturn(edge).when(edgeServiceMock).findEdgeByIdAsync(eq(TENANT_ID), any());

                break;
            default:
                throw new RuntimeException("Unexpected EntityType: " + entityType);
        }
    }

    private BaseData<? extends UUIDBased> getEntityFromEntityId(EntityId entityId) {
        switch (entityId.getEntityType()) {
            case TENANT:
                return new Tenant((TenantId) entityId);
            case CUSTOMER:
                return new Customer((CustomerId) entityId);
            case USER:
                return new User((UserId) entityId);
            case ASSET:
                return new Asset((AssetId) entityId);
            case DEVICE:
                return new Device((DeviceId) entityId);
            case ALARM:
                return new Alarm((AlarmId) entityId);
            case RULE_CHAIN:
                return new RuleChain((RuleChainId) entityId);
            case ENTITY_VIEW:
                return new EntityView((EntityViewId) entityId);
            case EDGE:
                return new Edge((EdgeId) entityId);
            default:
                throw new RuntimeException("Unexpected EntityType: " + entityId.getEntityType());
        }
    }

}
