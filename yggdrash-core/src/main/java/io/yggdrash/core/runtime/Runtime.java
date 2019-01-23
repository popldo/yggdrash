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

package io.yggdrash.core.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.yggdrash.common.util.JsonUtil;
import io.yggdrash.core.blockchain.BlockHusk;
import io.yggdrash.core.blockchain.TransactionHusk;
import io.yggdrash.core.contract.Contract;
import io.yggdrash.core.contract.ContractId;
import io.yggdrash.core.contract.ExecuteStatus;
import io.yggdrash.core.contract.TransactionReceipt;
import io.yggdrash.core.contract.TransactionReceiptImpl;
import io.yggdrash.core.runtime.result.BlockRuntimeResult;
import io.yggdrash.core.runtime.result.TransactionRuntimeResult;
import io.yggdrash.core.store.StateStore;
import io.yggdrash.core.store.Store;
import io.yggdrash.core.store.TempStateStore;
import io.yggdrash.core.store.TransactionReceiptStore;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Runtime<T> {
    protected static final Logger log = LoggerFactory.getLogger(Runtime.class);

    private StateStore<T> stateStore;
    private TransactionReceiptStore txReceiptStore;
    // TODO contract is map
    private Contract<T> contract;
    private Map<ContractId, Contract> contracts = new HashMap<>();
    private Method genesis;
    private RuntimeInvoke contractInvoke;
    private RuntimeQuery runtimeQuery;

    // All block chain has state root
    private byte[] stateRoot;


    // FIX runtime run contract will init
    // TODO Runtime get multi Contract
    public Runtime(Contract<T> contract,
                   StateStore<T> stateStore,
                   TransactionReceiptStore txReceiptStore) {
        this.stateStore = stateStore;
        this.txReceiptStore = txReceiptStore;
        this.contract = contract;

        contractInvoke = new RuntimeInvoke(contract);
        runtimeQuery = new RuntimeQuery(contract, stateStore);
    }

    // TODO contract move to Map
    public void addContract(ContractId contractId, Contract contract) {
        this.contracts.put(contractId, contract);
    }

    public boolean hasContract(ContractId contractId) {
        return this.contracts.containsKey(contractId);
    }


    public BlockRuntimeResult invokeBlock(BlockHusk block) {
        // Block Data
        // - Hash
        // - BranchId
        // - Index *(Height)
        // Map String

        if(block.getIndex() == 0) {
            // TODO first transaction is genesis
            // TODO genesis method don't call any more
        }
//        Map<Sha3Hash, Boolean> result = new HashMap<>();
        BlockRuntimeResult result = new BlockRuntimeResult(block);
        TempStateStore blockState = new TempStateStore(stateStore);
        for(TransactionHusk tx: block.getBody()) {
            TransactionReceipt txReceipt = new TransactionReceiptImpl(tx);
            // set Block ID
            txReceipt.setBlockId(block.getHash().toString());
            txReceipt.setBlockHeight(block.getIndex());
            txReceipt.setBranchId(block.getBranchId().toString());

            // Transaction invoke here
            // save Tranction Receipt

            TempStateStore txResult = invoke(tx, txReceipt, blockState);
            if (txReceipt.isSuccess()) {
                // stateStore revert values
                //submitTxState();
                blockState.putAll(txResult.changeValues());
            }

            result.addTxReceipt(txReceipt);
            // Save TxReceipt
        }
        // Save BlockStates
        result.setBlockResult(blockState.changeValues());

        return result;
    }

    public void commitBlockResult(BlockRuntimeResult result) {
        // store transaction
        Map<String, JsonObject> changes = result.getBlockResult();
        result.getTxReceipts().stream().forEach(txr -> {
            txReceiptStore.put(txr);
        });
        if (!changes.isEmpty()) {
            changes.entrySet().stream().forEach(r -> {
                stateStore.put(r.getKey(), r.getValue());
            });
        }

        // print transaction receiptEvent
        /*
        if (log.isInfoEnabled()) {
            // transction log print
            log.info("{} Branch {} Block {} Transaction  Status : {} ",
                    txReceipt.getBranchId(),
                    txReceipt.getBlockId(),
                    txReceipt.getTxId(),
                    txReceipt.getStatus()
            );
            for (JsonObject txLog: txReceipt.getTxLog()) {
                log.info("{} {}", txReceipt.getTxId(), txLog.toString());
            }
        }
        */
        // confirms

    }


    // This invoke is temp run Transaction
    public TransactionRuntimeResult invoke(TransactionHusk tx) {
        TransactionReceipt txReceipt = new TransactionReceiptImpl(tx);
        TransactionRuntimeResult trr = new TransactionRuntimeResult(tx);
        trr.setTransctionReceipt(txReceipt);

        TempStateStore store = invoke(tx, txReceipt, stateStore);
        trr.setChangeValues(store.changeValues());

        return trr;
    }


    public TempStateStore invoke(TransactionHusk tx, TransactionReceipt txReceipt, Store origin) {
        // Find invoke method and invoke
        // validation method
        TempStateStore txState = new TempStateStore(origin);
        try {
            // transaction is multiple method
            for (JsonElement transactionElement: JsonUtil.parseJsonArray(tx.getBody())) {
                JsonObject txBody = transactionElement.getAsJsonObject();
                // check contract Version
                // txBody.get("contractId")
                TempStateStore txElementState = contractInvoke.invokeTransaction(txBody, txReceipt, txState);
                if(txReceipt.isSuccess()) {
                    txState.putAll(txElementState.changeValues());
                }
            }

        } catch (Throwable e) {
            txReceipt.setStatus(ExecuteStatus.ERROR);
            JsonObject errorLog = new JsonObject();
            errorLog.addProperty("error", e.getMessage());
            txReceipt.addLog(errorLog);
        }
        return txState;
    }

    public Object query(String method, JsonObject params) throws Exception {
        // TODO runtimeQuery will move to ContractWrap
        return runtimeQuery.query(method, params);
    }

    // TODO Remove This
    public StateStore<T> getStateStore() {
        return this.stateStore;
    }

    public TransactionReceiptStore getTransactionReceiptStore() {
        return this.txReceiptStore;
    }
}