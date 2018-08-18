/*
 * Copyright 2018 Akashic Foundation
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

import io.yggdrash.TestUtils;
import io.yggdrash.common.Sha3Hash;
import io.yggdrash.core.husk.BlockHusk;
import org.junit.Test;

public class MetaStoreTest {
    @Test
    public void name() {
        MetaStore metaStore = new MetaStore();
        BlockHusk blockHusk = new BlockHusk(TestUtils.getBlockFixture());
        metaStore.put(MetaStore.MetaInfo.RECENT_BLOCK, blockHusk.getHash());

        Sha3Hash sha3Hash = metaStore.get(MetaStore.MetaInfo.RECENT_BLOCK);
    }
}
