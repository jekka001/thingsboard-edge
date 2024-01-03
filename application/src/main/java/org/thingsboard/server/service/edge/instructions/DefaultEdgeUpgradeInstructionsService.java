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
package org.thingsboard.server.service.edge.instructions;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.EdgeUpgradeInfo;
import org.thingsboard.server.common.data.edge.EdgeInstructions;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.install.InstallScripts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "edges", value = "enabled", havingValue = "true")
@TbCoreComponent
public class DefaultEdgeUpgradeInstructionsService implements EdgeUpgradeInstructionsService {

    private static final Map<String, EdgeUpgradeInfo> upgradeVersionHashMap = new HashMap<>();

    private static final String EDGE_DIR = "edge";
    private static final String INSTRUCTIONS_DIR = "instructions";
    private static final String UPGRADE_DIR = "upgrade";

    private final InstallScripts installScripts;
    private final AttributesService attributesService;

    @Value("${app.version:unknown}")
    @Setter
    private String appVersion;

    @Override
    public EdgeInstructions getUpgradeInstructions(String edgeVersion, String upgradeMethod) {
        String tbVersion = appVersion.replace("PE", "").replace("-SNAPSHOT", "");
        String currentEdgeVersion = convertEdgeVersionToDocsFormat(edgeVersion);
        switch (upgradeMethod.toLowerCase()) {
            case "docker":
                return getDockerUpgradeInstructions(tbVersion, currentEdgeVersion);
            case "ubuntu":
            case "centos":
                return getLinuxUpgradeInstructions(tbVersion, currentEdgeVersion, upgradeMethod.toLowerCase());
            default:
                throw new IllegalArgumentException("Unsupported upgrade method for Edge: " + upgradeMethod);
        }
    }

    @Override
    public void updateInstructionMap(Map<String, EdgeUpgradeInfo> map) {
        for (String key : map.keySet()) {
            upgradeVersionHashMap.put(key, map.get(key));
        }
    }

    @Override
    public boolean isUpgradeAvailable(TenantId tenantId, EdgeId edgeId) throws Exception {
        Optional<AttributeKvEntry> attributeKvEntryOpt = attributesService.find(tenantId, edgeId, DataConstants.SERVER_SCOPE, DataConstants.EDGE_VERSION_ATTR_KEY).get();
        if (attributeKvEntryOpt.isPresent()) {
            String edgeVersionFormatted = convertEdgeVersionToDocsFormat(attributeKvEntryOpt.get().getValueAsString());
            String versionTag = appVersion.replace("PE", "");
            return isVersionGreaterOrEqualsThan(edgeVersionFormatted, "3.6.0") && !isVersionGreaterOrEqualsThan(edgeVersionFormatted, versionTag);
        }
        return false;
    }

    private boolean isVersionGreaterOrEqualsThan(String version1, String version2) {
        String[] v1 = version1.split("\\.");
        String[] v2 = version2.split("\\.");

        int length = Math.max(v1.length, v2.length);
        for (int i = 0; i < length; i++) {
            int num1 = i < v1.length ? Integer.parseInt(v1[i]) : 0;
            int num2 = i < v2.length ? Integer.parseInt(v2[i]) : 0;

            if (num1 < num2) {
                return false;
            } else if (num1 > num2) {
                return true;
            }
        }
        return true;
    }

    private EdgeInstructions getDockerUpgradeInstructions(String tbVersion, String currentEdgeVersion) {
        EdgeUpgradeInfo edgeUpgradeInfo = upgradeVersionHashMap.get(currentEdgeVersion);
        if (edgeUpgradeInfo == null || edgeUpgradeInfo.getNextEdgeVersion() == null || tbVersion.equals(currentEdgeVersion)) {
            return new EdgeInstructions("Edge upgrade instruction for " + currentEdgeVersion + "EDGE is not available.");
        }
        StringBuilder result = new StringBuilder(readFile(resolveFile("docker", "upgrade_preparing.md")));
        while (edgeUpgradeInfo.getNextEdgeVersion() != null && !tbVersion.equals(currentEdgeVersion)) {
            String edgeVersion = edgeUpgradeInfo.getNextEdgeVersion();
            String dockerUpgradeInstructions = readFile(resolveFile("docker", "instructions.md"));
            if (edgeUpgradeInfo.isRequiresUpdateDb()) {
                String upgradeDb = readFile(resolveFile("docker", "upgrade_db.md"));
                dockerUpgradeInstructions = dockerUpgradeInstructions.replace("${UPGRADE_DB}", upgradeDb);
            } else {
                dockerUpgradeInstructions = dockerUpgradeInstructions.replace("${UPGRADE_DB}", "");
            }
            dockerUpgradeInstructions = dockerUpgradeInstructions.replace("${TB_EDGE_VERSION}", edgeVersion + "EDGEPE");
            dockerUpgradeInstructions = dockerUpgradeInstructions.replace("${FROM_TB_EDGE_VERSION}", currentEdgeVersion + "EDGEPE");
            currentEdgeVersion = edgeVersion;
            edgeUpgradeInfo = upgradeVersionHashMap.get(edgeUpgradeInfo.getNextEdgeVersion());
            result.append(dockerUpgradeInstructions);
        }
        String startService = readFile(resolveFile("docker", "start_service.md"));
        startService = startService.replace("${TB_EDGE_VERSION}", currentEdgeVersion + "EDGEPE");
        result.append(startService);
        return new EdgeInstructions(result.toString());
    }

    private EdgeInstructions getLinuxUpgradeInstructions(String tbVersion, String currentEdgeVersion, String os) {
        EdgeUpgradeInfo edgeUpgradeInfo = upgradeVersionHashMap.get(currentEdgeVersion);
        if (edgeUpgradeInfo == null || edgeUpgradeInfo.getNextEdgeVersion() == null || tbVersion.equals(currentEdgeVersion)) {
            return new EdgeInstructions("Edge upgrade instruction for " + currentEdgeVersion + "EDGE is not available.");
        }
        String upgrade_preparing = readFile(resolveFile("upgrade_preparing.md"));
        upgrade_preparing = upgrade_preparing.replace("${OS}", os.equals("centos") ? "RHEL/CentOS 7/8" : "Ubuntu");
        StringBuilder result = new StringBuilder(upgrade_preparing);
        while (edgeUpgradeInfo.getNextEdgeVersion() != null && !tbVersion.equals(currentEdgeVersion)) {
            String edgeVersion = edgeUpgradeInfo.getNextEdgeVersion();
            String linuxUpgradeInstructions = readFile(resolveFile(os, "instructions.md"));
            if (edgeUpgradeInfo.isRequiresUpdateDb()) {
                String upgradeDb = readFile(resolveFile("upgrade_db.md"));
                linuxUpgradeInstructions = linuxUpgradeInstructions.replace("${UPGRADE_DB}", upgradeDb);
            } else {
                linuxUpgradeInstructions = linuxUpgradeInstructions.replace("${UPGRADE_DB}", "");
            }
            linuxUpgradeInstructions = linuxUpgradeInstructions.replace("${TB_EDGE_TAG}", getTagVersion(edgeVersion));
            linuxUpgradeInstructions = linuxUpgradeInstructions.replace("${FROM_TB_EDGE_TAG}", getTagVersion(currentEdgeVersion));
            linuxUpgradeInstructions = linuxUpgradeInstructions.replace("${TB_EDGE_VERSION}", edgeVersion);
            linuxUpgradeInstructions = linuxUpgradeInstructions.replace("${FROM_TB_EDGE_VERSION}", currentEdgeVersion);
            currentEdgeVersion = edgeVersion;
            edgeUpgradeInfo = upgradeVersionHashMap.get(edgeUpgradeInfo.getNextEdgeVersion());
            result.append(linuxUpgradeInstructions);
        }
        String startService = readFile(resolveFile("start_service.md"));
        result.append(startService);
        return new EdgeInstructions(result.toString());
    }

    private String getTagVersion(String version) {
        return version.endsWith(".0") ? version.substring(0, version.length() - 2) : version;
    }

    private String convertEdgeVersionToDocsFormat(String edgeVersion) {
        return edgeVersion.replace("_", ".").substring(2);
    }

    private String readFile(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            log.warn("Failed to read file: {}", file, e);
            throw new RuntimeException(e);
        }
    }

    private Path resolveFile(String subDir, String... subDirs) {
        return getEdgeInstallInstructionsDir().resolve(Paths.get(subDir, subDirs));
    }

    private Path getEdgeInstallInstructionsDir() {
        return Paths.get(installScripts.getDataDir(), InstallScripts.JSON_DIR, EDGE_DIR, INSTRUCTIONS_DIR, UPGRADE_DIR);
    }
}
