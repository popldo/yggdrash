package io.yggdrash.node;

import io.grpc.Server;
import io.yggdrash.BlockChainTestUtils;
import io.yggdrash.PeerTestUtils;
import io.yggdrash.TestConstants;
import io.yggdrash.core.blockchain.BlockChain;
import io.yggdrash.core.blockchain.BlockChainSyncManager;
import io.yggdrash.core.blockchain.BranchGroup;
import io.yggdrash.core.blockchain.BranchId;
import io.yggdrash.core.net.BlockChainConsumer;
import io.yggdrash.core.net.BlockChainServiceConsumer;
import io.yggdrash.core.net.BootStrapNode;
import io.yggdrash.core.net.DiscoveryConsumer;
import io.yggdrash.core.net.DiscoveryServiceConsumer;
import io.yggdrash.core.net.NodeStatusMock;
import io.yggdrash.core.p2p.PeerDialer;
import io.yggdrash.core.p2p.PeerHandlerFactory;
import io.yggdrash.core.p2p.PeerTable;
import io.yggdrash.core.p2p.PeerTableGroup;
import io.yggdrash.core.p2p.SimplePeerDialer;
import io.yggdrash.core.util.PeerTableCounter;
import io.yggdrash.node.config.NetworkConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestNode extends BootStrapNode {
    private static final Logger log = LoggerFactory.getLogger(TestNode.class);
    private final BranchId branchId = TestConstants.yggdrash();

    final int port;
    Server server;

    // discovery specific
    DiscoveryConsumer discoveryConsumer;
    private PeerDialer peerDialer;
    public PeerTableGroup peerTableGroup;
    public PeerTask peerTask;

    // branch specific
    public BlockChainConsumer blockChainConsumer;

    TestNode(PeerHandlerFactory factory, int port, boolean enableBranch) {
        this.port = port;

        p2pConfiguration(factory);

        branchConfiguration(enableBranch);

        networkConfiguration();
    }

    private void p2pConfiguration(PeerHandlerFactory factory) {
        this.nodeStatus = NodeStatusMock.create();
        this.peerDialer = new SimplePeerDialer(factory);
        this.peerTableGroup = PeerTestUtils.createTableGroup(port, peerDialer);
        this.discoveryConsumer = new DiscoveryServiceConsumer(peerTableGroup);

        this.peerTask = new PeerTask();
        peerTask.setNodeStatus(nodeStatus);
        peerTask.setPeerDialer(peerDialer);
        peerTask.setPeerTableGroup(peerTableGroup);
    }

    private void branchConfiguration(boolean enableBranch) {
        this.branchGroup = new BranchGroup();
        if (isSeed()) {
            return;
        } else if (!enableBranch) {
            peerTableGroup.createTable(TestConstants.yggdrash());
            return;
        }
        BlockChain bc = BlockChainTestUtils.createBlockChain(false);
        branchGroup.addBranch(bc);
        blockChainConsumer = new BlockChainServiceConsumer(branchGroup);
    }

    private void networkConfiguration() {
        NetworkConfiguration config = new NetworkConfiguration();
        this.peerNetwork = config.peerNetwork(peerTableGroup, peerDialer, branchGroup);
        setSyncManager(config.syncManager(nodeStatus, peerNetwork, branchGroup));
        if (blockChainConsumer != null) {
            blockChainConsumer.setListener(getSyncManger());
        }
    }

    public BranchGroup getBranchGroup() {
        return branchGroup;
    }

    public BlockChain getDefaultBranch() {
        return branchGroup.getBranch(branchId);
    }

    public BlockChainSyncManager getSyncManger() {
        return (BlockChainSyncManager) this.syncManager;
    }

    public void generateBlock() {
        branchGroup.generateBlock(TestConstants.wallet(), branchId);
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
                    getDefaultBranch().transactionCount(), branchGroup.getUnconfirmedTxs(branchId).size());
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
}
