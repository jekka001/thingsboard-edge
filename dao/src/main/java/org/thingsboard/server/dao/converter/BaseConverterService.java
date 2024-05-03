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
package org.thingsboard.server.dao.converter;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.converter.Converter;
import org.thingsboard.server.common.data.id.ConverterId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.HasId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.entity.AbstractEntityService;
import org.thingsboard.server.dao.entity.EntityCountService;
import org.thingsboard.server.dao.eventsourcing.DeleteEntityEvent;
import org.thingsboard.server.dao.eventsourcing.SaveEntityEvent;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.service.PaginatedRemover;
import org.thingsboard.server.dao.sql.JpaExecutorService;
import org.thingsboard.server.exception.DataValidationException;

import java.util.List;
import java.util.Optional;

import static org.thingsboard.server.dao.DaoUtil.toUUIDs;
import static org.thingsboard.server.dao.service.Validator.validateId;
import static org.thingsboard.server.dao.service.Validator.validateIds;
import static org.thingsboard.server.dao.service.Validator.validatePageLink;
import static org.thingsboard.server.dao.service.Validator.validateString;

@Service("ConverterDaoService")
@Slf4j
public class BaseConverterService extends AbstractEntityService implements ConverterService {

    public static final String INCORRECT_TENANT_ID = "Incorrect tenantId ";
    public static final String INCORRECT_CONVERTER_ID = "Incorrect converterId ";
    public static final String INCORRECT_CONVERTER_NAME = "Incorrect converter name ";

    @Autowired
    private ConverterDao converterDao;

    @Autowired
    private DataValidator<Converter> converterValidator;

    @Autowired
    private EntityCountService entityCountService;

    @Autowired
    private JpaExecutorService executor;

    @Override
    public Converter saveConverter(Converter converter) {
        log.trace("Executing saveConverter [{}]", converter);
        converterValidator.validate(converter, Converter::getTenantId);
        try {
            Converter savedConverter = converterDao.save(converter.getTenantId(), converter);
            if (converter.getId() == null) {
                entityCountService.publishCountEntityEvictEvent(converter.getTenantId(), EntityType.CONVERTER);
            }
            eventPublisher.publishEvent(SaveEntityEvent.builder().tenantId(savedConverter.getTenantId()).entity(savedConverter)
                    .entityId(savedConverter.getId()).created(converter.getId() == null).build());
            return savedConverter;
        } catch (Exception t) {
            checkConstraintViolation(t,
                    "converter_external_id_unq_key", "Converter with such external id already exists!");
            throw t;
        }
    }

    @Override
    public Converter findConverterById(TenantId tenantId, ConverterId converterId) {
        log.trace("Executing findConverterById [{}]", converterId);
        validateId(converterId, id -> INCORRECT_CONVERTER_ID + id);
        return converterDao.findById(tenantId, converterId.getId());
    }

    @Override
    public Optional<Converter> findConverterByName(TenantId tenantId, String converterName) {
        log.trace("Executing findConverterByName, tenantId [{}], name [{}]", tenantId, converterName);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validateString(converterName, n -> INCORRECT_CONVERTER_NAME + n);
        return converterDao.findConverterByTenantIdAndName(tenantId.getId(), converterName);
    }

    @Override
    public ListenableFuture<Optional<Converter>> findConverterByNameAsync(TenantId tenantId, String converterName) {
        log.trace("Executing findConverterByNameAsync, tenantId [{}], name [{}]", tenantId, converterName);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validateString(converterName, n -> INCORRECT_CONVERTER_NAME + n);
        return executor.submit(() -> findConverterByName(tenantId, converterName));
    }

    @Override
    public ListenableFuture<Converter> findConverterByIdAsync(TenantId tenantId, ConverterId converterId) {
        log.trace("Executing findConverterByIdAsync [{}]", converterId);
        validateId(converterId, id -> INCORRECT_CONVERTER_ID + id);
        return converterDao.findByIdAsync(tenantId, converterId.getId());
    }

    @Override
    public ListenableFuture<List<Converter>> findConvertersByIdsAsync(TenantId tenantId, List<ConverterId> converterIds) {
        log.trace("Executing findConvertersByIdsAsync, tenantId [{}], converterIds [{}]", tenantId, converterIds);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validateIds(converterIds, ids -> "Incorrect converterIds " + ids);
        return converterDao.findConvertersByTenantIdAndIdsAsync(tenantId.getId(), toUUIDs(converterIds));
    }

    @Override
    public PageData<Converter> findTenantConverters(TenantId tenantId, PageLink pageLink) {
        log.trace("Executing findTenantConverters, tenantId [{}], pageLink [{}]", tenantId, pageLink);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validatePageLink(pageLink);
        return converterDao.findCoreConvertersByTenantId(tenantId.getId(), pageLink);
    }

    @Override
    public PageData<Converter> findTenantEdgeTemplateConverters(TenantId tenantId, PageLink pageLink) {
        log.trace("Executing findTenantEdgeTemplateConverters, tenantId [{}], pageLink [{}]", tenantId, pageLink);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validatePageLink(pageLink);
        return converterDao.findEdgeTemplateConvertersByTenantId(tenantId.getId(), pageLink);
    }

    @Override
    @Transactional
    public void deleteConverter(TenantId tenantId, ConverterId converterId) {
        log.trace("Executing deleteConverter [{}]", converterId);
        Converter converter = findConverterById(tenantId, converterId);
        validateId(converterId, id -> INCORRECT_CONVERTER_ID + id);
        try {
            converterDao.removeById(tenantId, converterId.getId());
        } catch (Exception t) {
            ConstraintViolationException e = DaoUtil.extractConstraintViolationException(t).orElse(null);
            if (e != null && e.getConstraintName() != null && e.getConstraintName().equalsIgnoreCase("fk_integration_converter")) {
                throw new DataValidationException("The converter referenced by the integration cannot be deleted!");
            } else if (e != null && e.getConstraintName() != null && e.getConstraintName().equalsIgnoreCase("fk_integration_downlink_converter")) {
                throw new DataValidationException("The downlink converter referenced by the integration cannot be deleted!");
            } else {
                throw t;
            }
        }
        eventPublisher.publishEvent(DeleteEntityEvent.builder().tenantId(tenantId).entity(converter).entityId(converterId).build());
        entityCountService.publishCountEntityEvictEvent(tenantId, EntityType.CONVERTER);
    }

    @Override
    @Transactional
    public void deleteEntity(TenantId tenantId, EntityId id, boolean force) {
        deleteConverter(tenantId, (ConverterId) id);
    }

    @Override
    public void deleteConvertersByTenantId(TenantId tenantId) {
        log.trace("Executing deleteConvertersByTenantId, tenantId [{}]", tenantId);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        tenantConvertersRemover.removeEntities(tenantId, tenantId);
    }

    @Override
    public void deleteByTenantId(TenantId tenantId) {
        deleteConvertersByTenantId(tenantId);
    }

    private PaginatedRemover<TenantId, Converter> tenantConvertersRemover =
            new PaginatedRemover<TenantId, Converter>() {

                @Override
                protected PageData<Converter> findEntities(TenantId tenantId, TenantId id, PageLink pageLink) {
                    return converterDao.findByTenantId(id.getId(), pageLink);
                }

                @Override
                protected void removeEntity(TenantId tenantId, Converter entity) {
                    deleteConverter(tenantId, new ConverterId(entity.getId().getId()));
                }
            };

    @Override
    public Optional<HasId<?>> findEntity(TenantId tenantId, EntityId entityId) {
        return Optional.ofNullable(findConverterById(tenantId, new ConverterId(entityId.getId())));
    }

    @Override
    public long countByTenantId(TenantId tenantId) {
        return converterDao.countByTenantId(tenantId);
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.CONVERTER;
    }

}
