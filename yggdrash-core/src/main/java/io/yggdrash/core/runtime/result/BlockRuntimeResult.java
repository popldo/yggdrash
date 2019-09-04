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

package io.yggdrash.core.runtime.result;

import com.google.gson.JsonObject;
import io.yggdrash.contract.core.Receipt;
import io.yggdrash.core.consensus.ConsensusBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlockRuntimeResult {
    private final Map<String, JsonObject> blockResult = new HashMap<>();
    private final List<Receipt> receipts = new ArrayList<>();
    private final ConsensusBlock block;

    public BlockRuntimeResult(ConsensusBlock block) {
        this.block = block;
    }

    public void setBlockResult(Set<Map.Entry<String, JsonObject>> values) {
        values.forEach(entry -> blockResult.put(entry.getKey(), entry.getValue()));
    }

    public void addReceipt(Receipt receipt) {
        receipts.add(receipt);
    }

    public List<Receipt> getReceipts() {
        return this.receipts;
    }

    public Map<String, JsonObject> getBlockResult() {
        return this.blockResult;
    }

    public ConsensusBlock getOriginBlock() {
        return this.block;
    }
}