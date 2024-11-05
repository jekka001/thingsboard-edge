/**
 * Copyright © 2016-2024 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.dao.cloud;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.AttributeScope;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.edge.EdgeSettings;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.BaseAttributeKvEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.dao.attributes.AttributesService;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultEdgeSettingsService implements EdgeSettingsService {
    private static final String FETCHING_EDGE_SETTINGS_ERROR_MESSAGE = "Fetching edge settings failed";
    private static final String FOUND_CURRENT_EDGE_SETTINGS_MESSAGE = "Found current edge settings ";
    private static final String NOT_FOUND_EDGE_SETTINGS_MESSAGE = "Edge settings not found";
    public final AttributesService attributesService;

    @Override
    public EdgeSettings findEdgeSettings(TenantId tenantId) {
        try {
            Optional<AttributeKvEntry> attr =
                    attributesService.find(tenantId, tenantId, AttributeScope.SERVER_SCOPE, DataConstants.EDGE_SETTINGS_ATTR_KEY).get();

            if (attr.isPresent()) {
                log.trace(FOUND_CURRENT_EDGE_SETTINGS_MESSAGE + "{}", attr.get().getValueAsString());
                return JacksonUtil.fromString(attr.get().getValueAsString(), EdgeSettings.class);
            } else {
                log.trace(NOT_FOUND_EDGE_SETTINGS_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            log.error(FETCHING_EDGE_SETTINGS_ERROR_MESSAGE, e);
            throw new RuntimeException(FETCHING_EDGE_SETTINGS_ERROR_MESSAGE, e);
        }
    }

    @Override
    public ListenableFuture<List<Long>> saveEdgeSettings(TenantId tenantId, EdgeSettings edgeSettings) {
        StringDataEntry dataEntry = new StringDataEntry(DataConstants.EDGE_SETTINGS_ATTR_KEY, JacksonUtil.toString(edgeSettings));
        BaseAttributeKvEntry edgeSettingAttr = new BaseAttributeKvEntry(dataEntry, System.currentTimeMillis());
        List<AttributeKvEntry> attributes = Collections.singletonList(edgeSettingAttr);

        return attributesService.save(tenantId, tenantId, AttributeScope.SERVER_SCOPE, attributes);
    }

}
