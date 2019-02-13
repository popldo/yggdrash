/*
 * Copyright 2019 Akashic Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yggdrash.core.contract.methods;

import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

public class ContractMethod {
    Method invokeMethod;
    boolean params = false;

    public ContractMethod(Method method) {
        Optional<Class<?>> m = Arrays.stream(method.getParameterTypes())
                .filter(p -> p == JsonObject.class).findFirst();
        if (method.getParameterCount() == 1 && m.isPresent()) {
            params = true;
        }
        invokeMethod = method;
    }

    public Method getMethod() {
        return invokeMethod;
    }

    public boolean isParams() {
        return params;
    }
}
