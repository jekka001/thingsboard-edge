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
package org.thingsboard.server.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.thingsboard.server.common.data.integration.IntegrationType;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.service.converter.ConverterLibraryService;
import org.thingsboard.server.service.converter.Model;
import org.thingsboard.server.service.converter.Vendor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DaoSqlTest
@TestPropertySource(properties = {
        "integrations.converters.library.enabled=true"
})
public class ConverterLibraryControllerTest extends AbstractControllerTest {

    @Autowired
    ConverterLibraryService converterLibraryService;

    @Before
    public void before() throws Exception {
        loginTenantAdmin();
        await("repo initialization").atMost(TIMEOUT, TimeUnit.SECONDS)
                .until(() -> !converterLibraryService.getConvertersInfo().isEmpty());
    }

    @Test
    public void testLibrary() throws Exception {
        Map<IntegrationType, List<Vendor>> vendorsMap = new HashMap<>();
        for (IntegrationType integrationType : IntegrationType.values()) {
            List<Vendor> vendors = doGetTyped("/api/converter/library/" + integrationType + "/vendors", new TypeReference<>() {});
            if (!vendors.isEmpty()) {
                vendorsMap.put(integrationType, vendors);
            }
        }

        for (Map.Entry<IntegrationType, List<Vendor>> entry : vendorsMap.entrySet()) {
            IntegrationType integrationType = entry.getKey();
            List<Vendor> vendors = entry.getValue();

            for (Vendor vendor : vendors) {
                assertThat(vendor.name()).as(vendor.name() + " vendor name").isNotBlank();
                assertThat(vendor.logo()).as(vendor.name() + " vendor logo").isNotBlank();

                List<Model> models = doGetTyped("/api/converter/library/" + integrationType + "/" + vendor.name() + "/models?converterType=uplink", new TypeReference<>() {});
                for (Model model : models) {
                    String modelUrl = integrationType + "/" + vendor.name() + "/" + model.name();

                    assertThat(model.name()).as("name for " + modelUrl).isNotBlank();
                    assertThat(model.photo()).as("photo for " + modelUrl).isNotBlank();
                    assertThat(model.info().toString()).as("info for " + modelUrl).isNotBlank().isNotEqualTo("{}");

                    ObjectNode uplinkConverter = doGet("/api/converter/library/" + modelUrl + "/uplink", ObjectNode.class);
                    if (uplinkConverter.isEmpty()) {
                        continue;
                    }
                    assertThat(uplinkConverter.get("type").asText()).as(modelUrl + " uplink converter type").isEqualTo("UPLINK");

                    ObjectNode uplinkConverterMetadata = doGet("/api/converter/library/" + modelUrl + "/uplink/metadata", ObjectNode.class);
                    assertThat(uplinkConverterMetadata).as("uplink converter metadata for " + modelUrl).isNotEmpty();
                    assertThat(uplinkConverterMetadata.has("integrationName")).as("downlink converter metadata integrationName for " + modelUrl).isTrue();

                    String uplinkPayload = doGet("/api/converter/library/" + modelUrl + "/uplink/payload", String.class);
                    assertThat(uplinkPayload).as("uplink payload for " + modelUrl).isNotBlank().isNotEqualTo("{}");


                    ObjectNode downlinkConverter = doGet("/api/converter/library/" + modelUrl + "/downlink", ObjectNode.class);
                    if (downlinkConverter.isEmpty()) {
                        continue;
                    }
                    assertThat(downlinkConverter.get("type").asText()).as(modelUrl + " downlink converter type").isEqualTo("DOWNLINK");

                    ObjectNode downlinkConverterMetadata = doGet("/api/converter/library/" + modelUrl + "/downlink/metadata", ObjectNode.class);
                    assertThat(downlinkConverterMetadata).as("downlink converter metadata for " + modelUrl).isNotEmpty();
                    assertThat(downlinkConverterMetadata.has("integrationName")).as("downlink converter metadata integrationName for " + modelUrl).isTrue();

                    String downlinkPayload = doGet("/api/converter/library/" + modelUrl + "/downlink/payload", String.class);
                    assertThat(downlinkPayload).as("downlink payload for " + modelUrl).isNotBlank().isNotEqualTo("{}");
                }
            }
        }

    }

}
