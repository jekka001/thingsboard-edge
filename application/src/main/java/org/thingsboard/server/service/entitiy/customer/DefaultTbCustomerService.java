/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2022 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.service.entitiy.customer;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.service.entitiy.AbstractTbEntityService;

import java.util.List;

@Service
@AllArgsConstructor
public class DefaultTbCustomerService extends AbstractTbEntityService implements TbCustomerService {

    @Override
    public Customer save(Customer customer, EntityGroup entityGroup, User user) throws ThingsboardException {
        ActionType actionType = customer.getId() == null ? ActionType.ADDED : ActionType.UPDATED;
        TenantId tenantId = customer.getTenantId();
        Customer savedCustomer = checkNotNull(customerService.saveCustomer(customer));
        createOrUpdateGroupEntity(tenantId, savedCustomer, entityGroup, actionType, user);
        return savedCustomer;
    }

    @Override
    public void delete(Customer customer, User user) {
        TenantId tenantId = customer.getTenantId();
        CustomerId customerId = customer.getId();
        List<EdgeId> relatedEdgeIds = findRelatedEdgeIds(tenantId, customerId);
        customerService.deleteCustomer(tenantId, customerId);
        notificationEntityService.notifyDeleteEntity(tenantId, customerId, customer, customerId,
                ActionType.DELETED, relatedEdgeIds, user, customerId.toString());
    }
}
