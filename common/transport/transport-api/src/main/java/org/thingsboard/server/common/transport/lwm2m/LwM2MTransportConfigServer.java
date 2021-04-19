/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.common.transport.lwm2m;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.leshan.server.model.LwM2mModelProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

@Slf4j
@Component
@ConditionalOnExpression("('${service.type:null}'=='tb-transport' && '${transport.lwm2m.enabled:false}'=='true') || '${service.type:null}'=='monolith' || '${service.type:null}'=='tb-core'")
public class LwM2MTransportConfigServer {

    @Getter
    private String KEY_STORE_DEFAULT_RESOURCE_PATH = "credentials";

    @Getter
    private String KEY_STORE_DEFAULT_FILE = "serverKeyStore.jks";

    @Getter
    private String APP_DIR = "common";

    @Getter
    private String TRANSPORT_DIR = "transport";

    @Getter
    private String LWM2M_DIR = "lwm2m";

    @Getter
    private String SRC_DIR = "src";

    @Getter
    private String MAIN_DIR = "main";

    @Getter
    private String RESOURCES_DIR = "resources";

    @Getter
    private String BASE_DIR_PATH = System.getProperty("user.dir");

    @Getter
    //    private String PATH_DATA_MICROSERVICE = "/usr/share/tb-lwm2m-transport/data$";
    private String PATH_DATA = "data";

    @Getter
    @Setter
    private LwM2mModelProvider modelProvider;

    @Getter
    @Value("${transport.lwm2m.timeout:}")
    private Long timeout;

    @Getter
    @Value("${transport.sessions.report_timeout}")
    private long sessionReportTimeout;

    @Getter
    @Value("${transport.lwm2m.recommended_ciphers:}")
    private boolean recommendedCiphers;

    @Getter
    @Value("${transport.lwm2m.recommended_supported_groups:}")
    private boolean recommendedSupportedGroups;

    @Getter
    @Value("${transport.lwm2m.response_pool_size:}")
    private int responsePoolSize;

    @Getter
    @Value("${transport.lwm2m.registered_pool_size:}")
    private int registeredPoolSize;

    @Getter
    @Value("${transport.lwm2m.update_registered_pool_size:}")
    private int updateRegisteredPoolSize;

    @Getter
    @Value("${transport.lwm2m.un_registered_pool_size:}")
    private int unRegisteredPoolSize;

    @Getter
    @Value("${transport.lwm2m.secure.key_store_type:}")
    private String keyStoreType;

    @Getter
    @Value("${transport.lwm2m.secure.key_store_path_file:}")
    private String keyStorePathFile;

    @Getter
    @Setter
    private KeyStore keyStoreValue;

    @Getter
    @Value("${transport.lwm2m.secure.key_store_password:}")
    private String keyStorePasswordServer;

    @Getter
    @Value("${transport.lwm2m.secure.root_alias:}")
    private String rootAlias;

    @Getter
    @Value("${transport.lwm2m.secure.enable_gen_new_key_psk_rpk:}")
    private Boolean enableGenNewKeyPskRpk;

    @Getter
    @Value("${transport.lwm2m.server.id:}")
    private Integer serverId;

    @Getter
    @Value("${transport.lwm2m.server.bind_address:}")
    private String serverHost;

    @Getter
    @Value("${transport.lwm2m.server.secure.bind_address_security:}")
    private String serverHostSecurity;

    @Getter
    @Value("${transport.lwm2m.server.bind_port_no_sec:}")
    private Integer serverPortNoSec;

    @Getter
    @Value("${transport.lwm2m.server.secure.bind_port_security:}")
    private Integer serverPortSecurity;

    @Getter
    @Value("${transport.lwm2m.server.secure.public_x:}")
    private String serverPublicX;

    @Getter
    @Value("${transport.lwm2m.server.secure.public_y:}")
    private String serverPublicY;

    @Getter
    @Value("${transport.lwm2m.server.secure.private_encoded:}")
    private String serverPrivateEncoded;

    @Getter
    @Value("${transport.lwm2m.server.secure.alias:}")
    private String serverAlias;

    @PostConstruct
    public void init() {
        this.getInKeyStore();
    }

    private KeyStore getInKeyStore() {
        try {
            if (keyStoreValue != null && keyStoreValue.size() > 0)
                return keyStoreValue;
        } catch (KeyStoreException e) {
            log.error("Uninitialized keystore [{}]", keyStoreValue.toString());
        }
        Path keyStorePath = (keyStorePathFile != null && !keyStorePathFile.isEmpty()) ? Paths.get(keyStorePathFile) :
                (new File(Paths.get(getBaseDirPath(), PATH_DATA, KEY_STORE_DEFAULT_RESOURCE_PATH, KEY_STORE_DEFAULT_FILE).toUri()).isFile()) ?
                        Paths.get(getBaseDirPath(), PATH_DATA, KEY_STORE_DEFAULT_RESOURCE_PATH, KEY_STORE_DEFAULT_FILE) :
                        Paths.get(getBaseDirPath(), APP_DIR, TRANSPORT_DIR, LWM2M_DIR, SRC_DIR, MAIN_DIR, RESOURCES_DIR, KEY_STORE_DEFAULT_RESOURCE_PATH, KEY_STORE_DEFAULT_FILE);
        File keyStoreFile = new File(keyStorePath.toUri());
        if (keyStoreFile.isFile()) {
            try {
                InputStream inKeyStore = new FileInputStream(keyStoreFile);
                keyStoreValue = KeyStore.getInstance(keyStoreType);
                keyStoreValue.load(inKeyStore, keyStorePasswordServer == null ? null : keyStorePasswordServer.toCharArray());
            } catch (CertificateException | NoSuchAlgorithmException | IOException | KeyStoreException e) {
                log.error("[{}] Unable to load KeyStore  files server, folder is not a directory", e.getMessage());
                keyStoreValue = null;
            }
            log.info("[{}] Load KeyStore  files server, folder is a directory", keyStoreFile.getAbsoluteFile());
        } else {
            log.error("[{}] Unable to load KeyStore  files server, is not a file", keyStoreFile.getAbsoluteFile());
            keyStoreValue = null;
        }
        return keyStoreValue;
    }

    private String getBaseDirPath() {
        Path FULL_FILE_PATH;
        if (BASE_DIR_PATH.endsWith("bin")) {
            FULL_FILE_PATH = Paths.get(BASE_DIR_PATH.replaceAll("bin$", ""));
        } else if (BASE_DIR_PATH.endsWith("conf")) {
            FULL_FILE_PATH = Paths.get(BASE_DIR_PATH.replaceAll("conf$", ""));
        } else if (BASE_DIR_PATH.endsWith("application")) {
            FULL_FILE_PATH = Paths.get(BASE_DIR_PATH.substring(0, BASE_DIR_PATH.length() - "application".length()));
        } else {
            FULL_FILE_PATH = Paths.get(BASE_DIR_PATH);
        }
        return FULL_FILE_PATH.toUri().getPath();
    }
}
