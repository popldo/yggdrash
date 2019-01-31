package io.yggdrash.core.contract;

import com.google.gson.JsonObject;
import io.yggdrash.core.runtime.annotation.*;
import io.yggdrash.core.store.StateStore;
import io.yggdrash.core.store.Store;

import java.math.BigDecimal;

@YggdrashContract
public class TestContract implements Contract {

    @ContractStateStore
    Store<String, JsonObject> state;


    @ContractTransactionReceipt
    TransactionReceipt txReceipt;

    public void init(StateStore stateStore) {
    }

    @InvokeTransction
    public Boolean doNothing() {
        // pass
        return true;
    }

    @InvokeTransction
    public TransactionReceipt transfer(JsonObject params) {

        String to = params.get("to").getAsString().toLowerCase();
        BigDecimal amount = params.get("amount").getAsBigDecimal();
        return txReceipt;
    }

    @ContractQuery
    public String someQuery() {
        return "";
    }

    @ContractQuery
    public void voidQuery() {

    }
}
