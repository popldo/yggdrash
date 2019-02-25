/*
 * Copyright 2018 Akashic Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yggdrash.core.blockchain;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.yggdrash.common.config.Constants;
import io.yggdrash.common.crypto.HexUtil;
import io.yggdrash.common.util.JsonUtil;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class Branch {

    private final BranchId branchId;
    private final String name;
    private final String symbol;
    private final String property;
    private final long timestamp;
    private final BigInteger fee;
    private final JsonObject json;
    protected List<BranchContract> contracts;
    protected String description;
    private final List<String> validator;

    protected Branch(JsonObject json) {
        this.json = json;
        this.branchId = BranchId.of(json);
        this.name = json.get("name").getAsString();
        this.symbol = json.get("symbol").getAsString();
        this.property = json.get("property").getAsString();
        this.fee = json.get("fee").getAsBigInteger();
        String timestampHex = json.get("timestamp").getAsString();
        this.timestamp = HexUtil.hexStringToLong(timestampHex);

        this.contracts = new ArrayList<>();

        JsonArray contractArray = json.getAsJsonArray("contracts");
        // Contracts
        if (contractArray != null) {
            for (JsonElement jsonElement : contractArray) {
                contracts.add(BranchContract.of(jsonElement.getAsJsonObject()));
            }
        }
        this.description = json.get("description").getAsString();
        this.validator = JsonUtil.convertJsonArrayToStringList(
                json.get("validator").getAsJsonArray());
    }

    public BranchId getBranchId() {
        return branchId;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getProperty() {
        return property;
    }

    public BigInteger getFee() {
        return fee;
    }

    public List<BranchContract> getBranchContracts() {
        return new ArrayList<>(this.contracts);
    }

    public String getDescription() {
        return description;
    }

    public List<String> getValidators() {
        return new ArrayList<>(this.validator);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public JsonObject getJson() {
        return json;
    }

    public boolean isYggdrash() {
        return Constants.YGGDRASH.equals(symbol);
    }

    public static Branch of(InputStream is) throws IOException {
        String branchString = IOUtils.toString(is, StandardCharsets.UTF_8);
        JsonObject json = JsonUtil.parseJsonObject(branchString);
        return of(json);
    }

    public static Branch of(JsonObject json) {
        return new Branch(json);
    }

    public enum BranchType {
        IMMUNITY, MUTABLE, INSTANT, PRIVATE, TEST;

        public static BranchType of(String val) {
            return Arrays.stream(BranchType.values())
                    .filter(e -> e.toString().toLowerCase().equals(val))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            String.format("Unsupported type %s.", val)));
        }
    }
}
