/**
 * Copyright © 2016-2022 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.service.security.auth.mfa.config;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.thingsboard.server.service.security.auth.mfa.config.provider.TwoFactorAuthProviderConfig;
import org.thingsboard.server.service.security.auth.mfa.provider.TwoFactorAuthProviderType;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import java.util.List;
import java.util.Optional;

@Data
public class TwoFactorAuthSettings {

    private boolean useSystemTwoFactorAuthSettings;
    @Valid
    private List<TwoFactorAuthProviderConfig> providers;

    @ApiModelProperty(example = "1:60 (1 request per minute)")
    @Pattern(regexp = "[1-9]\\d*:[1-9]\\d*", message = "verification code send rate limit configuration is invalid")
    private String verificationCodeSendRateLimit;
    @ApiModelProperty(example = "3:900 (3 requests per 15 minutes)")
    @Pattern(regexp = "[1-9]\\d*:[1-9]\\d*", message = "verification code check rate limit configuration is invalid")
    private String verificationCodeCheckRateLimit;
    @ApiModelProperty(example = "10")
    @Min(value = 0, message = "maximum number of verification failure before user lockout must be positive")
    private int maxVerificationFailuresBeforeUserLockout;
    @ApiModelProperty(value = "in seconds", example = "3600 (60 minutes)")
    @Min(value = 1, message = "total amount of time allotted for verification must be greater than 0")
    private Integer totalAllowedTimeForVerification;


    public Optional<TwoFactorAuthProviderConfig> getProviderConfig(TwoFactorAuthProviderType providerType) {
        return Optional.ofNullable(providers)
                .flatMap(providersConfigs -> providersConfigs.stream()
                        .filter(providerConfig -> providerConfig.getProviderType() == providerType)
                        .findFirst());
    }

}
