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
package org.thingsboard.server.common.util;

import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.BaseAttributeKvEntry;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.BooleanDataEntry;
import org.thingsboard.server.common.data.kv.DataType;
import org.thingsboard.server.common.data.kv.DoubleDataEntry;
import org.thingsboard.server.common.data.kv.JsonDataEntry;
import org.thingsboard.server.common.data.kv.KvEntry;
import org.thingsboard.server.common.data.kv.LongDataEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.gen.transport.TransportProtos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class KvProtoUtil {

    private static final DataType[] dataTypeByProtoNumber;

    static {
        int arraySize = Arrays.stream(DataType.values()).mapToInt(DataType::getProtoNumber).max().orElse(0);
        dataTypeByProtoNumber = new DataType[arraySize + 1];
        Arrays.stream(DataType.values()).forEach(dataType -> dataTypeByProtoNumber[dataType.getProtoNumber()] = dataType);
    }

    public static List<AttributeKvEntry> toAttributeKvList(List<TransportProtos.TsKvProto> dataList) {
        List<AttributeKvEntry> result = new ArrayList<>(dataList.size());
        dataList.forEach(proto -> result.add(new BaseAttributeKvEntry(fromTsKvProto(proto.getKv()), proto.getTs())));
        return result;
    }

    public static List<TransportProtos.TsKvProto> attrToTsKvProtos(List<AttributeKvEntry> result) {
        List<TransportProtos.TsKvProto> clientAttributes;
        if (result == null || result.isEmpty()) {
            clientAttributes = Collections.emptyList();
        } else {
            clientAttributes = new ArrayList<>(result.size());
            for (AttributeKvEntry attrEntry : result) {
                clientAttributes.add(toTsKvProto(attrEntry.getLastUpdateTs(), attrEntry));
            }
        }
        return clientAttributes;
    }

    public static List<TransportProtos.TsKvProto> toTsKvProtoList(List<TsKvEntry> result) {
        List<TransportProtos.TsKvProto> ts;
        if (result == null || result.isEmpty()) {
            ts = Collections.emptyList();
        } else {
            ts = new ArrayList<>(result.size());
            for (TsKvEntry attrEntry : result) {
                ts.add(toTsKvProto(attrEntry.getTs(), attrEntry));
            }
        }
        return ts;
    }

    public static List<TsKvEntry> fromTsKvProtoList(List<TransportProtos.TsKvProto> dataList) {
        List<TsKvEntry> result = new ArrayList<>(dataList.size());
        dataList.forEach(proto -> result.add(new BasicTsKvEntry(proto.getTs(), fromTsKvProto(proto.getKv()))));
        return result;
    }

    public static TransportProtos.TsKvProto toTsKvProto(long ts, KvEntry kvEntry) {
        return TransportProtos.TsKvProto.newBuilder().setTs(ts)
                .setKv(KvProtoUtil.toKeyValueTypeProto(kvEntry)).build();
    }

    public static TsKvEntry fromTsKvProto(TransportProtos.TsKvProto proto) {
        return new BasicTsKvEntry(proto.getTs(), fromTsKvProto(proto.getKv()));
    }

    public static TransportProtos.KeyValueProto toKeyValueTypeProto(KvEntry kvEntry) {
        TransportProtos.KeyValueProto.Builder builder = TransportProtos.KeyValueProto.newBuilder();
        builder.setKey(kvEntry.getKey());
        builder.setType(toKeyValueTypeProto(kvEntry.getDataType()));
        switch (kvEntry.getDataType()) {
            case BOOLEAN -> kvEntry.getBooleanValue().ifPresent(builder::setBoolV);
            case LONG -> kvEntry.getLongValue().ifPresent(builder::setLongV);
            case DOUBLE -> kvEntry.getDoubleValue().ifPresent(builder::setDoubleV);
            case JSON -> kvEntry.getJsonValue().ifPresent(builder::setJsonV);
            case STRING -> kvEntry.getStrValue().ifPresent(builder::setStringV);
        }
        return builder.build();
    }

    public static KvEntry fromTsKvProto(TransportProtos.KeyValueProto proto) {
        return switch (fromKeyValueTypeProto(proto.getType())) {
            case BOOLEAN -> new BooleanDataEntry(proto.getKey(), proto.getBoolV());
            case LONG -> new LongDataEntry(proto.getKey(), proto.getLongV());
            case DOUBLE -> new DoubleDataEntry(proto.getKey(), proto.getDoubleV());
            case STRING -> new StringDataEntry(proto.getKey(), proto.getStringV());
            case JSON -> new JsonDataEntry(proto.getKey(), proto.getJsonV());
        };
    }

    public static TransportProtos.TsKvProto.Builder toTsKvProtoBuilder(long ts, KvEntry kvEntry) {
        return TransportProtos.TsKvProto.newBuilder().setTs(ts).setKv(KvProtoUtil.toKeyValueTypeProto(kvEntry));
    }

    public static List<TsKvEntry> fromTsValueProtoList(List<TransportProtos.TsKvProto> dataList) {
        List<TsKvEntry> result = new ArrayList<>(dataList.size());
        dataList.forEach(proto -> result.add(new BasicTsKvEntry(proto.getTs(), fromTsKvProto(proto.getKv()))));
        return result;
    }

    public static List<TsKvEntry> fromTsValueProtoList(String key, List<TransportProtos.TsValueProto> dataList) {
        List<TsKvEntry> result = new ArrayList<>(dataList.size());
        dataList.forEach(proto -> result.add(new BasicTsKvEntry(proto.getTs(), fromTsValueProto(key, proto))));
        return result;
    }

    public static TransportProtos.TsValueProto toTsValueProto(long ts, KvEntry attr) {
        TransportProtos.TsValueProto.Builder dataBuilder = TransportProtos.TsValueProto.newBuilder();
        dataBuilder.setTs(ts);
        dataBuilder.setType(toKeyValueTypeProto(attr.getDataType()));
        switch (attr.getDataType()) {
            case BOOLEAN -> attr.getBooleanValue().ifPresent(dataBuilder::setBoolV);
            case LONG -> attr.getLongValue().ifPresent(dataBuilder::setLongV);
            case DOUBLE -> attr.getDoubleValue().ifPresent(dataBuilder::setDoubleV);
            case JSON -> attr.getJsonValue().ifPresent(dataBuilder::setJsonV);
            case STRING -> attr.getStrValue().ifPresent(dataBuilder::setStringV);
        }
        return dataBuilder.build();
    }

    public static KvEntry fromTsValueProto(String key, TransportProtos.TsValueProto proto) {
        return switch (fromKeyValueTypeProto(proto.getType())) {
            case BOOLEAN -> new BooleanDataEntry(key, proto.getBoolV());
            case LONG -> new LongDataEntry(key, proto.getLongV());
            case DOUBLE -> new DoubleDataEntry(key, proto.getDoubleV());
            case STRING -> new StringDataEntry(key, proto.getStringV());
            case JSON -> new JsonDataEntry(key, proto.getJsonV());
        };
    }

    public static TransportProtos.KeyValueType toKeyValueTypeProto(DataType dataType) {
        return TransportProtos.KeyValueType.forNumber(dataType.getProtoNumber());
    }

    public static DataType fromKeyValueTypeProto(TransportProtos.KeyValueType keyValueType) {
        return dataTypeByProtoNumber[keyValueType.getNumber()];
    }

}
