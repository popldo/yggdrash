package io.yggdrash.contract;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.yggdrash.common.contract.vo.dpoa.Validator;
import io.yggdrash.common.crypto.HashUtil;
import io.yggdrash.common.crypto.HexUtil;
import io.yggdrash.common.store.StateStore;
import io.yggdrash.common.store.datasource.HashMapDbSource;
import io.yggdrash.common.utils.BranchUtil;
import io.yggdrash.common.utils.ByteUtil;
import io.yggdrash.common.utils.ContractUtils;
import io.yggdrash.common.utils.FileUtil;
import io.yggdrash.common.utils.JsonUtil;
import io.yggdrash.contract.core.ExecuteStatus;
import io.yggdrash.contract.core.Receipt;
import io.yggdrash.contract.core.ReceiptImpl;
import io.yggdrash.core.blockchain.osgi.ContractCache;
import io.yggdrash.core.blockchain.osgi.ContractCacheImpl;
import io.yggdrash.core.blockchain.osgi.ContractChannelCoupler;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.InvalidCipherTextException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StemContractValidatorTest {

    private static final Logger log = LoggerFactory.getLogger(StemContractValidatorTest.class);
    private static final StemContract.StemService stemContract = new StemContract.StemService();

    private Field txReceiptField;
    TestYeed testYeed = new TestYeed();

    StateStore stateStore;

    TestWallet v1;
    TestWallet v2;
    TestWallet v3;
    TestWallet v4;

    String updateBranchId;
    String proposer = "5244d8163ea6fdd62aa08ae878b084faa0b013be";

    @Before
    public void setUp() throws IllegalAccessException, IOException, InvalidCipherTextException {
        // Setup StemContract

        stateStore = new StateStore(new HashMapDbSource());

        List<Field> txReceipt = ContractUtils.txReceiptFields(stemContract);
        if (txReceipt.size() == 1) {
            txReceiptField = txReceipt.get(0);
        }

        for (Field f : ContractUtils.stateStoreFields(stemContract)) {
            f.setAccessible(true);
            f.set(stemContract, stateStore);
        }

        // 1a0cdead3d1d1dbeef848fef9053b4f0ae06db9e
        JsonObject obj = new JsonObject();
        obj.addProperty("address", "1a0cdead3d1d1dbeef848fef9053b4f0ae06db9e");
        assertTrue("setup", BigInteger.ZERO.compareTo(testYeed.balanceOf(obj)) < 0);

        TestBranchStateStore branchStateStore = new TestBranchStateStore();
        branchStateStore.getValidators().getValidatorMap()
                .put("81b7e08f65bdf5648606c89998a9cc8164397647",
                        new Validator("81b7e08f65bdf5648606c89998a9cc8164397647"));

        for (Field f : ContractUtils.branchStateStoreField(stemContract)) {
            f.setAccessible(true);
            f.set(stemContract, branchStateStore);
        }

        // Create wallets
        String v1Path = getClass()
                .getResource("/validatorKeys/5244d8163ea6fdd62aa08ae878b084faa0b013be.json")
                .getFile();
        v1 = new TestWallet(new File(v1Path), "Aa1234567890!");

        String v2Path = getClass()
                .getResource("/validatorKeys/a4e063d728ee7a45c5bab3aa2283822d49a9f73a.json")
                .getFile();
        v2 = new TestWallet(new File(v2Path), "Password1234!");

        String v3Path = getClass()
                .getResource("/validatorKeys/e38f532685b5e61eca5bc25a0da8ea87d74e671e.json")
                .getFile();
        v3 = new TestWallet(new File(v3Path), "Password1234!");

        String v4Path = getClass()
                .getResource("/validatorKeys/f5927c28b66d4bb4b50395662a097370e8cd7e58.json")
                .getFile();
        v4 = new TestWallet(new File(v4Path), "Password1234!");

        // Update Branch is
        InputStream testUpdateBranch = getClass().getClassLoader().getResourceAsStream("update-branch.json");
        String branchString = IOUtils.toString(testUpdateBranch, FileUtil.DEFAULT_CHARSET);
        JsonObject updateBranchObject = JsonUtil.parseJsonObject(branchString);
        byte[] rawBranchId = BranchUtil.branchIdGenerator(updateBranchObject);
        updateBranchId = HexUtil.toHexString(rawBranchId);

        Receipt receipt = createReceipt(proposer);
        receipt.setIssuer(proposer);
        setUpReceipt(receipt);

        ContractChannelCoupler coupler = new ContractChannelCoupler();
        ContractCache cache = new ContractCacheImpl();
        Map<String, Object> contractMap = new HashMap<>();
        coupler.setContract(contractMap, cache);

        for (Field f : ContractUtils.contractChannelField(stemContract)) {
            f.setAccessible(true);
            f.set(stemContract, coupler);
        }

        cache.cacheContract("STEM", stemContract);
        cache.cacheContract("YEED", testYeed);

        contractMap.put("STEM", stemContract);
        contractMap.put("YEED", testYeed);

        JsonObject param = new JsonObject();
        param.add("branch", updateBranchObject);
        param.addProperty("serviceFee", BigInteger.valueOf(1000000));

        stemContract.create(param);

        assertTrue("Branch Create Success", receipt.isSuccess());
    }

    @Test
    public void updateValidatorsAddValidator() {
        // Validator can suggest validator and other validator vote to suggest validator
        // A transaction contains all messages

        // The Message property
        //  BRANCH_ID , (20byte)
        //  BLOCK_HEIGHT (8 BYTE),
        //  PROPOSER_VALIDATOR (20 byte)
        //  TARGET_VALIDATOR (20 byte)
        //  OPERATING_FLAG (1byte)
        //  SIGNATURE (65 byte) ( N Validators )

        String targetBranchId = updateBranchId;
        Long blockHeight = 100L;

        log.debug("BranchId : {}", updateBranchId);

        // Add a new validator
        String targetValidator = "c91e9d46dd4b7584f0b6348ee18277c10fd7cb94";
        String operatingFlag = StemOperation.ADD_VALIDATOR.toValue(); // ADD OR DELETE OR REPLACE

        byte[] message = ByteUtil.merge(
                HexUtil.hexStringToBytes(targetBranchId),
                ByteUtil.longToBytes(blockHeight),
                HexUtil.hexStringToBytes(proposer),
                HexUtil.hexStringToBytes(targetValidator),
                operatingFlag.getBytes()
        );
        // Message hashed by sha3
        message = HashUtil.sha3(message);
        log.debug("Message Size : {} ", message.length);
        assertEquals("Message Length : ", 32, message.length);

        // The message is signed by the validators
        byte[] signV1 = v1.sign(message, true);
        byte[] signV2 = v2.sign(message, true);
        byte[] signV3 = v3.sign(message, true);
        byte[] signV4 = v4.sign(message, true);

        String[] signed = new String[]{
                HexUtil.toHexString(signV1), HexUtil.toHexString(signV2),
                HexUtil.toHexString(signV3), HexUtil.toHexString(signV4)
        };

        // Create a param with list of signatures
        JsonArray signedArray = new JsonArray();
        Arrays.stream(signed).forEach(signedArray::add);

        JsonObject updateBranchValidator = new JsonObject();
        updateBranchValidator.addProperty("branchId", targetBranchId);
        updateBranchValidator.addProperty("blockHeight", blockHeight);
        updateBranchValidator.addProperty("proposer", proposer);
        updateBranchValidator.addProperty("targetValidator", targetValidator);
        updateBranchValidator.addProperty("operatingFlag", StemOperation.ADD_VALIDATOR.toValue());
        updateBranchValidator.add("signed", signedArray);

        log.debug(JsonUtil.prettyFormat(updateBranchValidator));

        Receipt updateReceipt = createReceipt(proposer);
        setUpReceipt(updateReceipt);

        stemContract.updateValidator(updateBranchValidator);
        assertEquals(ExecuteStatus.SUCCESS, updateReceipt.getStatus());

        JsonObject validators = stemContract.getValidators(updateBranchId);
        log.debug(JsonUtil.prettyFormat(validators));

        assertEquals("Add new Validator", 5, validators.getAsJsonArray("validators").size());
    }

    @Test
    public void updateValidatorsRemoveValidator() {
        // Remove validator

        String targetBranchId = updateBranchId;
        Long blockHeight = 100L;

        String targetValidator = "a4e063d728ee7a45c5bab3aa2283822d49a9f73a";
        String operatingFlag = StemOperation.REMOVE_VALIDATOR.toValue(); // ADD OR DELETE OR REPLACE

        byte[] message = ByteUtil.merge(
                HexUtil.hexStringToBytes(targetBranchId),
                ByteUtil.longToBytes(blockHeight),
                HexUtil.hexStringToBytes(proposer),
                HexUtil.hexStringToBytes(targetValidator),
                operatingFlag.getBytes()
        );
        // Message hashed by sha3
        message = HashUtil.sha3(message);
        log.debug("message Size : {} ", message.length);
        assertEquals("message Length : ", 32, message.length);

        log.debug(updateBranchId);

        // Remove exist validator (v2)
        byte[] signV1 = v1.sign(message, true);
        //byte[] signV2 = v2.sign(message, true);
        byte[] signV3 = v3.sign(message, true);
        byte[] signV4 = v4.sign(message, true);

        String[] signed = new String[]{
                HexUtil.toHexString(signV1), HexUtil.toHexString(signV3), HexUtil.toHexString(signV4)
        };

        // Create a param with list of signatures
        JsonArray signedArray = new JsonArray();
        Arrays.stream(signed).forEach(sg -> signedArray.add(sg));

        JsonObject updateBranchValiator = new JsonObject();
        updateBranchValiator.addProperty("branchId", targetBranchId);
        updateBranchValiator.addProperty("blockHeight", blockHeight);
        updateBranchValiator.addProperty("proposer", proposer);
        updateBranchValiator.addProperty("targetValidator", targetValidator);
        updateBranchValiator.addProperty("operatingFlag", operatingFlag);
        updateBranchValiator.add("signed", signedArray);

        log.debug(JsonUtil.prettyFormat(updateBranchValiator));

        Receipt updateReceipt = createReceipt(proposer);
        setUpReceipt(updateReceipt);

        stemContract.updateValidator(updateBranchValiator);
        assertEquals(ExecuteStatus.SUCCESS, updateReceipt.getStatus());

        JsonObject validators = stemContract.getValidators(updateBranchId);
        log.debug(JsonUtil.prettyFormat(validators));

        Set<String> validatorSet = JsonUtil.convertJsonArrayToSet(validators.getAsJsonArray("validators"));
        assertEquals("remove Validator", 3, validators.getAsJsonArray("validators").size());
        assertTrue("remove check validator", !validatorSet.contains(targetValidator));
    }

    @Test
    public void updateValidatorsReplaceValidator() {
        // Remove and add validator

        String targetBranchId = updateBranchId;
        Long blockHeight = 100L;
        String targetValidator = "c91e9d46dd4b7584f0b6348ee18277c10fd7cb94";
        String operatingFlag = StemOperation.REPLACE_VALIDATOR.toValue(); // ADD OR DELETE OR REPLACE

        byte[] message = ByteUtil.merge(
                HexUtil.hexStringToBytes(targetBranchId),
                ByteUtil.longToBytes(blockHeight),
                HexUtil.hexStringToBytes(proposer),
                HexUtil.hexStringToBytes(targetValidator),
                operatingFlag.getBytes()
        );
        // Message hashed by sha3
        message = HashUtil.sha3(message);
        log.debug("message Size : {} ", message.length);
        assertEquals("message Length : ", 32, message.length);

        log.debug(updateBranchId);

        // Remove exist validator (v2)
        byte[] signV1 = v1.sign(message, true);
        //byte[] signV2 = v2.sign(message, true);
        byte[] signV3 = v3.sign(message, true);
        byte[] signV4 = v4.sign(message, true);

        String[] signed = new String[]{
                HexUtil.toHexString(signV1), HexUtil.toHexString(signV3), HexUtil.toHexString(signV4)
        };

        // Create a param with list of signatures
        JsonArray signedArray = new JsonArray();
        Arrays.stream(signed).forEach(sg -> signedArray.add(sg));

        JsonObject updateBranchValiator = new JsonObject();
        updateBranchValiator.addProperty("branchId", targetBranchId);
        updateBranchValiator.addProperty("blockHeight", blockHeight);
        updateBranchValiator.addProperty("proposer", proposer);
        updateBranchValiator.addProperty("targetValidator", targetValidator);
        updateBranchValiator.addProperty("operatingFlag", operatingFlag);
        updateBranchValiator.add("signed", signedArray);

        log.debug(JsonUtil.prettyFormat(updateBranchValiator));

        Receipt updateReceipt = createReceipt(proposer);
        setUpReceipt(updateReceipt);

        stemContract.updateValidator(updateBranchValiator);
        assertEquals(ExecuteStatus.SUCCESS, updateReceipt.getStatus());

        JsonObject validators = stemContract.getValidators(updateBranchId);
        log.debug(JsonUtil.prettyFormat(validators));

        Set<String> validatorSet = JsonUtil.convertJsonArrayToSet(validators.getAsJsonArray("validators"));
        assertTrue("remove check validator", validatorSet.contains(targetValidator));
        assertTrue("remove check validator", !validatorSet.contains(proposer));
    }

    @Test
    public void updateValidatorsUpdateValidatorSet() {
        // Update validator Set

        JsonObject targetValidatorSet = stemContract.getValidators(updateBranchId);
        targetValidatorSet.getAsJsonArray("validators").add("101167aaf090581b91c08480f6e559acdd9a3ddd");
        targetValidatorSet.getAsJsonArray("validators").add("ffcbff030ecfa17628abdd0ff1990be003da35a2");
        targetValidatorSet.getAsJsonArray("validators").add("b0aee21c81bf6057efa9a321916f0f1a12f5c547");

        log.debug(JsonUtil.prettyFormat(targetValidatorSet));

        byte[] validatorsByteArray = targetValidatorSet.toString().getBytes(StandardCharsets.UTF_8);
        byte[] validatorsSha3 = HashUtil.sha3omit12(validatorsByteArray);
        String targetValidator = HexUtil.toHexString(validatorsSha3);

        String targetBranchId = updateBranchId;
        Long blockHeight = 100L;
        String operatingFlag = StemOperation.UPDATE_VALIDATOR_SET.toValue(); // ADD OR DELETE OR REPLACE

        byte[] message = ByteUtil.merge(
                HexUtil.hexStringToBytes(targetBranchId),
                ByteUtil.longToBytes(blockHeight),
                HexUtil.hexStringToBytes(proposer),
                HexUtil.hexStringToBytes(targetValidator),
                operatingFlag.getBytes()
        );
        // message make to sha3
        message = HashUtil.sha3(message);
        log.debug("message Size : {} ", message.length);
        assertEquals("message Length : ", 32, message.length);

        log.debug(updateBranchId);

        byte[] signV1 = v1.sign(message, true);
        byte[] signV2 = v2.sign(message, true);
        byte[] signV3 = v3.sign(message, true);
        byte[] signV4 = v4.sign(message, true);

        String[] signed = new String[]{
                HexUtil.toHexString(signV1),
                HexUtil.toHexString(signV2),
                HexUtil.toHexString(signV3),
                HexUtil.toHexString(signV4)
        };

        // Create a param with list of signatures
        JsonArray signedArray = new JsonArray();
        Arrays.stream(signed).forEach(sg -> signedArray.add(sg));

        JsonObject updateBranchValiator = new JsonObject();
        updateBranchValiator.addProperty("branchId", targetBranchId);
        updateBranchValiator.addProperty("blockHeight", blockHeight);
        updateBranchValiator.addProperty("proposer", proposer);
        updateBranchValiator.addProperty("targetValidator", targetValidator);
        updateBranchValiator.addProperty("operatingFlag", operatingFlag);
        updateBranchValiator.add("signed", signedArray);
        updateBranchValiator.add("validators", targetValidatorSet.getAsJsonArray("validators"));

        log.debug(JsonUtil.prettyFormat(updateBranchValiator));

        Receipt updateReceipt = createReceipt(proposer);
        setUpReceipt(updateReceipt);

        stemContract.updateValidator(updateBranchValiator);
        assertEquals(ExecuteStatus.SUCCESS, updateReceipt.getStatus());

        JsonObject validators = stemContract.getValidators(updateBranchId);
        log.debug(JsonUtil.prettyFormat(validators));

        assertEquals(7, targetValidatorSet.getAsJsonArray("validators").size());
    }

    private Receipt createReceipt(String issuer) {
        Receipt receipt = new ReceiptImpl();
        receipt.setIssuer(issuer);
        return receipt;
    }

    private void setUpReceipt(Receipt receipt) {
        try {
            txReceiptField.set(stemContract, receipt);
            testYeed.setTxReceipt(receipt);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

}
