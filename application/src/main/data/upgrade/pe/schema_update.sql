--
-- ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
--
-- Copyright © 2016-2024 ThingsBoard, Inc. All Rights Reserved.
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


CREATE TABLE IF NOT EXISTS entity_group (
    id uuid NOT NULL CONSTRAINT entity_group_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    type varchar(255) NOT NULL,
    name varchar(255),
    owner_id uuid,
    owner_type varchar(255),
    additional_info varchar,
    configuration varchar(10000000),
    external_id uuid,
    version BIGINT DEFAULT 1,
    CONSTRAINT group_name_per_owner_unq_key UNIQUE (owner_id, owner_type, type, name)
);

CREATE TABLE IF NOT EXISTS converter (
    id uuid NOT NULL CONSTRAINT converter_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    additional_info varchar,
    configuration varchar(10000000),
    debug_mode boolean,
    name varchar(255),
    tenant_id uuid,
    type varchar(255),
    external_id uuid,
    is_edge_template boolean DEFAULT false,
    version BIGINT DEFAULT 1,
    CONSTRAINT converter_external_id_unq_key UNIQUE (tenant_id, external_id)
);

CREATE TABLE IF NOT EXISTS integration (
    id uuid NOT NULL CONSTRAINT integration_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    additional_info varchar,
    configuration varchar(10000000),
    debug_mode boolean,
    enabled boolean,
    is_remote boolean,
    allow_create_devices_or_assets boolean,
    name varchar(255),
    secret varchar(255),
    converter_id uuid not null,
    downlink_converter_id uuid,
    routing_key varchar(255),
    tenant_id uuid,
    type varchar(255),
    external_id uuid,
    is_edge_template boolean DEFAULT false,
    version BIGINT DEFAULT 1,
    CONSTRAINT integration_external_id_unq_key UNIQUE (tenant_id, external_id),
    CONSTRAINT fk_integration_converter FOREIGN KEY (converter_id) REFERENCES converter(id),
    CONSTRAINT fk_integration_downlink_converter FOREIGN KEY (downlink_converter_id) REFERENCES converter(id)
);

ALTER TABLE admin_settings ALTER COLUMN json_value SET DATA TYPE varchar(10000000);

CREATE TABLE IF NOT EXISTS scheduler_event (
    id uuid NOT NULL CONSTRAINT scheduler_event_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    additional_info varchar,
    customer_id uuid,
    originator_id uuid,
    originator_type varchar(255),
    name varchar(255),
    tenant_id uuid,
    type varchar(255),
    schedule varchar,
    configuration varchar(10000000),
    enabled boolean,
    version BIGINT DEFAULT 1
);

CREATE TABLE IF NOT EXISTS blob_entity (
    id uuid NOT NULL,
    created_time bigint NOT NULL,
    tenant_id uuid,
    customer_id uuid,
    name varchar(255),
    type varchar(255),
    content_type varchar(255),
    data varchar(10485760),
    additional_info varchar
) PARTITION BY RANGE (created_time);

CREATE TABLE IF NOT EXISTS role (
    id uuid NOT NULL CONSTRAINT role_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    tenant_id uuid,
    customer_id uuid,
    name varchar(255),
    type varchar(255),
    permissions varchar(10000000),
    additional_info varchar,
    external_id uuid,
    version BIGINT DEFAULT 1,
    CONSTRAINT role_external_id_unq_key UNIQUE (tenant_id, external_id)
);

CREATE TABLE IF NOT EXISTS group_permission (
    id uuid NOT NULL CONSTRAINT group_permission_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    tenant_id uuid,
    role_id uuid,
    user_group_id uuid,
    entity_group_id uuid,
    entity_group_type varchar(255),
    is_public boolean
);

CREATE TABLE IF NOT EXISTS device_group_ota_package (
    id uuid NOT NULL CONSTRAINT entity_group_firmware_pkey PRIMARY KEY,
    group_id uuid NOT NULL,
    ota_package_type varchar(32) NOT NULL,
    ota_package_id uuid NOT NULL,
    ota_package_update_time bigint NOT NULL,
    CONSTRAINT device_group_ota_package_unq_key UNIQUE (group_id, ota_package_type),
    CONSTRAINT fk_ota_package_device_group_ota_package FOREIGN KEY (ota_package_id) REFERENCES ota_package(id) ON DELETE CASCADE,
    CONSTRAINT fk_entity_group_device_group_ota_package FOREIGN KEY (group_id) REFERENCES entity_group(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS converter_debug_event (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    ts bigint NOT NULL,
    entity_id uuid NOT NULL,
    service_id varchar NOT NULL,
    e_type varchar,
    e_in_message_type varchar,
    e_in_message varchar,
    e_out_message_type varchar,
    e_out_message varchar,
    e_metadata varchar,
    e_error varchar
) PARTITION BY RANGE (ts);

CREATE TABLE IF NOT EXISTS integration_debug_event (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    ts bigint NOT NULL,
    entity_id uuid NOT NULL,
    service_id varchar NOT NULL,
    e_type varchar,
    e_message_type varchar,
    e_message varchar,
    e_status varchar,
    e_error varchar
) PARTITION BY RANGE (ts);

CREATE TABLE IF NOT EXISTS raw_data_event (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    ts bigint NOT NULL,
    entity_id uuid NOT NULL,
    service_id varchar NOT NULL,
    e_uuid varchar,
    e_message_type varchar,
    e_message varchar
) PARTITION BY RANGE (ts);

CREATE TABLE IF NOT EXISTS white_labeling (
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL default '13814000-1dd2-11b2-8080-808080808080',
    type VARCHAR(16),
    settings VARCHAR(10000000),
    domain_name VARCHAR(255) UNIQUE,
    CONSTRAINT white_labeling_pkey PRIMARY KEY (tenant_id, customer_id, type)
);

CREATE TABLE IF NOT EXISTS custom_translation (
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL default '13814000-1dd2-11b2-8080-808080808080',
    locale_code VARCHAR(10),
    value VARCHAR(1000000),
    CONSTRAINT custom_translation_pkey PRIMARY KEY (tenant_id, customer_id, locale_code)
);

CREATE TABLE IF NOT EXISTS custom_menu (
    id uuid NOT NULL CONSTRAINT custom_menu_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL default '13814000-1dd2-11b2-8080-808080808080',
    name varchar(255) NOT NULL,
    scope VARCHAR(16),
    assignee_type VARCHAR(16),
    config VARCHAR(10000000)
);

ALTER TABLE resource ADD COLUMN IF NOT EXISTS customer_id uuid;

CREATE INDEX IF NOT EXISTS idx_entity_group_by_type_name_and_owner_id ON entity_group(type, name, owner_id);

CREATE INDEX IF NOT EXISTS idx_converter_external_id ON converter(tenant_id, external_id);

CREATE INDEX IF NOT EXISTS idx_integration_external_id ON integration(tenant_id, external_id);

CREATE INDEX IF NOT EXISTS idx_role_external_id ON role(tenant_id, external_id);

CREATE INDEX IF NOT EXISTS idx_entity_group_external_id ON entity_group(external_id);

CREATE INDEX IF NOT EXISTS idx_blob_entity_created_time ON blob_entity(tenant_id, created_time DESC);

CREATE INDEX IF NOT EXISTS idx_blob_entity_id ON blob_entity(id);

CREATE INDEX IF NOT EXISTS idx_converter_debug_event_main
    ON converter_debug_event (tenant_id ASC, entity_id ASC, ts DESC NULLS LAST) WITH (FILLFACTOR=95);

CREATE INDEX IF NOT EXISTS idx_integration_debug_event_main
    ON integration_debug_event (tenant_id ASC, entity_id ASC, ts DESC NULLS LAST) WITH (FILLFACTOR=95);

CREATE INDEX IF NOT EXISTS idx_raw_data_event_main
    ON raw_data_event (tenant_id ASC, entity_id ASC, ts DESC NULLS LAST) WITH (FILLFACTOR=95);

CREATE INDEX IF NOT EXISTS idx_group_permission_tenant_id ON group_permission(tenant_id);

ALTER TABLE mobile_app_settings ADD COLUMN IF NOT EXISTS use_system_settings boolean default true;

ALTER TABLE tb_user ADD COLUMN IF NOT EXISTS custom_menu_id UUID;

ALTER TABLE customer ADD COLUMN IF NOT EXISTS custom_menu_id UUID;

CREATE INDEX IF NOT EXISTS idx_custom_menu ON custom_menu(tenant_id, customer_id);
