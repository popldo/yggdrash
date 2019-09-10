package io.yggdrash.core.blockchain.osgi;

import com.google.gson.JsonObject;
import io.yggdrash.common.Sha3Hash;
import io.yggdrash.common.config.DefaultConfig;
import io.yggdrash.common.contract.BranchContract;
import io.yggdrash.common.contract.Contract;
import io.yggdrash.common.contract.ContractVersion;
import io.yggdrash.contract.core.ContractEvent;
import io.yggdrash.contract.core.channel.ContractEventType;
import io.yggdrash.core.blockchain.BranchId;
import io.yggdrash.core.blockchain.Log;
import io.yggdrash.core.blockchain.LogIndexer;
import io.yggdrash.core.blockchain.SystemProperties;
import io.yggdrash.core.blockchain.Transaction;
import io.yggdrash.core.blockchain.genesis.GenesisBlock;
import io.yggdrash.core.blockchain.osgi.framework.BundleService;
import io.yggdrash.core.blockchain.osgi.service.ContractProposal;
import io.yggdrash.core.blockchain.osgi.service.VersioningContract;
import io.yggdrash.core.consensus.ConsensusBlock;
import io.yggdrash.core.runtime.result.BlockRuntimeResult;
import io.yggdrash.core.runtime.result.TransactionRuntimeResult;
import io.yggdrash.core.store.ContractStore;
import io.yggdrash.core.store.LogStore;
import org.apache.commons.io.IOUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ContractManager implements ContractEventListener {
    private static final Logger log = LoggerFactory.getLogger(ContractManager.class);

    private final BranchId bootBranchId;
    private final ContractStore contractStore;
    private final LogStore logStore;

    private final String contractPath;
    private final SystemProperties systemProperties;
    private final ContractExecutor contractExecutor;

    private final BundleService bundleService;
    private final LogIndexer logIndexer;

    private final DefaultConfig defaultConfig;
    private final GenesisBlock genesis;


    private Map<String, Object> serviceMap;

    ContractManager(GenesisBlock genesis, BundleService bundleService, DefaultConfig defaultConfig,
                    ContractStore contractStore, LogStore logStore, SystemProperties systemProperties) {

        this.bootBranchId = genesis.getBranchId();
        this.contractStore = contractStore;
        this.logStore = logStore;

        this.contractPath = defaultConfig.getContractPath();

        this.systemProperties = systemProperties;

        this.logIndexer = new LogIndexer(logStore, contractStore.getReceiptStore());
        this.contractExecutor = new ContractExecutor(contractStore, logIndexer);

        this.bundleService = bundleService;
        this.defaultConfig = defaultConfig;
        this.genesis = genesis;

        this.serviceMap = new HashMap<>();

        initBootBundles();
        initNodeContract();

    }

    @Override
    public void endBlock(ContractEvent event) {
        // todo : remove this
    }

    @Override
    public void endBlock(BlockRuntimeResult result, ContractEvent event) {
        if (event.getContractVersion().equals(ContractConstants.VERSIONING_CONTRACT.toString())) {
            versioningContractEventHandler(result, event);
        } else {
            // reflect state for general end block result
            commitBlockResult(result);
        }
    }

    private void versioningContractEventHandler(BlockRuntimeResult result, ContractEvent event) {
        ContractEventType eventType = event.getType();
        ContractProposal proposal = (ContractProposal) event.getItem();
        ContractVersion proposalVersion = ContractVersion.of(proposal.getProposalVersion());

        try {
            switch (eventType) {
                case AGREE:
                    // download contract file to contract tmp folder
                    Downloader.downloadContract(String.format("%s/%s", contractPath, "tmp"), proposalVersion);
                    break;
                case APPLY:
                    // todo : pkg version check
                    Path tmp = Paths.get(String.format("%s/%s/%s", contractPath, "tmp", proposalVersion + ".jar"));
                    Path origin = Paths.get(contractPath);
                    Files.move(tmp, origin.resolve(tmp.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    // install
                    install(proposalVersion);

                    // bundle start and inject and register(service map & branch store)
                    bundleService.start(proposalVersion);
                    injectBundle(bundleService.getBundle(proposalVersion));
                    registerServiceMap(proposalVersion, bundleService.getBundle(proposalVersion));
                    addNewBranchContract(bundleService.getBundle(proposalVersion), proposalVersion);
                    break;
                default:
                    log.info("Not defined event type in version contract");
                    break;
            }
        } catch (BundleException | IOException e) {
            log.error("VersioningContract event failed. {} ", e.getMessage());
        }
        commitBlockResult(result);
    }

    private void addNewBranchContract(Bundle newBundle, ContractVersion contractVersion) {
        // @lucase. 190903
        // todo : Add logic what include newer contract not in branch store.
//        for (BranchContract branchContract : contractStore.getBranchStore().getBranchContacts()) {
//            ContractVersion originContractVersion = ContractVersion.of(branchContract.getContractVersion().toString());
//            String originSymbolicName = getBundle(originContractVersion).getSymbolicName();
//            if (originSymbolicName.equals(newBundle.getSymbolicName())) {
//                JsonObject newBranchContractJson = branchContract.getJson().deepCopy();
//                newBranchContractJson.addProperty("contractVersion", contractVersion.toString());
//
//                BranchContract newBranchContract = BranchContract.of(newBranchContractJson);
//
//                List<BranchContract> branchContacts = contractStore.getBranchStore().getBranchContacts();
//                branchContacts.add(newBranchContract);
//                contractStore.getBranchStore().setBranchContracts(branchContacts);
//                //TODO Stop origin contract bundle
//            }
//        }
            // fix me!
//        JsonObject branchContractJson = new JsonObject();
//        branchContractJson.addProperty("init", "{}");
//        branchContractJson.addProperty("name", "name");
//        branchContractJson.addProperty("description", "description");
//        branchContractJson.addProperty("property", "property");
//        // Mark: Is this property need?
//        branchContractJson.addProperty("isSystem", true);
//        branchContractJson.addProperty("contractVersion", "");
//        BranchContract newBranchContract = BranchContract.of(branchContractJson);
//
//        List<BranchContract> branchContracts = contractStore.getBranchStore().getBranchContacts();
//        branchContracts.add(newBranchContract);
//        contractStore.getBranchStore().setBranchContracts(branchContracts);

    }

    private void initBootBundles() {

        List<BranchContract> branchContractList = this.getContractList();

        if (branchContractList.isEmpty()) {
            log.warn("This branch {} has no any contract.", bootBranchId);
            return;
        }

        for (BranchContract branchContract : branchContractList) {
            ContractVersion contractVersion = branchContract.getContractVersion();
            loadBundle(contractVersion);
        }
    }

    private void initNodeContract() {
        VersioningContract service = new VersioningContract();
        serviceMap.put(ContractConstants.VERSIONING_CONTRACT.toString(), service);
        contractExecutor.injectNodeContract(service);

    }

    public void loadBundle(ContractVersion contractVersion) {
        // step 1. exist file and download file.
        File contractFile = null;
        if (isContractFileExist(contractVersion)) {
            contractFile = new File(contractFilePath(contractVersion));
        } else {
            contractFile = Downloader.downloadContract(contractPath, contractVersion);
        }

        if (!verifyContractFile(contractFile, contractVersion)) {
            return;
        }

        // step 2. install and start
        Bundle bundle = getBundle(contractVersion);

        if (bundle == null) {
            try {
                bundle = bundleService.install(contractVersion, contractFile);
            } catch (IOException | BundleException e) {
                // Mark : throw runtime error?
                log.error("ContractFile {} failed to install with {}", contractVersion, e.getMessage());
                return;
            }
        }

        try {
            bundleService.start(contractVersion);
        } catch (BundleException e) {
            log.error("Bundle {} failed to start with {}", bundle.getSymbolicName(), e.getMessage());
            return;
        }

        // step 3. register service and injectBundle
        registerServiceMap(contractVersion, bundle);
        injectBundle(bundle);
    }

    /**
     * get contract list from branchStore or genesis block.
     * @return contractList
     */
    private List<BranchContract> getContractList() {
        if (contractStore.getBranchStore().getBranchContacts().isEmpty()) {
            return genesis.getBranch().getBranchContracts();
        }
        return contractStore.getBranchStore().getBranchContacts();
    }

    private void injectBundle(Bundle bundle) {
        Object service = bundleService.getBundleService(bundle);
        if (service == null) {
            log.error("No available service in bundle {}", bundle.getSymbolicName());
            return;
        }
        contractExecutor.injectBundleContract(bundle, service);
    }

    private void registerServiceMap(ContractVersion contractVersion, Bundle bundle) {
        Object service = bundleService.getBundleService(bundle);
        this.serviceMap.put(contractVersion.toString(), service);
    }

    // bundle service actions.
    public Bundle getBundle(ContractVersion contractVersion) {
        return bundleService.getBundle(contractVersion);
    }

    public Bundle[] getBundles() {
        return bundleService.getBundles();
    }

    private void install(ContractVersion contractVersion) throws IOException, BundleException {
        File contractFile = new File(contractFilePath(contractVersion));
        bundleService.install(contractVersion, contractFile);
    }

    public void uninstall(ContractVersion contractVersion) {
        try {
            bundleService.uninstall(contractVersion);
        } catch (BundleException e) {
            log.error(e.getMessage());
        }
    }

    public void stop(ContractVersion contractVersion) throws BundleException {
        bundleService.stop(contractVersion);
    }

    public void start(ContractVersion contractVersion) throws BundleException {
        bundleService.start(contractVersion);
    }

    public List<ContractStatus> searchContracts() {
        // 시스템 번들을 출력할 필요가 있는지?
        return bundleService.getContractList();
    }

    public Long getStateSize() { // TODO for BranchController -> remove this
        return contractStore.getStateStore().getStateSize();
    }

    // Log Indexer Services
    public Log getLog(long index) {
        return logIndexer.getLog(index);
    }

    public List<Log> getLogs(long start, long offset) {
        return logIndexer.getLogs(start, offset);
    }

    public long getCurLogIndex() {
        return logIndexer.curIndex();
    }

    // Executor Services
    public Object query(String contractVersion, String methodName, JsonObject params) {
        return contractExecutor.query(serviceMap, contractVersion, methodName, params);
    }

    public BlockRuntimeResult endBlock(ConsensusBlock addedBlock) {
        return contractExecutor.endBlock(serviceMap, addedBlock);
    }

    public BlockRuntimeResult executeTxs(ConsensusBlock nextBlock) {
        return contractExecutor.executeTxs(serviceMap, nextBlock);
    }

    public Set<Sha3Hash> executePendingTxs(List<Transaction> txs) {
        return contractExecutor.executePendingTxs(serviceMap, txs);
    }

    public TransactionRuntimeResult executeTx(Transaction tx) {
        return contractExecutor.executeTx(serviceMap, tx);
    }

    public boolean executePendingTx(Transaction tx) {
        return contractExecutor.executePendingTx(serviceMap, tx);
    }

    public void commitBlockResult(BlockRuntimeResult result) {
        contractExecutor.commitBlockResult(result);
    }

    public void resetPendingStateStore() {
        contractStore.getPendingStateStore().close();
    }

    // file actions.
    public boolean isContractFileExist(ContractVersion version) {
        File contractDir = new File(contractPath);
        if (!contractDir.exists()) {
            contractDir.mkdirs();
            return false;
        }

        File contractFile = new File(contractFilePath(version));
        if (!contractFile.canRead()) {
            contractFile.setReadable(true, false);
        }

        return contractFile.isFile();
    }

    public boolean verifyContractFile(File contractFile, ContractVersion contractVersion) {
        // Contract Path + contract Version + .jar
        // check contractVersion Hex
        try (InputStream is = new FileInputStream(contractFile)) {
            byte[] contractBinary = IOUtils.toByteArray(is);
            ContractVersion checkVersion = ContractVersion.of(contractBinary);
            return contractVersion.toString().equals(checkVersion.toString());
        } catch (IOException e) {
            log.error(e.getMessage());
            return false;
        }
    }

    public boolean deleteContractFile(File contractFile) {
        return contractFile.delete();
    }

    private String contractFilePath(ContractVersion contractVersion) {
        return this.contractPath + File.separator + contractVersion + ".jar";
    }

    public void close() {
        contractStore.close();
        logStore.close();
    }

}
