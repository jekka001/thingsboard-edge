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
package org.thingsboard.server.service.job;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.common.util.ThingsBoardExecutors;
import org.thingsboard.rule.engine.api.JobManager;
import org.thingsboard.rule.engine.api.NotificationCenter;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.JobId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.job.Job;
import org.thingsboard.server.common.data.job.JobResult;
import org.thingsboard.server.common.data.job.JobStatus;
import org.thingsboard.server.common.data.job.JobType;
import org.thingsboard.server.common.data.job.task.Task;
import org.thingsboard.server.common.data.job.task.TaskResult;
import org.thingsboard.server.common.data.notification.info.GeneralNotificationInfo;
import org.thingsboard.server.common.data.notification.targets.platform.TenantAdministratorsFilter;
import org.thingsboard.server.common.data.notification.template.NotificationTemplate;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.dao.job.JobService;
import org.thingsboard.server.dao.notification.DefaultNotifications;
import org.thingsboard.server.gen.transport.TransportProtos.TaskProto;
import org.thingsboard.server.queue.TbQueueCallback;
import org.thingsboard.server.queue.TbQueueMsgMetadata;
import org.thingsboard.server.queue.TbQueueProducer;
import org.thingsboard.server.queue.common.TbProtoQueueMsg;
import org.thingsboard.server.queue.discovery.PartitionService;
import org.thingsboard.server.queue.settings.TasksQueueConfig;
import org.thingsboard.server.queue.task.JobStatsService;
import org.thingsboard.server.queue.task.TaskProducerQueueFactory;
import org.thingsboard.server.queue.util.TbCoreComponent;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

@TbCoreComponent
@Component
@Slf4j
public class DefaultJobManager implements JobManager {

    private final JobService jobService;
    private final JobStatsService jobStatsService;
    private final NotificationCenter notificationCenter;
    private final PartitionService partitionService;
    private final TasksQueueConfig queueConfig;
    private final Map<JobType, JobProcessor> jobProcessors;
    private final Map<JobType, TbQueueProducer<TbProtoQueueMsg<TaskProto>>> taskProducers;
    private final ExecutorService executor;

    public DefaultJobManager(JobService jobService, JobStatsService jobStatsService, NotificationCenter notificationCenter,
                             PartitionService partitionService, TaskProducerQueueFactory queueFactory, TasksQueueConfig queueConfig,
                             List<JobProcessor> jobProcessors) {
        this.jobService = jobService;
        this.jobStatsService = jobStatsService;
        this.notificationCenter = notificationCenter;
        this.partitionService = partitionService;
        this.queueConfig = queueConfig;
        this.jobProcessors = jobProcessors.stream().collect(Collectors.toMap(JobProcessor::getType, Function.identity()));
        this.taskProducers = Arrays.stream(JobType.values()).collect(Collectors.toMap(Function.identity(), queueFactory::createTaskProducer));
        this.executor = ThingsBoardExecutors.newWorkStealingPool(Math.max(4, Runtime.getRuntime().availableProcessors()), getClass());
    }

    @Override
    public Job submitJob(Job job) {
        log.debug("Submitting job: {}", job);
        return jobService.saveJob(job.getTenantId(), job);
    }

    @Override
    public void onJobUpdate(Job job) {
        JobStatus status = job.getStatus();
        switch (status) {
            case PENDING -> {
                executor.execute(() -> {
                    try {
                        processJob(job);
                    } catch (Throwable e) {
                        log.error("Failed to process job update: {}", job, e);
                    }
                });
            }
            case COMPLETED, FAILED -> {
                executor.execute(() -> {
                    try {
                        if (status == JobStatus.COMPLETED) {
                            getJobProcessor(job.getType()).onJobCompleted(job);
                        }
                        sendJobFinishedNotification(job);
                    } catch (Throwable e) {
                        log.error("Failed to process job update: {}", job, e);
                    }
                });
            }
        }
    }

    private void processJob(Job job) {
        TenantId tenantId = job.getTenantId();
        JobId jobId = job.getId();
        try {
            JobProcessor processor = getJobProcessor(job.getType());
            List<TaskResult> toReprocess = job.getConfiguration().getToReprocess();
            if (toReprocess == null) {
                int tasksCount = processor.process(job, this::submitTask);
                log.info("[{}][{}][{}] Submitted {} tasks", tenantId, jobId, job.getType(), tasksCount);
                jobStatsService.reportAllTasksSubmitted(tenantId, jobId, tasksCount);
            } else {
                processor.reprocess(job, toReprocess, this::submitTask);
                log.info("[{}][{}][{}] Submitted {} tasks for reprocessing", tenantId, jobId, job.getType(), toReprocess.size());
            }
        } catch (Throwable e) {
            log.error("[{}][{}][{}] Failed to submit tasks", tenantId, jobId, job.getType(), e);
            jobService.markAsFailed(tenantId, jobId, e.getMessage());
        }
    }

    @Override
    public void cancelJob(TenantId tenantId, JobId jobId) {
        log.info("[{}][{}] Cancelling job", tenantId, jobId);
        jobService.cancelJob(tenantId, jobId);
    }

    @Override
    public void reprocessJob(TenantId tenantId, JobId jobId) {
        log.info("[{}][{}] Reprocessing job", tenantId, jobId);
        Job job = jobService.findJobById(tenantId, jobId);
        if (job.getStatus() != JobStatus.FAILED) {
            throw new IllegalArgumentException("Job is not failed");
        }

        JobResult result = job.getResult();
        if (result.getGeneralError() != null) {
            job.presetResult();
        } else {
            List<TaskResult> taskFailures = result.getResults().stream()
                    .filter(taskResult -> !taskResult.isSuccess() && !taskResult.isDiscarded())
                    .toList();
            if (result.getFailedCount() > taskFailures.size()) {
                throw new IllegalArgumentException("Reprocessing not allowed since there are too many failures (more than " + taskFailures.size() + ")");
            }
            result.setFailedCount(0);
            result.setResults(result.getResults().stream()
                    .filter(TaskResult::isSuccess)
                    .toList());
            job.getConfiguration().setToReprocess(taskFailures);
        }
        job.getConfiguration().setTasksKey(UUID.randomUUID().toString());
        jobService.saveJob(tenantId, job);
    }

    private void submitTask(Task<?> task) {
        if (ObjectUtils.anyNull(task.getTenantId(), task.getJobId(), task.getKey())) {
            throw new IllegalArgumentException("Task " + task + " missing required fields");
        }

        log.debug("[{}][{}] Submitting task: {}", task.getTenantId(), task.getJobId(), task);
        TaskProto taskProto = TaskProto.newBuilder()
                .setValue(JacksonUtil.toString(task))
                .build();

        TbQueueProducer<TbProtoQueueMsg<TaskProto>> producer = taskProducers.get(task.getJobType());
        EntityId entityId = null;
        if (queueConfig.getPartitioningStrategy().equals("entity")) {
            entityId = task.getEntityId();
        }
        if (entityId == null) {
            entityId = task.getTenantId();
        }
        TopicPartitionInfo tpi = partitionService.resolve(ServiceType.TASK_PROCESSOR, task.getJobType().name(), task.getTenantId(), entityId);
        producer.send(tpi, new TbProtoQueueMsg<>(UUID.randomUUID(), taskProto), new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                log.trace("Submitted task to {}: {}", tpi, taskProto);
            }

            @Override
            public void onFailure(Throwable t) {
                log.warn("Failed to submit task: {}", task, t);
            }
        });
    }

    private void sendJobFinishedNotification(Job job) {
        NotificationTemplate template = DefaultNotifications.DefaultNotification.builder()
                .name("Job finished")
                .subject("${type} task ${status}")
                .text("${description} ${status}: ${result}")
                .build().toTemplate();
        GeneralNotificationInfo info = new GeneralNotificationInfo(Map.of(
                "type", job.getType().getTitle(),
                "description", job.getDescription(),
                "status", job.getStatus().name().toLowerCase(),
                "result", job.getResult().getDescription()
        ));
        // todo: button to see details (forward to jobs page)
        notificationCenter.sendGeneralWebNotification(job.getTenantId(), new TenantAdministratorsFilter(), template, info);
    }

    private JobProcessor getJobProcessor(JobType jobType) {
        return jobProcessors.get(jobType);
    }

    @PreDestroy
    private void destroy() {
        executor.shutdownNow();
    }

}
