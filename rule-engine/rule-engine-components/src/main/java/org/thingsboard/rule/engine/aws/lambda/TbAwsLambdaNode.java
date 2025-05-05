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
package org.thingsboard.rule.engine.aws.lambda;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.lambda.AWSLambdaAsync;
import com.amazonaws.services.lambda.AWSLambdaAsyncClientBuilder;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.rule.engine.api.RuleNode;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.rule.engine.external.TbAbstractExternalNode;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.exception.DataValidationException;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static org.thingsboard.server.dao.service.ConstraintValidator.validateFields;

@Slf4j
@RuleNode(
        type = ComponentType.EXTERNAL,
        name = "aws lambda",
        configClazz = TbAwsLambdaNodeConfiguration.class,
        nodeDescription = "Publish message to the AWS Lambda",
        nodeDetails = "Publishes messages to AWS Lambda, a service that lets you run code " +
                "without provisioning or managing servers. " +
                "It sends messages using a RequestResponse invocation type. " +
                "The node uses a pre-configured client and specified function to run.<br><br>" +
                "Output connections: <code>Success</code>, <code>Failure</code>.",
        configDirective = "tbExternalNodeLambdaConfig",
        iconUrl = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgd2lkdGg9IjQ4IiBoZWlnaHQ9IjQ4Ij48cGF0aCBkPSJNMTMuMjMgMTAuNTZWMTBjLTEuOTQgMC0zLjk5LjM5LTMuOTkgMi42NyAwIDEuMTYuNjEgMS45NSAxLjYzIDEuOTUuNzYgMCAxLjQzLS40NyAxLjg2LTEuMjIuNTItLjkzLjUtMS44LjUtMi44NG0yLjcgNi41M2MtLjE4LjE2LS40My4xNy0uNjMuMDYtLjg5LS43NC0xLjA1LTEuMDgtMS41NC0xLjc5LTEuNDcgMS41LTIuNTEgMS45NS00LjQyIDEuOTUtMi4yNSAwLTQuMDEtMS4zOS00LjAxLTQuMTcgMC0yLjE4IDEuMTctMy42NCAyLjg2LTQuMzggMS40Ni0uNjQgMy40OS0uNzYgNS4wNC0uOTNWNy41YzAtLjY2LjA1LTEuNDEtLjMzLTEuOTYtLjMyLS40OS0uOTUtLjctMS41LS43LTEuMDIgMC0xLjkzLjUzLTIuMTUgMS42MS0uMDUuMjQtLjI1LjQ4LS40Ny40OWwtMi42LS4yOGMtLjIyLS4wNS0uNDYtLjIyLS40LS41Ni42LTMuMTUgMy40NS00LjEgNi00LjEgMS4zIDAgMyAuMzUgNC4wMyAxLjMzQzE3LjExIDQuNTUgMTcgNi4xOCAxNyA3Ljk1djQuMTdjMCAxLjI1LjUgMS44MSAxIDIuNDguMTcuMjUuMjEuNTQgMCAuNzFsLTIuMDYgMS43OGgtLjAxIj48L3BhdGg+PHBhdGggZD0iTTIwLjE2IDE5LjU0QzE4IDIxLjE0IDE0LjgyIDIyIDEyLjEgMjJjLTMuODEgMC03LjI1LTEuNDEtOS44NS0zLjc2LS4yLS4xOC0uMDItLjQzLjI1LS4yOSAyLjc4IDEuNjMgNi4yNSAyLjYxIDkuODMgMi42MSAyLjQxIDAgNS4wNy0uNSA3LjUxLTEuNTMuMzctLjE2LjY2LjI0LjMyLjUxIj48L3BhdGg+PHBhdGggZD0iTTIxLjA3IDE4LjVjLS4yOC0uMzYtMS44NS0uMTctMi41Ny0uMDgtLjE5LjAyLS4yMi0uMTYtLjAzLS4zIDEuMjQtLjg4IDMuMjktLjYyIDMuNTMtLjMzLjI0LjMtLjA3IDIuMzUtMS4yNCAzLjMyLS4xOC4xNi0uMzUuMDctLjI2LS4xMS4yNi0uNjcuODUtMi4xNC41Ny0yLjV6Ij48L3BhdGg+PC9zdmc+",
        hasSecrets = true
)
public class TbAwsLambdaNode extends TbAbstractExternalNode {

    private TbAwsLambdaNodeConfiguration config;
    private AWSLambdaAsync client;

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        config = TbNodeUtils.convert(configuration, TbAwsLambdaNodeConfiguration.class);
        String errorPrefix = "'" + ctx.getSelf().getName() + "' node configuration is invalid: ";
        try {
            validateFields(config, errorPrefix);
            AWSCredentials awsCredentials = new BasicAWSCredentials(config.getAccessKey(), config.getSecretKey());
            client = AWSLambdaAsyncClientBuilder.standard()
                    .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                    .withRegion(config.getRegion())
                    .withClientConfiguration(new ClientConfiguration()
                            .withConnectionTimeout((int) TimeUnit.SECONDS.toMillis(config.getConnectionTimeout()))
                            .withRequestTimeout((int) TimeUnit.SECONDS.toMillis(config.getRequestTimeout())))
                    .build();
        } catch (DataValidationException e) {
            throw new TbNodeException(e, true);
        } catch (Exception e) {
            throw new TbNodeException(e);
        }
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) {
        var tbMsg = ackIfNeeded(ctx, msg);
        String functionName = TbNodeUtils.processPattern(config.getFunctionName(), tbMsg);
        String qualifier = StringUtils.isBlank(config.getQualifier()) ?
                TbAwsLambdaNodeConfiguration.DEFAULT_QUALIFIER :
                TbNodeUtils.processPattern(config.getQualifier(), tbMsg);
        InvokeRequest request = toRequest(tbMsg.getData(), functionName, qualifier);
        client.invokeAsync(request, new AsyncHandler<>() {
            @Override
            public void onError(Exception e) {
                tellFailure(ctx, tbMsg, e);
            }

            @Override
            public void onSuccess(InvokeRequest request, InvokeResult invokeResult) {
                try {
                    if (config.isTellFailureIfFuncThrowsExc() && invokeResult.getFunctionError() != null) {
                        throw new RuntimeException(getPayload(invokeResult));
                    }
                    tellSuccess(ctx, getResponseMsg(tbMsg, invokeResult));
                } catch (Exception e) {
                    tellFailure(ctx, processException(tbMsg, invokeResult, e), e);
                }
            }
        });
    }

    private InvokeRequest toRequest(String requestBody, String functionName, String qualifier) {
        return new InvokeRequest()
                .withFunctionName(functionName)
                .withPayload(requestBody)
                .withQualifier(qualifier);
    }

    private String getPayload(InvokeResult invokeResult) {
        ByteBuffer buf = invokeResult.getPayload();
        if (buf == null) {
            throw new RuntimeException("Payload from result of AWS Lambda function execution is null.");
        }
        byte[] responseBytes = new byte[buf.remaining()];
        buf.get(responseBytes);
        return new String(responseBytes);
    }

    private TbMsg getResponseMsg(TbMsg originalMsg, InvokeResult invokeResult) {
        TbMsgMetaData metaData = originalMsg.getMetaData().copy();
        metaData.putValue("requestId", invokeResult.getSdkResponseMetadata().getRequestId());
        String data = getPayload(invokeResult);
        return originalMsg.transform()
                .metaData(metaData)
                .data(data)
                .build();
    }

    private TbMsg processException(TbMsg origMsg, InvokeResult invokeResult, Throwable t) {
        TbMsgMetaData metaData = origMsg.getMetaData().copy();
        metaData.putValue("error", t.getClass() + ": " + t.getMessage());
        metaData.putValue("requestId", invokeResult.getSdkResponseMetadata().getRequestId());
        return origMsg.transform()
                .metaData(metaData)
                .build();
    }

    @Override
    public void destroy() {
        if (client != null) {
            try {
                client.shutdown();
            } catch (Exception e) {
                log.error("Failed to shutdown Lambda client during destroy", e);
            }
        }
    }
}
