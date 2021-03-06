/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.chaosblade.box.service.impl;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.EnumUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.chaosblade.box.dao.model.*;
import com.alibaba.chaosblade.box.dao.repository.*;
import com.alibaba.chaosblade.box.service.ExperimentActivityTaskService;
import com.alibaba.chaosblade.box.service.ExperimentMiniFlowService;
import com.alibaba.chaosblade.box.common.DeviceMeta;
import com.alibaba.chaosblade.box.common.enums.DeviceType;
import com.alibaba.chaosblade.box.common.enums.ExperimentDimension;
import com.alibaba.chaosblade.box.common.enums.ResultStatus;
import com.alibaba.chaosblade.box.common.enums.RunStatus;
import com.alibaba.chaosblade.box.common.exception.BizException;
import com.alibaba.chaosblade.box.common.exception.ExceptionMessageEnum;
import com.alibaba.chaosblade.box.common.utils.JsonUtils;
import com.alibaba.chaosblade.box.metric.MetricChartLineRequest;
import com.alibaba.chaosblade.box.metric.MetricService;
import com.alibaba.chaosblade.box.service.model.metric.MetricModel;
import com.alibaba.chaosblade.box.service.task.ActivityTask;
import com.alibaba.chaosblade.box.service.task.ActivityTaskExecuteContext;
import com.alibaba.chaosblade.box.service.task.ActivityTaskExecutePipeline;
import com.alibaba.chaosblade.box.service.task.log.i18n.TaskLogType;
import com.alibaba.chaosblade.box.service.task.log.i18n.TaskLogUtil;
import com.alibaba.chaosblade.box.dao.model.*;
import com.alibaba.chaosblade.box.dao.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.alibaba.chaosblade.box.common.enums.RunStatus.FINISHED;
import static com.alibaba.chaosblade.box.common.exception.ExceptionMessageEnum.EXPERIMENT_TASK_NOT_FOUNT;

/**
 * @author yefei
 */
@Slf4j
@Service
public class ExperimentActivityTaskServiceImpl implements ExperimentActivityTaskService {

    @Autowired
    private ExperimentTaskRepository experimentTaskRepository;

    @Autowired
    private ExperimentMiniFlowService experimentMiniFlowService;

    @Autowired
    private MetricService metricService;

    @Autowired
    private MetricTaskRepository metricTaskRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceNodeRepository deviceNodeRepository;

    @Autowired
    private DevicePodRepository devicePodRepository;

    @Autowired
    private ExperimentRepository experimentRepository;

    @Autowired
    private ActivityTaskExecuteContext activityTaskExecuteContext;

    @Override
    public void manualChecked(Long activityTaskId) {

    }

    @Override
    public void executeActivityTasks(List<ExperimentActivityTaskDO> experimentActivityTasks, ExperimentTaskDO experimentTaskDO) {
        ActivityTaskExecutePipeline pipeline = new ActivityTaskExecutePipeline();
        ExperimentDO experimentDO = experimentRepository.selectById(experimentTaskDO.getExperimentId())
                .orElseThrow(() -> new BizException(ExceptionMessageEnum.EXPERIMENT_NOT_FOUNT));

        for (ExperimentActivityTaskDO experimentActivityTask : experimentActivityTasks) {

            List<DeviceMeta> deviceMetas = experimentMiniFlowService.selectExperimentDeviceByFlowId(experimentActivityTask.getFlowId());
            String activityDefinition = experimentActivityTask.getRunParam();

            ActivityTask activityTask = JsonUtils.readValue(ActivityTask.class, activityDefinition);

            activityTask.setDeviceMetas(deviceMetas);
            activityTask.setFlowId(experimentActivityTask.getFlowId());
            activityTask.setExperimentTaskId(experimentActivityTask.getExperimentTaskId());
            activityTask.setActivityId(experimentActivityTask.getActivityId());
            activityTask.setActivityTaskId(experimentActivityTask.getId());
            activityTask.setPreActivityTaskId(experimentActivityTask.getPreActivityTaskId());
            activityTask.setNextActivityTaskId(experimentActivityTask.getNextActivityTaskId());
            activityTask.setPhase(experimentActivityTask.getPhase());
            activityTask.setExperimentDimension(EnumUtil.fromString(ExperimentDimension.class, experimentDO.getDimension().toUpperCase()));

            pipeline.addLast(activityTask);
            if (activityTask.getManualChecked()) {
                // todo
                break;
            }
        }

        // experiment before notify
        activityTaskExecuteContext.addExperimentTaskStartListener(pipeline, (context, activityTask) -> {

            Logger logger = context.getContextLogger();
            ExperimentTaskDO experimentTask = experimentTaskRepository.selectById(activityTask.getExperimentTaskId())
                    .orElseThrow(() -> new BizException(EXPERIMENT_TASK_NOT_FOUNT));

            if (experimentTask.getRunStatus().equals(RunStatus.READY.getValue())) {

                TaskLogUtil.info(logger, TaskLogType.START_EXPERIMENT, activityTask.getExperimentTaskId());

                experimentTaskRepository.updateByPrimaryKey(experimentTaskDO.getId(), ExperimentTaskDO.builder()
                        .gmtStart(DateUtil.date())
                        .runStatus(RunStatus.RUNNING.getValue())
                        .build());

                for (DeviceMeta deviceMeta : activityTask.getDeviceMetas()) {
                    // update device last experiment
                    deviceRepository.updateByPrimaryKey(deviceMeta.getDeviceId(),
                            DeviceDO.builder().lastExperimentTime(DateUtil.date())
                                    .isExperimented(true)
                                    .lastTaskId(activityTask.getExperimentTaskId())
                                    .build()
                    );
                }

                String metric = experimentTaskDO.getMetric();
                if (StrUtil.isNotBlank(metric)) {
                    TaskLogUtil.info(logger, TaskLogType.START_METRIC, activityTask.getExperimentTaskId());
                    List<MetricModel> metricModels = JsonUtils.readValue(new TypeReference<List<MetricModel>>() {
                    }, metric);
                    metricModels.forEach(metricModel -> metric(context, metricModel, activityTask));
                } else {
                    TaskLogUtil.info(logger, TaskLogType.NO_METRIC, activityTask.getExperimentTaskId());
                }
            }
        });

        // experiment after notify
        activityTaskExecuteContext.addExperimentTaskCompleteListener(pipeline, (context, activityTask, e) -> {
            Logger logger = context.getContextLogger();
            if (activityTask.isAttackPhase()) {
                ExperimentTaskDO experimentTask = experimentTaskRepository.selectById(activityTask.getExperimentTaskId())
                        .orElseThrow(() -> new BizException(EXPERIMENT_TASK_NOT_FOUNT));
                if (experimentTask.getResultStatus() == null) {
                    experimentTaskRepository.updateByPrimaryKey(activityTask.getExperimentTaskId(), ExperimentTaskDO.builder()
                            .resultStatus(ResultStatus.FAILED.getValue())
                            .errorMessage(e.getMessage())
                            .build());

                }
            }
            if (activityTask.isRecoverPhase()) {
                ExperimentTaskDO taskDO = ExperimentTaskDO.builder()
                        .runStatus(FINISHED.getValue())
                        .gmtEnd(DateUtil.date())
                        .build();
                if (e != null) {
                    TaskLogUtil.info(logger, TaskLogType.EXPERIMENT_RECOVER_ERROR, activityTask.getExperimentTaskId(), e.getMessage());
                    log.error(e.getMessage(), e);
                    taskDO.setResultStatus(ResultStatus.FAILED.getValue());
                    taskDO.setErrorMessage(e.getMessage());
                } else {
                    TaskLogUtil.info(logger, TaskLogType.EXPERIMENT_RECOVER_SUCCESS, activityTask.getExperimentTaskId());
                    taskDO.setResultStatus(ResultStatus.SUCCESS.getValue());
                }
                experimentTaskRepository.updateByPrimaryKey(activityTask.getExperimentTaskId(), taskDO);

                for (DeviceMeta deviceMeta : activityTask.getDeviceMetas()) {
                    if (deviceMeta.getDeviceType() == null) {
                        break;
                    } else {
                        DeviceType deviceType = DeviceType.transByCode(deviceMeta.getDeviceType());
                        switch (deviceType) {
                            case HOST:
                                // update device last experiment
                                deviceRepository.updateByPrimaryKey(deviceMeta.getDeviceId(),
                                        DeviceDO.builder()
                                                .isExperimented(true)
                                                .lastExperimentTime(DateUtil.date())
                                                .lastTaskId(activityTask.getExperimentTaskId())
                                                .lastTaskStatus(taskDO.getResultStatus())
                                                .build()
                                );
                                break;
                            case NODE:
                                deviceNodeRepository.selectByNodeName(deviceMeta.getNodeName()).ifPresent(node ->
                                        deviceRepository.updateByPrimaryKey(node.getDeviceId(),
                                                DeviceDO.builder().lastExperimentTime(DateUtil.date())
                                                        .isExperimented(true)
                                                        .lastTaskId(activityTask.getExperimentTaskId())
                                                        .lastTaskStatus(taskDO.getResultStatus())
                                                        .build()
                                        ));
                                break;
                            case POD:
                                devicePodRepository.selectByNameAndNamespace(deviceMeta.getNamespace(), deviceMeta.getPodName())
                                        .ifPresent(pod ->
                                                deviceRepository.updateByPrimaryKey(pod.getDeviceId(),
                                                        DeviceDO.builder().lastExperimentTime(DateUtil.date())
                                                                .isExperimented(true)
                                                                .lastTaskId(activityTask.getExperimentTaskId())
                                                                .lastTaskStatus(taskDO.getResultStatus())
                                                                .build()
                                                )
                                        );
                                break;

                        }
                    }

                }
            }
        });

        // fire experiment
        activityTaskExecuteContext.fireExecute(pipeline);
    }

    private void metric(ActivityTaskExecuteContext context, MetricModel metricModel, ActivityTask activityTask) {
        Logger logger = context.getContextLogger();
        context.timer().newTimeout(timeout -> {

            metricService.selectChartLine(MetricChartLineRequest.builder()
                    .devices(activityTask.getDeviceMetas())
                    .startTime(DateUtil.date())
                    .endTime(DateUtil.date().offset(DateField.SECOND, +10))
                    .categoryCode(metricModel.getCode())
                    .params(metricModel.getParams())
                    .build())
                    .handleAsync((r, e) -> {
                        if (e != null) {
                            TaskLogUtil.info(logger,
                                    TaskLogType.GET_METRIC_ERROR,
                                    activityTask.getExperimentTaskId(),
                                    JsonUtils.writeValueAsString(activityTask.getDeviceMetas()),
                                    e.getMessage());
                        } else {
                            metricTaskRepository.saveBatch(
                                    r.stream().flatMap(v -> v.getMetricChartLines().stream().map(metricChartLine ->
                                            MetricTaskDO.builder()
                                                    .ip(v.getDeviceMeta().getIp())
                                                    .deviceId(v.getDeviceMeta().getDeviceId())
                                                    .hostname(v.getDeviceMeta().getHostname())
                                                    .categoryId(metricModel.getCategoryId())
                                                    .categoryCode(metricModel.getCode())
                                                    .date(metricChartLine.getTime())
                                                    .value(metricChartLine.getValue())
                                                    .taskId(activityTask.getExperimentTaskId())
                                                    .metric(v.getMetric())
                                                    .build()
                                    )).collect(Collectors.toList()));
                        }
                        return null;
                    }, context.executor());


            Byte status = experimentTaskRepository.selectById(activityTask.getExperimentTaskId())
                    .map(ExperimentTaskDO::getRunStatus)
                    .orElseThrow(() -> new BizException(EXPERIMENT_TASK_NOT_FOUNT));
            RunStatus runStatus = RunStatus.parse(status);
            if (runStatus != FINISHED) {
                metric(context, metricModel, activityTask);
            }
        }, 10, TimeUnit.SECONDS);
    }

}
