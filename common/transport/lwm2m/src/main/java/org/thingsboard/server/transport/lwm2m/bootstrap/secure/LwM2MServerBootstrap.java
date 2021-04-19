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
package org.thingsboard.server.transport.lwm2m.bootstrap.secure;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.leshan.core.SecurityMode;

@Slf4j
@Data
public class LwM2MServerBootstrap {

    String clientPublicKeyOrId = "";
    String clientSecretKey = "";
    String serverPublicKey = "";
    Integer clientHoldOffTime = 1;
    Integer bootstrapServerAccountTimeout = 0;

    String host = "0.0.0.0";
    Integer port = 0;

    String securityMode = SecurityMode.NO_SEC.name();

    Integer serverId = 123;
    boolean bootstrapServerIs = false;

    public LwM2MServerBootstrap(){};

    public LwM2MServerBootstrap(LwM2MServerBootstrap bootstrapFromCredential, LwM2MServerBootstrap profileServerBootstrap) {
            this.clientPublicKeyOrId = bootstrapFromCredential.getClientPublicKeyOrId();
            this.clientSecretKey = bootstrapFromCredential.getClientSecretKey();
            this.serverPublicKey = profileServerBootstrap.getServerPublicKey();
            this.clientHoldOffTime = profileServerBootstrap.getClientHoldOffTime();
            this.bootstrapServerAccountTimeout = profileServerBootstrap.getBootstrapServerAccountTimeout();
            this.host = (profileServerBootstrap.getHost().equals("0.0.0.0")) ? "localhost" : profileServerBootstrap.getHost();
            this.port = profileServerBootstrap.getPort();
            this.securityMode = profileServerBootstrap.getSecurityMode();
            this.serverId = profileServerBootstrap.getServerId();
            this.bootstrapServerIs = profileServerBootstrap.bootstrapServerIs;
    }
}
