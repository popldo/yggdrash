/*
 * Copyright 2019 Akashic Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yggdrash.core.store;

import io.yggdrash.common.Sha3Hash;
import io.yggdrash.contract.core.store.ReadWriterStore;
import io.yggdrash.core.consensus.ConsensusBlock;

public interface ConsensusBlockStore<T> extends ReadWriterStore<Sha3Hash, ConsensusBlock<T>> {

    long size();

    void addBlock(ConsensusBlock<T> block);

    ConsensusBlock<T> getBlockByIndex(long index);
}
