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

package com.alibaba.chaosblade.box.collector.prometheus;

import com.alibaba.chaosblade.box.collector.CollectorStrategy;
import com.alibaba.chaosblade.box.collector.CollectorType;
import com.alibaba.chaosblade.box.collector.ContainerCollector;
import com.alibaba.chaosblade.box.collector.model.Container;
import com.alibaba.chaosblade.box.collector.model.Query;
import com.alibaba.chaosblade.box.common.utils.JsonUtils;
import com.alibaba.chaosblade.box.collector.prometheus.model.PrometheusContainer;
import com.alibaba.chaosblade.box.common.model.PrometheusResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * @author yefei
 */
@Component
@CollectorStrategy(CollectorType.PROMETHEUS)
public class PrometheusContainerCollector extends AbstractCollector<Container> implements ContainerCollector {

    @Override
    public CompletableFuture<List<Container>> collect(Query query) {
        return collect(String.format("kube_pod_container_info{pod='%s'}", query.getPodName()));
    }

    @Override
    List<Container> pack(byte[] bytes) {

        PrometheusResponse<PrometheusContainer> response = JsonUtils.readValue(new TypeReference<PrometheusResponse<PrometheusContainer>>() {
        }, bytes);

        return response.getData().getResult().stream().map(result ->
                Container.builder().name(result.getMetric().getName())
                        .pod(result.getMetric().getPod())
                        .containerId(result.getMetric().getContainerId())
                        .namespace(result.getMetric().getNamespace())
                        .build()
        ).collect(Collectors.toList());
    }
}
