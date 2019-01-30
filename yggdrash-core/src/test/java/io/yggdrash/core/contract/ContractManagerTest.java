/*
 * Copyright 2019 Akashic Foundation
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

package io.yggdrash.core.contract;

import io.yggdrash.common.config.DefaultConfig;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ContractManagerTest {
    Logger log = LoggerFactory.getLogger(ContractManagerTest.class);

    private static DefaultConfig defaultConfig = new DefaultConfig();
    private static ContractManager contractManager;
    private static Map<ContractId, ContractMeta> contracts;


    @Before
    public void loadTest() {
        if (defaultConfig.isProductionMode()) {
            ContractClassLoader.copyResourcesToContractPath(defaultConfig.getContractPath());
        }
        this.contractManager = new ContractManager(defaultConfig.getContractPath());
        this.contracts = contractManager.getContracts();
    }

    @Test
    public void getContractById() {
        List<ContractId> sampleContractIdList = contractSample();
        if (sampleContractIdList == null || contracts == null) return;

        ContractMeta meta = ContractClassLoader.loadContractClass(StemContract.class);
        ContractMeta meta2 = contractManager.getContractById(meta.getContractId());
        assertNotNull(meta);
        log.debug("StemContract.class id={}", meta.getContractId().toString());
        assertEquals(meta2.getContractId(), meta.getContractId());
        assertEquals(meta2.getContract().getName(), meta.getContract().getName());
    }

    @Test
    public void getContractIdList() {
        List<ContractId> sampleContractIdList = contractSample();
        if (sampleContractIdList == null || contracts == null) return;
        assertEquals(sampleContractIdList.size(), contractManager.getContractIdList().size());
    }

    @Test
    public void getContractList() {
        List<ContractId> sampleContractIdList = contractSample();
        if (sampleContractIdList == null || contracts == null) return;
        assertEquals(sampleContractIdList.size(), contractManager.getContractList().size());
    }

    @Test
    public void isContract() {
        if (contracts == null) return;
        contracts.entrySet().stream().forEach(set -> {
            if (set.getKey() != null){
                assertEquals(true, contractManager.isContract(set.getKey()));
            }
        });
    }

    @Test
    public void convertContractToVersion() {
        ContractId version = contractManager.convertContractToVersion(TestContract.class);
        ContractMeta meta = ContractClassLoader.loadContractClass(TestContract.class);
        assertEquals(version, meta.getContractId());
    }

    @Test
    public void addContract() {
        Class<? extends Contract> contract = TestContract.class;
        Map<ContractId, ContractMeta> contracts = contractManager.getContracts();
        if (contracts == null) return;
        long beforSize = contracts.entrySet().size();
        contractManager.addContract(contract);
        assertEquals(beforSize + 1, contracts.entrySet().size());

        ContractMeta contractMeta = ContractClassLoader.loadContractClass(contract);
        ContractId contractVersion = contractMeta.getContractId();
        contractManager.removeContract(contractVersion);
    }

    @Test
    public void contractManagerCall() {
        List<ContractMeta> contractList = contractManager.getContractList();
        System.out.println(contractList);
        ContractMeta meta = ContractClassLoader.loadContractClass(StemContract.class);
        System.out.println(contractManager.getContractById(meta.getContractId()));
    }

    private List<ContractId> contractSample() {
        DefaultConfig defaultConfig = new DefaultConfig();
        List<ContractId> cIds = new ArrayList<>();
        try (Stream<Path> filePathStream = Files.walk(Paths.get(String.valueOf(defaultConfig.getContractPath())))) {
            filePathStream.forEach(contractPath -> {
                File contractFile = new File(String.valueOf(contractPath));
                if(contractFile.isDirectory()) return;
                byte[] contractBinary;
                try (FileInputStream inputStream = new FileInputStream(contractFile)) {
                    contractBinary = new byte[Math.toIntExact(contractFile.length())];
                    inputStream.read(contractBinary);

                    ContractId contractId = ContractId.of(contractBinary);
                    ContractMeta contractMeta = ContractClassLoader.loadContractById(
                            defaultConfig.getContractPath(), contractId);

                    if(contractMeta.getStateStore() !=null || contractMeta.getTxReceipt() !=null) {
                        cIds.add(contractId);
                    }
                } catch (IOException e) {
                    log.warn(e.getMessage());
                }
            });
            return cIds;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}


