package io.yggdrash.validator;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.grpc.stub.StreamObserver;
import io.yggdrash.common.config.DefaultConfig;
import io.yggdrash.core.blockchain.Block;
import io.yggdrash.core.exception.NotValidateException;
import io.yggdrash.core.wallet.Wallet;
import io.yggdrash.proto.ConsensusEbftGrpc;
import io.yggdrash.proto.EbftProto;
import io.yggdrash.validator.data.BlockCon;
import io.yggdrash.validator.data.BlockConChain;
import io.yggdrash.validator.data.NodeStatus;
import io.yggdrash.validator.util.TestUtils;
import org.lognet.springboot.grpc.GRpcService;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static io.yggdrash.common.util.Utils.sleep;

@GRpcService
@EnableScheduling
public class GrpcNodeServer extends ConsensusEbftGrpc.ConsensusEbftImplBase
        implements CommandLineRunner {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(GrpcNodeServer.class);
    private static final ResourceLoader loader = new DefaultResourceLoader();

    private final int consenusCount;

    private DefaultConfig defaultConfig;
    private final Wallet wallet;
    private final BlockConChain blockConChain;
    private final GrpcNodeClient myNode;
    private final Map<String, GrpcNodeClient> totalValidatorMap;
    private final boolean isValidator;
    private boolean isActive;
    private boolean isSynced;
    private ReentrantLock lock = new ReentrantLock();

    private static final boolean TEST_OMIT_VERIFY = true;
    private static final boolean TEST_MEMORYFREE = false; // todo: this is only static node test
    private static final boolean ABNORMAL_TEST = false;

    @Autowired
    public GrpcNodeServer(DefaultConfig defaultConfig, Wallet wallet, BlockConChain blockConChain) {
        this.defaultConfig = defaultConfig;
        this.wallet = wallet;
        this.blockConChain = blockConChain;
        this.myNode = initMyNode();
        this.totalValidatorMap = initTotalValidator();
        this.isValidator = initValidator();
        this.isActive = false;
        if (totalValidatorMap != null) {
            this.consenusCount = totalValidatorMap.size() / 2 + 1;
        } else {
            this.consenusCount = 0;
            throw new NotValidateException();
        }
    }

    @Override
    public void run(String... args) {
        printInitInfo();
    }

    @Scheduled(cron = "*/5 * * * * *")
    public void mainScheduler() {

        checkNode();

        lock.lock();
        BlockCon proposedBlockCon = makeProposedBlock();
        lock.unlock();
        if (proposedBlockCon != null) {
            broadcast(proposedBlockCon.clone());
        }

        sleep(1000);

        lock.lock();
        BlockCon consensusedBlockCon = makeConsensus();
        lock.unlock();
        if (consensusedBlockCon != null) {
            broadcast(consensusedBlockCon.clone());
        }

        sleep(1000);

        lock.lock();
        confirmFinalBlock();
        lock.unlock();

        loggingNode();

    }

    @Override
    public void pingPongTime(EbftProto.PingTime request,
                             StreamObserver<EbftProto.PongTime> responseObserver) {
        long timestamp = System.currentTimeMillis();
        EbftProto.PongTime pongTime
                = EbftProto.PongTime.newBuilder().setTimestamp(timestamp).build();
        responseObserver.onNext(pongTime);
        responseObserver.onCompleted();
    }

    @Override
    public void getNodeStatus(
            EbftProto.Chain request,
            StreamObserver<io.yggdrash.proto.EbftProto.NodeStatus> responseObserver) {
        NodeStatus newNodeStatus = getMyNodeStatus();
        responseObserver.onNext(NodeStatus.toProto(newNodeStatus));
        responseObserver.onCompleted();
    }

    @Override
    public void exchangeNodeStatus(io.yggdrash.proto.EbftProto.NodeStatus request,
            io.grpc.stub.StreamObserver<io.yggdrash.proto.EbftProto.NodeStatus> responseObserver) {
        NodeStatus blockStatus = new NodeStatus(request);
        updateStatus(blockStatus);

        NodeStatus newNodeStatus = getMyNodeStatus();
        responseObserver.onNext(NodeStatus.toProto(newNodeStatus));
        responseObserver.onCompleted();
    }

    @Override
    public void broadcastBlockCon(io.yggdrash.proto.EbftProto.BlockCon request,
            io.grpc.stub.StreamObserver<io.yggdrash.proto.NetProto.Empty> responseObserver) {
        BlockCon newBlockCon = new BlockCon(request);
        if (!BlockCon.verify(newBlockCon) || !consensusVerify(newBlockCon)) {
            log.error("Verify Fail");
            return;
        }

        BlockCon lastBlockCon = this.blockConChain.getLastConfirmedBlockCon();

        responseObserver.onNext(io.yggdrash.proto.NetProto.Empty.newBuilder().build());
        responseObserver.onCompleted();

        if (lastBlockCon.getIndex() == newBlockCon.getIndex() - 1
                && Arrays.equals(lastBlockCon.getHash(), newBlockCon.getPrevBlockHash())) {

            lock.lock();
            updateUnconfirmedBlock(newBlockCon);
            lock.unlock();
        }
    }

    @Override
    public void getBlockConList(io.yggdrash.proto.EbftProto.Offset request,
            io.grpc.stub.StreamObserver<EbftProto.BlockConList> responseObserver) {
        long start = request.getIndex();
        long count = request.getCount();
        List<BlockCon> blockConList = new ArrayList<>();

        long end = Math.min(start - 1 + count,
                this.blockConChain.getLastConfirmedBlockCon().getIndex());

        log.debug("start: " + start);
        log.debug("end: " + end);

        if (start < end) {
            for (long l = start; l <= end; l++) {
                blockConList.add(this.blockConChain.getBlockConStore().get(
                        this.blockConChain.getBlockConKeyStore().get(l)));
            }
        }

        responseObserver.onNext(BlockCon.toProtoList(blockConList));
        responseObserver.onCompleted();
    }

    private void checkNode() {
        for (String key : totalValidatorMap.keySet()) {
            GrpcNodeClient client = totalValidatorMap.get(key);
            if (client.isMyclient()) {
                continue;
            }

            long pingTime = System.currentTimeMillis();
            long pongTime = client.pingPongTime(pingTime);

            if (pongTime > 0L) {
                checkNodeStatus(client);
            } else {
                client.setIsRunning(false);
            }
        }

        this.isSynced = true;
        setActiveMode();
    }

    private void checkNodeStatus(GrpcNodeClient client) {
        NodeStatus nodeStatus = client.exchangeNodeStatus(NodeStatus.toProto(getMyNodeStatus()));
        updateStatus(client, nodeStatus);
    }

    private void updateStatus(GrpcNodeClient client, NodeStatus nodeStatus) {
        if (NodeStatus.verify(nodeStatus)) {
            client.setIsRunning(true);

            if (nodeStatus.getLastConfirmedBlockCon().getIndex()
                    > this.blockConChain.getLastConfirmedBlockCon().getIndex()) {
                log.debug("this Index: "
                        + this.blockConChain.getLastConfirmedBlockCon().getIndex());
                log.debug("client Index: " + nodeStatus.getLastConfirmedBlockCon().getIndex());
                log.debug("client : " + client.getId());

                // blockConSyncing
                this.isSynced = false;
                blockConSyncing(client.getId(),
                        nodeStatus.getLastConfirmedBlockCon().getIndex());
            } else if (nodeStatus.getLastConfirmedBlockCon().getIndex()
                    == this.blockConChain.getLastConfirmedBlockCon().getIndex()) {
                // unconfirmed block update
                for (BlockCon blockCon : nodeStatus.getUnConfirmedBlockConList()) {
                    updateUnconfirmedBlock(blockCon);
                }
            }
        } else {
            client.setIsRunning(false);
        }
    }

    private void updateStatus(NodeStatus nodeStatus) {
        if (NodeStatus.verify(nodeStatus)) {
            for (BlockCon blockCon : nodeStatus.getUnConfirmedBlockConList()) {
                if (blockCon.getIndex()
                        <= this.blockConChain.getLastConfirmedBlockCon().getIndex()) {
                    continue;
                }
                updateUnconfirmedBlock(blockCon);
            }
        }
    }

    private void blockConSyncing(String nodeId, long index) {
        GrpcNodeClient client = totalValidatorMap.get(nodeId);
        BlockCon blockCon;
        if (client.isRunning()) {
            List<BlockCon> blockConList = client.getBlockConList(
                    this.blockConChain.getLastConfirmedBlockCon().getIndex());

            log.debug("node: " + nodeId);
            log.debug("index: " + index);
            log.debug("blockConList size: " + blockConList.size());

            if (blockConList == null || blockConList.size() == 0) {
                return;
            }

            int i = 0;
            for (; i < blockConList.size(); i++) {
                blockCon = blockConList.get(i);
                if (!BlockCon.verify(blockCon) || !consensusVerify(blockCon)) {
                    log.error("blockConSyncing Verify Fail");
                    continue;
                }
                this.blockConChain.getBlockConStore().put(blockCon.getHash(), blockCon);
                this.blockConChain.getBlockConKeyStore()
                        .put(blockCon.getIndex(), blockCon.getHash());
            }
            blockCon = blockConList.get(i - 1);
            if (blockCon.getConsensusList().size() >= consenusCount) {
                changeLastConfirmedBlock(blockCon);
                this.blockConChain.setProposed(false);
                this.blockConChain.setConsensused(false);
            }
        }

        if (this.blockConChain.getLastConfirmedBlockCon().getIndex() < index) {
            blockConSyncing(nodeId, index);
        }
    }

    public BlockCon makeProposedBlock() {
        if (this.isValidator
                && this.isActive
                && !this.blockConChain.isProposed()
                && this.isSynced) {
            long index = this.blockConChain.getLastConfirmedBlockCon().getIndex() + 1;
            byte[]  prevBlockHash = this.blockConChain.getLastConfirmedBlockCon().getHash();
            Block newBlock
                    = new TestUtils(wallet).sampleBlock(index, prevBlockHash);
            BlockCon newBlockCon
                    = new BlockCon(index, prevBlockHash, newBlock);

            // add in unconfirmed blockConMap & unconfirmed blockCon
            this.blockConChain.getUnConfirmedBlockConMap()
                    .putIfAbsent(newBlockCon.getHashHex(), newBlockCon);
            this.blockConChain.setProposed(true);

            log.debug("make Proposed Block"
                    + "["
                    + newBlockCon.getIndex()
                    + "]"
                    + newBlockCon.getHashHex()
                    + " ("
                    + newBlockCon.getBlock().getAddressHexString()
                    + ")");

            return newBlockCon;
        }

        return null;
    }

    private BlockCon makeConsensus() {
        if (this.blockConChain.isConsensused() || !this.isSynced) {
            return null;
        }

        Map<String, BlockCon> unConfirmedBlockConMap =
                this.blockConChain.getUnConfirmedBlockConMap();
        int unconfirmedBlockConCount = getUnconfirmedBlockConCount(
                unConfirmedBlockConMap,
                this.blockConChain.getLastConfirmedBlockCon().getIndex() + 1);
        if (unconfirmedBlockConCount >= getActiveNodeCount()
                && unconfirmedBlockConCount >= consenusCount
                && checkReceiveProposedBlockCon()) { //todo: check efficiency

            String minKey = null;
            for (String key : unConfirmedBlockConMap.keySet()) {
                if (unConfirmedBlockConMap.get(key).getIndex()
                        != this.blockConChain.getLastConfirmedBlockCon().getIndex() + 1) {
                    continue;
                }
                if (minKey == null) {
                    minKey = key;
                    if (ABNORMAL_TEST) {
                        // for test abnormal node(attacker)
                        break;
                    }
                } else {
                    if (org.spongycastle.util.Arrays.compareUnsigned(Hex.decode(minKey),
                            Hex.decode(key)) > 0) {
                        minKey = key;
                    }
                }
            }

            BlockCon blockCon = unConfirmedBlockConMap.get(minKey);
            String consensus = Hex.toHexString(wallet.signHashedData(blockCon.getHash()));
            blockCon.getConsensusList().add(consensus);
            this.blockConChain.setConsensused(true);

            log.debug("make Consensus: "
                    + "["
                    + blockCon.getIndex()
                    + "] "
                    + blockCon.getHashHex()
                    + " ("
                    + consensus
                    + ")");

            return blockCon;
        }

        return null;
    }

    private boolean checkReceiveProposedBlockCon() {
        long index = this.blockConChain.getLastConfirmedBlockCon().getIndex() + 1;
        List<String> proposedPubkey = new ArrayList<>();
        for (String key : this.blockConChain.getUnConfirmedBlockConMap().keySet()) {
            BlockCon proposedBlockCon = this.blockConChain.getUnConfirmedBlockConMap().get(key);

            if (proposedBlockCon.getIndex() == index) {
                proposedPubkey.add(Hex.toHexString(proposedBlockCon.getBlock().getPubKey()));
            }
        }

        for (String key : this.totalValidatorMap.keySet()) {
            GrpcNodeClient client = this.totalValidatorMap.get(key);
            if (client.isRunning()) {
                if (proposedPubkey.contains("04" + client.getPubKey())) {
                    continue;
                } else {
                    return false;
                }
            }
        }

        return true;
    }

    private int getUnconfirmedBlockConCount(Map<String, BlockCon> map, long index) {
        int count = 0;
        for (String key : map.keySet()) {
            if (map.get(key).getIndex() == index) {
                count++;
            }
        }
        return count;
    }

    private void confirmFinalBlock() {
        boolean moreConfirmFlag = false;
        for (String key : this.blockConChain.getUnConfirmedBlockConMap().keySet()) {
            BlockCon unconfirmedNode = this.blockConChain.getUnConfirmedBlockConMap().get(key);
            if (unconfirmedNode == null) {
                continue;
            }
            if (unconfirmedNode.getIndex()
                    <= this.blockConChain.getLastConfirmedBlockCon().getIndex()) {
                this.blockConChain.getUnConfirmedBlockConMap().remove(key);
            } else if (unconfirmedNode.getIndex()
                    == this.blockConChain.getLastConfirmedBlockCon().getIndex() + 1) {
                if (unconfirmedNode.getConsensusList().size() >= consenusCount) {
                    confirmedBlock(unconfirmedNode);
                }
            } else {
                if (unconfirmedNode.getConsensusList().size() >= consenusCount) {
                    moreConfirmFlag = true;
                }
            }
        }

        if (moreConfirmFlag) {
            confirmFinalBlock();
        }
    }

    private void confirmedBlock(BlockCon blockCon) {
        // add newBlockCon into blockConMap
        this.blockConChain.getBlockConStore().put(blockCon.getHash(), blockCon);
        this.blockConChain.getBlockConKeyStore().put(blockCon.getIndex(), blockCon.getHash());

        changeLastConfirmedBlock(blockCon);
        this.blockConChain.setProposed(false);
        this.blockConChain.setConsensused(false);

        log.debug("ConfirmedBlockCon="
                + "["
                + this.blockConChain.getLastConfirmedBlockCon().getIndex()
                + "]"
                + this.blockConChain.getLastConfirmedBlockCon().getHashHex()
                + "("
                + this.blockConChain.getLastConfirmedBlockCon().getConsensusList().size()
                + ")");
    }

    private void changeLastConfirmedBlock(BlockCon blockCon) {
        // change lastConfirmedBlockCon
        this.blockConChain.setLastConfirmedBlockCon(blockCon);

        // clear unConfirmedBlockCon
        for (String key : this.blockConChain.getUnConfirmedBlockConMap().keySet()) {
            BlockCon unConfirmedBlockCon = this.blockConChain.getUnConfirmedBlockConMap().get(key);
            if (unConfirmedBlockCon.getIndex() <= blockCon.getIndex()) {
                this.blockConChain.getUnConfirmedBlockConMap().remove(key);
            }
        }
    }

    private void loggingNode() {

        log.info("["
                + this.blockConChain.getLastConfirmedBlockCon().getIndex()
                + "]"
                + this.blockConChain.getLastConfirmedBlockCon().getHashHex()
                + " ("
                + this.blockConChain.getLastConfirmedBlockCon().getBlock().getAddressHexString()
                + ") "
                + "["
                + this.blockConChain.getLastConfirmedBlockCon().getConsensusList().size()
                + "]");

        if (log.isDebugEnabled()) {
            log.debug("map size= " + this.blockConChain.getBlockConStore().size());
            log.debug("key size= " + this.blockConChain.getBlockConKeyStore().size());
            log.debug("proposedBlock size= "
                    + this.blockConChain.getUnConfirmedBlockConMap().size());
            log.debug("isSynced= " + isSynced);
            log.debug("isProposed= " + this.blockConChain.isProposed());
            log.debug("isConsensused= " + this.blockConChain.isConsensused());
            for (String key : this.blockConChain.getUnConfirmedBlockConMap().keySet()) {
                BlockCon blockCon = this.blockConChain.getUnConfirmedBlockConMap().get(key);
                if (blockCon == null) {
                    break;
                }
                log.debug("proposed ["
                        + blockCon.getIndex()
                        + "]"
                        + blockCon.getHashHex()
                        + " ("
                        + blockCon.getBlock().getAddressHexString()
                        + ")");
                for (int i = 0; i < blockCon.getConsensusList().size(); i++) {
                    if (blockCon.getConsensusList().get(i) != null) {
                        log.debug(blockCon.getConsensusList().get(i)
                                + " ("
                                + Hex.toHexString(
                                Wallet.calculateAddress(
                                        Wallet.calculatePubKey(
                                                blockCon.getHash(),
                                                Hex.decode(blockCon.getConsensusList().get(i)),
                                                true)))
                                + ")");
                    }
                }
            }
        }

        log.info("");
    }

    private void broadcast(BlockCon blockCon) {
        for (String key : totalValidatorMap.keySet()) {
            GrpcNodeClient client = totalValidatorMap.get(key);
            if (client.isMyclient()) {
                continue;
            }
            if (client.isRunning()) {
                try {
                    client.broadcastBlockCon(BlockCon.toProto(blockCon));
                } catch (Exception e) {
                    log.debug("broadcast exception: " + e.getMessage());
                    log.debug("client: " + client.getId());
                    log.debug("blockCon: " + blockCon.getHashHex());
                    // continue
                }
            }
        }
    }

    private void updateUnconfirmedBlock(BlockCon blockCon) {
        if (this.blockConChain.getUnConfirmedBlockConMap().containsKey(blockCon.getHashHex())) {
            // if exist, update consensus
            if (blockCon.getConsensusList().size() > 0) {
                for (String consensus : blockCon.getConsensusList()) {
                    if (!this.blockConChain.getUnConfirmedBlockConMap().get(blockCon.getHashHex())
                            .getConsensusList().contains(consensus)) {
                        this.blockConChain.getUnConfirmedBlockConMap().get(blockCon.getHashHex())
                                .getConsensusList().add(consensus);
                    }
                }
            }
        } else {
            // if not exist, add blockCon
            this.blockConChain.getUnConfirmedBlockConMap().put(blockCon.getHashHex(), blockCon);
        }
    }

    private NodeStatus getMyNodeStatus() {
        NodeStatus newNodeStatus = new NodeStatus(this.getActiveNodeList(),
                this.blockConChain.getLastConfirmedBlockCon(),
                this.blockConChain.getUnConfirmedBlockConMap().values()
                        .stream().collect(Collectors.toList()));
        newNodeStatus.setSignature(wallet.sign(newNodeStatus.getDataForSignning()));
        return newNodeStatus;
    }

    private void printInitInfo() {
        log.info("Node Started");
        log.info("wallet address: " + wallet.getHexAddress());
        log.info("wallet pubKey: " + Hex.toHexString(wallet.getPubicKey()));
        log.info("isValidator: " + this.isValidator);
    }

    private Map<String, GrpcNodeClient> initTotalValidator() {
        String jsonString;
        ClassPathResource cpr = new ClassPathResource("validator.json");
        try {
            byte[] bdata = FileCopyUtils.copyToByteArray(cpr.getInputStream());
            jsonString = new String(bdata, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.debug("Error validator.json");
            return null;
        }

        JsonObject validatorJsonObject = new Gson().fromJson(jsonString, JsonObject.class);
        Map<String, GrpcNodeClient> nodeMap = new ConcurrentHashMap<>();

        Set<Map.Entry<String, JsonElement>> entrySet =
                validatorJsonObject.get("validator").getAsJsonObject().entrySet();
        for (Map.Entry<String, JsonElement> entry : entrySet) {
            GrpcNodeClient client = new GrpcNodeClient(entry.getKey(),
                    entry.getValue().getAsJsonObject().get("host").getAsString(),
                    entry.getValue().getAsJsonObject().get("port").getAsInt());
            if (client.getId().equals(myNode.getId())) {
                nodeMap.put(myNode.getId(), myNode);
            } else {
                nodeMap.put(client.getId(), client);
            }
        }

        log.debug("isValidator" + validatorJsonObject.toString());
        return nodeMap;
    }

    private GrpcNodeClient initMyNode() {
        byte[] realPubKey = new byte[64];
        System.arraycopy(wallet.getPubicKey(), 1, realPubKey, 0, 64);
        GrpcNodeClient client = new GrpcNodeClient(Hex.toHexString(realPubKey),
                InetAddress.getLoopbackAddress().getHostAddress(),
                Integer.parseInt(System.getProperty("grpc.port")));
        client.setMyclient(true);
        client.setIsRunning(true);

        return client;
    }

    private boolean initValidator() {
        log.debug("MyNode ID: " + this.myNode.getId());
        return totalValidatorMap.containsKey(this.myNode.getId());
    }

    private List<String> getActiveNodeList() {
        List<String> activeNodeList = new ArrayList<>();
        for (String key : totalValidatorMap.keySet()) {
            GrpcNodeClient client = totalValidatorMap.get(key);
            if (client.isMyclient()) {
                continue;
            }
            if (client.isRunning()) {
                activeNodeList.add(client.getId());
            }
        }
        return activeNodeList;
    }

    private void setActiveMode() {
        int runningNodeCount = getActiveNodeCount();
        if (runningNodeCount >= consenusCount) {
            if (!this.isActive) {
                this.isActive = true;
                log.info("Node is activated. Start make a proposed Block.");
            }
        } else {
            if (this.isActive) {
                this.isActive = false;
                log.info("Node is deactivated.");
            }
        }

        log.debug("running node: " + runningNodeCount);
    }

    private int getActiveNodeCount() {
        int count = 0;
        for (String key : totalValidatorMap.keySet()) {
            if (totalValidatorMap.get(key).isRunning()) {
                count++;
            }
        }
        return count;
    }

    private boolean consensusVerify(BlockCon blockCon) {
        if (TEST_OMIT_VERIFY) {
            return true;
        }

        if (blockCon.getConsensusList().size() <= 0) {
            return true;
        }

        for (String signature : blockCon.getConsensusList()) {
            if (Wallet.verify(blockCon.getHash(), Hex.decode(signature), true)) {
                // todo: check validator
                continue;
            } else {
                return false;
            }
        }
        return true;
    }

    private int getUnconfirmedConsenusCount() {
        int count = 0;
        long index = this.blockConChain.getLastConfirmedBlockCon().getIndex() + 1;
        for (String key : this.blockConChain.getUnConfirmedBlockConMap().keySet()) {
            BlockCon blockCon = this.blockConChain.getUnConfirmedBlockConMap().get(key);
            if (count < blockCon.getConsensusList().size()
                    && blockCon.getIndex() == index) {
                count = blockCon.getConsensusList().size();
            }
        }
        return count;
    }

    private void loggingMap(Map<String, BlockCon> map) {
        log.debug("[" + map.size() + "]");
        for (String key : map.keySet()) {
            BlockCon blockCon = map.get(key);
            log.debug(blockCon.getBlock().getAddressHexString()
                    + " "
                    + "["
                    + blockCon.getIndex()
                    + "]"
                    + blockCon.getHashHex());
        }
    }
}
