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
package org.thingsboard.rule.engine.analytics.latest.alarm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.thingsboard.rule.engine.api.NodeConfiguration;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.msg.session.SessionMsgType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TbAlarmsCountNodeV2Configuration implements NodeConfiguration<TbAlarmsCountNodeV2Configuration> {
    private List<AlarmsCountMapping> alarmsCountMappings;
    private boolean countAlarmsForPropagationEntities;
    private List<EntityType> propagationEntityTypes;
    private String queueName;
    private String outMsgType;

    @Override
    public TbAlarmsCountNodeV2Configuration defaultConfiguration() {
        TbAlarmsCountNodeV2Configuration configuration = new TbAlarmsCountNodeV2Configuration();
        List<AlarmsCountMapping> alarmsCountMappings = new ArrayList<>();
        AlarmsCountMapping alarmsCountMapping = new AlarmsCountMapping();
        alarmsCountMapping.setTarget("alarmsCount");
        alarmsCountMappings.add(alarmsCountMapping);

        configuration.setCountAlarmsForPropagationEntities(true);
        configuration.setPropagationEntityTypes(Collections.emptyList());
        configuration.setAlarmsCountMappings(alarmsCountMappings);
        configuration.setOutMsgType(SessionMsgType.POST_TELEMETRY_REQUEST.name());
        return configuration;
    }
}
