package io.yggdrash.node.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImpl;
import io.yggdrash.common.utils.JsonUtil;
import io.yggdrash.core.blockchain.BranchGroup;
import io.yggdrash.core.blockchain.BranchId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@AutoJsonRpcServiceImpl
public class ContractApiImpl implements ContractApi {

    private static final Logger log = LoggerFactory.getLogger(ContractApiImpl.class);
    private final BranchGroup branchGroup;

    @Autowired
    public ContractApiImpl(BranchGroup branchGroup) {
        this.branchGroup = branchGroup;
    }

    @Override
    public Object query(String branchId, String contractVersion, String method, Map params) {
        JsonObject jsonParams = null;

        if (params != null && !params.isEmpty()) {
            jsonParams = JsonUtil.convertMapToJson(params);
        }

        Object result = null;
        try {
            result = branchGroup.query(BranchId.of(branchId), contractVersion, method, jsonParams);
            if (result instanceof JsonElement) {
                return JsonUtil.convertJsonToMap((JsonElement) result);
            } else if (result instanceof BigInteger) {
                return String.valueOf(result);
            } else if (result instanceof HashSet) {
                Set<Map> mapSet = new HashSet<>();
                Set<JsonElement> obj = (Set<JsonElement>) result;
                obj.stream().map(JsonUtil::convertJsonToMap).forEach(mapSet::add);
                return mapSet;
            }
        } catch (Exception e) { // TODO: check more exceptions and logics
            log.debug("Invalid query branch {}", branchId);
        }

        return result;
    }

}
