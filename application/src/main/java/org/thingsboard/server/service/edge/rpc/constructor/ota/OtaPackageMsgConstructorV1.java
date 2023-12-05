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
package org.thingsboard.server.service.edge.rpc.constructor.ota;

import com.google.protobuf.ByteString;
import org.springframework.stereotype.Component;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.OtaPackage;
import org.thingsboard.server.gen.edge.v1.OtaPackageUpdateMsg;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.queue.util.TbCoreComponent;

@Component
@TbCoreComponent
public class OtaPackageMsgConstructorV1 extends BaseOtaPackageMsgConstructor {

    @Override
    public OtaPackageUpdateMsg constructOtaPackageUpdatedMsg(UpdateMsgType msgType, OtaPackage otaPackage) {
        OtaPackageUpdateMsg.Builder builder = OtaPackageUpdateMsg.newBuilder()
                .setMsgType(msgType)
                .setIdMSB(otaPackage.getId().getId().getMostSignificantBits())
                .setIdLSB(otaPackage.getId().getId().getLeastSignificantBits())
                .setType(otaPackage.getType().name())
                .setTitle(otaPackage.getTitle())
                .setVersion(otaPackage.getVersion())
                .setTag(otaPackage.getTag());

        if (otaPackage.getDeviceProfileId() != null) {
            builder.setDeviceProfileIdMSB(otaPackage.getDeviceProfileId().getId().getMostSignificantBits())
                    .setDeviceProfileIdLSB(otaPackage.getDeviceProfileId().getId().getLeastSignificantBits());
        }

        if (otaPackage.getUrl() != null) {
            builder.setUrl(otaPackage.getUrl());
        }
        if (otaPackage.getAdditionalInfo() != null) {
            builder.setAdditionalInfo(JacksonUtil.toString(otaPackage.getAdditionalInfo()));
        }
        if (otaPackage.getFileName() != null) {
            builder.setFileName(otaPackage.getFileName());
        }
        if (otaPackage.getContentType() != null) {
            builder.setContentType(otaPackage.getContentType());
        }
        if (otaPackage.getChecksumAlgorithm() != null) {
            builder.setChecksumAlgorithm(otaPackage.getChecksumAlgorithm().name());
        }
        if (otaPackage.getChecksum() != null) {
            builder.setChecksum(otaPackage.getChecksum());
        }
        if (otaPackage.getDataSize() != null) {
            builder.setDataSize(otaPackage.getDataSize());
        }
        if (otaPackage.getData() != null) {
            builder.setData(ByteString.copyFrom(otaPackage.getData().array()));
        }
        return builder.build();
    }
}
