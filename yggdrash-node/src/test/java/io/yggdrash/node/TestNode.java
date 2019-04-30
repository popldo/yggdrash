package io.yggdrash.node;

import io.grpc.Server;
import io.yggdrash.BlockChainTestUtils;
import io.yggdrash.PeerTestUtils;
import io.yggdrash.TestConstants;
import io.yggdrash.common.config.Constants;
import io.yggdrash.core.blockchain.BlockChain;
import io.yggdrash.core.blockchain.BlockChainSyncManager;
import io.yggdrash.core.blockchain.BranchGroup;
import io.yggdrash.core.blockchain.BranchId;
import io.yggdrash.core.blockchain.Transaction;
import io.yggdrash.core.consensus.ConsensusBlock;
import io.yggdrash.core.net.BootStrapNode;
import io.yggdrash.core.net.DiscoveryConsumer;
import io.yggdrash.core.net.DiscoveryServiceConsumer;
import io.yggdrash.core.net.NodeStatus;
import io.yggdrash.core.net.NodeStatusMock;
import io.yggdrash.core.p2p.BlockChainDialer;
import io.yggdrash.core.p2p.BlockChainHandlerFactory;
import io.yggdrash.core.p2p.Peer;
import io.yggdrash.core.p2p.PeerDialer;
import io.yggdrash.core.p2p.PeerTable;
import io.yggdrash.core.p2p.PeerTableGroup;
import io.yggdrash.core.util.PeerTableCounter;
import io.yggdrash.node.config.NetworkConfiguration;
import io.yggdrash.node.config.NodeProperties;
import io.yggdrash.node.service.TransactionService;
import io.yggdrash.proto.PbftProto;
import io.yggdrash.validator.data.pbft.PbftBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TestNode extends BootStrapNode {
    private static final Logger log = LoggerFactory.getLogger(TestNode.class);

    private final BranchId branchId = TestConstants.yggdrash();
    private final BranchGroup branchGroup = new BranchGroup();
    private final NodeStatus nodeStatus = NodeStatusMock.create();

    private boolean enableBranch;
    private BlockChainHandlerFactory factory;
    private NodeProperties nodeProperties;

    final int port;
    Server server;

    // discovery specific
    DiscoveryConsumer discoveryConsumer;
    private PeerDialer peerDialer;
    public PeerTableGroup peerTableGroup;
    public PeerTask peerTask;

    // branch specific
    TransactionService transactionService;

    private TestNode(BlockChainHandlerFactory factory, int port, NodeProperties nodeProperties) {
        this.factory = factory;
        this.port = port;
        this.nodeProperties = nodeProperties;
        this.enableBranch = true;
    }

    TestNode(BlockChainHandlerFactory factory, int port, boolean enableBranch) {
        this(factory, port, createNodeProperties(new ArrayList<>()));
        this.enableBranch = enableBranch;
        config();
    }

    private static NodeProperties createNodeProperties(List<String> validatorList) {
        NodeProperties nodeProperties = new NodeProperties();
        nodeProperties.setValidatorList(validatorList);
        return nodeProperties;
    }

    private void config() {
        p2pConfiguration();
        branchConfiguration();
        networkConfiguration();
    }

    private void p2pConfiguration() {
        this.peerDialer = new BlockChainDialer(factory);
        this.peerTableGroup = PeerTestUtils.createTableGroup(port, peerDialer);
        this.discoveryConsumer = new DiscoveryServiceConsumer(peerTableGroup);

        this.peerTask = new PeerTask();
        peerTask.setNodeStatus(nodeStatus);
        peerTask.setPeerDialer(peerDialer);
        peerTask.setPeerTableGroup(peerTableGroup);
    }

    private void branchConfiguration() {
        if (isSeed()) {
            return;
        } else if (!enableBranch) {
            peerTableGroup.createTable(TestConstants.yggdrash());
            return;
        }
        BlockChain bc = BlockChainTestUtils.createBlockChain(false);
        branchGroup.addBranch(bc);
        transactionService = new TransactionService(branchGroup);
    }

    private void networkConfiguration() {
        NetworkConfiguration config = new NetworkConfiguration(nodeProperties);
        this.peerNetwork = config.peerNetwork(peerTableGroup, peerDialer, branchGroup);
        setSyncManager(config.syncManager(nodeStatus, peerNetwork, branchGroup));
    }

    public Peer getPeer() {
        return peerTableGroup.getOwner();
    }

    public BranchGroup getBranchGroup() {
        return branchGroup;
    }

    public BlockChain getDefaultBranch() {
        return branchGroup.getBranch(branchId);
    }

    public int countUnconfirmedTx() {
        return branchGroup.getUnconfirmedTxs(branchId).size();
    }

    BlockChainSyncManager getSyncManger() {
        return (BlockChainSyncManager) this.syncManager;
    }

    public void generateBlock() {
        for (BlockChain branch : branchGroup.getAllBranch()) {
            List<Transaction> txs =
                    branch.getTransactionStore().getUnconfirmedTxsWithLimit(Constants.Limit.BLOCK_SYNC_SIZE);
            ConsensusBlock block = BlockChainTestUtils.createNextBlock(txs, branch.getLastConfirmedBlock());

            PbftBlock newBlock = new PbftBlock(PbftProto.PbftBlock.newBuilder()
                    .setBlock(block.getBlock().getProtoBlock()).build());
            branch.addBlock(newBlock);
        }
    }

    public int getActivePeerCount() {
        return peerDialer.handlerCount();
    }

    public void shutdown() {
        peerNetwork.destroy();
        server.shutdownNow();
    }

    public void logDebugging() {
        PeerTable peerTable = peerTableGroup.getPeerTable(branchId);
        String branchInfo = "";
        if (getDefaultBranch() != null) {
            branchInfo = String.format(" bestBlock=%d, txCnt=%d, unConfirmed=%d,", getDefaultBranch().getLastIndex(),
                    getDefaultBranch().countOfTxs(), branchGroup.getUnconfirmedTxs(branchId).size());
        }

        log.info("{} =>{} peer={}, bucket={}, active={}",
                peerTableGroup.getOwner().toAddress(), branchInfo,
                String.format("%3d", PeerTableCounter.of(peerTable).totalPeerOfBucket()),
                peerTable.getBucketsCount(),
                getActivePeerCount());
    }

    public boolean isSeed() {
        return port == PeerTestUtils.SEED_PORT;
    }

    public static TestNode createProxyNode(BlockChainHandlerFactory factory, List<String> validatorList) {
        NodeProperties nodeProperties = createNodeProperties(validatorList);
        TestNode node = new TestNode(factory, PeerTestUtils.OWNER_PORT, nodeProperties);
        node.config();
        return node;
    }
}
