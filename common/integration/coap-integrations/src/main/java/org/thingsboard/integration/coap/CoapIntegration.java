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
package org.thingsboard.integration.coap;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.leshan.core.request.ContentFormat;
import org.thingsboard.integration.api.AbstractIntegration;
import org.thingsboard.integration.api.IntegrationContext;
import org.thingsboard.integration.api.TbIntegrationInitParams;
import org.thingsboard.integration.api.data.UplinkData;
import org.thingsboard.integration.api.data.UplinkMetaData;
import org.thingsboard.integration.api.util.ConvertUtil;
import org.thingsboard.server.coapserver.CoapServerService;
import org.thingsboard.server.common.data.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class CoapIntegration extends AbstractIntegration<CoapIntegrationMsg> {

    private static final String DTLS_SESSION_ID_KEY = "DTLS_SESSION_ID";

    private CoapServerService coapServerService;

    private CoapResource integrationResource;

    private CoapResource dtlsIntegrationResource;

    public CoapIntegration(CoapServerService coapServerService) {
        this.coapServerService = coapServerService;
    }

    @Override
    public void init(TbIntegrationInitParams params) throws Exception {
        super.init(params);
        try {
            CoapClientConfiguration coapClientConfiguration = getClientConfiguration(configuration, CoapClientConfiguration.class);
            setupConfiguration(coapClientConfiguration);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize CoAP integration due to: ", e);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        if (this.integrationResource != null) {
            this.integrationResource.delete();
        }
        if (this.dtlsIntegrationResource != null) {
            this.dtlsIntegrationResource.delete();
        }
    }

    @Override
    public void process(CoapIntegrationMsg msg) {
        CoapExchange exchange = msg.getExchange();
        Request request = exchange.advanced().getRequest();
        String dtlsSessionIdStr = request.getSourceContext().get(DTLS_SESSION_ID_KEY);
        if (integrationResource == null && dtlsSessionIdStr == null) {
            exchange.respond(CoAP.ResponseCode.BAD_REQUEST, "No secure connection is not allowed");
            return;
        }
        CoAP.ResponseCode status = CoAP.ResponseCode.CREATED;
        Exception exception = null;
        try {
            List<UplinkData> uplinkDataList = getUplinkDataList(context, msg);
            processUplinkData(context, uplinkDataList);
        } catch (Exception e) {
            log.error("Failed to apply data converter function: {}", e.getMessage(), e);
            status = CoAP.ResponseCode.BAD_REQUEST;
            exception = e;
        }
        exchange.respond(status);
        boolean isOk = status.equals(CoAP.ResponseCode.CREATED);
        if (isOk) {
            integrationStatistics.incMessagesProcessed();
        } else {
            integrationStatistics.incErrorsOccurred();
        }
        if (configuration.isDebugMode()) {
            try {
                persistDebug(context, "Uplink", msg.getContentType(),
                        ConvertUtil.toDebugMessage(msg.getContentType(), msg.getPayloadBytes()), isOk ? "OK" : "ERROR", exception);
            } catch (Exception e) {
                log.warn("Failed to persist debug message", e);
            }
        }
    }

    protected void doValidateConfiguration(JsonNode configuration, boolean allowLocalNetworkHosts) {
        CoapClientConfiguration coapClientConfiguration;
        try {
            coapClientConfiguration = getClientConfiguration(configuration.get("clientConfiguration"), CoapClientConfiguration.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid CoAP Integration configuration structure!");
        }
        CoapSecurityMode securityMode = coapClientConfiguration.getSecurityMode();
        switch (securityMode) {
            case MIXED:
                checkDtlsEnabled();
                validateUri(allowLocalNetworkHosts, coapClientConfiguration.getCoapEndpoint());
                validateUri(allowLocalNetworkHosts, coapClientConfiguration.getDtlsCoapEndpoint());
                break;
            case NO_SECURE:
                validateUri(allowLocalNetworkHosts, coapClientConfiguration.getCoapEndpoint());
                break;
            case DTLS:
                checkDtlsEnabled();
                validateUri(allowLocalNetworkHosts, coapClientConfiguration.getDtlsCoapEndpoint());
                break;
            default:
                throw new IllegalArgumentException("Unsupported CoAP security mode! value: " + securityMode);
        }
    }

    private void validateUri(boolean allowLocalNetworkHosts, String strEndpoint) {
        URI uri = URI.create(strEndpoint);
        if (!allowLocalNetworkHosts && isLocalNetworkHost(uri.getHost())) {
            throw new IllegalArgumentException("Usage of local network host for CoAP endpoint connection is not allowed!");
        }
    }

    private void setupConfiguration(CoapClientConfiguration coapClientConfiguration) throws URISyntaxException, UnknownHostException {
        CoapSecurityMode securityMode = coapClientConfiguration.getSecurityMode();
        switch (securityMode) {
            case NO_SECURE:
                this.integrationResource =
                        addIntegrationResource(getEndpointUri(coapClientConfiguration.getCoapEndpoint()));
                break;
            case DTLS:
                checkDtlsEnabled();
                this.dtlsIntegrationResource =
                        addIntegrationResource(getEndpointUri(coapClientConfiguration.getDtlsCoapEndpoint()));
                break;
            case MIXED:
                checkDtlsEnabled();
                String noSecEndpointUri = getEndpointUri(coapClientConfiguration.getCoapEndpoint());
                String dtlsCoapEndpointUri = getEndpointUri(coapClientConfiguration.getDtlsCoapEndpoint());
                this.integrationResource = addIntegrationResource(noSecEndpointUri);
                if (!noSecEndpointUri.equals(dtlsCoapEndpointUri)) {
                    this.dtlsIntegrationResource = addIntegrationResource(dtlsCoapEndpointUri);
                }
                break;
        }
    }

    private String getEndpointUri(String coapEndpoint) throws URISyntaxException {
        return new URI(coapEndpoint)
                .getPath()
                .replace(this.configuration.getRoutingKey(), "");
    }

    private void checkDtlsEnabled() {
        if (!coapServerService.isDtlsEnabled()) {
            throw new RuntimeException("CoAP server doesn't have DTLS Endpoint enabled!");
        }
    }

    private CoapResource addIntegrationResource(String uriPath) throws UnknownHostException {
        List<String> resourceHierarchy = getResourceHierarchy(uriPath);
        Resource integrations = coapServerService.addResourceHierarchicallyAndReturnLast(resourceHierarchy);
        CoapResource newIntegrationResource = createNewIntegrationResource();
        integrations.add(newIntegrationResource);
        return newIntegrationResource;
    }

    private CoapResource createNewIntegrationResource() {
        return new CoapResource(configuration.getRoutingKey()) {

            @Override
            public Resource getChild(String name) {
                return this;
            }

            @Override
            public void handlePOST(CoapExchange exchange) {
                process(new CoapIntegrationMsg(exchange));
            }

            @Override
            public void handlePUT(CoapExchange exchange) {
                process(new CoapIntegrationMsg(exchange));
            }

        };
    }

    private void processUplinkData(IntegrationContext context, List<UplinkData> uplinkDataList) throws Exception {
        if (uplinkDataList != null && !uplinkDataList.isEmpty()) {
            for (UplinkData uplinkData : uplinkDataList) {
                processUplinkData(context, uplinkData);
                log.info("Processed uplink data: [{}]", uplinkData);
            }
        }
    }

    private List<UplinkData> getUplinkDataList(IntegrationContext context, CoapIntegrationMsg msg) throws Exception {
        Map<String, String> metadataMap = new HashMap<>(metadataTemplate.getKvMap());
        OptionSet options = msg.getExchange().getRequestOptions();
        if (options.getContentFormat() >= 0) {
            metadataMap.put("Option:content-format", Integer.toString(options.getContentFormat()));
        }
        if (options.getLocationPath() != null && !options.getLocationPath().isEmpty()) {
            metadataMap.put("Option:location-path", String.join(",", options.getLocationPath()));
        }
        if (options.getLocationQuery() != null && !options.getLocationQuery().isEmpty()) {
            metadataMap.put("Option:location-query", String.join(",", options.getLocationQuery()));
        }
        if (StringUtils.isNotEmpty(options.getUriString())) {
            metadataMap.put("Option:uri", options.getUriString());
        }
        return convertToUplinkDataList(context, msg.getPayloadBytes(), new UplinkMetaData(msg.getContentType(), metadataMap));
    }

    private List<String> getResourceHierarchy(String path) {
        final String slash = "/";

        // remove leading slash
        if (path.startsWith(slash)) {
            path = path.substring(slash.length());
        }
        return Arrays.asList(path.split(slash));
    }
}
