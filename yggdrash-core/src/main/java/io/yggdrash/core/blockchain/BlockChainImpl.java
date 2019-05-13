package io.yggdrash.core.blockchain;

import io.yggdrash.common.Sha3Hash;
import io.yggdrash.common.exception.FailedOperationException;
import io.yggdrash.core.blockchain.osgi.ContractManager;
import io.yggdrash.core.consensus.Consensus;
import io.yggdrash.core.consensus.ConsensusBlock;
import io.yggdrash.core.exception.NotValidateException;
import io.yggdrash.core.runtime.result.BlockRuntimeResult;
import io.yggdrash.core.store.BlockKeyStore;
import io.yggdrash.core.store.BranchStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BlockChainImpl<T, V> implements BlockChain<T, V> {
    private static final Logger log = LoggerFactory.getLogger(BlockChainImpl.class);

    private final List<BranchEventListener> listenerList = new ArrayList<>();

    private final Branch branch;
    private final ConsensusBlock<T> genesisBlock;
    private final Map<String, V> unConfirmedBlockMap = new ConcurrentHashMap<>();

    private final BlockChainManager<T> blockChainManager;
    private final ContractManager contractManager;
    private final BranchStore branchStore; //TODO merge with stateStore + branchStateStore
    //private final Map<OutputType, OutputStore> outputStores; //TODO move to gw module

    private final Consensus consensus;

    public BlockChainImpl(Branch branch,
                          ConsensusBlock<T> genesisBlock,
                          BranchStore branchStore,
                          BlockChainManager<T> blockChainManager,
                          ContractManager contractManager) {
        this.branch = branch;
        this.consensus = new Consensus(branch.getConsensus());
        this.branchStore = branchStore;
        this.genesisBlock = genesisBlock;
        this.blockChainManager = blockChainManager;
        this.contractManager = contractManager;

        if (blockChainManager.verifyGenesis(genesisBlock)) {
            log.error("GenesisBlock is not valid.");
            throw new NotValidateException();
        }

        init();
    }

    private void init() {
        // Check BlockChain is Ready
        PrepareBlockchain prepareBlockchain = new PrepareBlockchain(contractManager.getContractPath());
        // check block chain is ready
        if (prepareBlockchain.checkBlockChainIsReady(this)) {
            // install bundles
            for (BranchContract contract : prepareBlockchain.getContractList()) {
                File branchContractFile = prepareBlockchain.loadContractFile(contract.getContractVersion());
                contractManager.installContract(contract.getContractVersion(), branchContractFile, contract.isSystem());
            }
            try {
                contractManager.reloadInject();
            } catch (IllegalAccessException e) {
                log.error(e.getMessage());
                throw new RuntimeException("contract Inject Fail");
            }
        } else {
            // TODO BlockChain ready fails
            log.error("BlockChain is not Ready");
        }

        // getGenesis Block by Store
        Sha3Hash blockHash = branchStore.getGenesisBlockHash();
        if (blockHash == null || !blockChainManager.containsBlockHash(blockHash)) {
            log.debug("BlockChain init Genesis");
            initGenesis();
        } else {
            log.debug("BlockChain Load in Storage");
            // Load Block Chain Information
            loadTransaction();
            // load contract
        }
    }


    private void initGenesis() {
        blockChainManager.initGenesis(genesisBlock);
        addBlock(genesisBlock, false);

        // Add Meta Information
        branchStore.setBranch(branch);
        branchStore.setGenesisBlockHash(genesisBlock.getHash());
        // TODO new Validators
        //branchStore.setValidators(branch.getValidators());
        branchStore.setBranchContracts(branch.getBranchContracts());
    }

    private void loadTransaction() {
        // load recent 1000 block
        // Start Block and End Block
        long bestBlock = branchStore.getBestBlock();
        long loadStart = bestBlock > 1000 ? bestBlock - 1000 : 0;
        for (long i = loadStart; i <= bestBlock; i++) {
            // recent block load and update Cache
            ConsensusBlock<T> block = blockChainManager.getBlockByIndex(i);
            // TODO node can be shutdown before blockStore.addBlock()
            // addBlock(): branchStore.setBestBlock() -> executeTransactions() -> blockStore.addBlock()
            if (block == null) {
                long prevIdx = i - 1;
                branchStore.setBestBlock(blockChainManager.getBlockByIndex(prevIdx));
                log.warn("reset branchStore bestBlock: {} -> {}", bestBlock, prevIdx);
                break;
            }
            blockChainManager.updateTxCache(block);
            // set Last Best Block
            if (i == bestBlock) {
                blockChainManager.setLastConfirmedBlock(block);
            }
        }
    }

    @Override
    public BranchId getBranchId() {
        return genesisBlock.getBranchId();
    }

    @Override
    public Branch getBranch() {
        return branch;
    }

    @Override
    public Consensus getConsensus() {
        return consensus;
    }

    @Override
    public BlockKeyStore getBlockKeyStore() {
        throw new FailedOperationException("To be removed");
    }

    @Override
    public ConsensusBlock<T> getGenesisBlock() {
        return genesisBlock;
    }

    @Override
    public Map<String, V> getUnConfirmedData() {
        return unConfirmedBlockMap;
    }

    @Override
    public ConsensusBlock<T> addBlock(ConsensusBlock<T> nextBlock, boolean broadcast) {
        if (blockChainManager.contains(nextBlock) || !blockChainManager.verifyNewBlock(nextBlock)) {
            return null;
        }

        // add best Block
        branchStore.setBestBlock(nextBlock);

        // run Block Transactions
        // TODO run block execute move to other process (or thread)
        // TODO last execute block will invoke
        if (nextBlock.getIndex() > branchStore.getLastExecuteBlockIndex()) {
            BlockRuntimeResult result = contractManager.executeTxs(nextBlock); //TODO Exception
            // Save Result
            contractManager.commitBlockResult(result);
            branchStore.setLastExecuteBlock(nextBlock);

            /*
            //Store event //TODO move to gw module
            if (outputStores != null && outputStores.size() > 0) {
                Map<String, JsonObject> transactionMap = new HashMap<>();
                List<Transaction> txList = nextBlock.getBody().getTransactionList();
                txList.forEach(tx -> {
                    String txHash = tx.getHash().toString();
                    transactionMap.put(txHash, tx.toJsonObjectFromProto());
                });

                outputStores.forEach((storeType, store) -> {
                    store.put(nextBlock.toJsonObjectByProto());
                    store.put(nextBlock.getHash().toString(), transactionMap);
                });
            }
            */
        }

        // BlockChainManager add nextBlock to the blockStore, set the lastConfirmedBlock to nextBlock,
        // and then batch the transactions.
        blockChainManager.addBlock(nextBlock);
        if (!listenerList.isEmpty() && broadcast) {
            listenerList.forEach(listener -> listener.chainedBlock(nextBlock));
        }
        log.debug("Added idx=[{}], tx={}, branch={}, blockHash={}", nextBlock.getIndex(),
                nextBlock.getBody().getCount(), getBranchId(), nextBlock.getHash());
        return nextBlock;
    }

    @Override
    public ConsensusBlock<T> addBlock(ConsensusBlock<T> block) {
        return addBlock(block, true);
    }

    @Override
    public boolean isValidator(String addr) {
        return branchStore.isValidator(addr);
    }

    @Override
    public Transaction addTransaction(Transaction tx) {
        return addTransaction(tx, true);
    }

    public Transaction addTransaction(Transaction tx, boolean broadcast) {
        Transaction res = blockChainManager.addTransaction(tx);
        if (res != null) {
            if (!listenerList.isEmpty() && broadcast) {
                listenerList.forEach(listener -> listener.receivedTransaction(tx));
            }

        }
        return res;
    }

    @Override
    public ContractManager getContractManager() {
        return contractManager;
    }

    @Override
    public BlockChainManager<T> getBlockChainManager() {
        return blockChainManager;
    }

    @Override
    public List<BranchContract> getBranchContracts() {
        if (this.branchStore.getBranchContacts().isEmpty()) {
            return branch.getBranchContracts();
        } else {
            return this.branchStore.getBranchContacts();
        }
    }

    @Override
    public void close() {
        this.branchStore.close();
        this.blockChainManager.close();
        this.contractManager.close();
    }

    @Override
    public void addListener(BranchEventListener listener) {
        listenerList.add(listener);
    }
}