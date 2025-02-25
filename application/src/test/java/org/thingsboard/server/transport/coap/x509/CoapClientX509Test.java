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
package org.thingsboard.server.transport.coap.x509;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.elements.config.ValueException;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConfig.SignatureAndHashAlgorithmsDefinition;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.CertificateType;
import org.eclipse.californium.scandium.dtls.SignatureAndHashAlgorithm;
import org.eclipse.californium.scandium.dtls.SignatureAndHashAlgorithm.HashAlgorithm;
import org.eclipse.californium.scandium.dtls.SignatureAndHashAlgorithm.SignatureAlgorithm;
import org.eclipse.californium.scandium.dtls.cipher.CipherSuite;
import org.eclipse.californium.scandium.dtls.x509.CertificateProvider;
import org.eclipse.californium.scandium.dtls.x509.SingleCertificateProvider;
import org.thingsboard.server.common.msg.session.FeatureType;
import org.thingsboard.server.transport.coap.CoapTestCallback;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.eclipse.californium.core.config.CoapConfig.DEFAULT_BLOCKWISE_STATUS_LIFETIME_IN_SECONDS;
import static org.eclipse.californium.scandium.config.DtlsConfig.DTLS_AUTO_HANDSHAKE_TIMEOUT;
import static org.eclipse.californium.scandium.config.DtlsConfig.DTLS_CIPHER_SUITES;
import static org.eclipse.californium.scandium.config.DtlsConfig.DTLS_MAX_FRAGMENTED_HANDSHAKE_MESSAGE_LENGTH;
import static org.eclipse.californium.scandium.config.DtlsConfig.DTLS_MAX_PENDING_HANDSHAKE_RESULT_JOBS;
import static org.eclipse.californium.scandium.config.DtlsConfig.DTLS_MAX_RETRANSMISSIONS;
import static org.eclipse.californium.scandium.config.DtlsConfig.DTLS_MAX_RETRANSMISSION_TIMEOUT;
import static org.eclipse.californium.scandium.config.DtlsConfig.DTLS_RECEIVE_BUFFER_SIZE;
import static org.eclipse.californium.scandium.config.DtlsConfig.DTLS_RECOMMENDED_CIPHER_SUITES_ONLY;
import static org.eclipse.californium.scandium.config.DtlsConfig.DTLS_RETRANSMISSION_TIMEOUT;
import static org.eclipse.californium.scandium.config.DtlsConfig.DTLS_ROLE;
import static org.eclipse.californium.scandium.config.DtlsConfig.DTLS_SIGNATURE_AND_HASH_ALGORITHMS;
import static org.eclipse.californium.scandium.config.DtlsConfig.DTLS_USE_HELLO_VERIFY_REQUEST;
import static org.eclipse.californium.scandium.config.DtlsConfig.DTLS_USE_MULTI_HANDSHAKE_MESSAGE_RECORDS;
import static org.eclipse.californium.scandium.config.DtlsConfig.DTLS_VERIFY_SERVER_CERTIFICATES_SUBJECT;
import static org.eclipse.californium.scandium.config.DtlsConfig.DtlsRole.CLIENT_ONLY;
import static org.eclipse.californium.scandium.config.DtlsConfig.MODULE;
import static org.eclipse.californium.scandium.dtls.SignatureAndHashAlgorithm.SHA256_WITH_ECDSA;
import static org.eclipse.californium.scandium.dtls.SignatureAndHashAlgorithm.SHA256_WITH_RSA;
import static org.eclipse.californium.scandium.dtls.SignatureAndHashAlgorithm.SHA384_WITH_ECDSA;

@Slf4j
public class CoapClientX509Test {

    private static final long CLIENT_REQUEST_TIMEOUT = 60000L;

    private final CoapClient clientX509;
    private final DTLSConnector dtlsConnector;
    private final Configuration config;
    private final CertPrivateKey certPrivateKey;
    private final String coapsBaseUrl;

    @Getter
    private CoAP.Type type = CoAP.Type.CON;

    public CoapClientX509Test(CertPrivateKey certPrivateKey, FeatureType featureType, String coapsBaseUrl, Integer fixedPort) {
        this.certPrivateKey = certPrivateKey;
        this.coapsBaseUrl = coapsBaseUrl;
        this.config = createConfiguration();
        this.dtlsConnector = createDTLSConnector(fixedPort);
        this.clientX509 = createClient(getFeatureTokenUrl(featureType));
    }
    public void disconnect() {
        if (clientX509 != null) {
            clientX509.shutdown();
        }
    }

    public CoapResponse postMethod(String requestBody) throws ConnectorException, IOException {
        return this.postMethod(requestBody.getBytes());
    }

    public CoapResponse postMethod(byte[] requestBodyBytes) throws ConnectorException, IOException {
        return clientX509.setTimeout(CLIENT_REQUEST_TIMEOUT).post(requestBodyBytes, MediaTypeRegistry.APPLICATION_JSON);
    }

    public void postMethod(CoapHandler handler, String payload, int format) {
        clientX509.setTimeout(CLIENT_REQUEST_TIMEOUT).post(handler, payload, format);
    }

    public void postMethod(CoapHandler handler, byte[] payload) {
        clientX509.setTimeout(CLIENT_REQUEST_TIMEOUT).post(handler, payload,  MediaTypeRegistry.APPLICATION_JSON);
    }
    public void postMethod(CoapHandler handler, byte[] payload, int format) {
        clientX509.setTimeout(CLIENT_REQUEST_TIMEOUT).post(handler, payload, format);
    }

    public CoapResponse getMethod() throws ConnectorException, IOException {
        return clientX509.setTimeout(CLIENT_REQUEST_TIMEOUT).get();
    }

    public CoapObserveRelation getObserveRelation(CoapTestCallback callback) {
        return getObserveRelation(callback, true);
    }

    public CoapObserveRelation getObserveRelation(CoapTestCallback callback, boolean confirmable) {
        Request request = Request.newGet().setObserve();
        request.setType(confirmable ? CoAP.Type.CON : CoAP.Type.NON);
        return clientX509.observe(request, callback);
    }

    public void setURI(String featureTokenUrl) {
        if (clientX509 == null) {
            throw new RuntimeException("Failed to connect! CoapClient is not initialized!");
        }
        clientX509.setURI(featureTokenUrl);
    }

    public void setURI(String accessToken, FeatureType featureType) {
        if (featureType == null) {
            featureType = FeatureType.ATTRIBUTES;
        }
        setURI(getFeatureTokenUrl(accessToken, featureType));
    }

    public void useCONs() {
        if (clientX509 == null) {
            throw new RuntimeException("Failed to connect! CoapClient is not initialized!");
        }
        type = CoAP.Type.CON;
        clientX509.useCONs();
    }

    public void useNONs() {
        if (clientX509 == null) {
            throw new RuntimeException("Failed to connect! CoapClient is not initialized!");
        }
        type = CoAP.Type.NON;
        clientX509.useNONs();
    }

    private Configuration createConfiguration() {
        Configuration clientCoapConfig = new Configuration();
        clientCoapConfig.set(CoapConfig.BLOCKWISE_STRICT_BLOCK2_OPTION, true);
        clientCoapConfig.set(CoapConfig.BLOCKWISE_ENTITY_TOO_LARGE_AUTO_FAILOVER, true);
        clientCoapConfig.set(CoapConfig.BLOCKWISE_STATUS_LIFETIME, DEFAULT_BLOCKWISE_STATUS_LIFETIME_IN_SECONDS, TimeUnit.SECONDS);
        clientCoapConfig.set(CoapConfig.MAX_RESOURCE_BODY_SIZE, 256 * 1024 * 1024);
        clientCoapConfig.set(CoapConfig.RESPONSE_MATCHING, CoapConfig.MatcherMode.RELAXED);
        clientCoapConfig.set(CoapConfig.PREFERRED_BLOCK_SIZE, 1024);
        clientCoapConfig.set(CoapConfig.MAX_MESSAGE_SIZE, 1024);
        clientCoapConfig.set(DTLS_ROLE, CLIENT_ONLY);
        clientCoapConfig.set(DTLS_MAX_RETRANSMISSIONS, 2);
        clientCoapConfig.set(DTLS_RETRANSMISSION_TIMEOUT, 5000, MILLISECONDS);
        clientCoapConfig.set(DTLS_MAX_RETRANSMISSION_TIMEOUT, 60000, TimeUnit.MILLISECONDS);
        clientCoapConfig.set(DTLS_USE_HELLO_VERIFY_REQUEST, false);
        clientCoapConfig.set(DTLS_VERIFY_SERVER_CERTIFICATES_SUBJECT, false);
        clientCoapConfig.set(DTLS_MAX_FRAGMENTED_HANDSHAKE_MESSAGE_LENGTH, 22490);
        clientCoapConfig.set(DTLS_AUTO_HANDSHAKE_TIMEOUT, 100000,  TimeUnit.MILLISECONDS);
        clientCoapConfig.set(DTLS_MAX_PENDING_HANDSHAKE_RESULT_JOBS, 64);
        clientCoapConfig.set(DTLS_USE_MULTI_HANDSHAKE_MESSAGE_RECORDS, false);
        clientCoapConfig.set(DTLS_RECEIVE_BUFFER_SIZE, 8192);
        clientCoapConfig.setTransient(DTLS_RECOMMENDED_CIPHER_SUITES_ONLY);
        SignatureAndHashAlgorithmsDefinition algorithmsDefinition = new SignatureAndHashAlgorithmsDefinition(MODULE + "SIGNATURE_AND_HASH_ALGORITHMS", "List of DTLS signature- and hash-algorithms.\nValues e.g SHA256withECDSA or ED25519.");
        SignatureAndHashAlgorithm SHA384_WITH_RSA = new SignatureAndHashAlgorithm(HashAlgorithm.SHA384,
                SignatureAlgorithm.RSA);
        List<SignatureAndHashAlgorithm> algorithms = null;
        try {
            algorithms = algorithmsDefinition.checkValue(Arrays.asList(SHA256_WITH_ECDSA, SHA256_WITH_RSA, SHA384_WITH_ECDSA, SHA384_WITH_RSA));
        } catch (ValueException e) {
            throw new RuntimeException(e);
        }
        clientCoapConfig.setTransient(DTLS_SIGNATURE_AND_HASH_ALGORITHMS);
        clientCoapConfig.set(DTLS_SIGNATURE_AND_HASH_ALGORITHMS, algorithms);
        clientCoapConfig.setTransient(DTLS_CIPHER_SUITES);
        clientCoapConfig.set(DTLS_CIPHER_SUITES, Arrays.asList(CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256));
        clientCoapConfig.setTransient(DTLS_VERIFY_SERVER_CERTIFICATES_SUBJECT);
        clientCoapConfig.set(DTLS_VERIFY_SERVER_CERTIFICATES_SUBJECT, false);
        return clientCoapConfig;
    }

    private DTLSConnector createDTLSConnector(Integer fixedPort) {
        try {
            // Create DTLS config client
            DtlsConnectorConfig.Builder configBuilder = new DtlsConnectorConfig.Builder(this.config);
            configBuilder.setAdvancedCertificateVerifier(new TbAdvancedCertificateVerifier());
            X509Certificate[] certificateChainClient = new X509Certificate[]{this.certPrivateKey.getCert()};
            CertificateProvider certificateProvider = new SingleCertificateProvider(this.certPrivateKey.getPrivateKey(), certificateChainClient, Collections.singletonList(CertificateType.X_509));
            configBuilder.setCertificateIdentityProvider(certificateProvider);
            if (fixedPort != null) {
                InetSocketAddress localAddress = new InetSocketAddress("0.0.0.0", fixedPort);
                configBuilder.setAddress(localAddress);
                configBuilder.setReuseAddress(true);
            }
            return new DTLSConnector(configBuilder.build());
        } catch (Exception e) {
            throw new RuntimeException("", e);
        }
    }

    private CoapClient createClient(String featureTokenUrl) {
        CoapClient client = new CoapClient(featureTokenUrl);
        CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
        builder.setConnector(dtlsConnector);
        client.setEndpoint(builder.build());
        return client;
    }

    public String getFeatureTokenUrl(FeatureType featureType) {
        return this.coapsBaseUrl + featureType.name().toLowerCase();
    }

    public String getFeatureTokenUrl(String token, FeatureType featureType) {
        return this.coapsBaseUrl + token + "/" + featureType.name().toLowerCase();
    }
}

