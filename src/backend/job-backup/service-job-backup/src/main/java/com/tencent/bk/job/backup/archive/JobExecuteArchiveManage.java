/*
 * Tencent is pleased to support the open source community by making BK-JOB蓝鲸智云作业平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-JOB蓝鲸智云作业平台 is licensed under the MIT License.
 *
 * License for BK-JOB蓝鲸智云作业平台:
 * --------------------------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.tencent.bk.job.backup.archive;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.tencent.bk.job.backup.archive.impl.*;
import com.tencent.bk.job.backup.config.ArchiveConfig;
import com.tencent.bk.job.backup.dao.ExecuteArchiveDAO;
import com.tencent.bk.job.backup.dao.JobExecuteDAO;
import com.tencent.bk.job.backup.service.ArchiveProgressService;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.*;

@Slf4j
public class JobExecuteArchiveManage implements SmartLifecycle {

    private final JobExecuteDAO jobExecuteDAO;
    private final ArchiveConfig archiveConfig;

    private static final ExecutorService ARCHIVE_THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(20, 20,
        0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(20),
        new ThreadFactoryBuilder().setNameFormat("archive-thread-pool-%d").build(),
        new ThreadPoolExecutor.AbortPolicy());

    private final FileSourceTaskLogArchivist fileSourceTaskLogArchivist;
    private final StepInstanceArchivist stepInstanceArchivist;
    private final StepInstanceConfirmArchivist stepInstanceConfirmArchivist;
    private final StepInstanceFileArchivist stepInstanceFileArchivist;
    private final StepInstanceScriptArchivist stepInstanceScriptArchivist;
    private final StepInstanceVariableArchivist stepInstanceVariableArchivist;
    private final GseTaskLogArchivist gseTaskLogArchivist;
    private final GseTaskIpLogArchivist gseTaskIpLogArchivist;
    private final TaskInstanceArchivist taskInstanceArchivist;
    private final TaskInstanceVariableArchivist taskInstanceVariableArchivist;
    private final OperationLogArchivist operationLogArchivist;


    /**
     * whether this component is currently running(Spring Lifecycle isRunning method)
     */
    private volatile boolean running = false;

    public JobExecuteArchiveManage(JobExecuteDAO jobExecuteDAO,
                                   ExecuteArchiveDAO executeArchiveDAO,
                                   ArchiveProgressService archiveProgressService,
                                   ArchiveConfig archiveConfig) {
        log.info("Init JobExecuteArchiveManage! archiveConfig: {}", archiveConfig);
        this.jobExecuteDAO = jobExecuteDAO;
        this.archiveConfig = archiveConfig;
        this.fileSourceTaskLogArchivist = new FileSourceTaskLogArchivist(jobExecuteDAO, executeArchiveDAO,
            archiveProgressService);
        this.stepInstanceArchivist = new StepInstanceArchivist(jobExecuteDAO, executeArchiveDAO,
            archiveProgressService);
        this.stepInstanceConfirmArchivist = new StepInstanceConfirmArchivist(jobExecuteDAO, executeArchiveDAO,
            archiveProgressService);
        this.stepInstanceFileArchivist = new StepInstanceFileArchivist(jobExecuteDAO, executeArchiveDAO,
            archiveProgressService);
        this.stepInstanceScriptArchivist = new StepInstanceScriptArchivist(jobExecuteDAO, executeArchiveDAO,
            archiveProgressService);
        this.stepInstanceVariableArchivist = new StepInstanceVariableArchivist(jobExecuteDAO, executeArchiveDAO,
            archiveProgressService);
        this.gseTaskLogArchivist = new GseTaskLogArchivist(jobExecuteDAO, executeArchiveDAO, archiveProgressService);
        this.gseTaskIpLogArchivist = new GseTaskIpLogArchivist(jobExecuteDAO, executeArchiveDAO,
            archiveProgressService);
        this.taskInstanceArchivist = new TaskInstanceArchivist(jobExecuteDAO, executeArchiveDAO,
            archiveProgressService);
        this.taskInstanceVariableArchivist = new TaskInstanceVariableArchivist(jobExecuteDAO, executeArchiveDAO,
            archiveProgressService);
        this.operationLogArchivist = new OperationLogArchivist(jobExecuteDAO, executeArchiveDAO,
            archiveProgressService);
    }

    @Scheduled(cron = "${job.execute.archive.cron:0 0 4 * * *}")
    public void cronArchive() {
        archive(archiveConfig);
    }

    public void archive(ArchiveConfig archiveConfig) {
        try {
            new ArchiveThread(archiveConfig).start();
        } catch (Throwable e) {
            log.error("Error while archive job_execute data", e);
        }
    }

    @Override
    public void start() {
        this.running = true;
    }

    @Override
    public void stop() {
        log.info("Stop JobExecuteArchiveManage!");
        ArchiveTaskLock.getInstance().unlockAll();
        log.info("Release all archive locks when stop!");
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }


    class ArchiveThread extends Thread {
        private ArchiveConfig archiveConfig;


        ArchiveThread(ArchiveConfig archiveConfig) {
            this.archiveConfig = archiveConfig;
            this.setName("Data-Archive-Thread");
        }

        @Override
        public void run() {
            try {
                log.info("Job Execute archive task begin|{}", System.currentTimeMillis());
                log.info("Archive days : {}", archiveConfig.getDataKeepDays());
                if (archiveConfig.isArchiveEnabled() || archiveConfig.isDeleteEnabled()) {
                    doArchive(getEndTime(archiveConfig.getDataKeepDays()));
                } else {
                    log.info("Archive and delete tasks are disabled, skip archive");
                }
                log.info("Job Execute archive task finished|{}", System.currentTimeMillis());
            } catch (InterruptedException e) {
                log.warn("Thread interrupted!");
            }
        }

        private Long getEndTime(int archiveDays) {
            DateTime now = DateTime.now();
            // 置为前一天天 24:00:00
            long todayMaxMills = now.minusMillis(now.getMillisOfDay()).getMillis();

            //减掉当前xx天后
            long archiveMills = archiveDays * 24 * 3600 * 1000L;
            return todayMaxMills - archiveMills;
        }


        private void doArchive(Long endTime) throws InterruptedException {
            try {
                log.info("Start job execute archive before {} at {}", endTime, System.currentTimeMillis());

                long maxNeedArchiveTaskInstanceId = jobExecuteDAO.getMaxNeedArchiveTaskInstanceId(endTime);
                long maxNeedArchiveStepInstanceId =
                    jobExecuteDAO.getMaxNeedArchiveStepInstanceId(maxNeedArchiveTaskInstanceId);

                ArchiveSummaryHolder.getInstance().init(endTime);
                archive(maxNeedArchiveTaskInstanceId, maxNeedArchiveStepInstanceId);
                ArchiveSummaryHolder.getInstance().print();

                log.info("Execute log archive before {} success at {}", endTime, System.currentTimeMillis());
            } catch (InterruptedException e) {
                throw e;
            } catch (Throwable e) {
                log.error("Error while do archive!|{}", endTime, e);
            }
        }

        private void archive(long maxNeedArchiveTaskInstanceId, long maxNeedArchiveStepInstanceId)
            throws InterruptedException {
            CountDownLatch countDownLatch = new CountDownLatch(8);
            log.info("Submitting archive task...");

            ARCHIVE_THREAD_POOL_EXECUTOR.execute(() -> taskInstanceArchivist.archive(archiveConfig,
                maxNeedArchiveTaskInstanceId, countDownLatch));


            ARCHIVE_THREAD_POOL_EXECUTOR.execute(() -> stepInstanceArchivist.archive(archiveConfig,
                maxNeedArchiveStepInstanceId, countDownLatch));
            ARCHIVE_THREAD_POOL_EXECUTOR.execute(() -> stepInstanceConfirmArchivist.archive(archiveConfig,
                maxNeedArchiveStepInstanceId, countDownLatch));
            ARCHIVE_THREAD_POOL_EXECUTOR.execute(() -> stepInstanceFileArchivist.archive(archiveConfig,
                maxNeedArchiveStepInstanceId, countDownLatch));
            ARCHIVE_THREAD_POOL_EXECUTOR.execute(() -> stepInstanceScriptArchivist.archive(archiveConfig,
                maxNeedArchiveStepInstanceId, countDownLatch));


//            ARCHIVE_THREAD_POOL_EXECUTOR.execute(() -> gseTaskLogArchivist.archive(deleteAfterArchive,
//                    maxNeedArchiveStepInstanceId, countDownLatch));
//            ARCHIVE_THREAD_POOL_EXECUTOR.execute(() -> fileSourceTaskLogArchivist.archive(deleteAfterArchive,
//                    maxNeedArchiveStepInstanceId, countDownLatch));
            ARCHIVE_THREAD_POOL_EXECUTOR.execute(() -> gseTaskIpLogArchivist.archive(archiveConfig,
                maxNeedArchiveStepInstanceId, countDownLatch));

            ARCHIVE_THREAD_POOL_EXECUTOR.execute(() -> taskInstanceVariableArchivist.archive(archiveConfig,
                maxNeedArchiveTaskInstanceId, countDownLatch));
//            ARCHIVE_THREAD_POOL_EXECUTOR.execute(() -> stepInstanceVariableArchivist.archive(deleteAfterArchive,
//                    maxNeedArchiveStepInstanceId, countDownLatch));

            ARCHIVE_THREAD_POOL_EXECUTOR.execute(() -> operationLogArchivist.archive(archiveConfig,
                maxNeedArchiveTaskInstanceId, countDownLatch));

            log.info("Archive task submitted. Waiting for complete...");
            countDownLatch.await();

            log.info("Archive task execute completed.");
        }
    }
}
