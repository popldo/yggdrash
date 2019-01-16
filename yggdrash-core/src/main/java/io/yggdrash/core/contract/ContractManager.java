/*
 * Copyright 2018 Akashic Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yggdrash.core.contract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class ContractManager<T> extends ClassLoader {
    private static final Logger log = LoggerFactory.getLogger(ContractManager.class);

    private final List<ContractId> contractIds = new ArrayList<>();
    private final List<Object> contracts = new ArrayList<>();
    private Map<ContractId, Object> contractList = new Hashtable<>();

    public ContractManager(String contractPath) {
        load(contractPath);
    }

    private void load(String contractRoot) {
        try (Stream<Path> filePathStream = Files.walk(Paths.get(String.valueOf(contractRoot)))) {
            filePathStream.forEach(contractPath -> {
                File contractFile = new File(String.valueOf(contractPath));
                byte[] contractBinary;
                try (FileInputStream inputStream = new FileInputStream(contractFile)) {
                    contractBinary = new byte[Math.toIntExact(contractFile.length())];
                    inputStream.read(contractBinary);

                    ContractId contractId = ContractId.of(contractBinary);

                    ContractMeta contractMeta = ContractClassLoader.loadContractById(
                            contractRoot, contractId);

                    if (Files.isRegularFile(contractPath)) {
                        contractIds.add(contractId);
                        contracts.add(contractMeta.getContract().getDeclaredConstructor().newInstance());
                        contractList.put(contractMeta.getContractId(), contractMeta.getContract().getDeclaredConstructor().newInstance());
                    }

                } catch (IOException | NoSuchMethodException | InstantiationException | IllegalAccessException
                        | InvocationTargetException e) {
                    log.warn(e.getMessage());
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<ContractId> getContractList() {
        return contractIds;
    }

}
