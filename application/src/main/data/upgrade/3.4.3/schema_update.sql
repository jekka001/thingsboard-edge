--
-- ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
--
-- Copyright © 2016-2022 ThingsBoard, Inc. All Rights Reserved.
--
-- NOTICE: All information contained herein is, and remains
-- the property of ThingsBoard, Inc. and its suppliers,
-- if any.  The intellectual and technical concepts contained
-- herein are proprietary to ThingsBoard, Inc.
-- and its suppliers and may be covered by U.S. and Foreign Patents,
-- patents in process, and are protected by trade secret or copyright law.
--
-- Dissemination of this information or reproduction of this material is strictly forbidden
-- unless prior written permission is obtained from COMPANY.
--
-- Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
-- managers or contractors who have executed Confidentiality and Non-disclosure agreements
-- explicitly covering such access.
--
-- The copyright notice above does not evidence any actual or intended publication
-- or disclosure  of  this source code, which includes
-- information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
-- ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
-- OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
-- THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
-- AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
-- THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
-- DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
-- OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
--

CREATE TABLE IF NOT EXISTS notification_target (
    id UUID NOT NULL CONSTRAINT notification_target_pkey PRIMARY KEY,
    created_time BIGINT NOT NULL,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    configuration VARCHAR(10000) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_notification_target_tenant_id_created_time ON notification_target(tenant_id, created_time DESC);

CREATE TABLE IF NOT EXISTS notification_template (
    id UUID NOT NULL CONSTRAINT notification_template_pkey PRIMARY KEY,
    created_time BIGINT NOT NULL,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    configuration VARCHAR(10000) NOT NULL
);

CREATE TABLE IF NOT EXISTS notification_rule (
    id UUID NOT NULL CONSTRAINT notification_rule_pkey PRIMARY KEY,
    created_time BIGINT NOT NULL,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    template_id UUID NOT NULL CONSTRAINT fk_notification_rule_template_id REFERENCES notification_template(id),
    delivery_methods VARCHAR(255),
    initial_notification_target_id UUID NULL CONSTRAINT fk_notification_rule_target_id REFERENCES notification_target(id),
    escalation_config VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS notification_request (
    id UUID NOT NULL CONSTRAINT notification_request_pkey PRIMARY KEY,
    created_time BIGINT NOT NULL,
    tenant_id UUID NOT NULL,
    target_id UUID NOT NULL CONSTRAINT fk_notification_request_target_id REFERENCES notification_target(id),
    type VARCHAR(255) NOT NULL,
    template_id UUID NOT NULL CONSTRAINT fk_notification_request_template_id REFERENCES notification_template(id),
    info VARCHAR(1000),
    delivery_methods VARCHAR(255),
    additional_config VARCHAR(1000),
    originator_type VARCHAR(32) NOT NULL,
    originator_entity_id UUID,
    originator_entity_type VARCHAR(32),
    rule_id UUID NULL CONSTRAINT fk_notification_request_rule_id REFERENCES notification_rule(id),
    status VARCHAR(32)
);
CREATE INDEX IF NOT EXISTS idx_notification_request_tenant_id_originator_type_created_time ON notification_request(tenant_id, originator_type, created_time DESC);

CREATE TABLE IF NOT EXISTS notification (
    id UUID NOT NULL,
    created_time BIGINT NOT NULL,
    request_id UUID NOT NULL CONSTRAINT fk_notification_request_id REFERENCES notification_request(id) ON DELETE CASCADE,
    recipient_id UUID NOT NULL CONSTRAINT fk_notification_recipient_id REFERENCES tb_user(id) ON DELETE CASCADE,
    type VARCHAR(255) NOT NULL,
    text VARCHAR(1000) NOT NULL,
    info VARCHAR(1000),
    originator_type VARCHAR(32) NOT NULL,
    status VARCHAR(32)
) PARTITION BY RANGE (created_time);
CREATE INDEX IF NOT EXISTS idx_notification_id ON notification(id);
CREATE INDEX IF NOT EXISTS idx_notification_recipient_id_created_time ON notification(recipient_id, created_time DESC);
CREATE INDEX IF NOT EXISTS idx_notification_notification_request_id ON notification(request_id);

ALTER TABLE alarm ADD COLUMN IF NOT EXISTS notification_rule_id UUID;

ALTER TABLE tb_user ADD COLUMN IF NOT EXISTS phone VARCHAR(255);
