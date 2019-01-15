/*
 * Copyright 2018 Akashic Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yggdrash.node.springboot.grpc.autoconfigure;

import io.grpc.ServerBuilder;
import io.yggdrash.node.springboot.grpc.GrpcServerBuilderConfigurer;
import io.yggdrash.node.springboot.grpc.GrpcServerRunner;
import io.yggdrash.node.springboot.grpc.GrpcService;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

@AutoConfigureOrder
@ConditionalOnBean(annotation = GrpcService.class)
public class GrpcAutoConfiguration {

    public GrpcServerRunner grpcServerRunner(GrpcServerBuilderConfigurer configurer) {
        return new GrpcServerRunner(configurer, ServerBuilder.forPort(32918));
    }
}
