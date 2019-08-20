package io.yggdrash.contract.token;import java.math.BigInteger;import java.util.Hashtable;import com.google.gson.JsonObject;import io.yggdrash.common.contract.vo.PrefixKeyEnum;import io.yggdrash.common.crypto.HashUtil;import io.yggdrash.common.crypto.HexUtil;import io.yggdrash.common.store.BranchStateStore;import io.yggdrash.common.utils.ByteUtil;import io.yggdrash.contract.core.ExecuteStatus;import io.yggdrash.contract.core.TransactionReceipt;import io.yggdrash.contract.core.annotation.ContractBranchStateStore;import io.yggdrash.contract.core.annotation.ContractChannelField;import io.yggdrash.contract.core.annotation.ContractChannelMethod;import io.yggdrash.contract.core.annotation.ContractQuery;import io.yggdrash.contract.core.annotation.ContractStateStore;import io.yggdrash.contract.core.annotation.ContractTransactionReceipt;import io.yggdrash.contract.core.annotation.Genesis;import io.yggdrash.contract.core.annotation.InvokeTransaction;import io.yggdrash.contract.core.channel.ContractChannel;import io.yggdrash.contract.core.channel.ContractMethodType;import io.yggdrash.contract.core.store.ReadWriterStore;import org.osgi.framework.BundleActivator;import org.osgi.framework.BundleContext;import org.osgi.framework.ServiceEvent;import org.osgi.framework.ServiceListener;import org.slf4j.Logger;import org.slf4j.LoggerFactory;public class TokenContract implements BundleActivator, ServiceListener {    private static final String TOKEN_PREFIX = "TOKEN-";    private static final String TOKEN_ID = "TOKEN_ID";    private static final String TOKEN_NAME = "TOKEN_NAME";    private static final String TOKEN_OWNER_ACCOUNT = "TOKEN_OWNER_ACCOUNT";    private static final String TOKEN_INIT_STAKE_YEED_AMOUNT = "TOKEN_INIT_STAKE_YEED_AMOUNT";    private static final String TOKEN_INIT_MINT_AMOUNT = "TOKEN_INIT_MINT_AMOUNT";    private static final String TOKEN_MINTABLE = "TOKEN_MINTABLE";    private static final String TOKEN_BURNABLE = "TOKEN_BURNABLE";    private static final String TOKEN_EXCHANGEABLE = "TOKEN_EXCHANGEABLE";    private static final String TOKEN_EX_TYPE = "TOKEN_EX_TYPE";    private static final String TOKEN_EX_TYPE_FIXED = "TOKEN_EX_TYPE_FIXED";    private static final String TOKEN_EX_TYPE_LINED = "TOKEN_EX_TYPE_LINKED";    private static final String TOKEN_EX_RATE = "TOKEN_EX_RATE";    private static final String TOKEN_PHASE = "TOKEN_PHASE";    private static final String TOKEN_PHASE_INIT = "init";    private static final String TOKEN_PHASE_RUN = "run";    private static final String TOKEN_PHASE_PAUSE = "pause";    private static final String TOKEN_PHASE_STOP = "stop";    private static final String TOTAL_SUPPLY = "TOTAL_SUPPLY";    private static final String ADDRESS = "address";    private static final String AMOUNT = "amount";    private static final String BALANCE = "balance";    private static final String SPENDER = "spender";    private static final String OWNER = "owner";    private static final Logger log = LoggerFactory.getLogger(TokenContract.class);    @Override    public void start(BundleContext context) throws Exception {        log.info("Start Token Contract");        Hashtable<String, String> props = new Hashtable<>();        props.put("YGGDRASH", "Token");        context.registerService(TokenService.class.getName(), new TokenService(), props);    }    @Override    public void stop(BundleContext context) {        log.info("Stop Token contract");    }    @Override    public void serviceChanged(ServiceEvent event) {        log.info("Token contract serviceChanged called");    }    public static class TokenService {        @ContractChannelField        public ContractChannel channel;        @ContractTransactionReceipt        TransactionReceipt txReceipt;        @ContractStateStore        ReadWriterStore<String, JsonObject> store;        @ContractBranchStateStore        BranchStateStore branchStateStore;        private static String getTokenAddress(String tokenId, String address) {            return tokenId.concat("-").concat(address);        }        /**         * @return Total amount of token in existence         */        @ContractQuery        public BigInteger totalSupply(String tokenId) {            return getBalance(tokenId, TOTAL_SUPPLY);        }        @ContractQuery        public BigInteger balanceOf(JsonObject params) {            String tokenId = params.get(TOKEN_ID).getAsString().toLowerCase();            String address = params.get(ADDRESS).getAsString().toLowerCase();            return getBalance(tokenId, address);        }        @ContractQuery        @ContractChannelMethod        public BigInteger getYeedBalanceOf(JsonObject params) {            String tokenId = params.get(TOKEN_ID).getAsString().toLowerCase();            // FIXME !!!!!!!!!!!!!!/            /*            String yeedContractVersion = this.branchStateStore.getContractVersion("YEED");            JsonObject param = new JsonObject();            String tokenAccountAddress = makeTokenAccountAddress(tokenId);            param.addProperty(ADDRESS, tokenAccountAddress);            JsonObject result = this.channel.call(                    yeedContractVersion, ContractMethodType.CHANNEL_METHOD, "balanceOf", param);            // return YEED balance of token account address            return result.get("result").getAsBigInteger();             */            return null;        }        @ContractQuery        @ContractChannelMethod        public BigInteger getTokenBalanceOf(JsonObject params) {            String tokenId = params.get(TOKEN_ID).getAsString().toLowerCase();            String tokenAccountAddress = makeTokenAccountAddress(tokenId);            return getBalance(tokenId, tokenAccountAddress);        }        @ContractQuery        public BigInteger allowance(JsonObject params) {            String tokenId = params.get(TOKEN_ID).getAsString().toLowerCase();            String owner = params.get(OWNER).getAsString().toLowerCase();            String spender = params.get(SPENDER).getAsString().toLowerCase();            String approveKey = approveKey(owner, spender);            return getBalance(tokenId, approveKey);        }        /**         * Genesis of CONTRACT not TOKEN         */        @Genesis        @InvokeTransaction        public TransactionReceipt init(JsonObject params) {            // TODO : @kevin : 2019-08-20 : check what to do            setSuccessTxReceipt("Token contract i18n completed successfully.");            return txReceipt;        }        private JsonObject makeTokenObject(JsonObject params) {            JsonObject token = new JsonObject();            token.addProperty(TOKEN_ID, params.get(TOKEN_ID).getAsString().toLowerCase());            token.addProperty(TOKEN_NAME, params.get(TOKEN_NAME).getAsString());            token.addProperty(TOKEN_INIT_STAKE_YEED_AMOUNT, params.get(TOKEN_INIT_STAKE_YEED_AMOUNT).getAsBigInteger());            // TODO : @kevin : 2019-08-20 : check owner account == create token issuer            token.addProperty(TOKEN_OWNER_ACCOUNT, txReceipt.getIssuer());            token.addProperty(TOKEN_INIT_MINT_AMOUNT, params.get(TOKEN_NAME).getAsBigInteger());            token.addProperty(TOKEN_MINTABLE, params.get(TOKEN_MINTABLE).getAsBoolean());            token.addProperty(TOKEN_BURNABLE, params.get(TOKEN_BURNABLE).getAsBoolean());            token.addProperty(TOKEN_EXCHANGEABLE, params.get(TOKEN_EXCHANGEABLE).getAsBoolean());            token.addProperty(TOKEN_EX_TYPE, params.get(TOKEN_EX_TYPE).getAsString());            token.addProperty(TOKEN_EX_RATE, params.get(TOKEN_EX_RATE).getAsDouble());            token.addProperty(TOKEN_PHASE, TOKEN_PHASE_INIT);            return token;        }        private void saveTokenObject(JsonObject token) {            String tokenId = token.get(TOKEN_ID).getAsString();            store.put(TOKEN_PREFIX.concat(tokenId), token);        }        @InvokeTransaction        public TransactionReceipt createToken(JsonObject params) {            JsonObject token = makeTokenObject(params);            String tokenId = token.get(TOKEN_ID).getAsString();            BigInteger totalSupply = token.get(TOKEN_INIT_MINT_AMOUNT).getAsBigInteger();            putBalance(tokenId, TOTAL_SUPPLY, totalSupply);            // STAKE            BigInteger stake = token.get(TOKEN_INIT_STAKE_YEED_AMOUNT).getAsBigInteger();            JsonObject param = new JsonObject();            param.addProperty("from", token.get(TOKEN_OWNER_ACCOUNT).getAsString());            param.addProperty("to", makeTokenAccountAddress(tokenId));            param.addProperty(AMOUNT, stake);            String yeedContractVersion = this.branchStateStore.getContractVersion("YEED");            log.debug("YEED Contract {}", yeedContractVersion);            JsonObject result = this.channel.call(yeedContractVersion,                    ContractMethodType.CHANNEL_METHOD, "transferChannel", param);            boolean isSuccess = result.get("result").getAsBoolean();            if (isSuccess == false) {                setErrorTxReceipt("Insufficient balance to stake!");                return txReceipt;            }            // create token success!            saveTokenObject(token);            setSuccessTxReceipt(                    String.format("Token [%s] creation completed successfully. Initial mint amount is %s.", tokenId, totalSupply));            return txReceipt;        }        @InvokeTransaction        public TransactionReceipt movePhaseRun(JsonObject params) {            String issuer = txReceipt.getIssuer();            String tokenId = params.get(TOKEN_ID).getAsString().toLowerCase();            JsonObject token = findTokenFromStore(tokenId);            if (issuer.equals(token.get(TOKEN_OWNER_ACCOUNT).getAsString()) == false) {                setErrorTxReceipt("Issuer must be token owner!");                return txReceipt;            }            String phase = token.get(TOKEN_PHASE).getAsString();            if ((TOKEN_PHASE_INIT.equals(phase) || TOKEN_PHASE_PAUSE.equals(phase)) == false) {                setErrorTxReceipt("If you want to move token phase to RUN, current token phase must be INIT or PAUSE!");                return txReceipt;            }            token.addProperty(TOKEN_PHASE, TOKEN_PHASE_RUN);            saveTokenObject(token);            setSuccessTxReceipt("Token phase was moved to RUN!");            return txReceipt;        }        @InvokeTransaction        public TransactionReceipt movePhasePause(JsonObject params) {            String issuer = txReceipt.getIssuer();            String tokenId = params.get(TOKEN_ID).getAsString().toLowerCase();            JsonObject token = findTokenFromStore(tokenId);            if (issuer.equals(token.get(TOKEN_OWNER_ACCOUNT).getAsString()) == false) {                setErrorTxReceipt("Issuer must be token owner!");                return txReceipt;            }            String phase = token.get(TOKEN_PHASE).getAsString();            if (TOKEN_PHASE_RUN.equals(phase) == false) {                setErrorTxReceipt("If you want to move token phase to PAUSE, current token phase must be RUN!");                return txReceipt;            }            token.addProperty(TOKEN_PHASE, TOKEN_PHASE_PAUSE);            saveTokenObject(token);            setSuccessTxReceipt("Token phase was moved to PAUSE!");            return txReceipt;        }        @InvokeTransaction        public TransactionReceipt movePhaseStop(JsonObject params) {            String issuer = txReceipt.getIssuer();            String tokenId = params.get(TOKEN_ID).getAsString().toLowerCase();            JsonObject token = findTokenFromStore(tokenId);            if (issuer.equals(token.get(TOKEN_OWNER_ACCOUNT).getAsString()) == false) {                setErrorTxReceipt("Issuer must be token owner!");                return txReceipt;            }            String phase = token.get(TOKEN_PHASE).getAsString();            if ((TOKEN_PHASE_RUN.equals(phase) || TOKEN_PHASE_PAUSE.equals(phase)) == false) {                setErrorTxReceipt("If you want to move token phase to STOP, current token phase must be RUN or PAUSE!");                return txReceipt;            }            token.addProperty(TOKEN_PHASE, TOKEN_PHASE_STOP);            saveTokenObject(token);            setSuccessTxReceipt("Token phase was moved to STOP!");            return txReceipt;        }        // TODO : @kevin : 2019-08-20 : should check destroy needed        @InvokeTransaction        public TransactionReceipt destroyToken(JsonObject params) {            // caller 가 토큰 오너인가            // 현재 상태가 stop 인가            return null;        }        @InvokeTransaction        public TransactionReceipt transfer(JsonObject params) {            String tokenId = params.get(TOKEN_ID).getAsString().toLowerCase();            if (isTokenRunning(tokenId) == false) {                setErrorTxReceipt("Token is not running!");                return txReceipt;            }            String from = txReceipt.getIssuer();            BigInteger fromBalance = getBalance(tokenId, from);            BigInteger transferAmount = params.get(AMOUNT).getAsBigInteger();            if (transferAmount.compareTo(BigInteger.ZERO) <= 0) {                setErrorTxReceipt("Transfer amount must be greater than ZERO!");                return txReceipt;            }            if (fromBalance.compareTo(transferAmount) < 0) {                setErrorTxReceipt("Insufficient balance to transfer!");                return txReceipt;            }            // TODO : @kevin : 2019-08-20 : should pay YEED fee from stake            String to = params.get("to").getAsString().toLowerCase();            BigInteger newFromBalance = getBalance(tokenId, from).subtract(transferAmount);            BigInteger newToBalance = getBalance(tokenId, to).add(transferAmount);            putBalance(tokenId, from, newFromBalance);            putBalance(tokenId, to, newToBalance);            setSuccessTxReceipt(                    String.format("[Token Transferred] Token [%s] transfer %s from %s to %s.", tokenId, transferAmount, from, to));            return txReceipt;        }        @InvokeTransaction        public TransactionReceipt approve(JsonObject params) {            String tokenId = params.get(TOKEN_ID).getAsString().toLowerCase();            if (isTokenRunning(tokenId) == false) {                setErrorTxReceipt("Token is not running!");                return txReceipt;            }            String sender = txReceipt.getIssuer();            BigInteger approveAmount = params.get(AMOUNT).getAsBigInteger();            BigInteger senderBalance = getBalance(tokenId, sender);            if (senderBalance.compareTo(approveAmount) < 0) {                setErrorTxReceipt("Insufficient balance to approve!");                return txReceipt;            }            String spender = params.get(SPENDER).getAsString().toLowerCase();            String approveKey = approveKey(sender, spender);            putBalance(tokenId, approveKey, approveAmount);            setSuccessTxReceipt(                    String.format("[Token Approved] Token [%s] approve %s to %s from %s.", tokenId, spender, approveAmount, sender));            return txReceipt;        }        @InvokeTransaction        public TransactionReceipt transferFrom(JsonObject params) {            String tokenId = params.get(TOKEN_ID).getAsString().toLowerCase();            if (isTokenRunning(tokenId) == false) {                setErrorTxReceipt("Token is not running!");                return txReceipt;            }            String from = params.get("from").getAsString().toLowerCase();            String to = params.get("to").getAsString().toLowerCase();            BigInteger transferAmount = params.get(AMOUNT).getAsBigInteger();            if (transferAmount.compareTo(BigInteger.ZERO) <= 0) {                setErrorTxReceipt("Transfer amount must be greater than ZERO!");                return txReceipt;            }            String spender = from;            String sender = txReceipt.getIssuer();            String approveKey = approveKey(spender, sender);            BigInteger approveBalance = getBalance(tokenId, approveKey);            if (transferAmount.compareTo(approveBalance) > 0) {                setErrorTxReceipt("Insufficient approved balance to transferFrom!");                return txReceipt;            }            BigInteger newApproveBalance = getBalance(tokenId, approveKey).subtract(transferAmount);            BigInteger newToBalance = getBalance(tokenId, to).add(transferAmount);            putBalance(tokenId, approveKey, newApproveBalance);            putBalance(tokenId, to, newToBalance);            setSuccessTxReceipt(                    String.format("[Token TransferredFrom] Token [%s] transferred %s from %s to %s by %s.", tokenId, transferAmount, from, to, spender));            return txReceipt;        }        @InvokeTransaction        public TransactionReceipt mint(JsonObject params) {            String issuer = txReceipt.getIssuer();            String tokenId = params.get(TOKEN_ID).getAsString().toLowerCase();            JsonObject token = findTokenFromStore(tokenId);            if (issuer.equals(token.get(TOKEN_OWNER_ACCOUNT).getAsString()) == false) {                setErrorTxReceipt("Issuer must be token owner!");                return txReceipt;            }            boolean mintable = token.get(TOKEN_MINTABLE).getAsBoolean();            if (mintable == false) {                setErrorTxReceipt("Token is not mintable!");                return txReceipt;            }            String tokenOwnerAccount = token.get(TOKEN_OWNER_ACCOUNT).getAsString();            BigInteger mintAmount = params.get(AMOUNT).getAsBigInteger();            if (mintAmount.compareTo(BigInteger.ZERO) <= 0) {                setErrorTxReceipt("Mint amount must be greater than ZERO!");                return txReceipt;            }            BigInteger tokenOwnerAccountBalance = getBalance(tokenId, tokenOwnerAccount);            BigInteger newTokenOwnerAccountBalance = tokenOwnerAccountBalance.add(mintAmount);            putBalance(tokenId, tokenOwnerAccount, newTokenOwnerAccountBalance);            BigInteger totalSupply = getBalance(tokenId, TOTAL_SUPPLY);            putBalance(tokenId, TOTAL_SUPPLY, totalSupply.add(mintAmount));            setSuccessTxReceipt(                    String.format("[Token Minted] Token [%s] minted %s.", tokenId, mintAmount));            return txReceipt;        }        @InvokeTransaction        public TransactionReceipt burn(JsonObject params) {            String issuer = txReceipt.getIssuer();            String tokenId = params.get(TOKEN_ID).getAsString().toLowerCase();            JsonObject token = findTokenFromStore(tokenId);            if (issuer.equals(token.get(TOKEN_OWNER_ACCOUNT).getAsString()) == false) {                setErrorTxReceipt("Issuer must be token owner!");                return txReceipt;            }            boolean burnable = token.get(TOKEN_BURNABLE).getAsBoolean();            if (burnable == false) {                setErrorTxReceipt("Token is not burnable!");                return txReceipt;            }            String tokenOwnerAccount = token.get(TOKEN_OWNER_ACCOUNT).getAsString();            BigInteger burnAmount = params.get(AMOUNT).getAsBigInteger();            if (burnAmount.compareTo(BigInteger.ZERO) <= 0) {                setErrorTxReceipt("Burn amount must be greater than ZERO!");                return txReceipt;            }            BigInteger tokenOwnerAccountBalance = getBalance(tokenId, tokenOwnerAccount);            if (tokenOwnerAccountBalance.compareTo(burnAmount) < 0) {                setErrorTxReceipt("Insufficient token owner balance to burn!");                return txReceipt;            }            BigInteger newTokenOwnerAccountBalance = tokenOwnerAccountBalance.subtract(burnAmount);            putBalance(tokenId, tokenOwnerAccount, newTokenOwnerAccountBalance);            BigInteger totalSupply = getBalance(tokenId, TOTAL_SUPPLY);            putBalance(tokenId, TOTAL_SUPPLY, totalSupply.subtract(burnAmount));            setSuccessTxReceipt(                    String.format("[Token Burned] Token [%s] burned %s.", tokenId, burnAmount));            return txReceipt;        }        @InvokeTransaction        public TransactionReceipt exchangeT2Y(JsonObject params) {            String tokenId = params.get(TOKEN_ID).getAsString().toLowerCase();            if (isTokenRunning(tokenId) == false) {                setErrorTxReceipt("Token is not running!");                return txReceipt;            }            String issuer = txReceipt.getIssuer();            JsonObject token = findTokenFromStore(tokenId);            boolean exchangeable = token.get(TOKEN_EXCHANGEABLE).getAsBoolean();            if (exchangeable == false) {                setErrorTxReceipt("Token is not exchangeable!");                return txReceipt;            }            BigInteger issuerTokenBalance = getBalance(tokenId, issuer);            BigInteger exchangeAmount = params.get(AMOUNT).getAsBigInteger();            if (exchangeAmount.compareTo(BigInteger.ZERO) <= 0) {                setErrorTxReceipt("Exchange amount must be greater than ZERO!");                return txReceipt;            }            if (issuerTokenBalance.compareTo(exchangeAmount) < 0) {                setErrorTxReceipt("Insufficient balance to exchange!");                return txReceipt;            }            String exType = token.get(TOKEN_EX_TYPE).getAsString();            switch (exType) {                case TOKEN_EX_TYPE_FIXED :                    double exRate = params.get(TOKEN_EX_RATE).getAsDouble();                    long yeedAmountL = (long)((double)(exchangeAmount.longValue()) / exRate);                    BigInteger yeedAmount = BigInteger.valueOf(yeedAmountL);                    // tokenAccountAddress -> issuer 로 yeedAmount 전송                    // YeedContract.transferFrom() 을 사용해야 한다.                        // tokenOwner 가 tokenAccountAddress 에 대해 YeedContract.approve() 를 해두었어야 가능                    break;                case TOKEN_EX_TYPE_LINED :                    break;                default :                    break;            }            // 교환 유형과 교환비를 구한다.            // caller 소유 토큰을 YEED 로 교환한다            // token amount 만큼 totalSupply 를 감소시킨다            return null;        }        @InvokeTransaction        public TransactionReceipt exchangeY2T(JsonObject params) {            // exchangeable 한가            // caller 소유 YEED 잔고가 yeed amount 보다 큰가            // 교환 유형과 교환비를 구한다.            // caller 소유 YEED 를 토큰으로 교환한다            // token amount 만큼 totalSupply 를 증가시킨다            // 교환 대상 YEED 는 토큰 계정으로 보낸다. (스테이크)            // TOTAL_SUPPLY            return null;        }        @InvokeTransaction        public TransactionReceipt exchangeT2T(JsonObject params) {            // exchangeable 한가            // caller 소유 토큰 잔고가 token amount 보다 큰가            // 교환 유형과 교환비를 구한다.            // TOTAL_SUPPLY            // TODO : @kevin : 2019-08-19 : check exchange t2t spec!!!!            return null;        }        private BigInteger getBalance(String tokenId, String address) {            String targetAddress =                    PrefixKeyEnum.ACCOUNT.toValue().concat(getTokenAddress(tokenId, address));            JsonObject storeValue = store.get(targetAddress);            return storeValue != null && storeValue.has(BALANCE)                    ? storeValue.get(BALANCE).getAsBigInteger() : BigInteger.ZERO;        }        private void putBalance(String tokenId, String address, BigInteger value) {            String targetAddress =                    PrefixKeyEnum.ACCOUNT.toValue().concat(getTokenAddress(tokenId, address));            JsonObject storeValue = new JsonObject();            storeValue.addProperty(BALANCE, value);            store.put(targetAddress, storeValue);        }        private String approveKey(String sender, String spender) {            byte[] approveKeyByteArray = ByteUtil.merge(sender.getBytes(), spender.getBytes());            byte[] approveKey = HashUtil.sha3(approveKeyByteArray);            return PrefixKeyEnum.APPROVE.toValue().concat(HexUtil.toHexString(approveKey));        }        private void setErrorTxReceipt(String msg) {            this.txReceipt.setStatus(ExecuteStatus.ERROR);            this.txReceipt.addLog(msg);        }        private void setSuccessTxReceipt(String msg) {            this.txReceipt.setStatus(ExecuteStatus.SUCCESS);            this.txReceipt.addLog(msg);        }        private JsonObject findTokenFromStore(String tokenId) {            return store.get(TOKEN_PREFIX.concat(tokenId));        }        private String makeTokenAccountAddress(String tokenId) {            // TODO : @kevin : 2019-08-16 : should define the format of tokenAccountAddress            return tokenId;        }        private boolean isTokenRunning(String tokenId) {            return TOKEN_PHASE_RUN.equals(findTokenFromStore(tokenId).get(TOKEN_PHASE).getAsString());        }    }}