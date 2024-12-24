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
package org.thingsboard.server.msa;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.extensions.cpsuite.ClasspathSuite;
import org.junit.runner.RunWith;
import org.testcontainers.containers.DockerComposeContainer;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

import static org.thingsboard.server.msa.AbstractContainerTest.TB_EDGE_SERVICE_NAME;
import static org.thingsboard.server.msa.AbstractContainerTest.TB_MONOLITH_SERVICE_NAME;
import static org.thingsboard.server.msa.AbstractContainerTest.edgeConfigurations;

@RunWith(ClasspathSuite.class)
@ClasspathSuite.ClassnameFilters({"org.thingsboard.server.msa.edge.*Test"})
@Slf4j
public class ContainerTestSuite {

    private static final String SOURCE_DIR = "./../../docker-edge/";

    public static DockerComposeContainer<?> testContainer;

    @ClassRule
    public static ThingsBoardDbInstaller installTb = new ThingsBoardDbInstaller();

    @ClassRule
    public static DockerComposeContainer getTestContainer() {
        HashMap<String, String> env = new HashMap<>();
        env.put("CLOUD_RPC_HOST", TB_MONOLITH_SERVICE_NAME);
        for (TestEdgeConfiguration config : edgeConfigurations) {
            env.put("CLOUD_ROUTING_KEY_" + config.getIdx(), config.getRoutingKey());
            env.put("CLOUD_ROUTING_SECRET_" + config.getIdx(), config.getSecret());
        }

        if (testContainer == null) {
            try {
                final String targetDir = FileUtils.getTempDirectoryPath() + "/" + "ContainerTestSuite-" + UUID.randomUUID() + "/";
                log.info("targetDir {}", targetDir);
                FileUtils.copyDirectory(new File(SOURCE_DIR), new File(targetDir));

                final String httpIntegrationDir = "src/test/resources";
                FileUtils.copyDirectory(new File(httpIntegrationDir), new File(targetDir));

                class DockerComposeContainerImpl<SELF extends DockerComposeContainer<SELF>> extends DockerComposeContainer<SELF> {
                    public DockerComposeContainerImpl(File... composeFiles) {
                        super(composeFiles);
                    }

                    @Override
                    public void stop() {
                        super.stop();
                        tryDeleteDir(targetDir);
                    }
                }

                testContainer = new DockerComposeContainerImpl<>(
                        new File("./../../docker-edge/docker-compose.yml"),
                        new File("./../../docker-edge/docker-compose.postgres.yml"),
                        new File("./../../docker-edge/docker-compose.volumes.yml"))
                        .withPull(false)
                        .withLocalCompose(true)
                        .withOptions("--compatibility")
                        .withTailChildContainers(true)
                        .withEnv(installTb.getEnv())
                        .withEnv(env)
                        .withExposedService(TB_MONOLITH_SERVICE_NAME, 8080)
                        .withExposedService("kafka", 9092);
                for (TestEdgeConfiguration edgeConfiguration : edgeConfigurations) {
                    testContainer.withExposedService(TB_EDGE_SERVICE_NAME + "-" + edgeConfiguration.getIdx(), edgeConfiguration.getPort());
                    if (edgeConfiguration.getName().contains("kafka")) {
                        testContainer.withExposedService("kafka-edge-" + edgeConfiguration.getIdx(), 9092);
                    }
                }

            } catch (Exception e) {
                log.error("Failed to create test container", e);
                Assert.fail("Failed to create test container");
            }
        }
        return testContainer;
    }

    private static void tryDeleteDir(String targetDir) {
        try {
            log.info("Trying to delete temp dir {}", targetDir);
            FileUtils.deleteDirectory(new File(targetDir));
        } catch (IOException e) {
            log.error("Can't delete temp directory " + targetDir, e);
        }
    }
}
