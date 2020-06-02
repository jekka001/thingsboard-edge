/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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

import org.junit.rules.ExternalResource;
import org.testcontainers.utility.Base58;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ThingsBoardDbInstaller extends ExternalResource {

    private final static String POSTGRES_DATA_VOLUME = "tb-postgres-test-data-volume";
    private final static String TB_LOG_VOLUME = "tb-log-test-volume";
    private final static String TB_COAP_TRANSPORT_LOG_VOLUME = "tb-coap-transport-log-test-volume";
    private final static String TB_HTTP_TRANSPORT_LOG_VOLUME = "tb-http-transport-log-test-volume";
    private final static String TB_MQTT_TRANSPORT_LOG_VOLUME = "tb-mqtt-transport-log-test-volume";

    private final DockerComposeExecutor dockerCompose;

    private final String postgresDataVolume;
    private final String tbLogVolume;
    private final String tbCoapTransportLogVolume;
    private final String tbHttpTransportLogVolume;
    private final String tbMqttTransportLogVolume;
    private final Map<String, String> env;

    public ThingsBoardDbInstaller() {
        List<File> composeFiles = Arrays.asList(new File("./../../docker/docker-compose.yml"),
                new File("./../../docker/docker-compose.postgres.yml"),
                new File("./../../docker/docker-compose.postgres.volumes.yml"));

        String identifier = Base58.randomString(6).toLowerCase();
        String project = identifier + Base58.randomString(6).toLowerCase();

        postgresDataVolume = project + "_" + POSTGRES_DATA_VOLUME;
        tbLogVolume = project + "_" + TB_LOG_VOLUME;
        tbCoapTransportLogVolume = project + "_" + TB_COAP_TRANSPORT_LOG_VOLUME;
        tbHttpTransportLogVolume = project + "_" + TB_HTTP_TRANSPORT_LOG_VOLUME;
        tbMqttTransportLogVolume = project + "_" + TB_MQTT_TRANSPORT_LOG_VOLUME;

        dockerCompose = new DockerComposeExecutor(composeFiles, project);

        env = new HashMap<>();
        env.put("POSTGRES_DATA_VOLUME", postgresDataVolume);
        env.put("TB_LOG_VOLUME", tbLogVolume);
        env.put("TB_COAP_TRANSPORT_LOG_VOLUME", tbCoapTransportLogVolume);
        env.put("TB_HTTP_TRANSPORT_LOG_VOLUME", tbHttpTransportLogVolume);
        env.put("TB_MQTT_TRANSPORT_LOG_VOLUME", tbMqttTransportLogVolume);
        env.put("DOCKER_REPO", "thingsboard");
        env.put("TB_VERSION", "latest");
        dockerCompose.withEnv(env);
    }

    public Map<String, String> getEnv() {
        return env;
    }

    @Override
    protected void before() throws Throwable {
        try {

            dockerCompose.withCommand("volume create " + postgresDataVolume);
            dockerCompose.invokeDocker();

            dockerCompose.withCommand("volume create " + tbLogVolume);
            dockerCompose.invokeDocker();

            dockerCompose.withCommand("volume create " + tbCoapTransportLogVolume);
            dockerCompose.invokeDocker();

            dockerCompose.withCommand("volume create " + tbHttpTransportLogVolume);
            dockerCompose.invokeDocker();

            dockerCompose.withCommand("volume create " + tbMqttTransportLogVolume);
            dockerCompose.invokeDocker();

            dockerCompose.withCommand("up -d redis postgres");
            dockerCompose.invokeCompose();

            dockerCompose.withCommand("run --no-deps --rm -e INSTALL_TB=true -e LOAD_DEMO=true tb-core1");
            dockerCompose.invokeCompose();

        } finally {
            try {
                dockerCompose.withCommand("down -v");
                dockerCompose.invokeCompose();
            } catch (Exception e) {}
        }
    }

    @Override
    protected void after() {
        copyLogs(tbLogVolume, "./target/tb-logs/");
        copyLogs(tbCoapTransportLogVolume, "./target/tb-coap-transport-logs/");
        copyLogs(tbHttpTransportLogVolume, "./target/tb-http-transport-logs/");
        copyLogs(tbMqttTransportLogVolume, "./target/tb-mqtt-transport-logs/");

        dockerCompose.withCommand("volume rm -f " + postgresDataVolume + " " + tbLogVolume +
                " " + tbCoapTransportLogVolume + " " + tbHttpTransportLogVolume + " " + tbMqttTransportLogVolume);
        dockerCompose.invokeDocker();
    }

    private void copyLogs(String volumeName, String targetDir) {
        File tbLogsDir = new File(targetDir);
        tbLogsDir.mkdirs();

        dockerCompose.withCommand("run -d --rm --name tb-logs-container -v " + volumeName + ":/root alpine tail -f /dev/null");
        dockerCompose.invokeDocker();

        dockerCompose.withCommand("cp tb-logs-container:/root/. "+tbLogsDir.getAbsolutePath());
        dockerCompose.invokeDocker();

        dockerCompose.withCommand("rm -f tb-logs-container");
        dockerCompose.invokeDocker();
    }

}
