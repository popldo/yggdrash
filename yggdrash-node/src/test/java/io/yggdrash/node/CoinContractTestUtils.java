/*
 * Copyright 2018 Akashic Foundation
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

package io.yggdrash.node;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.yggdrash.ContractTestUtils;

import java.math.BigDecimal;

public class CoinContractTestUtils {

    public static JsonArray createTransferBody(String to, BigDecimal amount) {
        JsonObject params = new JsonObject();
        params.addProperty("to", to);
        params.addProperty("amount", amount);

        return ContractTestUtils.txBodyJson("transfer", params);
    }

    public static JsonArray createApproveBody(String spender, BigDecimal amount) {
        JsonObject params = new JsonObject();
        params.addProperty("spender", spender);
        params.addProperty("amount", amount);

        return ContractTestUtils.txBodyJson("approve", params);
    }

    public static JsonArray createTransferFromBody(String from, String to, BigDecimal amount) {
        JsonObject params = new JsonObject();
        params.addProperty("from", from);
        params.addProperty("to", to);
        params.addProperty("amount", amount);

        return ContractTestUtils.txBodyJson("transferfrom", params);
    }
}