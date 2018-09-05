/*
 * Copyright 2018 Akashic Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yggdrash.node.config;

import io.yggdrash.contract.Contract;
import io.yggdrash.contract.StemContract;
import io.yggdrash.core.BlockChain;
import io.yggdrash.core.BlockChainLoader;
import io.yggdrash.core.BlockHusk;
import io.yggdrash.core.BranchGroup;
import io.yggdrash.core.store.BlockStore;
import io.yggdrash.core.store.TransactionStore;
import io.yggdrash.core.store.datasource.DbSource;
import io.yggdrash.core.store.datasource.LevelDbDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;

import java.io.IOException;

@Configuration
public class StemBranchConfiguration {

    @Value("classpath:/branch-stem.json")
    Resource resource;

    @Bean
    BlockChain blockChain(BranchGroup branchGroup, BlockStore blockStore,
                          TransactionStore transactionStore,
                          @Qualifier("stemGenesis") BlockHusk genesisBlock,
                          @Qualifier("stemContract") Contract contract) {
        BlockChain branch = new BlockChain(genesisBlock, blockStore, transactionStore, contract);
        branchGroup.addBranch(branch.getBranchId(), branch);
        return branch;
    }

    @Bean(name = "stemGenesis")
    BlockHusk genesisBlock() throws IOException {
        return new BlockChainLoader(resource.getInputStream()).getGenesis();
    }

    @Bean(name = "stemContract")
    Contract contract() {
        return new StemContract();
    }

    @Primary
    @Profile("prod")
    @Bean(name = "blockDbSource")
    DbSource blockLevelDbSource(@Qualifier("stemGenesis") BlockHusk genesisBlock) {
        return new LevelDbDataSource(genesisBlock.getHash() + "/blocks");
    }

    @Primary
    @Profile("prod")
    @Bean(name = "txDbSource")
    DbSource txLevelDbSource(@Qualifier("stemGenesis") BlockHusk genesisBlock) {
        return new LevelDbDataSource(genesisBlock.getHash() + "/txs");
    }

}
