/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.rule.engine.rest;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsAsyncClientHttpRequestFactory;
import org.springframework.http.client.Netty4ClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.TbRelationTypes;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.security.NoSuchAlgorithmException;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

@Data
@Slf4j
class TbHttpClient {

    private static final String STATUS = "status";
    private static final String STATUS_CODE = "statusCode";
    private static final String STATUS_REASON = "statusReason";
    private static final String ERROR = "error";
    private static final String ERROR_BODY = "error_body";
    private static final String ERROR_SYSTEM_PROPERTIES = "Didn't set any system proxy properties. Should be added next system proxy properties: \"http.proxyHost\" and \"http.proxyPort\" or  \"https.proxyHost\" and \"https.proxyPort\" or \"socksProxyHost\" and \"socksProxyPort\"";

    private final TbRestApiCallNodeConfiguration config;

    private EventLoopGroup eventLoopGroup;
    private AsyncRestTemplate httpClient;
    private Deque<ListenableFuture<ResponseEntity<String>>> pendingFutures;

    TbHttpClient(TbRestApiCallNodeConfiguration config) throws TbNodeException {
        try {
            this.config = config;
            if (config.getMaxParallelRequestsCount() > 0) {
                pendingFutures = new ConcurrentLinkedDeque<>();
            }

            if (config.isEnableProxy()) {
                checkProxyHost(config.getProxyHost());
                checkProxyPort(config.getProxyPort());

                String proxyUser;
                String proxyPassword;

                CloseableHttpAsyncClient asyncClient;
                HttpComponentsAsyncClientHttpRequestFactory requestFactory = new HttpComponentsAsyncClientHttpRequestFactory();

                if (config.isUseSystemProxyProperties()) {
                    checkSystemProxyProperties();

                    asyncClient = HttpAsyncClients.createSystem();

                    proxyUser = System.getProperty("tb.proxy.user");
                    proxyPassword = System.getProperty("tb.proxy.password");

                    if (useAuth(proxyUser, proxyPassword)) {
                        Authenticator.setDefault(new Authenticator() {
                            protected PasswordAuthentication getPasswordAuthentication() {
                                return new PasswordAuthentication(proxyUser, proxyPassword.toCharArray());
                            }
                        });
                    }
                } else {
                    HttpAsyncClientBuilder httpAsyncClientBuilder = HttpAsyncClientBuilder.create()
                            .setSSLHostnameVerifier(new DefaultHostnameVerifier())
                            .setSSLContext(SSLContext.getDefault())
                            .setProxy(new HttpHost(config.getProxyHost(), config.getProxyPort(), config.getProxyScheme()));

                    proxyUser = config.getProxyUser();
                    proxyPassword = config.getProxyPassword();

                    if (useAuth(proxyUser, proxyPassword)) {
                        CredentialsProvider credsProvider = new BasicCredentialsProvider();
                        credsProvider.setCredentials(
                                new AuthScope(config.getProxyHost(), config.getProxyPort()),
                                new UsernamePasswordCredentials(proxyUser, proxyPassword)
                        );
                        httpAsyncClientBuilder.setDefaultCredentialsProvider(credsProvider);
                    }
                    asyncClient = httpAsyncClientBuilder.build();
                }

                requestFactory.setAsyncClient(asyncClient);
                requestFactory.setReadTimeout(config.getReadTimeoutMs());
                httpClient = new AsyncRestTemplate(requestFactory);
            } else if (config.isUseSimpleClientHttpFactory()) {
                httpClient = new AsyncRestTemplate();
            } else {
                this.eventLoopGroup = new NioEventLoopGroup();
                Netty4ClientHttpRequestFactory nettyFactory = new Netty4ClientHttpRequestFactory(this.eventLoopGroup);
                nettyFactory.setSslContext(SslContextBuilder.forClient().build());
                nettyFactory.setReadTimeout(config.getReadTimeoutMs());
                httpClient = new AsyncRestTemplate(nettyFactory);
            }
        } catch (SSLException | NoSuchAlgorithmException e) {
            throw new TbNodeException(e);
        }
    }

    private void checkSystemProxyProperties() throws TbNodeException {
        boolean useHttpProxy = !StringUtils.isEmpty(System.getProperty("http.proxyHost")) && !StringUtils.isEmpty(System.getProperty("http.proxyPort"));
        boolean useHttpsProxy = !StringUtils.isEmpty(System.getProperty("https.proxyHost")) && !StringUtils.isEmpty(System.getProperty("https.proxyPort"));
        boolean useSocksProxy = !StringUtils.isEmpty(System.getProperty("socksProxyHost")) && !StringUtils.isEmpty(System.getProperty("socksProxyPort"));
        if (!(useHttpProxy || useHttpsProxy || useSocksProxy)) {
            log.warn(ERROR_SYSTEM_PROPERTIES);
            throw new TbNodeException(ERROR_SYSTEM_PROPERTIES);
        }
    }

    private boolean useAuth(String proxyUser, String proxyPassword) {
        return !StringUtils.isEmpty(proxyUser) && !StringUtils.isEmpty(proxyPassword);
    }

    void destroy() {
        if (this.eventLoopGroup != null) {
            this.eventLoopGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
    }

    void processMessage(TbContext ctx, TbMsg msg) {
        String endpointUrl = TbNodeUtils.processPattern(config.getRestEndpointUrlPattern(), msg.getMetaData());
        HttpHeaders headers = prepareHeaders(msg.getMetaData());
        HttpMethod method = HttpMethod.valueOf(config.getRequestMethod());
        HttpEntity<String> entity = new HttpEntity<>(msg.getData(), headers);

        ListenableFuture<ResponseEntity<String>> future = httpClient.exchange(
                endpointUrl, method, entity, String.class);
        future.addCallback(new ListenableFutureCallback<ResponseEntity<String>>() {
            @Override
            public void onFailure(Throwable throwable) {
                TbMsg next = processException(ctx, msg, throwable);
                ctx.tellFailure(next, throwable);
            }

            @Override
            public void onSuccess(ResponseEntity<String> responseEntity) {
                if (responseEntity.getStatusCode().is2xxSuccessful()) {
                    TbMsg next = processResponse(ctx, msg, responseEntity);
                    ctx.tellSuccess(next);
                } else {
                    TbMsg next = processFailureResponse(ctx, msg, responseEntity);
                    ctx.tellNext(next, TbRelationTypes.FAILURE);
                }
            }
        });
        if (pendingFutures != null) {
            processParallelRequests(future);
        }
    }

    private TbMsg processResponse(TbContext ctx, TbMsg origMsg, ResponseEntity<String> response) {
        TbMsgMetaData metaData = origMsg.getMetaData();
        metaData.putValue(STATUS, response.getStatusCode().name());
        metaData.putValue(STATUS_CODE, response.getStatusCode().value() + "");
        metaData.putValue(STATUS_REASON, response.getStatusCode().getReasonPhrase());
        response.getHeaders().toSingleValueMap().forEach(metaData::putValue);
        return ctx.transformMsg(origMsg, origMsg.getType(), origMsg.getOriginator(), metaData, response.getBody());
    }

    private TbMsg processFailureResponse(TbContext ctx, TbMsg origMsg, ResponseEntity<String> response) {
        TbMsgMetaData metaData = origMsg.getMetaData();
        metaData.putValue(STATUS, response.getStatusCode().name());
        metaData.putValue(STATUS_CODE, response.getStatusCode().value() + "");
        metaData.putValue(STATUS_REASON, response.getStatusCode().getReasonPhrase());
        metaData.putValue(ERROR_BODY, response.getBody());
        return ctx.transformMsg(origMsg, origMsg.getType(), origMsg.getOriginator(), metaData, origMsg.getData());
    }

    private TbMsg processException(TbContext ctx, TbMsg origMsg, Throwable e) {
        TbMsgMetaData metaData = origMsg.getMetaData();
        metaData.putValue(ERROR, e.getClass() + ": " + e.getMessage());
        if (e instanceof HttpClientErrorException) {
            HttpClientErrorException httpClientErrorException = (HttpClientErrorException) e;
            metaData.putValue(STATUS, httpClientErrorException.getStatusText());
            metaData.putValue(STATUS_CODE, httpClientErrorException.getRawStatusCode() + "");
            metaData.putValue(ERROR_BODY, httpClientErrorException.getResponseBodyAsString());
        }
        return ctx.transformMsg(origMsg, origMsg.getType(), origMsg.getOriginator(), metaData, origMsg.getData());
    }

    private HttpHeaders prepareHeaders(TbMsgMetaData metaData) {
        HttpHeaders headers = new HttpHeaders();
        config.getHeaders().forEach((k, v) -> headers.add(TbNodeUtils.processPattern(k, metaData), TbNodeUtils.processPattern(v, metaData)));
        return headers;
    }

    private void processParallelRequests(ListenableFuture<ResponseEntity<String>> future) {
        pendingFutures.add(future);
        if (pendingFutures.size() > config.getMaxParallelRequestsCount()) {
            for (int i = 0; i < config.getMaxParallelRequestsCount(); i++) {
                try {
                    ListenableFuture<ResponseEntity<String>> pendingFuture = pendingFutures.removeFirst();
                    try {
                        pendingFuture.get(config.getReadTimeoutMs(), TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        log.warn("Timeout during waiting for reply!", e);
                        pendingFuture.cancel(true);
                    }
                } catch (Exception e) {
                    log.warn("Failure during waiting for reply!", e);
                }
            }
        }
    }

    private static void checkProxyHost(String proxyHost) throws TbNodeException {
        if (StringUtils.isEmpty(proxyHost)) {
            throw new TbNodeException("Proxy host can't be empty");
        }
    }

    private static void checkProxyPort(int proxyPort) throws TbNodeException {
        if (proxyPort < 0 || proxyPort > 65535) {
            throw new TbNodeException("Proxy port out of range:" + proxyPort);
        }
    }
}
