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
package org.thingsboard.server.service.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.handler.ssl.SslContextBuilder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;
import org.springframework.web.reactive.function.client.WebClient;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.rule.engine.api.ReportService;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.permission.MergedUserPermissions;
import org.thingsboard.server.common.data.report.ReportConfig;
import org.thingsboard.server.common.data.report.ReportData;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.security.UserCredentials;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.dao.user.UserService;
import org.thingsboard.server.dao.util.limits.LimitedApi;
import org.thingsboard.server.dao.util.limits.RateLimitService;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.model.UserPrincipal;
import org.thingsboard.server.service.security.model.token.AccessJwtToken;
import org.thingsboard.server.service.security.model.token.JwtTokenFactory;
import org.thingsboard.server.service.security.permission.UserPermissionsService;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@SuppressWarnings("deprecation")
@RequiredArgsConstructor
public class DefaultReportService implements ReportService {

    private static final Pattern reportNameDatePattern = Pattern.compile("%d\\{([^\\}]*)\\}");

    @Value("${reports.server.endpointUrl}")
    private String reportsServerEndpointUrl;

    @Value("${reports.rate_limits.enabled:false}")
    private boolean rateLimitsEnabled;

    @Value("${reports.rate_limits.configuration:5:300}")
    private String rateLimitsConfiguration;

    @Value("${reports.server.maxResponseSize:52428800}")
    private int maxResponseSize;

    private final UserService userService;
    private final CustomerService customerService;
    private final JwtTokenFactory jwtTokenFactory;
    private final UserPermissionsService userPermissionsService;
    private final RateLimitService rateLimitService;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        try {
            HttpClient httpClient = HttpClient.create()
                    .secure(t -> {
                        try {
                            t.sslContext(SslContextBuilder.forClient().build());
                        } catch (SSLException e) {
                            throw new RuntimeException(e);
                        }
                    });

            this.webClient = WebClient.builder()
                    .filter(ExchangeFilterFunctions.limitResponseSize(maxResponseSize))
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();
        } catch (Exception e) {
            log.error("Can't initialize report service due to {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void checkLimits(TenantId tenantId) {
        if (rateLimitsEnabled) {
            if (!rateLimitService.checkRateLimit(LimitedApi.REPORTS, (Object) tenantId, rateLimitsConfiguration)) {
                log.trace("[{}] Report generation limits exceeded!", tenantId);
                throw new RuntimeException("Failed to generate report due to rate limits!");
            }
        }
    }

    @Override
    public void generateDashboardReport(String baseUrl, DashboardId dashboardId, TenantId tenantId, UserId userId, String publicId,
                                        String reportName, JsonNode reportParams, Consumer<ReportData> onSuccess,
                                        Consumer<Throwable> onFailure) {
        checkLimits(tenantId);
        log.trace("Executing generateDashboardReport, baseUrl [{}], dashboardId [{}], userId [{}]", baseUrl, dashboardId, userId);

        AccessJwtToken accessToken;
        if (StringUtils.isEmpty(publicId)) {
            accessToken = calculateUserAccessToken(tenantId, userId);
        } else {
            accessToken = calculateUserAccessTokenFromPublicId(tenantId, publicId);
            ((ObjectNode) reportParams).put("publicId", publicId);
        }

        String token = accessToken.getToken();
        long expiration = accessToken.getClaims().getExpiration().getTime();

        ObjectNode dashboardReportRequest = JacksonUtil.newObjectNode();
        dashboardReportRequest.put("baseUrl", baseUrl);
        dashboardReportRequest.put("dashboardId", dashboardId.toString());
        dashboardReportRequest.set("reportParams", reportParams);
        dashboardReportRequest.put("name", reportName);
        dashboardReportRequest.put("token", token);
        dashboardReportRequest.put("expiration", expiration);

        requestReport(dashboardReportRequest, null, onSuccess, onFailure);
    }

    @Override
    public void generateReport(TenantId tenantId, ReportConfig reportConfig, String reportsServerEndpointUrl, Consumer<ReportData> onSuccess, Consumer<Throwable> onFailure) {
        checkLimits(tenantId);
        log.trace("Executing generateReport, reportConfig [{}]", reportConfig);

        JsonNode dashboardReportRequest = createDashboardReportRequest(tenantId, reportConfig);
        requestReport(dashboardReportRequest, reportsServerEndpointUrl, onSuccess, onFailure);
    }

    private void requestReport(JsonNode dashboardReportRequest, String reportsServerEndpointUrl, Consumer<ReportData> onSuccess,
                               Consumer<Throwable> onFailure) {
        if (StringUtils.isEmpty(reportsServerEndpointUrl)) {
            reportsServerEndpointUrl = this.reportsServerEndpointUrl;
        }
        String endpointUrl = reportsServerEndpointUrl + "/dashboardReport";

        byte[] requestBody = JacksonUtil.writeValueAsBytes(dashboardReportRequest);

        webClient.post()
                .uri(endpointUrl)
                .headers(headers -> prepareHeaders(headers, requestBody))
                .bodyValue(requestBody)
                .retrieve()
                .toEntity(byte[].class)
                .subscribe(responseEntity -> {
                    try {
                        onSuccess.accept(extractResponse(responseEntity));
                    } catch (Throwable t) {
                        processError(onFailure, t);
                    }
                }, t -> {
                    processError(onFailure, t);
                });
    }

    private void processError(Consumer<Throwable> onFailure, Throwable t) {
        if (t instanceof RestClientResponseException) {
            onFailure.accept(new ThingsboardException(((RestClientResponseException) t).getStatusText(), ThingsboardErrorCode.GENERAL));
        } else {
            onFailure.accept(t);
        }
    }

    private JsonNode createDashboardReportRequest(TenantId tenantId, ReportConfig reportConfig) {
        AccessJwtToken accessToken = calculateUserAccessToken(tenantId, new UserId(UUID.fromString(reportConfig.getUserId())));
        String token = accessToken.getToken();
        long expiration = accessToken.getClaims().getExpiration().getTime();
        TimeZone tz = TimeZone.getTimeZone(reportConfig.getTimezone());
        String reportName = prepareReportName(reportConfig.getNamePattern(), new Date(), tz);
        ObjectNode dashboardReportRequest = JacksonUtil.newObjectNode();
        dashboardReportRequest.put("baseUrl", reportConfig.getBaseUrl());
        dashboardReportRequest.put("dashboardId", reportConfig.getDashboardId());
        dashboardReportRequest.put("token", token);
        dashboardReportRequest.put("expiration", expiration);
        dashboardReportRequest.put("name", reportName);
        dashboardReportRequest.set("reportParams", createReportParams(reportConfig));
        return dashboardReportRequest;
    }

    private JsonNode createReportParams(ReportConfig reportConfig) {
        ObjectNode reportParams = JacksonUtil.newObjectNode();
        reportParams.put("type", reportConfig.getType());
        reportParams.put("state", reportConfig.getState());
        if (!reportConfig.isUseDashboardTimewindow()) {
            reportParams.set("timewindow", reportConfig.getTimewindow());
        }
        reportParams.put("timezone", reportConfig.getTimezone());
        return reportParams;
    }

    private String prepareReportName(String namePattern, Date reportDate, TimeZone tz) {
        String name = namePattern;
        Matcher matcher = reportNameDatePattern.matcher(namePattern);
        while (matcher.find()) {
            String toReplace = matcher.group(0);
            SimpleDateFormat dateFormat = new SimpleDateFormat(matcher.group(1));
            dateFormat.setTimeZone(tz);
            String replacement = dateFormat.format(reportDate);
            name = name.replace(toReplace, replacement);
        }
        return name;
    }

    private AccessJwtToken calculateUserAccessToken(TenantId tenantId, UserId userId) {
        User user = userService.findUserById(tenantId, userId);
        UserCredentials credentials = userService.findUserCredentialsByUserId(tenantId, userId);
        UserPrincipal principal = new UserPrincipal(UserPrincipal.Type.USER_NAME, user.getEmail());
        MergedUserPermissions mergedUserPermissions;
        try {
            mergedUserPermissions = userPermissionsService.getMergedPermissions(user, false);
        } catch (Exception e) {
            throw new BadCredentialsException("Failed to get user permissions", e);
        }

        SecurityUser securityUser = new SecurityUser(user, credentials.isEnabled(), principal, mergedUserPermissions);
        return jwtTokenFactory.createAccessJwtToken(securityUser);
    }

    private AccessJwtToken calculateUserAccessTokenFromPublicId(TenantId tenantId, String publicId) {
        CustomerId customerId;
        try {
            customerId = new CustomerId(UUID.fromString(publicId));
        } catch (Exception e) {
            throw new BadCredentialsException("Authentication Failed. Public Id is not valid.");
        }
        Customer publicCustomer = customerService.findCustomerById(tenantId, customerId);
        if (publicCustomer == null) {
            throw new UsernameNotFoundException("Public entity not found: " + publicId);
        }
        if (!publicCustomer.isPublic()) {
            throw new BadCredentialsException("Authentication Failed. Public Id is not valid.");
        }
        User user = new User(new UserId(EntityId.NULL_UUID));
        user.setTenantId(publicCustomer.getTenantId());
        user.setCustomerId(publicCustomer.getId());
        user.setEmail(publicId);
        user.setAuthority(Authority.CUSTOMER_USER);
        user.setFirstName("Public");
        user.setLastName("Public");

        SecurityUser securityUser = new SecurityUser(user, true, new UserPrincipal(UserPrincipal.Type.PUBLIC_ID, publicId), new MergedUserPermissions(new HashMap<>(), new HashMap<>()));
        return jwtTokenFactory.createAccessJwtToken(securityUser);
    }

    private ReportData extractResponse(ResponseEntity<byte[]> responseEntity) throws UnsupportedEncodingException {
        ReportData reportData = new ReportData();
        reportData.setData(responseEntity.getBody());
        reportData.setContentType(responseEntity.getHeaders().getContentType().toString());
        String disposition = responseEntity.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        String fileName = disposition.replaceFirst("(?i)^.*filename=\"?([^\"]+)\"?.*$", "$1");
        fileName = URLDecoder.decode(fileName, "ISO_8859_1");
        reportData.setName(fileName);
        return reportData;
    }

    private void prepareHeaders(HttpHeaders headers, byte[] json) {
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentLength(json.length);
        headers.setConnection("keep-alive");
    }

}
