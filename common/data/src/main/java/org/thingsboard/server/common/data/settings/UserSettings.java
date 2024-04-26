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
package org.thingsboard.server.common.data.settings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.validation.Length;
import org.thingsboard.server.common.data.validation.NoXss;

import java.io.Serializable;

import static org.thingsboard.server.common.data.BaseDataWithAdditionalInfo.getJson;
import static org.thingsboard.server.common.data.BaseDataWithAdditionalInfo.setJson;

@Schema
@Data
public class UserSettings implements Serializable {

    private static final long serialVersionUID = 2628320657987010348L;

    @Schema(description = "JSON object with User id.", accessMode = Schema.AccessMode.READ_ONLY)
    private UserId userId;

    @Schema(description = "Type of the settings.")
    @NoXss
    @Length(fieldName = "type", max = 50)
    private UserSettingsType type;

    @Schema(description = "JSON object with user settings.",implementation = com.fasterxml.jackson.databind.JsonNode.class)
    @NoXss
    @Length(fieldName = "settings", max = 100000)
    private transient JsonNode settings;

    @JsonIgnore
    @ToString.Exclude
    private byte[] settingsBytes;

    public JsonNode getSettings() {
        return getJson(() -> settings, () -> settingsBytes);
    }

    public void setSettings(JsonNode settings) {
        setJson(settings, json -> this.settings = json, bytes -> this.settingsBytes = bytes);
    }
}
