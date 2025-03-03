/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.server.utils;

import lombok.SneakyThrows;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteOptions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;

public class TbRocksDb {

    protected final String path;
    private final WriteOptions writeOptions;
    protected final RocksDB db;

    static {
        RocksDB.loadLibrary();
    }

    public TbRocksDb(String path, Options dbOptions, WriteOptions writeOptions) throws Exception {
        this.path = path;
        this.writeOptions = writeOptions;
        Files.createDirectories(Path.of(path).getParent());
        this.db = RocksDB.open(dbOptions, path);
    }

    @SneakyThrows
    public void put(String key, byte[] value) {
        db.put(writeOptions, key.getBytes(StandardCharsets.UTF_8), value);
    }

    public void forEach(BiConsumer<String, byte[]> processor) {
        try (RocksIterator iterator = db.newIterator()) {
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                String key = new String(iterator.key(), StandardCharsets.UTF_8);
                processor.accept(key, iterator.value());
            }
        }
    }

    @SneakyThrows
    public void delete(String key) {
        db.delete(writeOptions, key.getBytes(StandardCharsets.UTF_8));
    }

    public void close() {
        if (db != null) {
            db.close();
        }
    }

}