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

import org.assertj.core.api.ThrowingConsumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;
import org.thingsboard.rule.engine.api.JobManager;
import org.thingsboard.server.common.data.id.JobId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.job.DummyJobConfiguration;
import org.thingsboard.server.common.data.job.Job;
import org.thingsboard.server.common.data.job.JobFilter;
import org.thingsboard.server.common.data.job.JobResult;
import org.thingsboard.server.common.data.job.JobStatus;
import org.thingsboard.server.common.data.job.JobType;
import org.thingsboard.server.common.data.job.task.DummyTaskResult;
import org.thingsboard.server.common.data.job.task.DummyTaskResult.DummyTaskFailure;
import org.thingsboard.server.common.data.notification.Notification;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.controller.AbstractControllerTest;
import org.thingsboard.server.dao.job.JobDao;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.queue.task.JobStatsService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DaoSqlTest
@TestPropertySource(properties = {
        "queue.tasks.stats.processing_interval=0"
})
public class JobManagerTest extends AbstractControllerTest {

    @Autowired
    private JobManager jobManager;

    @SpyBean
    private TestTaskProcessor taskProcessor;

    @SpyBean
    private JobStatsService jobStatsService;

    @Autowired
    private JobDao jobDao;

    @Before
    public void setUp() throws Exception {
        loginTenantAdmin();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testSubmitJob_allTasksSuccessful() {
        int tasksCount = 5;
        JobId jobId = jobManager.submitJob(Job.builder()
                .tenantId(tenantId)
                .type(JobType.DUMMY)
                .key("test-job")
                .description("Test job")
                .configuration(DummyJobConfiguration.builder()
                        .successfulTasksCount(tasksCount)
                        .taskProcessingTimeMs(1000)
                        .build())
                .build()).getId();

        await().atMost(TIMEOUT, TimeUnit.SECONDS).untilAsserted(() -> {
            Job job = findJobById(jobId);
            assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
            assertThat(job.getResult().getSuccessfulCount()).isBetween(1, tasksCount - 1);
            assertThat(job.getResult().getTotalCount()).isEqualTo(tasksCount);
        });
        await().atMost(TIMEOUT, TimeUnit.SECONDS).untilAsserted(() -> {
            Job job = findJobById(jobId);
            assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
            assertThat(job.getResult().getSuccessfulCount()).isEqualTo(tasksCount);
            assertThat(job.getResult().getResults()).isEmpty();
            assertThat(job.getResult().getCompletedCount()).isEqualTo(tasksCount);
        });

        checkJobNotification(notification -> {
            assertThat(notification.getSubject()).isEqualTo("Dummy job task completed");
            assertThat(notification.getText()).isEqualTo("Test job completed: 5/5 successful, 0 failed");
        });
    }

    @Test
    public void testSubmitJob_someTasksPermanentlyFailed() {
        int successfulTasks = 3;
        int failedTasks = 2;
        JobId jobId = jobManager.submitJob(Job.builder()
                .tenantId(tenantId)
                .type(JobType.DUMMY)
                .key("test-job")
                .description("Test job")
                .configuration(DummyJobConfiguration.builder()
                        .successfulTasksCount(successfulTasks)
                        .failedTasksCount(failedTasks)
                        .errors(List.of("error1", "error2", "error3"))
                        .retries(2)
                        .taskProcessingTimeMs(100)
                        .build())
                .build()).getId();

        await().atMost(TIMEOUT, TimeUnit.SECONDS).untilAsserted(() -> {
            Job job = findJobById(jobId);
            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
            JobResult jobResult = job.getResult();
            assertThat(jobResult.getSuccessfulCount()).isEqualTo(successfulTasks);
            assertThat(jobResult.getFailedCount()).isEqualTo(failedTasks);
            assertThat(jobResult.getTotalCount()).isEqualTo(successfulTasks + failedTasks);
            assertThat(getFailures(jobResult)).hasSize(2).allSatisfy(failure -> {
                assertThat(failure.getError()).isEqualTo("error3"); // last error
            });
            assertThat(jobResult.getCompletedCount()).isEqualTo(jobResult.getTotalCount());
        });

        checkJobNotification(notification -> {
            assertThat(notification.getSubject()).isEqualTo("Dummy job task failed");
            assertThat(notification.getText()).isEqualTo("Test job failed: 3/5 successful, 2 failed");
        });
    }

    @Test
    public void testSubmitJob_taskTimeout() {
        JobId jobId = jobManager.submitJob(Job.builder()
                .tenantId(tenantId)
                .type(JobType.DUMMY)
                .key("test-job")
                .description("Test job")
                .configuration(DummyJobConfiguration.builder()
                        .successfulTasksCount(1)
                        .taskProcessingTimeMs(5000) // bigger than DummyTaskProcessor.getTaskProcessingTimeout()
                        .build())
                .build()).getId();

        await().atMost(TIMEOUT, TimeUnit.SECONDS).untilAsserted(() -> {
            Job job = findJobById(jobId);
            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
            JobResult jobResult = job.getResult();
            assertThat(jobResult.getFailedCount()).isEqualTo(1);
            assertThat(((DummyTaskResult) jobResult.getResults().get(0)).getFailure().getError()).isEqualTo("Timeout after 2000 ms"); // last error
        });
    }

    @Test
    public void testCancelJob_whileRunning() throws Exception {
        int tasksCount = 100;
        JobId jobId = jobManager.submitJob(Job.builder()
                .tenantId(tenantId)
                .type(JobType.DUMMY)
                .key("test-job")
                .description("test job")
                .configuration(DummyJobConfiguration.builder()
                        .successfulTasksCount(tasksCount)
                        .taskProcessingTimeMs(100)
                        .build())
                .build()).getId();

        Thread.sleep(500);
        cancelJob(jobId);
        await().atMost(TIMEOUT, TimeUnit.SECONDS).untilAsserted(() -> {
            Job job = findJobById(jobId);
            assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
            assertThat(job.getResult().getSuccessfulCount()).isBetween(1, tasksCount - 1);
            assertThat(job.getResult().getDiscardedCount()).isBetween(1, tasksCount - 1);
            assertThat(job.getResult().getTotalCount()).isEqualTo(tasksCount);
            assertThat(job.getResult().getCompletedCount()).isEqualTo(tasksCount);
        });
    }

    @Test
    public void testCancelJob_simulateTaskProcessorRestart() throws Exception {
        int tasksCount = 10;
        JobId jobId = jobManager.submitJob(Job.builder()
                .tenantId(tenantId)
                .type(JobType.DUMMY)
                .key("test-job")
                .description("test job")
                .configuration(DummyJobConfiguration.builder()
                        .successfulTasksCount(tasksCount)
                        .taskProcessingTimeMs(500)
                        .build())
                .build()).getId();

        // simulate cancelled jobs are forgotten
        AtomicInteger cancellationRenotifyAttempt = new AtomicInteger(0);
        doAnswer(inv -> {
            if (cancellationRenotifyAttempt.incrementAndGet() >= 5) {
                inv.callRealMethod();
            }
            return null;
        }).when(taskProcessor).addToDiscarded(any()); // ignoring cancellation event,
        cancelJob(jobId);

        await().atMost(TIMEOUT, TimeUnit.SECONDS).untilAsserted(() -> {
            Job job = findJobById(jobId);
            assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
            assertThat(job.getResult().getSuccessfulCount()).isBetween(1, tasksCount - 1);
            assertThat(job.getResult().getDiscardedCount()).isBetween(1, tasksCount - 1);
            assertThat(job.getResult().getTotalCount()).isEqualTo(tasksCount);
            assertThat(job.getResult().getCompletedCount()).isEqualTo(tasksCount);
        });
    }

    @Test
    public void whenTenantIsDeleted_thenCancelAllTheJobs() throws Exception {
        loginSysAdmin();
        createDifferentTenant();

        TenantId tenantId = this.differentTenantId;
        jobManager.submitJob(Job.builder()
                .tenantId(tenantId)
                .type(JobType.DUMMY)
                .key("test-job")
                .description("test job")
                .configuration(DummyJobConfiguration.builder()
                        .successfulTasksCount(1000)
                        .taskProcessingTimeMs(500)
                        .build())
                .build());

        Thread.sleep(2000);
        deleteDifferentTenant();
        Mockito.reset(jobStatsService);

        Thread.sleep(3000);
        verify(jobStatsService, never()).reportTaskResult(any(), any(), any());
        assertThat(jobDao.findByTenantIdAndFilter(tenantId, JobFilter.builder().build(), new PageLink(100)).getData()).isEmpty();
    }

    @Test
    public void testSubmitMultipleJobs() {
        int tasksCount = 3;
        int jobsCount = 3;
        for (int i = 1; i <= jobsCount; i++) {
            Job job = Job.builder()
                    .tenantId(tenantId)
                    .type(JobType.DUMMY)
                    .key("test-job-" + i)
                    .description("test job")
                    .configuration(DummyJobConfiguration.builder()
                            .successfulTasksCount(tasksCount)
                            .taskProcessingTimeMs(1000)
                            .build())
                    .build();
            jobManager.submitJob(job);
        }

        await().atMost(TIMEOUT, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Job> jobs = findJobs(JobType.DUMMY);
            assertThat(jobs).hasSize(jobsCount);
            Job firstJob = jobs.get(2); // ordered by createdTime descending
            assertThat(firstJob.getStatus()).isEqualTo(JobStatus.RUNNING);
            Job secondJob = jobs.get(1);
            assertThat(secondJob.getStatus()).isEqualTo(JobStatus.QUEUED);
            Job thirdJob = jobs.get(0);
            assertThat(thirdJob.getStatus()).isEqualTo(JobStatus.QUEUED);
        });

        await().atMost(TIMEOUT, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Job> jobs = findJobs();
            for (Job job : jobs) {
                assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
                assertThat(job.getResult().getSuccessfulCount()).isEqualTo(tasksCount);
                assertThat(job.getResult().getTotalCount()).isEqualTo(tasksCount);
            }
        });
    }

    @Test
    public void testCancelQueuedJob() throws Exception {
        int tasksCount = 3;
        int jobsCount = 3;
        List<JobId> jobIds = new ArrayList<>();
        for (int i = 1; i <= jobsCount; i++) {
            Job job = Job.builder()
                    .tenantId(tenantId)
                    .type(JobType.DUMMY)
                    .key("test-job-" + i)
                    .description("test job")
                    .configuration(DummyJobConfiguration.builder()
                            .successfulTasksCount(tasksCount)
                            .taskProcessingTimeMs(1000)
                            .build())
                    .build();
            jobIds.add(jobManager.submitJob(job).getId());
        }

        for (int i = 1; i < jobIds.size(); i++) {
            cancelJob(jobIds.get(i));
        }

        await().atMost(TIMEOUT, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Job> jobs = findJobs();

            Job firstJob = jobs.get(2);
            assertThat(firstJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
            assertThat(firstJob.getResult().getSuccessfulCount()).isEqualTo(tasksCount);
            assertThat(firstJob.getResult().getTotalCount()).isEqualTo(tasksCount);

            Job secondJob = jobs.get(1);
            assertThat(secondJob.getStatus()).isEqualTo(JobStatus.CANCELLED);
            assertThat(secondJob.getResult().getCompletedCount()).isZero();

            Job thirdJob = jobs.get(0);
            assertThat(thirdJob.getStatus()).isEqualTo(JobStatus.CANCELLED);
            assertThat(thirdJob.getResult().getCompletedCount()).isZero();
        });
    }

    @Test
    public void testSubmitJob_generalError() {
        int submittedTasks = 100;
        JobId jobId = jobManager.submitJob(Job.builder()
                .tenantId(tenantId)
                .type(JobType.DUMMY)
                .key("test-job")
                .description("Test job")
                .configuration(DummyJobConfiguration.builder()
                        .generalError("Some error while submitting tasks")
                        .submittedTasksBeforeGeneralError(submittedTasks)
                        .taskProcessingTimeMs(10)
                        .build())
                .build()).getId();

        await().atMost(TIMEOUT, TimeUnit.SECONDS).untilAsserted(() -> {
            Job job = findJobById(jobId);
            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
            assertThat(job.getResult().getSuccessfulCount()).isBetween(1, submittedTasks);
            assertThat(job.getResult().getDiscardedCount()).isZero();
            assertThat(job.getResult().getTotalCount()).isNull();
        });

        checkJobNotification(notification -> {
            assertThat(notification.getSubject()).isEqualTo("Dummy job task failed");
            assertThat(notification.getText()).isEqualTo("Test job failed: Some error while submitting tasks");
        });
    }

    @Test
    public void testSubmitJob_immediateGeneralError() {
        JobId jobId = jobManager.submitJob(Job.builder()
                .tenantId(tenantId)
                .type(JobType.DUMMY)
                .key("test-job")
                .description("Test job")
                .configuration(DummyJobConfiguration.builder()
                        .generalError("Some error while submitting tasks")
                        .submittedTasksBeforeGeneralError(0)
                        .build())
                .build()).getId();

        await().atMost(TIMEOUT, TimeUnit.SECONDS).untilAsserted(() -> {
            Job job = findJobById(jobId);
            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
            assertThat(job.getResult().getSuccessfulCount()).isZero();
            assertThat(job.getResult().getDiscardedCount()).isZero();
            assertThat(job.getResult().getFailedCount()).isZero();
            assertThat(job.getResult().getTotalCount()).isNull();
        });
    }

    @Test
    public void testReprocessJob_generalError() throws Exception {
        int submittedTasks = 100;
        JobId jobId = jobManager.submitJob(Job.builder()
                .tenantId(tenantId)
                .type(JobType.DUMMY)
                .key("test-job")
                .description("Test job")
                .configuration(DummyJobConfiguration.builder()
                        .generalError("Some error while submitting tasks")
                        .submittedTasksBeforeGeneralError(submittedTasks)
                        .taskProcessingTimeMs(10)
                        .build())
                .build()).getId();

        await().atMost(TIMEOUT, TimeUnit.SECONDS).untilAsserted(() -> {
            Job job = findJobById(jobId);
            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
            assertThat(job.getResult().getGeneralError()).isEqualTo("Some error while submitting tasks");
        });

        Job savedJob = jobDao.findById(tenantId, jobId.getId());
        DummyJobConfiguration configuration = savedJob.getConfiguration();
        configuration.setGeneralError(null);
        configuration.setSuccessfulTasksCount(submittedTasks);
        jobDao.save(tenantId, savedJob);

        reprocessJob(jobId);

        await().atMost(TIMEOUT, TimeUnit.SECONDS).untilAsserted(() -> {
            Job job = findJobById(jobId);
            assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
            assertThat(job.getResult().getGeneralError()).isNull();
            assertThat(job.getResult().getSuccessfulCount()).isEqualTo(submittedTasks);
            assertThat(job.getResult().getTotalCount()).isEqualTo(submittedTasks);
            assertThat(job.getResult().getFailedCount()).isZero();
            assertThat(job.getResult().getDiscardedCount()).isZero();
        });
    }

    @Test
    public void testReprocessJob() throws Exception {
        int successfulTasks = 3;
        int failedTasks = 2;
        int totalTasksCount = successfulTasks + failedTasks;
        JobId jobId = jobManager.submitJob(Job.builder()
                .tenantId(tenantId)
                .type(JobType.DUMMY)
                .key("test-job")
                .description("test job")
                .configuration(DummyJobConfiguration.builder()
                        .successfulTasksCount(successfulTasks)
                        .failedTasksCount(failedTasks)
                        .errors(List.of("error"))
                        .taskProcessingTimeMs(100)
                        .build())
                .build()).getId();

        await().atMost(TIMEOUT, TimeUnit.SECONDS).untilAsserted(() -> {
            Job job = findJobById(jobId);
            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
            JobResult jobResult = job.getResult();
            assertThat(jobResult.getSuccessfulCount()).isEqualTo(successfulTasks);
            assertThat(jobResult.getFailedCount()).isEqualTo(failedTasks);

            List<DummyTaskFailure> failures = getFailures(jobResult);
            for (int i = 0, taskNumber = successfulTasks + 1; taskNumber <= totalTasksCount; i++, taskNumber++) {
                DummyTaskFailure failure = failures.get(i);
                assertThat(failure.getNumber()).isEqualTo(taskNumber);
                assertThat(failure.getError()).isEqualTo("error");
            }
        });

        reprocessJob(jobId);

        await().atMost(TIMEOUT, TimeUnit.SECONDS).untilAsserted(() -> {
            Job job = findJobById(jobId);
            assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
            assertThat(job.getResult().getSuccessfulCount()).isEqualTo(totalTasksCount);
            assertThat(job.getResult().getFailedCount()).isZero();
            assertThat(job.getResult().getTotalCount()).isEqualTo(totalTasksCount);
            assertThat(job.getResult().getResults()).isEmpty();
            assertThat(job.getConfiguration().getToReprocess()).isNullOrEmpty();
        });
    }

    @Test
    public void testReprocessJob_somePermanentlyFailed() throws Exception {
        int successfulTasks = 3;
        int failedTasks = 2;
        int permanentlyFailedTasks = 1;
        int totalTasksCount = successfulTasks + failedTasks + permanentlyFailedTasks;
        JobId jobId = jobManager.submitJob(Job.builder()
                .tenantId(tenantId)
                .type(JobType.DUMMY)
                .key("test-job")
                .description("test job")
                .configuration(DummyJobConfiguration.builder()
                        .successfulTasksCount(successfulTasks)
                        .failedTasksCount(failedTasks)
                        .permanentlyFailedTasksCount(permanentlyFailedTasks)
                        .errors(List.of("error"))
                        .taskProcessingTimeMs(100)
                        .build())
                .build()).getId();

        await().atMost(TIMEOUT, TimeUnit.SECONDS).untilAsserted(() -> {
            Job job = findJobById(jobId);
            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
            JobResult jobResult = job.getResult();
            assertThat(jobResult.getSuccessfulCount()).isEqualTo(successfulTasks);
            assertThat(jobResult.getFailedCount()).isEqualTo(failedTasks + permanentlyFailedTasks);
            assertThat(jobResult.getTotalCount()).isEqualTo(totalTasksCount);

            List<DummyTaskFailure> failures = getFailures(jobResult);
            for (int i = 0, taskNumber = successfulTasks + 1; taskNumber <= totalTasksCount; i++, taskNumber++) {
                DummyTaskFailure failure = failures.get(i);
                assertThat(failure.getNumber()).isEqualTo(taskNumber);
                assertThat(failure.getError()).isEqualTo("error");
            }
        });

        reprocessJob(jobId);

        await().atMost(TIMEOUT, TimeUnit.SECONDS).untilAsserted(() -> {
            Job job = findJobById(jobId);
            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
            JobResult jobResult = job.getResult();
            assertThat(jobResult.getSuccessfulCount()).isEqualTo(successfulTasks + failedTasks);
            assertThat(jobResult.getFailedCount()).isEqualTo(permanentlyFailedTasks);
            assertThat(jobResult.getTotalCount()).isEqualTo(totalTasksCount);

            List<DummyTaskFailure> failures = getFailures(jobResult);
            for (int i = 0, taskNumber = successfulTasks + failedTasks + 1; taskNumber <= totalTasksCount; i++, taskNumber++) {
                DummyTaskFailure failure = failures.get(i);
                assertThat(failure.getNumber()).isEqualTo(taskNumber);
                assertThat(failure.getError()).isEqualTo("error");
                assertThat(failure.isFailAlways()).isTrue();
            }
        });
    }

    private void checkJobNotification(ThrowingConsumer<Notification> assertFunction) {
        await().atMost(TIMEOUT, TimeUnit.SECONDS).untilAsserted(() -> {
            Notification notification = getMyNotifications(true, 100).stream()
                    .findFirst().orElse(null);
            assertThat(notification).isNotNull();

            assertFunction.accept(notification);
        });
    }

    private List<DummyTaskFailure> getFailures(JobResult jobResult) {
        return jobResult.getResults().stream()
                .map(taskResult -> ((DummyTaskResult) taskResult).getFailure())
                .sorted(Comparator.comparingInt(DummyTaskFailure::getNumber))
                .toList();
    }

}