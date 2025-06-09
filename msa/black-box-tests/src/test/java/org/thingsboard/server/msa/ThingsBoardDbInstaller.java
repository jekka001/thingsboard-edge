/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2025 ThingsBoard, Inc. All Rights Reserved.
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

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.utility.Base58;
import org.thingsboard.server.common.data.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class ThingsBoardDbInstaller {

    final static boolean IS_VALKEY_CLUSTER = Boolean.parseBoolean(System.getProperty("blackBoxTests.redisCluster"));
    final static boolean IS_VALKEY_SENTINEL = Boolean.parseBoolean(System.getProperty("blackBoxTests.redisSentinel"));
    final static boolean IS_HYBRID_MODE = Boolean.parseBoolean(System.getProperty("blackBoxTests.hybridMode"));
    private final static String POSTGRES_DATA_VOLUME = "tb-postgres-test-data-volume";

    private final static String CASSANDRA_DATA_VOLUME = "tb-cassandra-test-data-volume";
    private final static String VALKEY_DATA_VOLUME = "tb-valkey-data-volume";
    private final static String VALKEY_CLUSTER_DATA_VOLUME = "tb-valkey-cluster-data-volume";
    private final static String VALKEY_SENTINEL_DATA_VOLUME = "tb-valkey-sentinel-data-volume";
    private final static String TB_LOG_VOLUME = "tb-log-test-volume";
    private final static String TB_COAP_TRANSPORT_LOG_VOLUME = "tb-coap-transport-log-test-volume";
    private final static String TB_LWM2M_TRANSPORT_LOG_VOLUME = "tb-lwm2m-transport-log-test-volume";
    private final static String TB_HTTP_TRANSPORT_LOG_VOLUME = "tb-http-transport-log-test-volume";
    private final static String TB_MQTT_TRANSPORT_LOG_VOLUME = "tb-mqtt-transport-log-test-volume";
    private final static String TB_SNMP_TRANSPORT_LOG_VOLUME = "tb-snmp-transport-log-test-volume";
    private final static String TB_INTEGRATION_EXECUTOR_LOG_VOLUME = "tb-ie-log-test-volume";
    private final static String TB_VC_EXECUTOR_LOG_VOLUME = "tb-vc-executor-log-test-volume";
    private final static String TB_HTTP_INTEGRATION_LOG_VOLUME = "tb-http-integration-log-test-volume";
    private final static String TB_MQTT_INTEGRATION_LOG_VOLUME = "tb-mqtt-integration-log-test-volume";
    private final static String TB_COAP_INTEGRATION_LOG_VOLUME = "tb-coap-integration-log-test-volume";
    private final static String TB_TCP_INTEGRATION_LOG_VOLUME = "tb-tcp-integration-log-test-volume";
    private final static String TB_UDP_INTEGRATION_LOG_VOLUME = "tb-udp-integration-log-test-volume";
    private final static String TB_EDQS_LOG_VOLUME = "tb-edqs-log-test-volume";
    private final static String JAVA_OPTS = "-Xmx512m";

    private final DockerComposeExecutor dockerCompose;

    private final String postgresDataVolume;
    private final String cassandraDataVolume;

    private final String valkeyDataVolume;
    private final String valkeyClusterDataVolume;
    private final String valkeySentinelDataVolume;
    private final String tbLogVolume;
    private final String tbIntegrationExecutorLogVolume;
    private final String tbCoapTransportLogVolume;
    private final String tbLwm2mTransportLogVolume;
    private final String tbHttpTransportLogVolume;
    private final String tbMqttTransportLogVolume;
    private final String tbSnmpTransportLogVolume;
    private final String tbVcExecutorLogVolume;
    private final String tbHttpIntegrationLogVolume;
    private final String tbMqttIntegrationLogVolume;
    private final String tbCoapIntegrationLogVolume;
    private final String tbTcpIntegrationLogVolume;
    private final String tbUdpIntegrationLogVolume;
    private final String tbEdqsLogVolume;

    private final Map<String, String> env;

    public ThingsBoardDbInstaller() {
        log.info("System property of blackBoxTests.redisCluster is {}", IS_VALKEY_CLUSTER);
        log.info("System property of blackBoxTests.redisCluster is {}", IS_VALKEY_SENTINEL);
        log.info("System property of blackBoxTests.hybridMode is {}", IS_HYBRID_MODE);
        List<File> composeFiles = new ArrayList<>(Arrays.asList(
                new File("./../../docker/advanced/docker-compose.yml"),
                new File("./../../docker/advanced/docker-compose.volumes.yml"),
                IS_HYBRID_MODE
                        ? new File("./../../docker/advanced/docker-compose.hybrid.yml")
                        : new File("./../../docker/advanced/docker-compose.postgres.yml"),
                new File("./../../docker/advanced/docker-compose.postgres.volumes.yml"),
                resolveValkeyComposeFile(),
                resolveValkeyComposeVolumesFile()
        ));
        if (IS_HYBRID_MODE) {
            composeFiles.add(new File("./../../docker/advanced/docker-compose.cassandra.volumes.yml"));
            composeFiles.add(new File("src/test/resources/docker-compose.hybrid-test-extras.yml"));
        } else {
            composeFiles.add(new File("src/test/resources/docker-compose.postgres-test-extras.yml"));
        }

        String identifier = Base58.randomString(6).toLowerCase();
        String project = identifier + Base58.randomString(6).toLowerCase();

        postgresDataVolume = project + "_" + POSTGRES_DATA_VOLUME;
        cassandraDataVolume = project + "_" + CASSANDRA_DATA_VOLUME;
        valkeyDataVolume = project + "_" + VALKEY_DATA_VOLUME;
        valkeyClusterDataVolume = project + "_" + VALKEY_CLUSTER_DATA_VOLUME;
        valkeySentinelDataVolume = project + "_" + VALKEY_SENTINEL_DATA_VOLUME;
        tbLogVolume = project + "_" + TB_LOG_VOLUME;
        tbIntegrationExecutorLogVolume = project + "_" + TB_INTEGRATION_EXECUTOR_LOG_VOLUME;
        tbCoapTransportLogVolume = project + "_" + TB_COAP_TRANSPORT_LOG_VOLUME;
        tbLwm2mTransportLogVolume = project + "_" + TB_LWM2M_TRANSPORT_LOG_VOLUME;
        tbHttpTransportLogVolume = project + "_" + TB_HTTP_TRANSPORT_LOG_VOLUME;
        tbMqttTransportLogVolume = project + "_" + TB_MQTT_TRANSPORT_LOG_VOLUME;
        tbSnmpTransportLogVolume = project + "_" + TB_SNMP_TRANSPORT_LOG_VOLUME;
        tbVcExecutorLogVolume = project + "_" + TB_VC_EXECUTOR_LOG_VOLUME;
        tbHttpIntegrationLogVolume = project + "_" + TB_HTTP_INTEGRATION_LOG_VOLUME;
        tbMqttIntegrationLogVolume = project + "_" + TB_MQTT_INTEGRATION_LOG_VOLUME;
        tbCoapIntegrationLogVolume = project + "_" + TB_COAP_INTEGRATION_LOG_VOLUME;
        tbTcpIntegrationLogVolume = project + "_" + TB_TCP_INTEGRATION_LOG_VOLUME;
        tbUdpIntegrationLogVolume = project + "_" + TB_UDP_INTEGRATION_LOG_VOLUME;
        tbEdqsLogVolume = project + "_" + TB_EDQS_LOG_VOLUME;

        dockerCompose = new DockerComposeExecutor(composeFiles, project);

        Dotenv dotenv = Dotenv.configure().directory("./../../docker").filename(".env").load();

        env = new HashMap<>();
        for (DotenvEntry entry : dotenv.entries()) {
            env.put(entry.getKey(), entry.getValue());
        }
        env.put("JAVA_OPTS", JAVA_OPTS);
        env.put("POSTGRES_DATA_VOLUME", postgresDataVolume);
        if (IS_HYBRID_MODE) {
            env.put("CASSANDRA_DATA_VOLUME", cassandraDataVolume);
        }
        env.put("TB_LOG_VOLUME", tbLogVolume);
        env.put("TB_INTEGRATION_EXECUTOR_LOG_VOLUME", tbIntegrationExecutorLogVolume);
        env.put("TB_COAP_TRANSPORT_LOG_VOLUME", tbCoapTransportLogVolume);
        env.put("TB_LWM2M_TRANSPORT_LOG_VOLUME", tbLwm2mTransportLogVolume);
        env.put("TB_HTTP_TRANSPORT_LOG_VOLUME", tbHttpTransportLogVolume);
        env.put("TB_MQTT_TRANSPORT_LOG_VOLUME", tbMqttTransportLogVolume);
        env.put("TB_SNMP_TRANSPORT_LOG_VOLUME", tbSnmpTransportLogVolume);
        env.put("TB_VC_EXECUTOR_LOG_VOLUME", tbVcExecutorLogVolume);
        env.put("TB_HTTP_INTEGRATION_VOLUME", tbHttpIntegrationLogVolume);
        env.put("TB_MQTT_INTEGRATION_VOLUME", tbMqttIntegrationLogVolume);
        env.put("TB_COAP_INTEGRATION_VOLUME", tbCoapIntegrationLogVolume);
        env.put("TB_TCP_INTEGRATION_VOLUME", tbTcpIntegrationLogVolume);
        env.put("TB_UDP_INTEGRATION_VOLUME", tbUdpIntegrationLogVolume);
        env.put("TB_EDQS_LOG_VOLUME", tbEdqsLogVolume);

        if (IS_VALKEY_CLUSTER) {
            for (int i = 0; i < 6; i++) {
                env.put("VALKEY_CLUSTER_DATA_VOLUME_" + i, valkeyClusterDataVolume + '-' + i);
            }
        } else if (IS_VALKEY_SENTINEL) {
            env.put("VALKEY_SENTINEL_DATA_VOLUME_PRIMARY", valkeySentinelDataVolume + "-" + "primary");
            env.put("VALKEY_SENTINEL_DATA_VOLUME_REPLICA", valkeySentinelDataVolume + "-" + "replica");
            env.put("VALKEY_SENTINEL_DATA_VOLUME_SENTINEL", valkeySentinelDataVolume + "-" + "sentinel");
        } else {
            env.put("VALKEY_DATA_VOLUME", valkeyDataVolume);
        }

        env.put("DOCKER_REPO", "thingsboard");
        env.put("TB_VERSION", "latest");

        dockerCompose.withEnv(env);
    }

    private static File resolveValkeyComposeVolumesFile() {
        if (IS_VALKEY_CLUSTER) {
            return new File("./../../docker/advanced/docker-compose.valkey-cluster.volumes.yml");
        }
        if (IS_VALKEY_SENTINEL) {
            return new File("./../../docker/advanced/docker-compose.valkey-sentinel.volumes.yml");
        }
        return new File("./../../docker/advanced/docker-compose.valkey.volumes.yml");
    }

    private static File resolveValkeyComposeFile() {
        if (IS_VALKEY_CLUSTER) {
            return new File("./../../docker/advanced/docker-compose.valkey-cluster.yml");
        }
        if (IS_VALKEY_SENTINEL) {
            return new File("./../../docker/advanced/docker-compose.valkey-sentinel.yml");
        }
        return new File("./../../docker/advanced/docker-compose.valkey.yml");
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void createVolumes() {
        try {

            dockerCompose.withCommand("volume create " + postgresDataVolume);
            dockerCompose.invokeDocker();

            if (IS_HYBRID_MODE) {
                dockerCompose.withCommand("volume create " + cassandraDataVolume);
                dockerCompose.invokeDocker();
            }

            if (IS_HYBRID_MODE) {
                dockerCompose.withCommand("volume create " + cassandraDataVolume);
                dockerCompose.invokeDocker();
            }

            dockerCompose.withCommand("volume create " + tbLogVolume);
            dockerCompose.invokeDocker();

            dockerCompose.withCommand("volume create " + tbIntegrationExecutorLogVolume);
            dockerCompose.invokeDocker();

            dockerCompose.withCommand("volume create " + tbCoapTransportLogVolume);
            dockerCompose.invokeDocker();

            dockerCompose.withCommand("volume create " + tbLwm2mTransportLogVolume);
            dockerCompose.invokeDocker();

            dockerCompose.withCommand("volume create " + tbHttpTransportLogVolume);
            dockerCompose.invokeDocker();

            dockerCompose.withCommand("volume create " + tbMqttTransportLogVolume);
            dockerCompose.invokeDocker();

            dockerCompose.withCommand("volume create " + tbSnmpTransportLogVolume);
            dockerCompose.invokeDocker();

            dockerCompose.withCommand("volume create " + tbVcExecutorLogVolume);
            dockerCompose.invokeDocker();

            dockerCompose.withCommand("volume create " + tbHttpIntegrationLogVolume);
            dockerCompose.invokeDocker();

            dockerCompose.withCommand("volume create " + tbMqttIntegrationLogVolume);
            dockerCompose.invokeDocker();

            dockerCompose.withCommand("volume create " + tbCoapIntegrationLogVolume);
            dockerCompose.invokeDocker();

            dockerCompose.withCommand("volume create " + tbTcpIntegrationLogVolume);
            dockerCompose.invokeDocker();

            dockerCompose.withCommand("volume create " + tbUdpIntegrationLogVolume);
            dockerCompose.invokeDocker();

            dockerCompose.withCommand("volume create " + tbEdqsLogVolume);
            dockerCompose.invokeDocker();

            StringBuilder additionalServices = new StringBuilder();
            if (IS_HYBRID_MODE) {
                additionalServices.append(" cassandra");
            }
            if (IS_VALKEY_CLUSTER) {
                for (int i = 0; i < 6; i++) {
                    additionalServices.append(" valkey-node-").append(i);
                    dockerCompose.withCommand("volume create " + valkeyClusterDataVolume + '-' + i);
                    dockerCompose.invokeDocker();
                }
            } else if (IS_VALKEY_SENTINEL) {
                additionalServices.append(" valkey-primary");
                dockerCompose.withCommand("volume create " + valkeySentinelDataVolume + "-" + "primary");
                dockerCompose.invokeDocker();

                additionalServices.append(" valkey-replica");
                dockerCompose.withCommand("volume create " + valkeySentinelDataVolume + '-' + "replica");
                dockerCompose.invokeDocker();

                additionalServices.append(" valkey-sentinel");
                dockerCompose.withCommand("volume create " + valkeySentinelDataVolume + '-' + "sentinel");
                dockerCompose.invokeDocker();
            } else {
                additionalServices.append(" valkey");
                dockerCompose.withCommand("volume create " + valkeyDataVolume);
                dockerCompose.invokeDocker();
            }

            dockerCompose.withCommand("up -d postgres" + additionalServices);
            dockerCompose.invokeCompose();

            dockerCompose.withCommand("run --no-deps --rm -e INSTALL_TB=true -e LOAD_DEMO=true tb-core1");
            dockerCompose.invokeCompose();

        } finally {
            try {
                dockerCompose.withCommand("down -v");
                dockerCompose.invokeCompose();
            } catch (Exception ignored) {
            }
        }
    }

    public void savaLogsAndRemoveVolumes() {
        copyLogs(tbLogVolume, "./target/tb-logs/");
        copyLogs(tbIntegrationExecutorLogVolume, "./target/tb-integration-executor-logs/");
        copyLogs(tbCoapTransportLogVolume, "./target/tb-coap-transport-logs/");
        copyLogs(tbLwm2mTransportLogVolume, "./target/tb-lwm2m-transport-logs/");
        copyLogs(tbHttpTransportLogVolume, "./target/tb-http-transport-logs/");
        copyLogs(tbMqttTransportLogVolume, "./target/tb-mqtt-transport-logs/");
        copyLogs(tbSnmpTransportLogVolume, "./target/tb-snmp-transport-logs/");
        copyLogs(tbVcExecutorLogVolume, "./target/tb-vc-executor-logs/");
        copyLogs(tbHttpIntegrationLogVolume, "./target/tb-http_integration-logs/");
        copyLogs(tbMqttIntegrationLogVolume, "./target/tb-mqtt_integration-logs/");
        copyLogs(tbCoapIntegrationLogVolume, "./target/tb-coap_integration-logs/");
        copyLogs(tbTcpIntegrationLogVolume, "./target/tb-tcp_integration-logs/");
        copyLogs(tbUdpIntegrationLogVolume, "./target/tb-udp_integration-logs/");
        copyLogs(tbEdqsLogVolume, "./target/tb-edqs-logs/");

        StringJoiner rmVolumesCommand = new StringJoiner(" ")
                .add("volume rm -f")
                .add(postgresDataVolume)
                .add(tbLogVolume)
                .add(tbCoapTransportLogVolume)
                .add(tbLwm2mTransportLogVolume)
                .add(tbHttpTransportLogVolume)
                .add(tbMqttTransportLogVolume)
                .add(tbSnmpTransportLogVolume)
                .add(tbVcExecutorLogVolume)
                .add(resolveValkeyComposeVolumeLog())
                .add(tbIntegrationExecutorLogVolume)
                .add(tbHttpIntegrationLogVolume)
                .add(tbMqttIntegrationLogVolume)
                .add(tbCoapIntegrationLogVolume)
                .add(tbTcpIntegrationLogVolume)
                .add(tbUdpIntegrationLogVolume)
                .add(tbEdqsLogVolume);

        if (IS_HYBRID_MODE) {
            rmVolumesCommand.add(cassandraDataVolume);
        }

        dockerCompose.withCommand(rmVolumesCommand.toString());
        dockerCompose.invokeDocker();
    }

    private String resolveValkeyComposeVolumeLog() {
        if (IS_VALKEY_CLUSTER) {
            return IntStream.range(0, 6).mapToObj(i -> " " + valkeyClusterDataVolume + "-" + i).collect(Collectors.joining());
        }
        if (IS_VALKEY_SENTINEL) {
            return valkeySentinelDataVolume + "-" + "primary " + " " +
                   valkeySentinelDataVolume + "-" + "replica" + " " +
                   valkeySentinelDataVolume + " " + "sentinel";
        }
        return valkeyDataVolume;
    }

    private void copyLogs(String volumeName, String targetDir) {
        File tbLogsDir = new File(targetDir);
        tbLogsDir.mkdirs();

        String logsContainerName = "tb-logs-container-" + StringUtils.randomAlphanumeric(10);

        dockerCompose.withCommand("run -d --rm --name " + logsContainerName + " -v " + volumeName + ":/root alpine tail -f /dev/null");
        dockerCompose.invokeDocker();

        dockerCompose.withCommand("cp " + logsContainerName + ":/root/. " + tbLogsDir.getAbsolutePath());
        dockerCompose.invokeDocker();

        dockerCompose.withCommand("rm -f " + logsContainerName);
        dockerCompose.invokeDocker();
    }

}
