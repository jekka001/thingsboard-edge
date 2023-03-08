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

-- USER CREDENTIALS START

ALTER TABLE user_credentials
    ADD COLUMN IF NOT EXISTS additional_info varchar NOT NULL DEFAULT '{}';

UPDATE user_credentials
    SET additional_info = json_build_object('userPasswordHistory', (u.additional_info::json -> 'userPasswordHistory'))
    FROM tb_user u WHERE user_credentials.user_id = u.id AND u.additional_info::jsonb ? 'userPasswordHistory';

UPDATE tb_user SET additional_info = tb_user.additional_info::jsonb - 'userPasswordHistory' WHERE additional_info::jsonb ? 'userPasswordHistory';

-- USER CREDENTIALS END

-- ALARM ASSIGN TO USER START

ALTER TABLE alarm ADD COLUMN IF NOT EXISTS assign_ts BIGINT;
ALTER TABLE alarm ADD COLUMN IF NOT EXISTS assignee_id UUID;

CREATE INDEX IF NOT EXISTS idx_alarm_tenant_assignee_created_time ON alarm(tenant_id, assignee_id, created_time DESC);

-- ALARM ASSIGN TO USER END

-- ALARM STATUS REFACTORING START

ALTER TABLE alarm ADD COLUMN IF NOT EXISTS acknowledged boolean;
ALTER TABLE alarm ADD COLUMN IF NOT EXISTS cleared boolean;

ALTER TABLE alarm ADD COLUMN IF NOT EXISTS status varchar; -- to avoid failure of the subsequent upgrade.
UPDATE alarm SET acknowledged = true, cleared = true WHERE status = 'CLEARED_ACK';
UPDATE alarm SET acknowledged = true, cleared = false WHERE status = 'ACTIVE_ACK';
UPDATE alarm SET acknowledged = false, cleared = true WHERE status = 'CLEARED_UNACK';
UPDATE alarm SET acknowledged = false, cleared = false WHERE status = 'ACTIVE_UNACK';

-- Drop index by 'status' column and replace with new one that has only active alarms;
DROP INDEX IF EXISTS idx_alarm_originator_alarm_type_active;
CREATE INDEX IF NOT EXISTS idx_alarm_originator_alarm_type_active
    ON alarm USING btree (originator_id, type) WHERE cleared = false;

-- Cover index by alarm type to optimize propagated alarm queries;
DROP INDEX IF EXISTS idx_entity_alarm_entity_id_alarm_type_created_time_alarm_id;
CREATE INDEX IF NOT EXISTS idx_entity_alarm_entity_id_alarm_type_created_time_alarm_id ON entity_alarm
USING btree (tenant_id, entity_id, alarm_type, created_time DESC) INCLUDE(alarm_id);

DROP INDEX IF EXISTS idx_alarm_tenant_status_created_time;
ALTER TABLE alarm DROP COLUMN IF EXISTS status;

-- Update old alarms and set their state to clear, if there are newer alarms.
UPDATE alarm a
SET cleared = TRUE
WHERE cleared = FALSE
  AND id != (SELECT l.id
             FROM alarm l
             WHERE l.tenant_id = a.tenant_id
               AND l.originator_id = a.originator_id
               AND l.type = a.type
             ORDER BY l.created_time DESC, l.id
             LIMIT 1);

VACUUM FULL ANALYZE alarm;

-- ALARM STATUS REFACTORING END

-- ALARM COMMENTS START

CREATE TABLE IF NOT EXISTS alarm_comment (
    id uuid NOT NULL,
    created_time bigint NOT NULL,
    alarm_id uuid NOT NULL,
    user_id uuid,
    type varchar(255) NOT NULL,
    comment varchar(10000),
    CONSTRAINT fk_alarm_comment_alarm_id FOREIGN KEY (alarm_id) REFERENCES alarm(id) ON DELETE CASCADE
) PARTITION BY RANGE (created_time);
CREATE INDEX IF NOT EXISTS idx_alarm_comment_alarm_id ON alarm_comment(alarm_id);

CREATE TABLE IF NOT EXISTS user_settings (
    user_id uuid NOT NULL CONSTRAINT user_settings_pkey PRIMARY KEY,
    settings varchar(100000),
    CONSTRAINT fk_user_id FOREIGN KEY (user_id) REFERENCES tb_user(id) ON DELETE CASCADE
);

-- ALARM COMMENTS END

-- ALARM INFO VIEW

DROP VIEW IF EXISTS alarm_info CASCADE;
CREATE VIEW alarm_info AS
SELECT a.*,
(CASE WHEN a.acknowledged AND a.cleared THEN 'CLEARED_ACK'
      WHEN NOT a.acknowledged AND a.cleared THEN 'CLEARED_UNACK'
      WHEN a.acknowledged AND NOT a.cleared THEN 'ACTIVE_ACK'
      WHEN NOT a.acknowledged AND NOT a.cleared THEN 'ACTIVE_UNACK' END) as status,
COALESCE(CASE WHEN a.originator_type = 0 THEN (select title from tenant where id = a.originator_id)
              WHEN a.originator_type = 1 THEN (select title from customer where id = a.originator_id)
              WHEN a.originator_type = 2 THEN (select email from tb_user where id = a.originator_id)
              WHEN a.originator_type = 3 THEN (select title from dashboard where id = a.originator_id)
              WHEN a.originator_type = 4 THEN (select name from asset where id = a.originator_id)
              WHEN a.originator_type = 5 THEN (select name from device where id = a.originator_id)
              WHEN a.originator_type = 8 THEN (select name from converter where id = a.originator_id)
              WHEN a.originator_type = 9 THEN (select name from integration where id = a.originator_id)
              WHEN a.originator_type = 14 THEN (select name from entity_view where id = a.originator_id)
              WHEN a.originator_type = 20 THEN (select name from device_profile where id = a.originator_id)
              WHEN a.originator_type = 21 THEN (select name from asset_profile where id = a.originator_id)
              WHEN a.originator_type = 25 THEN (select name from edge where id = a.originator_id) END
    , 'Deleted') originator_name,
COALESCE(CASE WHEN a.originator_type = 0 THEN (select title from tenant where id = a.originator_id)
              WHEN a.originator_type = 1 THEN (select COALESCE(title, email) from customer where id = a.originator_id)
              WHEN a.originator_type = 2 THEN (select email from tb_user where id = a.originator_id)
              WHEN a.originator_type = 3 THEN (select title from dashboard where id = a.originator_id)
              WHEN a.originator_type = 4 THEN (select COALESCE(label, name) from asset where id = a.originator_id)
              WHEN a.originator_type = 5 THEN (select COALESCE(label, name) from device where id = a.originator_id)
              WHEN a.originator_type = 8 THEN (select name from converter where id = a.originator_id)
              WHEN a.originator_type = 9 THEN (select name from integration where id = a.originator_id)
              WHEN a.originator_type = 14 THEN (select name from entity_view where id = a.originator_id)
              WHEN a.originator_type = 20 THEN (select name from device_profile where id = a.originator_id)
              WHEN a.originator_type = 21 THEN (select name from asset_profile where id = a.originator_id)
              WHEN a.originator_type = 25 THEN (select COALESCE(label, name) from edge where id = a.originator_id) END
    , 'Deleted') as originator_label,
u.first_name as assignee_first_name, u.last_name as assignee_last_name, u.email as assignee_email
FROM alarm a
LEFT JOIN tb_user u ON u.id = a.assignee_id;


-- ALARM INFO VIEW END

-- ALARM FUNCTIONS START

CREATE OR REPLACE FUNCTION create_or_update_active_alarm(
                                        t_id uuid, c_id uuid, a_id uuid, a_created_ts bigint,
                                        a_o_id uuid, a_o_type integer, a_type varchar,
                                        a_severity varchar, a_start_ts bigint, a_end_ts bigint,
                                        a_details varchar,
                                        a_propagate boolean, a_propagate_to_owner boolean, a_propagate_to_owner_hierarchy boolean,
                                        a_propagate_to_tenant boolean, a_propagation_types varchar,
                                        a_creation_enabled boolean)
    RETURNS varchar
    LANGUAGE plpgsql
AS
$$
DECLARE
    null_id constant uuid = '13814000-1dd2-11b2-8080-808080808080'::uuid;
    existing  alarm;
    result    alarm_info;
    row_count integer;
BEGIN
    SELECT * INTO existing FROM alarm a WHERE a.originator_id = a_o_id AND a.type = a_type AND a.cleared = false ORDER BY a.start_ts DESC FOR UPDATE;
    IF existing.id IS NULL THEN
        IF a_creation_enabled = FALSE THEN
            RETURN json_build_object('success', false)::text;
        END IF;
        IF c_id = null_id THEN
            c_id = NULL;
        end if;
        INSERT INTO alarm
            (tenant_id, customer_id, id, created_time,
             originator_id, originator_type, type,
             severity, start_ts, end_ts,
             additional_info,
             propagate, propagate_to_owner, propagate_to_owner_hierarchy, propagate_to_tenant, propagate_relation_types,
             acknowledged, ack_ts,
             cleared, clear_ts,
             assignee_id, assign_ts)
             VALUES
            (t_id, c_id, a_id, a_created_ts,
             a_o_id, a_o_type, a_type,
             a_severity, a_start_ts, a_end_ts,
             a_details,
             a_propagate, a_propagate_to_owner, a_propagate_to_owner_hierarchy, a_propagate_to_tenant, a_propagation_types,
             false, 0, false, 0, NULL, 0);
        SELECT * INTO result FROM alarm_info a WHERE a.id = a_id AND a.tenant_id = t_id;
        RETURN json_build_object('success', true, 'created', true, 'modified', true, 'alarm', row_to_json(result))::text;
    ELSE
        UPDATE alarm a
        SET severity                 = a_severity,
            start_ts                 = a_start_ts,
            end_ts                   = a_end_ts,
            additional_info          = a_details,
            propagate                = a_propagate,
            propagate_to_owner       = a_propagate_to_owner,
            propagate_to_owner_hierarchy       = a_propagate_to_owner_hierarchy,
            propagate_to_tenant      = a_propagate_to_tenant,
            propagate_relation_types = a_propagation_types
        WHERE a.id = existing.id
          AND a.tenant_id = t_id
          AND (severity != a_severity OR start_ts != a_start_ts OR end_ts != a_end_ts OR additional_info != a_details
            OR propagate != a_propagate OR propagate_to_owner != a_propagate_to_owner OR propagate_to_owner_hierarchy != a_propagate_to_owner_hierarchy
            OR propagate_to_tenant != a_propagate_to_tenant OR propagate_relation_types != a_propagation_types);
        GET DIAGNOSTICS row_count = ROW_COUNT;
        SELECT * INTO result FROM alarm_info a WHERE a.id = existing.id AND a.tenant_id = t_id;
        IF row_count > 0 THEN
            RETURN json_build_object('success', true, 'modified', true, 'alarm', row_to_json(result), 'old', row_to_json(existing))::text;
        ELSE
            RETURN json_build_object('success', true, 'modified', false, 'alarm', row_to_json(result))::text;
        END IF;
    END IF;
END
$$;

DROP FUNCTION IF EXISTS update_alarm;
CREATE OR REPLACE FUNCTION update_alarm(t_id uuid, a_id uuid, a_severity varchar, a_start_ts bigint, a_end_ts bigint,
                                        a_details varchar,
                                        a_propagate boolean, a_propagate_to_owner boolean, a_propagate_to_owner_hierarchy boolean,
                                        a_propagate_to_tenant boolean, a_propagation_types varchar)
    RETURNS varchar
    LANGUAGE plpgsql
AS
$$
DECLARE
    existing  alarm;
    result    alarm_info;
    row_count integer;
BEGIN
    SELECT * INTO existing FROM alarm a WHERE a.id = a_id AND a.tenant_id = t_id FOR UPDATE;
    IF existing IS NULL THEN
        RETURN json_build_object('success', false)::text;
    END IF;
    UPDATE alarm a
    SET severity                 = a_severity,
        start_ts                 = a_start_ts,
        end_ts                   = a_end_ts,
        additional_info          = a_details,
        propagate                = a_propagate,
        propagate_to_owner       = a_propagate_to_owner,
        propagate_to_owner_hierarchy = a_propagate_to_owner_hierarchy,
        propagate_to_tenant      = a_propagate_to_tenant,
        propagate_relation_types = a_propagation_types
    WHERE a.id = a_id
      AND a.tenant_id = t_id
      AND (severity != a_severity OR start_ts != a_start_ts OR end_ts != a_end_ts OR additional_info != a_details
        OR propagate != a_propagate OR propagate_to_owner != a_propagate_to_owner OR propagate_to_owner_hierarchy != a_propagate_to_owner_hierarchy
        OR propagate_to_tenant != a_propagate_to_tenant OR propagate_relation_types != a_propagation_types);
    GET DIAGNOSTICS row_count = ROW_COUNT;
    SELECT * INTO result FROM alarm_info a WHERE a.id = a_id AND a.tenant_id = t_id;
    IF row_count > 0 THEN
        RETURN json_build_object('success', true, 'modified', row_count > 0, 'alarm', row_to_json(result), 'old', row_to_json(existing))::text;
    ELSE
        RETURN json_build_object('success', true, 'modified', row_count > 0, 'alarm', row_to_json(result))::text;
    END IF;
END
$$;

DROP FUNCTION IF EXISTS acknowledge_alarm;
CREATE OR REPLACE FUNCTION acknowledge_alarm(t_id uuid, a_id uuid, a_ts bigint)
    RETURNS varchar
    LANGUAGE plpgsql
AS
$$
DECLARE
    existing alarm;
    result   alarm_info;
    modified boolean = FALSE;
BEGIN
    SELECT * INTO existing FROM alarm a WHERE a.id = a_id AND a.tenant_id = t_id FOR UPDATE;
    IF existing IS NULL THEN
        RETURN json_build_object('success', false)::text;
    END IF;

    IF NOT (existing.acknowledged) THEN
        modified = TRUE;
        UPDATE alarm a SET acknowledged = true, ack_ts = a_ts WHERE a.id = a_id AND a.tenant_id = t_id;
    END IF;
    SELECT * INTO result FROM alarm_info a WHERE a.id = a_id AND a.tenant_id = t_id;
    RETURN json_build_object('success', true, 'modified', modified, 'alarm', row_to_json(result))::text;
END
$$;

DROP FUNCTION IF EXISTS clear_alarm;
CREATE OR REPLACE FUNCTION clear_alarm(t_id uuid, a_id uuid, a_ts bigint, a_details varchar)
    RETURNS varchar
    LANGUAGE plpgsql
AS
$$
DECLARE
    existing alarm;
    result   alarm_info;
    cleared boolean = FALSE;
BEGIN
    SELECT * INTO existing FROM alarm a WHERE a.id = a_id AND a.tenant_id = t_id FOR UPDATE;
    IF existing IS NULL THEN
        RETURN json_build_object('success', false)::text;
    END IF;
    IF NOT(existing.cleared) THEN
        cleared = TRUE;
        UPDATE alarm a SET cleared = true, clear_ts = a_ts, additional_info = a_details WHERE a.id = a_id AND a.tenant_id = t_id;
    END IF;
    SELECT * INTO result FROM alarm_info a WHERE a.id = a_id AND a.tenant_id = t_id;
    RETURN json_build_object('success', true, 'cleared', cleared, 'alarm', row_to_json(result))::text;
END
$$;

DROP FUNCTION IF EXISTS assign_alarm;
CREATE OR REPLACE FUNCTION assign_alarm(t_id uuid, a_id uuid, u_id uuid, a_ts bigint)
    RETURNS varchar
    LANGUAGE plpgsql
AS
$$
DECLARE
    existing alarm;
    result   alarm_info;
    modified boolean = FALSE;
BEGIN
    SELECT * INTO existing FROM alarm a WHERE a.id = a_id AND a.tenant_id = t_id FOR UPDATE;
    IF existing IS NULL THEN
        RETURN json_build_object('success', false)::text;
    END IF;
    IF existing.assignee_id IS NULL OR existing.assignee_id != u_id THEN
        modified = TRUE;
        UPDATE alarm a SET assignee_id = u_id, assign_ts = a_ts WHERE a.id = a_id AND a.tenant_id = t_id;
    END IF;
    SELECT * INTO result FROM alarm_info a WHERE a.id = a_id AND a.tenant_id = t_id;
    RETURN json_build_object('success', true, 'modified', modified, 'alarm', row_to_json(result))::text;
END
$$;

DROP FUNCTION IF EXISTS unassign_alarm;
CREATE OR REPLACE FUNCTION unassign_alarm(t_id uuid, a_id uuid, a_ts bigint)
    RETURNS varchar
    LANGUAGE plpgsql
AS
$$
DECLARE
    existing alarm;
    result   alarm_info;
    modified boolean = FALSE;
BEGIN
    SELECT * INTO existing FROM alarm a WHERE a.id = a_id AND a.tenant_id = t_id FOR UPDATE;
    IF existing IS NULL THEN
        RETURN json_build_object('success', false)::text;
    END IF;
    IF existing.assignee_id IS NOT NULL THEN
        modified = TRUE;
        UPDATE alarm a SET assignee_id = NULL, assign_ts = a_ts WHERE a.id = a_id AND a.tenant_id = t_id;
    END IF;
    SELECT * INTO result FROM alarm_info a WHERE a.id = a_id AND a.tenant_id = t_id;
    RETURN json_build_object('success', true, 'modified', modified, 'alarm', row_to_json(result))::text;
END
$$;

-- ALARM FUNCTIONS END
