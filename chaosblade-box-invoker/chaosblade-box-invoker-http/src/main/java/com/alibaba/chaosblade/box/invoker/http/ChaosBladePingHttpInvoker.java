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

package com.alibaba.chaosblade.box.invoker.http;

import com.alibaba.chaosblade.box.common.enums.DeviceType;
import com.alibaba.chaosblade.box.invoker.ChaosInvokerStrategy;
import com.alibaba.chaosblade.box.invoker.ResponseCommand;
import com.alibaba.chaosblade.box.invoker.http.model.reuest.HttpChannelRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * @author yefei
 */
@Slf4j
@Component
@ChaosInvokerStrategy(deviceType = DeviceType.HOST)
public class ChaosBladePingHttpInvoker extends AbstractHttpInvoker {

    @Override
    public CompletableFuture<ResponseCommand> invoke(HttpChannelRequest requestCommand) {
        CompletableFuture<ResponseCommand> completableFuture = super.invoke(requestCommand);
        return completableFuture;
    }
}
