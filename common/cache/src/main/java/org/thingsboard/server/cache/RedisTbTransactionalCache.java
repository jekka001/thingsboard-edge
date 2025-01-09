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

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.support.NullValue;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.connection.jedis.JedisClusterConnection;
import org.springframework.data.redis.connection.jedis.JedisConnection;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.thingsboard.server.common.data.FstStatsService;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.util.JedisClusterCRC16;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
public abstract class RedisTbTransactionalCache<K extends Serializable, V extends Serializable> implements TbTransactionalCache<K, V> {

    static final byte[] BINARY_NULL_VALUE = RedisSerializer.java().serialize(NullValue.INSTANCE);
    static final JedisPool MOCK_POOL = new JedisPool(); //non-null pool required for JedisConnection to trigger closing jedis connection

    @Autowired
    private FstStatsService fstStatsService;

    @Getter
    private final String cacheName;
    @Getter
    private final JedisConnectionFactory connectionFactory;
    private final RedisSerializer<String> keySerializer = StringRedisSerializer.UTF_8;
    private final TbRedisSerializer<K, V> valueSerializer;
    protected final Expiration evictExpiration;
    protected final Expiration cacheTtl;
    protected final boolean cacheEnabled;

    static final byte[] EVICT_BY_PREFIX_LUA_SCRIPT = StringRedisSerializer.UTF_8.serialize("""
            local prefix = ARGV[1]
            local count  = tonumber(ARGV[2]) or 1000
            
            local totalDeleted = 0
            local pass = 1
            local maxPasses = 3
            
            while pass <= maxPasses do
                local passCursor = "0"
                local passDeleted = 0
            
                repeat
                    local scanResult = redis.call("SCAN", passCursor, "MATCH", prefix .. "*", "COUNT", count)
                    passCursor = scanResult[1]
                    local keys = scanResult[2]
            
                    if #keys > 0 then
                        redis.call("DEL", unpack(keys))
                        passDeleted = passDeleted + #keys
                        totalDeleted = totalDeleted + #keys
                    end
                until passCursor == "0"

                if passDeleted == 0 then
                    break
                end
            
                pass = pass + 1
            end
            
            return totalDeleted
            """);

    static final byte[] EVICT_BY_PREFIX_SHA = StringRedisSerializer.UTF_8.serialize("7ecc145912da532cb9c3356be8cf4952f6138d1e");

    public RedisTbTransactionalCache(String cacheName,
                                     CacheSpecsMap cacheSpecsMap,
                                     RedisConnectionFactory connectionFactory,
                                     TBRedisCacheConfiguration configuration,
                                     TbRedisSerializer<K, V> valueSerializer) {
        this.cacheName = cacheName;
        this.connectionFactory = (JedisConnectionFactory) connectionFactory;
        this.valueSerializer = valueSerializer;
        this.evictExpiration = Expiration.from(configuration.getEvictTtlInMs(), TimeUnit.MILLISECONDS);
        this.cacheTtl = Optional.ofNullable(cacheSpecsMap)
                .map(CacheSpecsMap::getSpecs)
                .map(specs -> specs.get(cacheName))
                .map(CacheSpecs::getTimeToLiveInMinutes)
                .filter(ttl -> !ttl.equals(0))
                .map(ttl -> Expiration.from(ttl, TimeUnit.MINUTES))
                .orElseGet(Expiration::persistent);
        this.cacheEnabled = Optional.ofNullable(cacheSpecsMap)
                .map(CacheSpecsMap::getSpecs)
                .map(x -> x.get(cacheName))
                .map(CacheSpecs::getMaxSize)
                .map(size -> size > 0)
                .orElse(false);
    }

    @PostConstruct
    public void init() {
        loadLuaScript(EVICT_BY_PREFIX_SHA, EVICT_BY_PREFIX_LUA_SCRIPT);
    }

    @Override
    public TbCacheValueWrapper<V> get(K key) {
        if (!cacheEnabled) {
            return null;
        }
        try (var connection = connectionFactory.getConnection()) {
            byte[] rawValue = doGet(key, connection);
            if (rawValue == null || rawValue.length == 0) {
                return null;
            } else if (Arrays.equals(rawValue, BINARY_NULL_VALUE)) {
                return SimpleTbCacheValueWrapper.empty();
            } else {
                long startTime = System.nanoTime();
                V value = valueSerializer.deserialize(key, rawValue);
                if (value != null) {
                    fstStatsService.recordDecodeTime(value.getClass(), startTime);
                    fstStatsService.incrementDecode(value.getClass());
                }
                return SimpleTbCacheValueWrapper.wrap(value);
            }
        }
    }

    protected byte[] doGet(K key, RedisConnection connection) {
        return connection.stringCommands().get(getRawKey(key));
    }

    @Override
    public void put(K key, V value) {
        if (!cacheEnabled) {
            return;
        }
        try (var connection = connectionFactory.getConnection()) {
            put(key, value, connection);
        }
    }

    public void put(K key, V value, RedisConnection connection) {
        put(connection, key, value, RedisStringCommands.SetOption.UPSERT);
    }

    @Override
    public void putIfAbsent(K key, V value) {
        if (!cacheEnabled) {
            return;
        }
        try (var connection = connectionFactory.getConnection()) {
            put(connection, key, value, RedisStringCommands.SetOption.SET_IF_ABSENT);
        }
    }

    @Override
    public void evict(K key) {
        if (!cacheEnabled) {
            return;
        }
        try (var connection = connectionFactory.getConnection()) {
            connection.keyCommands().del(getRawKey(key));
        }
    }

    @Override
    public void evict(Collection<K> keys) {
        if (!cacheEnabled) {
            return;
        }
        //Redis expects at least 1 key to delete. Otherwise - ERR wrong number of arguments for 'del' command
        if (keys.isEmpty()) {
            return;
        }
        try (var connection = connectionFactory.getConnection()) {
            connection.keyCommands().del(keys.stream().map(this::getRawKey).toArray(byte[][]::new));
        }
    }

    @Override
    public void evictOrPut(K key, V value) {
        if (!cacheEnabled) {
            return;
        }
        try (var connection = connectionFactory.getConnection()) {
            var rawKey = getRawKey(key);
            var records = connection.keyCommands().del(rawKey);
            if (records == null || records == 0) {
                //We need to put the value in case of Redis, because evict will NOT cancel concurrent transaction used to "get" the missing value from cache.
                connection.stringCommands().set(rawKey, getRawValue(value), evictExpiration, RedisStringCommands.SetOption.UPSERT);
            }
        }
    }

    @Override
    public void evictByPrefix(String prefix) {
        if (!cacheEnabled) {
            return;
        }
        byte[] rawPrefix = StringRedisSerializer.UTF_8.serialize(cacheName + prefix);
        byte[] rawBatchSize = StringRedisSerializer.UTF_8.serialize(String.valueOf(1000));
        try (var connection = getConnection(rawPrefix)) {
            executeScript(connection, EVICT_BY_PREFIX_SHA, EVICT_BY_PREFIX_LUA_SCRIPT, ReturnType.INTEGER, 0, rawPrefix, rawBatchSize);
        }
    }

    @Override
    public TbCacheTransaction<K, V> newTransactionForKey(K key) {
        byte[][] rawKey = new byte[][]{getRawKey(key)};
        RedisConnection connection = watch(rawKey);
        return new RedisTbCacheTransaction<>(this, connection);
    }

    @Override
    public TbCacheTransaction<K, V> newTransactionForKeys(List<K> keys) {
        RedisConnection connection = watch(keys.stream().map(this::getRawKey).toArray(byte[][]::new));
        return new RedisTbCacheTransaction<>(this, connection);
    }

    @Override
    public <R> R getAndPutInTransaction(K key, Supplier<R> dbCall, Function<V, R> cacheValueToResult, Function<R, V> dbValueToCacheValue, boolean cacheNullValue) {
        if (!cacheEnabled) {
            return dbCall.get();
        }
        return TbTransactionalCache.super.getAndPutInTransaction(key, dbCall, cacheValueToResult, dbValueToCacheValue, cacheNullValue);
    }

    protected RedisConnection getConnection(byte[] rawKey) {
        if (!connectionFactory.isRedisClusterAware()) {
            return connectionFactory.getConnection();
        }
        RedisConnection connection = connectionFactory.getClusterConnection();

        int slotNum = JedisClusterCRC16.getSlot(rawKey);
        Jedis jedis = new Jedis((((JedisClusterConnection) connection).getNativeConnection().getConnectionFromSlot(slotNum)));

        JedisConnection jedisConnection = new JedisConnection(jedis, MOCK_POOL, jedis.getDB());
        jedisConnection.setConvertPipelineAndTxResults(connectionFactory.getConvertPipelineAndTxResults());

        return jedisConnection;
    }

    protected RedisConnection watch(byte[][] rawKeysList) {
        RedisConnection connection = getConnection(rawKeysList[0]);
        try {
            connection.watch(rawKeysList);
            connection.multi();
        } catch (Exception e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    protected byte[] getRawKey(K key) {
        String keyString = cacheName + key.toString();
        byte[] rawKey;
        try {
            rawKey = keySerializer.serialize(keyString);
        } catch (Exception e) {
            log.warn("Failed to serialize the cache key: {}", key, e);
            throw new RuntimeException(e);
        }
        if (rawKey == null) {
            log.warn("Failed to serialize the cache key: {}", key);
            throw new IllegalArgumentException("Failed to serialize the cache key!");
        }
        return rawKey;
    }

    protected byte[] getRawValue(V value) {
        if (value == null) {
            return BINARY_NULL_VALUE;
        } else {
            try {
                long startTime = System.nanoTime();
                var bytes = valueSerializer.serialize(value);
                fstStatsService.recordEncodeTime(value.getClass(), startTime);
                fstStatsService.incrementEncode(value.getClass());
                return bytes;
            } catch (Exception e) {
                log.warn("Failed to serialize the cache value: {}", value, e);
                throw new RuntimeException(e);
            }
        }
    }

    public void put(RedisConnection connection, K key, V value, RedisStringCommands.SetOption setOption) {
        if (!cacheEnabled) {
            return;
        }
        byte[] rawKey = getRawKey(key);
        put(connection, rawKey, value, setOption);
    }

    public void put(RedisConnection connection, byte[] rawKey, V value, RedisStringCommands.SetOption setOption) {
        byte[] rawValue = getRawValue(value);
        connection.stringCommands().set(rawKey, rawValue, this.cacheTtl, setOption);
    }

    protected void loadLuaScript(byte[] expectedSha, byte[] luaScript) {
        try (var connection = getConnection(expectedSha)) {
            log.debug("Loading LUA with expected SHA [{}], connection [{}]", new String(expectedSha), connection.getNativeConnection());
            String actualSha = connection.scriptingCommands().scriptLoad(luaScript);
            if (!Arrays.equals(expectedSha, StringRedisSerializer.UTF_8.serialize(actualSha))) {
                String message = String.format("SHA for LUA script wrong! Expected [%s], but actual [%s], connection [%s]",
                        new String(expectedSha), actualSha, connection.getNativeConnection());
                throw new IllegalStateException(message);
            }
        }
    }

    protected void executeScript(RedisConnection connection, byte[] scriptSha, byte[] luaScript, ReturnType returnType, int numKeys, byte[]... keysAndArgs) {
        try {
            connection.scriptingCommands().evalSha(scriptSha, returnType, numKeys, keysAndArgs);
        } catch (InvalidDataAccessApiUsageException ignored) {
            log.debug("Loading LUA [{}]", connection.getNativeConnection());
            connection.scriptingCommands().scriptLoad(luaScript);
            try {
                connection.scriptingCommands().evalSha(scriptSha, returnType, numKeys, keysAndArgs);
            } catch (InvalidDataAccessApiUsageException exception) {
                log.warn("Slowly executing eval instead of fast evalSha", exception);
                connection.scriptingCommands().eval(luaScript, returnType, numKeys, keysAndArgs);
            }
        }
    }

}
