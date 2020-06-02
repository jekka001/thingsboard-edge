/*
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
const {logLevel, Kafka} = require('kafkajs');

const config = require('config'),
    JsInvokeMessageProcessor = require('../api/jsInvokeMessageProcessor'),
    logger = require('../config/logger')._logger('kafkaTemplate'),
    KafkaJsWinstonLogCreator = require('../config/logger').KafkaJsWinstonLogCreator;
const replicationFactor = config.get('kafka.replication_factor');
const topicProperties = config.get('kafka.topic_properties');

let kafkaClient;
let kafkaAdmin;
let consumer;
let producer;

const topics = [];
const configEntries = [];

function KafkaProducer() {
    this.send = async (responseTopic, scriptId, rawResponse, headers) => {

        if (!topics.includes(responseTopic)) {
            let createResponseTopicResult = await createTopic(responseTopic);
            topics.push(responseTopic);
            if (createResponseTopicResult) {
                logger.info('Created new topic: %s', requestTopic);
            }
        }

        return producer.send(
            {
                topic: responseTopic,
                messages: [
                    {
                        key: scriptId,
                        value: rawResponse,
                        headers: headers.data
                    }
                ]
            });
    }
}

(async () => {
    try {
        logger.info('Starting ThingsBoard JavaScript Executor Microservice...');

        const kafkaBootstrapServers = config.get('kafka.bootstrap.servers');
        const requestTopic = config.get('request_topic');

        logger.info('Kafka Bootstrap Servers: %s', kafkaBootstrapServers);
        logger.info('Kafka Requests Topic: %s', requestTopic);

        kafkaClient = new Kafka({
            brokers: kafkaBootstrapServers.split(','),
            logLevel: logLevel.INFO,
            logCreator: KafkaJsWinstonLogCreator
        });

        parseTopicProperties();

        kafkaAdmin = kafkaClient.admin();
        await kafkaAdmin.connect();

        let createRequestTopicResult = await createTopic(requestTopic);

        if (createRequestTopicResult) {
            logger.info('Created new topic: %s', requestTopic);
        }

        consumer = kafkaClient.consumer({groupId: 'js-executor-group'});
        producer = kafkaClient.producer();
        const messageProcessor = new JsInvokeMessageProcessor(new KafkaProducer());
        await consumer.connect();
        await producer.connect();
        await consumer.subscribe({topic: requestTopic});

        logger.info('Started ThingsBoard JavaScript Executor Microservice.');
        await consumer.run({
            eachMessage: async ({topic, partition, message}) => {
                let headers = message.headers;
                let key = message.key;
                let msg = {};
                msg.key = key.toString('utf8');
                msg.data = message.value;
                msg.headers = {data: headers};
                messageProcessor.onJsInvokeMessage(msg);
            },
        });

    } catch (e) {
        logger.error('Failed to start ThingsBoard JavaScript Executor Microservice: %s', e.message);
        logger.error(e.stack);
        exit(-1);
    }
})();

function createTopic(topic) {
    return kafkaAdmin.createTopics({
        topics: [{
            topic: topic,
            replicationFactor: replicationFactor,
            configEntries: configEntries
        }]
    });
}

function parseTopicProperties() {
    const props = topicProperties.split(';');
    props.forEach(p => {
        const delimiterPosition = p.indexOf(':');
        configEntries.push({name: p.substring(0, delimiterPosition), value: p.substring(delimiterPosition + 1)});
    });
}

process.on('exit', () => {
    exit(0);
});

async function exit(status) {
    logger.info('Exiting with status: %d ...', status);

    if (kafkaAdmin) {
        logger.info('Stopping Kafka Admin...');
        await kafkaAdmin.disconnect();
        logger.info('Kafka Admin stopped.');
    }

    if (consumer) {
        logger.info('Stopping Kafka Consumer...');
        let _consumer = consumer;
        consumer = null;
        try {
            await _consumer.disconnect();
            logger.info('Kafka Consumer stopped.');
            await disconnectProducer();
            process.exit(status);
        } catch (e) {
            logger.info('Kafka Consumer stop error.');
            await disconnectProducer();
            process.exit(status);
        }
    } else {
        process.exit(status);
    }
}

async function disconnectProducer() {
    if (producer) {
        logger.info('Stopping Kafka Producer...');
        var _producer = producer;
        producer = null;
        try {
            await _producer.disconnect();
            logger.info('Kafka Producer stopped.');
        } catch (e) {
            logger.info('Kafka Producer stop error.');
        }
    }
}
