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

package com.alibaba.chaosblade.box.service.model.experiment;

import com.alibaba.chaosblade.box.service.model.flow.FlowGroup;
import lombok.Data;

import java.util.List;

@Data
public class ExperimentDefinitionRequest {

    /**
     * 演练ID
     */
    private String experimentId;

    /**
     * 微流程group
     */
    private List<FlowGroup> flowGroups;

}
