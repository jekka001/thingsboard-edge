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
package org.thingsboard.server.service.lwm2m;


import lombok.extern.slf4j.Slf4j;
import org.eclipse.leshan.core.util.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.lwm2m.ServerSecurityConfig;
import org.thingsboard.server.common.transport.lwm2m.LwM2MTransportConfigBootstrap;
import org.thingsboard.server.common.transport.lwm2m.LwM2MTransportConfigServer;
import org.thingsboard.server.transport.lwm2m.secure.LwM2MSecurityMode;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStoreException;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.KeySpec;

@Slf4j
@Service
@ConditionalOnExpression("('${service.type:null}'=='tb-transport' && '${transport.lwm2m.enabled:false}'=='true') || '${service.type:null}'=='monolith' || '${service.type:null}'=='tb-core'")
public class LwM2MModelsRepository {

    private static final String INCORRECT_TENANT_ID = "Incorrect tenantId ";

    @Autowired
    LwM2MTransportConfigServer contextServer;


    @Autowired
    LwM2MTransportConfigBootstrap contextBootStrap;

    /**
     * @param securityMode
     * @param bootstrapServerIs
     * @return ServerSecurityConfig more value is default: Important - port, host, publicKey
     */
    public ServerSecurityConfig getBootstrapSecurityInfo(String securityMode, boolean bootstrapServerIs) {
        LwM2MSecurityMode lwM2MSecurityMode = LwM2MSecurityMode.fromSecurityMode(securityMode.toLowerCase());
        return getBootstrapServer(bootstrapServerIs, lwM2MSecurityMode);
    }

    /**
     * @param bootstrapServerIs
     * @param mode
     * @return ServerSecurityConfig more value is default: Important - port, host, publicKey
     */
    private ServerSecurityConfig getBootstrapServer(boolean bootstrapServerIs, LwM2MSecurityMode mode) {
        ServerSecurityConfig bsServ = new ServerSecurityConfig();
        bsServ.setBootstrapServerIs(bootstrapServerIs);
        if (bootstrapServerIs) {
            bsServ.setServerId(contextBootStrap.getBootstrapServerId());
            switch (mode) {
                case NO_SEC:
                    bsServ.setHost(contextBootStrap.getBootstrapHost());
                    bsServ.setPort(contextBootStrap.getBootstrapPortNoSec());
                    bsServ.setServerPublicKey("");
                    break;
                case PSK:
                    bsServ.setHost(contextBootStrap.getBootstrapHostSecurity());
                    bsServ.setPort(contextBootStrap.getBootstrapPortSecurity());
                    bsServ.setServerPublicKey("");
                    break;
                case RPK:
                case X509:
                    bsServ.setHost(contextBootStrap.getBootstrapHostSecurity());
                    bsServ.setPort(contextBootStrap.getBootstrapPortSecurity());
                    bsServ.setServerPublicKey(getPublicKey (contextBootStrap.getBootstrapAlias(), this.contextBootStrap.getBootstrapPublicX(), this.contextBootStrap.getBootstrapPublicY()));
                    break;
                default:
                    break;
            }
        } else {
            bsServ.setServerId(contextServer.getServerId());
            switch (mode) {
                case NO_SEC:
                    bsServ.setHost(contextServer.getServerHost());
                    bsServ.setPort(contextServer.getServerPortNoSec());
                    bsServ.setServerPublicKey("");
                    break;
                case PSK:
                    bsServ.setHost(contextServer.getServerHostSecurity());
                    bsServ.setPort(contextServer.getServerPortSecurity());
                    bsServ.setServerPublicKey("");
                    break;
                case RPK:
                case X509:
                    bsServ.setHost(contextServer.getServerHostSecurity());
                    bsServ.setPort(contextServer.getServerPortSecurity());
                    bsServ.setServerPublicKey(getPublicKey (contextServer.getServerAlias(), this.contextServer.getServerPublicX(), this.contextServer.getServerPublicY()));
                    break;
                default:
                    break;
            }
        }
        return bsServ;
    }

    private String getPublicKey (String alias, String publicServerX, String publicServerY) {
        String publicKey = getServerPublicKeyX509(alias);
        return publicKey != null ? publicKey : getRPKPublicKey(publicServerX, publicServerY);
    }

    /**
     * @param alias
     * @return PublicKey format HexString or null
     */
    private String getServerPublicKeyX509(String alias) {
        try {
            X509Certificate serverCertificate = (X509Certificate) contextServer.getKeyStoreValue().getCertificate(alias);
            return Hex.encodeHexString(serverCertificate.getEncoded());
        } catch (CertificateEncodingException | KeyStoreException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * @param publicServerX
     * @param publicServerY
     * @return PublicKey format HexString or null
     */
    private String getRPKPublicKey(String publicServerX, String publicServerY) {
        try {
            /** Get Elliptic Curve Parameter spec for secp256r1 */
            AlgorithmParameters algoParameters = AlgorithmParameters.getInstance("EC");
            algoParameters.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec parameterSpec = algoParameters.getParameterSpec(ECParameterSpec.class);
            if (publicServerX != null && !publicServerX.isEmpty() && publicServerY != null && !publicServerY.isEmpty()) {
                /** Get point values */
                byte[] publicX = Hex.decodeHex(publicServerX.toCharArray());
                byte[] publicY = Hex.decodeHex(publicServerY.toCharArray());
                /** Create key specs */
                KeySpec publicKeySpec = new ECPublicKeySpec(new ECPoint(new BigInteger(publicX), new BigInteger(publicY)),
                        parameterSpec);
                /** Get keys */
                PublicKey publicKey = KeyFactory.getInstance("EC").generatePublic(publicKeySpec);
                if (publicKey != null && publicKey.getEncoded().length > 0) {
                    return Hex.encodeHexString(publicKey.getEncoded());
                }
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.error("[{}] Failed generate Server RPK for profile", e.getMessage());
            throw new RuntimeException(e);
        }
        return null;
    }
}

