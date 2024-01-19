/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2024 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.cache;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.util.Assert;
import org.thingsboard.common.util.SslUtil;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.id.EntityId;
import redis.clients.jedis.JedisPoolConfig;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Configuration
@ConditionalOnProperty(prefix = "cache", value = "type", havingValue = "redis")
@EnableCaching
@Data
@Slf4j
public abstract class TBRedisCacheConfiguration {

    private static final String COMMA = ",";
    private static final String COLON = ":";

    @Value("${redis.evictTtlInMs:60000}")
    private int evictTtlInMs;

    @Value("${redis.pool_config.maxTotal:128}")
    private int maxTotal;

    @Value("${redis.pool_config.maxIdle:128}")
    private int maxIdle;

    @Value("${redis.pool_config.minIdle:16}")
    private int minIdle;

    @Value("${redis.pool_config.testOnBorrow:true}")
    private boolean testOnBorrow;

    @Value("${redis.pool_config.testOnReturn:true}")
    private boolean testOnReturn;

    @Value("${redis.pool_config.testWhileIdle:true}")
    private boolean testWhileIdle;

    @Value("${redis.pool_config.minEvictableMs:60000}")
    private long minEvictableMs;

    @Value("${redis.pool_config.evictionRunsMs:30000}")
    private long evictionRunsMs;

    @Value("${redis.pool_config.maxWaitMills:60000}")
    private long maxWaitMills;

    @Value("${redis.pool_config.numberTestsPerEvictionRun:3}")
    private int numberTestsPerEvictionRun;

    @Value("${redis.pool_config.blockWhenExhausted:true}")
    private boolean blockWhenExhausted;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return loadFactory();
    }

    @Autowired
    private RedisSslCredentialsConfiguration redisSslCredentials;

    protected abstract JedisConnectionFactory loadFactory();

    /**
     * Transaction aware RedisCacheManager.
     * Enable RedisCaches to synchronize cache put/evict operations with ongoing Spring-managed transactions.
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory cf) {
        DefaultFormattingConversionService redisConversionService = new DefaultFormattingConversionService();
        RedisCacheConfiguration.registerDefaultConverters(redisConversionService);
        registerDefaultConverters(redisConversionService);
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig().withConversionService(redisConversionService);
        return RedisCacheManager.builder(cf).cacheDefaults(configuration)
                .transactionAware()
                .build();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());
        return template;
    }

    private static void registerDefaultConverters(ConverterRegistry registry) {
        Assert.notNull(registry, "ConverterRegistry must not be null!");
        registry.addConverter(EntityId.class, String.class, EntityId::toString);
    }

    protected JedisPoolConfig buildPoolConfig() {
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setTestOnBorrow(testOnBorrow);
        poolConfig.setTestOnReturn(testOnReturn);
        poolConfig.setTestWhileIdle(testWhileIdle);
        poolConfig.setSoftMinEvictableIdleTime(Duration.ofMillis(minEvictableMs));
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofMillis(evictionRunsMs));
        poolConfig.setMaxWaitMillis(maxWaitMills);
        poolConfig.setNumTestsPerEvictionRun(numberTestsPerEvictionRun);
        poolConfig.setBlockWhenExhausted(blockWhenExhausted);
        return poolConfig;
    }

    protected List<RedisNode> getNodes(String nodes) {
        List<RedisNode> result;
        if (StringUtils.isBlank(nodes)) {
            result = Collections.emptyList();
        } else {
            result = new ArrayList<>();
            for (String hostPort : nodes.split(COMMA)) {
                String host = hostPort.split(COLON)[0];
                int port = Integer.parseInt(hostPort.split(COLON)[1]);
                result.add(new RedisNode(host, port));
            }
        }
        return result;
    }

    protected SSLSocketFactory createSslSocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            KeyManagerFactory keyManagerFactory = createAndInitKeyManagerFactory();
            TrustManagerFactory trustManagerFactory = createAndInitTrustManagerFactory();
            sslContext.init(keyManagerFactory == null ? null : keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TrustManagerFactory createAndInitTrustManagerFactory() throws Exception {
        String type = redisSslCredentials.getType();
        if ("pem".equals(type)) {
            RedisPemCredentialsConfig pemCredentials = redisSslCredentials.getPem();
            List<X509Certificate> caCerts = SslUtil.readCertFileByPath(pemCredentials.getCertFile());

            KeyStore caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            caKeyStore.load(null, null);
            for (X509Certificate caCert : caCerts) {
                caKeyStore.setCertificateEntry("redis-caCert-cert-" + caCert.getSubjectX500Principal().getName(), caCert);
            }

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
            trustManagerFactory.init(caKeyStore);
            return trustManagerFactory;
        } else if ("keystore".equals(type)) {
            RedisKeystoreCredentialsConfig keystore = redisSslCredentials.getKeystore();
            KeyStore trustStore = KeyStore.getInstance(keystore.getKeystoreType());
            trustStore.load(new FileInputStream(keystore.getTruststoreLocation()), keystore.getTruststorePassword().toCharArray());

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
            trustManagerFactory.init(trustStore);
            return trustManagerFactory;
        } else {
            throw new RuntimeException(type + ": Invalid SSL credentials configuration. None of the PEM or KEYSTORE configurations can be used!");
        }
    }

    private KeyManagerFactory createAndInitKeyManagerFactory() throws Exception {
        String type = redisSslCredentials.getType();
        if ("pem".equals(type)) {
            RedisPemCredentialsConfig pemCredentials = redisSslCredentials.getPem();
            return getKeyManagerFactory(pemCredentials);
        } else if ("keystore".equals(type)) {
            RedisKeystoreCredentialsConfig keystore = redisSslCredentials.getKeystore();
            return getKeyManagerFactory(keystore);
        } else {
            throw new RuntimeException(type + ": Invalid SSL credentials configuration. None of the PEM or KEYSTORE configurations can be used!");
        }
    }

    private KeyManagerFactory getKeyManagerFactory(RedisPemCredentialsConfig pemCredentials) throws Exception {
        if (pemCredentials.getUserCertFile().isBlank() || pemCredentials.getUserKeyFile().isBlank()) {
            return null;
        }
        List<X509Certificate> certificates = SslUtil.readCertFileByPath(pemCredentials.getCertFile());
        PrivateKey privateKey = SslUtil.readPrivateKeyByFilePath(pemCredentials.getUserKeyFile(), null);

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null);
        List<X509Certificate> unique = certificates.stream().distinct().toList();
        for (X509Certificate cert : unique) {
            keyStore.setCertificateEntry("redis-cert" + cert.getSubjectX500Principal().getName(), cert);
        }

        if (privateKey != null) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            CertPath certPath = factory.generateCertPath(certificates);
            List<? extends Certificate> path = certPath.getCertificates();
            Certificate[] x509Certificates = path.toArray(new Certificate[0]);
            keyStore.setKeyEntry("redis-private-key", privateKey, null, x509Certificates);
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
        kmf.init(keyStore, null);
        return kmf;
    }

    private KeyManagerFactory getKeyManagerFactory(RedisKeystoreCredentialsConfig keystore) throws Exception {
        if (keystore.getKeystoreLocation().isBlank() || keystore.getKeystoreLocation().isBlank()) {
            return null;
        }
        KeyStore keyStore = KeyStore.getInstance(keystore.getKeystoreType());
        keyStore.load(new FileInputStream(keystore.getKeystoreLocation()), keystore.getKeystorePassword().toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
        kmf.init(keyStore, keystore.getKeystorePassword().toCharArray());
        return kmf;
    }
}
