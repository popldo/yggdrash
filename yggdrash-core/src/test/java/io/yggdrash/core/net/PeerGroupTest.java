package io.yggdrash.core.net;

import io.yggdrash.BlockChainTestUtils;
import io.yggdrash.TestConstants;
import io.yggdrash.common.config.DefaultConfig;
import io.yggdrash.core.blockchain.BlockHusk;
import io.yggdrash.core.blockchain.BranchId;
import io.yggdrash.core.store.StoreBuilder;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

public class PeerGroupTest {

    private static final int MAX_PEERS = 25;
    private static final BranchId BRANCH = TestConstants.STEM;
    private static final Peer OWNER = Peer.valueOf("ynode://75bff16c@127.0.0.1:32920");
    private static final StoreBuilder storeBuilder = new StoreBuilder(new DefaultConfig());

    private PeerGroup peerGroup;

    @Before
    public void setUp() {
        this.peerGroup = new PeerGroup(OWNER, storeBuilder.buildPeerStore(), MAX_PEERS);
        peerGroup.setPeerHandlerFactory(PeerHandlerMock.factory);
    }

    @Test
    public void getPeerTest() {
        Peer requester = Peer.valueOf("ynode://75bff16c@127.0.0.1:32918");
        Collection<String> peerListWithoutRequester = peerGroup.getPeers(requester);
        assert peerListWithoutRequester.size() == 0;
        // requester 가 peer 목록 조회 후에는 peerTable 에 등록되어 있다
        assert peerGroup.getPeerUriList().contains(requester.getYnodeUri());
    }

    @Test
    public void addPeerByYnodeUriTest() {
        assert peerGroup.count() == 0;

        peerGroup.addPeerByYnodeUri("ynode://75bff16c@127.0.0.1:32918");
        assert peerGroup.getPeerUriList().contains("ynode://75bff16c@127.0.0.1:32918");
        assert peerGroup.count() == 1;

        peerGroup.addPeerByYnodeUri("ynode://75bff16c@127.0.0.1:32919");
        assert peerGroup.getPeerUriList().contains("ynode://75bff16c@127.0.0.1:32919");
        assert peerGroup.count() == 2;
    }

    /**
     * PeerHandlerMock 은 Pong 응답이 토클 됩니다.
     * 처음에는 정상적으로 Pong이 응답되서 안정적으로 채널에 추가시키기 위함
     * 이후 healthCheck 에서 null이 응답되어 피어 테이블과 채널에서 제거될 수 있게됨
     */
    @Test
    public void healthCheck() {
        Peer peer = Peer.valueOf("ynode://75bff16c@127.0.0.1:32918");
        peerGroup.addHandler(peer); // Pong 정상응답
        assert !peerGroup.getActivePeerList().isEmpty();

        peerGroup.healthCheck(); // Pong null 응답

        assert peerGroup.getActivePeerList().isEmpty();
    }

    @Test
    public void syncBlock() {
        addPeerHandler();
        List<BlockHusk> blockHuskList = peerGroup.syncBlock(BRANCH, 0);
        assert !blockHuskList.isEmpty();
    }

    @Test
    public void syncTransaction() {
        addPeerHandler();
        peerGroup.receivedTransaction(BlockChainTestUtils.createTransferTxHusk());
        assert !peerGroup.syncTransaction(BRANCH).isEmpty();
    }

    @Test
    public void addActivePeer() {
        int testCount = MAX_PEERS + 5;
        for (int i = 0; i < testCount; i++) {
            int port = i + 32918;
            Peer peer = Peer.valueOf("ynode://75bff16c@localhost:" + port);
            peerGroup.addHandler(peer);
        }
        assert MAX_PEERS == peerGroup.getActivePeerList().size();
    }

    @Test
    public void getAllPeersFromBucketOf() {
        assert peerGroup.getAllPeersFromBucketsOf().size() == 0;
    }

    @Test
    public void getBootstrappingSeedList() {
        assert peerGroup.getBootstrappingSeedList().size() == 0;
    }

    @Test
    public void getClosestPeers() {
        assert peerGroup.getClosestPeers().size() == 0;
    }

    @Test
    public void destroy() {
        peerGroup.destroy();
    }

    @Test
    public void chainedBlock() {
        BlockHusk genesis = BlockChainTestUtils.genesisBlock();
        peerGroup.chainedBlock(BlockChainTestUtils.genesisBlock());
        for (BestBlock bestBlock : peerGroup.getOwner().getBestBlocks()) {
            assert !bestBlock.getBranchId().equals(genesis.getBranchId()) ||
                    bestBlock.getIndex() == genesis.getIndex();
        }
    }

    @Test
    public void isClosePeer() {
        assert !peerGroup.isClosePeer(Peer.valueOf("ynode://75bff16c@127.0.0.1:32918"));
    }

    @Test
    public void reloadPeerHandler() {
        peerGroup.reloadPeerHandler(Peer.valueOf("ynode://75bff16c@127.0.0.1:32918"));
    }

    @Test
    public void getLatestPeers() {
        peerGroup.addPeerByYnodeUri("ynode://75bff16c@127.0.0.1:32918");
        assert peerGroup.getLatestPeers(1000).size() == 1;
    }

    @Test
    public void getActivePeerListOf() {
        assert peerGroup.getActivePeerListOf().size() == 0;
    }

    @Test
    public void touchPeer() {
        peerGroup.touchPeer(OWNER);
    }

    private void addPeerHandler() {
        assert peerGroup.getActivePeerList().isEmpty();
        peerGroup.addHandler(Peer.valueOf("ynode://75bff16c@127.0.0.1:32918"));
        assert !peerGroup.getActivePeerList().isEmpty();
    }
}