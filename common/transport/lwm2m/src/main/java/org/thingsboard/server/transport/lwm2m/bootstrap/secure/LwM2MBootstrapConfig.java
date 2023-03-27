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
package org.thingsboard.server.transport.lwm2m.bootstrap.secure;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.leshan.core.SecurityMode;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.core.util.Hex;
import org.eclipse.leshan.server.bootstrap.BootstrapConfig;
import org.thingsboard.server.common.data.device.credentials.lwm2m.AbstractLwM2MBootstrapClientCredentialWithKeys;
import org.thingsboard.server.common.data.device.credentials.lwm2m.LwM2MBootstrapClientCredential;
import org.thingsboard.server.common.data.device.credentials.lwm2m.LwM2MSecurityMode;
import org.thingsboard.server.common.data.device.profile.lwm2m.bootstrap.AbstractLwM2MBootstrapServerCredential;
import org.thingsboard.server.common.data.device.profile.lwm2m.bootstrap.LwM2MBootstrapServerCredential;

import java.io.Serializable;
import java.util.List;

@Slf4j
@Data
public class LwM2MBootstrapConfig implements Serializable {

    private static final long serialVersionUID = -4729088085817468640L;

    List<LwM2MBootstrapServerCredential> serverConfiguration;

    /** -bootstrapServer, lwm2mServer
     * interface ServerSecurityConfig
     *   host?: string,
     *   port?: number,
     *   isBootstrapServer?: boolean,
     *   securityMode: string,
     *   clientPublicKeyOrId?: string,
     *   clientSecretKey?: string,
     *   serverPublicKey?: string;
     *   clientHoldOffTime?: number,
     *   serverId?: number,
     *   bootstrapServerAccountTimeout: number
     * */
    @Getter
    @Setter
    private LwM2MBootstrapClientCredential bootstrapServer;

    @Getter
    @Setter
    private LwM2MBootstrapClientCredential lwm2mServer;

    public LwM2MBootstrapConfig(){};

    public LwM2MBootstrapConfig(List<LwM2MBootstrapServerCredential> serverConfiguration, LwM2MBootstrapClientCredential bootstrapClientServer, LwM2MBootstrapClientCredential lwm2mClientServer) {
        this.serverConfiguration = serverConfiguration;
        this.bootstrapServer = bootstrapClientServer;
        this.lwm2mServer = lwm2mClientServer;

    }

    @JsonIgnore
    public BootstrapConfig getLwM2MBootstrapConfig() {
        BootstrapConfig configBs = new BootstrapConfig();
        configBs.autoIdForSecurityObject = true;
        int id = 0;
        for (LwM2MBootstrapServerCredential serverCredential : serverConfiguration) {
            BootstrapConfig.ServerConfig serverConfig = setServerConfig((AbstractLwM2MBootstrapServerCredential) serverCredential);
            configBs.servers.put(id, serverConfig);
            BootstrapConfig.ServerSecurity serverSecurity = setServerSecurity((AbstractLwM2MBootstrapServerCredential) serverCredential, serverCredential.getSecurityMode());
            configBs.security.put(id, serverSecurity);
            id++;
        }
        /** in LwM2mDefaultBootstrapSessionManager -> initTasks
         * Delete all security/config objects if update bootstrap server and lwm2m server
         * if other: del or update only instances */

        return configBs;
    }

    private BootstrapConfig.ServerSecurity setServerSecurity(AbstractLwM2MBootstrapServerCredential serverCredential, LwM2MSecurityMode securityMode) {
        BootstrapConfig.ServerSecurity serverSecurity = new BootstrapConfig.ServerSecurity();
        String serverUri = "coap://";
        byte[] publicKeyOrId = new byte[]{};
        byte[] secretKey = new byte[]{};
        byte[] serverPublicKey = new byte[]{};
        serverSecurity.serverId = serverCredential.getShortServerId();
        serverSecurity.securityMode = SecurityMode.valueOf(securityMode.name());
        serverSecurity.bootstrapServer = serverCredential.isBootstrapServerIs();
        if (!LwM2MSecurityMode.NO_SEC.equals(securityMode)) {
            AbstractLwM2MBootstrapClientCredentialWithKeys server;
            if (serverSecurity.bootstrapServer) {
                server = (AbstractLwM2MBootstrapClientCredentialWithKeys) this.bootstrapServer;

            } else {
                server = (AbstractLwM2MBootstrapClientCredentialWithKeys) this.lwm2mServer;
            }
            serverUri = "coaps://";
            if (LwM2MSecurityMode.PSK.equals(securityMode)) {
                publicKeyOrId = server.getClientPublicKeyOrId().getBytes();
                secretKey = Hex.decodeHex(server.getClientSecretKey().toCharArray());
            } else {
                publicKeyOrId = server.getDecodedClientPublicKeyOrId();
                secretKey = server.getDecodedClientSecretKey();
            }
            serverPublicKey = serverCredential.getDecodedCServerPublicKey();
        }
        serverUri += (((serverCredential.getHost().equals("0.0.0.0") ? "localhost" : serverCredential.getHost()) + ":" + serverCredential.getPort()));
        serverSecurity.uri = serverUri;
        serverSecurity.publicKeyOrId = publicKeyOrId;
        serverSecurity.secretKey = secretKey;
        serverSecurity.serverPublicKey = serverPublicKey;
        return serverSecurity;
    }

    private BootstrapConfig.ServerConfig setServerConfig (AbstractLwM2MBootstrapServerCredential serverCredential) {
        BootstrapConfig.ServerConfig serverConfig = new BootstrapConfig.ServerConfig();
        serverConfig.shortId = serverCredential.getShortServerId();
        serverConfig.lifetime = serverCredential.getLifetime();
        serverConfig.defaultMinPeriod = serverCredential.getDefaultMinPeriod();
        serverConfig.notifIfDisabled = serverCredential.isNotifIfDisabled();
        serverConfig.binding = BindingMode.parse(serverCredential.getBinding());
        return serverConfig;
    }
}
