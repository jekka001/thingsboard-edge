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
package org.thingsboard.server.common.data.security.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Schema
@Data
public class UserPasswordPolicy implements Serializable {

    @Schema(description = "Minimum number of symbols in the password." )
    private Integer minimumLength;
    @Schema(description = "Maximum number of symbols in the password." )
    private Integer maximumLength;
    @Schema(description = "Minimum number of uppercase letters in the password." )
    private Integer minimumUppercaseLetters;
    @Schema(description = "Minimum number of lowercase letters in the password." )
    private Integer minimumLowercaseLetters;
    @Schema(description = "Minimum number of digits in the password." )
    private Integer minimumDigits;
    @Schema(description = "Minimum number of special in the password." )
    private Integer minimumSpecialCharacters;
    @Schema(description = "Allow whitespaces")
    private Boolean allowWhitespaces = true;
    @Schema(description = "Force user to update password if existing one does not pass validation")
    private Boolean forceUserToResetPasswordIfNotValid = false;

    @Schema(description = "Password expiration period (days). Force expiration of the password." )
    private Integer passwordExpirationPeriodDays;
    @Schema(description = "Password reuse frequency (days). Disallow to use the same password for the defined number of days" )
    private Integer passwordReuseFrequencyDays;

}
