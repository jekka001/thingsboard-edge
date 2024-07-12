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
package org.thingsboard.server.common.data.rule;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.BaseDataWithAdditionalInfo;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ExportableEntity;
import org.thingsboard.server.common.data.HasDefaultOption;
import org.thingsboard.server.common.data.HasName;
import org.thingsboard.server.common.data.TenantEntity;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.RuleNodeId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.validation.Length;
import org.thingsboard.server.common.data.validation.NoXss;

@Schema
@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class RuleChain extends BaseDataWithAdditionalInfo<RuleChainId> implements HasName, TenantEntity, ExportableEntity<RuleChainId>, HasDefaultOption {

    private static final long serialVersionUID = -5656679015121935465L;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "JSON object with Tenant Id.", accessMode = Schema.AccessMode.READ_ONLY)
    private TenantId tenantId;
    @NoXss
    @Length(fieldName = "name")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Rule Chain name", example = "Humidity data processing")
    private String name;
    @Schema(description = "Rule Chain type. 'EDGE' rule chains are processing messages on the edge devices only.", example = "A4B72CCDFF33")
    private RuleChainType type;
    @Schema(description = "JSON object with Rule Chain Id. Pointer to the first rule node that should receive all messages pushed to this rule chain.")
    private RuleNodeId firstRuleNodeId;
    @Schema(description = "Indicates root rule chain. The root rule chain process messages from all devices and entities by default. User may configure default rule chain per device profile.")
    private boolean root;
    @Schema(description = "Reserved for future usage.")
    private boolean debugMode;
    @Schema(description = "Reserved for future usage. The actual list of rule nodes and their relations is stored in the database separately.")
    private transient JsonNode configuration;

    private RuleChainId externalId;

    @JsonIgnore
    private byte[] configurationBytes;

    public RuleChain() {
        super();
    }

    public RuleChain(RuleChainId id) {
        super(id);
    }

    public RuleChain(RuleChain ruleChain) {
        super(ruleChain);
        this.tenantId = ruleChain.getTenantId();
        this.name = ruleChain.getName();
        this.type = ruleChain.getType();
        this.firstRuleNodeId = ruleChain.getFirstRuleNodeId();
        this.root = ruleChain.isRoot();
        this.setConfiguration(ruleChain.getConfiguration());
        this.setExternalId(ruleChain.getExternalId());
    }

    @Override
    public String getName() {
        return name;
    }

    @Schema(description = "JSON object with the Rule Chain Id. " +
            "Specify this field to update the Rule Chain. " +
            "Referencing non-existing Rule Chain Id will cause error. " +
            "Omit this field to create new rule chain." )
    @Override
    public RuleChainId getId() {
        return super.getId();
    }

    @Schema(description = "Timestamp of the rule chain creation, in milliseconds", example = "1609459200000", accessMode = Schema.AccessMode.READ_ONLY)
    @Override
    public long getCreatedTime() {
        return super.getCreatedTime();
    }

    public JsonNode getConfiguration() {
        return BaseDataWithAdditionalInfo.getJson(() -> configuration, () -> configurationBytes);
    }

    public void setConfiguration(JsonNode data) {
        setJson(data, json -> this.configuration = json, bytes -> this.configurationBytes = bytes);
    }

    @JsonIgnore
    @Override
    public boolean isDefault() {
        return root && type == RuleChainType.CORE;
    }

    @Override
    @JsonIgnore
    public EntityType getEntityType() {
        return EntityType.RULE_CHAIN;
    }

}
