--
-- ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
--
-- Copyright © 2016-2023 ThingsBoard, Inc. All Rights Reserved.
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

ALTER TABLE notification_rule ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT true;

-- NOTIFICATION CONFIGS VERSION CONTROL START

ALTER TABLE notification_template
    ADD COLUMN IF NOT EXISTS external_id UUID;
DO
$$
    BEGIN
        IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname = 'uq_notification_template_external_id') THEN
            ALTER TABLE notification_template ADD CONSTRAINT uq_notification_template_external_id UNIQUE (tenant_id, external_id);
        END IF;
    END;
$$;

ALTER TABLE notification_target
    ADD COLUMN IF NOT EXISTS external_id UUID;
DO
$$
    BEGIN
        IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname = 'uq_notification_target_external_id') THEN
            ALTER TABLE notification_target ADD CONSTRAINT uq_notification_target_external_id UNIQUE (tenant_id, external_id);
        END IF;
    END;
$$;

ALTER TABLE notification_rule
    ADD COLUMN IF NOT EXISTS external_id UUID;
DO
$$
    BEGIN
        IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname = 'uq_notification_rule_external_id') THEN
            ALTER TABLE notification_rule ADD CONSTRAINT uq_notification_rule_external_id UNIQUE (tenant_id, external_id);
        END IF;
    END;
$$;

-- NOTIFICATION CONFIGS VERSION CONTROL END

ALTER TABLE resource
    ADD COLUMN IF NOT EXISTS etag varchar;

UPDATE resource
    SET etag = encode(sha256(decode(resource.data, 'base64')),'hex') WHERE resource.data is not null;

