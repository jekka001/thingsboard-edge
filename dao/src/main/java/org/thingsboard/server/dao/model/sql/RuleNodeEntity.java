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
package org.thingsboard.server.dao.model.sql;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.RuleNodeId;
import org.thingsboard.server.common.data.rule.RuleNode;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.model.BaseSqlEntity;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.dao.util.mapping.JsonConverter;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = ModelConstants.RULE_NODE_TABLE_NAME)
public class RuleNodeEntity extends BaseSqlEntity<RuleNode> {

    @Column(name = ModelConstants.RULE_NODE_CHAIN_ID_PROPERTY)
    private UUID ruleChainId;

    @Column(name = ModelConstants.RULE_NODE_TYPE_PROPERTY)
    private String type;

    @Column(name = ModelConstants.RULE_NODE_NAME_PROPERTY)
    private String name;

    @Column(name = ModelConstants.RULE_NODE_VERSION_PROPERTY)
    private int configurationVersion;

    @Convert(converter = JsonConverter.class)
    @Column(name = ModelConstants.RULE_NODE_CONFIGURATION_PROPERTY)
    private JsonNode configuration;

    @Convert(converter = JsonConverter.class)
    @Column(name = ModelConstants.ADDITIONAL_INFO_PROPERTY)
    private JsonNode additionalInfo;

    @Column(name = ModelConstants.DEBUG_FAILURES)
    private boolean debugFailures;

    @Column(name = ModelConstants.DEBUG__ALL_UNTIL)
    private long debugAllUntil;

    @Column(name = ModelConstants.SINGLETON_MODE)
    private boolean singletonMode;

    @Column(name = ModelConstants.QUEUE_NAME)
    private String queueName;

    @Column(name = ModelConstants.EXTERNAL_ID_PROPERTY)
    private UUID externalId;

    public RuleNodeEntity() {
    }

    public RuleNodeEntity(RuleNode ruleNode) {
        if (ruleNode.getId() != null) {
            this.setUuid(ruleNode.getUuidId());
        }
        this.setCreatedTime(ruleNode.getCreatedTime());
        if (ruleNode.getRuleChainId() != null) {
            this.ruleChainId = DaoUtil.getId(ruleNode.getRuleChainId());
        }
        this.type = ruleNode.getType();
        this.name = ruleNode.getName();
        this.debugFailures = ruleNode.isDebugFailures();
        this.debugAllUntil = ruleNode.getDebugAllUntil();
        this.singletonMode = ruleNode.isSingletonMode();
        this.queueName = ruleNode.getQueueName();
        this.configurationVersion = ruleNode.getConfigurationVersion();
        this.configuration = ruleNode.getConfiguration();
        this.additionalInfo = ruleNode.getAdditionalInfo();
        if (ruleNode.getExternalId() != null) {
            this.externalId = ruleNode.getExternalId().getId();
        }
    }

    @Override
    public RuleNode toData() {
        RuleNode ruleNode = new RuleNode(new RuleNodeId(this.getUuid()));
        ruleNode.setCreatedTime(createdTime);
        if (ruleChainId != null) {
            ruleNode.setRuleChainId(new RuleChainId(ruleChainId));
        }
        ruleNode.setType(type);
        ruleNode.setName(name);
        ruleNode.setDebugFailures(debugFailures);
        ruleNode.setDebugAllUntil(debugAllUntil);
        ruleNode.setSingletonMode(singletonMode);
        ruleNode.setQueueName(queueName);
        ruleNode.setConfigurationVersion(configurationVersion);
        ruleNode.setConfiguration(configuration);
        ruleNode.setAdditionalInfo(additionalInfo);
        if (externalId != null) {
            ruleNode.setExternalId(new RuleNodeId(externalId));
        }
        return ruleNode;
    }
}
