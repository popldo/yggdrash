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

package io.yggdrash.node.api.dto;

import io.yggdrash.common.util.ByteUtil;
import io.yggdrash.core.blockchain.BlockHusk;
import io.yggdrash.proto.Proto;
import org.spongycastle.util.encoders.Hex;

import java.util.List;
import java.util.stream.Collectors;

public class BlockDto {
    private static final int MAX_TX_BODY = 100;

    public String branchId;
    public String version;
    public String type;
    public String prevBlockId;
    public long index;
    public long timestamp;
    public String merkleRoot;
    public long bodyLength;
    public long txSize;
    public String signature;
    public List<TransactionDto> body;
    public String author;
    public String blockId;

    public static BlockDto createBy(BlockHusk block) {
        return createBy(block, block.getBodySize() < MAX_TX_BODY);
    }

    private static BlockDto createBy(BlockHusk block, boolean withBody) {
        BlockDto blockDto = new BlockDto();
        Proto.Block.Header header = block.getInstance().getHeader();
        blockDto.branchId = Hex.toHexString(header.getChain().toByteArray());
        blockDto.version = Hex.toHexString(header.getVersion().toByteArray());
        blockDto.type = Hex.toHexString(header.getType().toByteArray());
        blockDto.prevBlockId = Hex.toHexString(block.getPrevHash().getBytes());
        blockDto.index = block.getIndex();
        blockDto.timestamp = ByteUtil.byteArrayToLong(header.getTimestamp().toByteArray());
        blockDto.merkleRoot = Hex.toHexString(header.getMerkleRoot().toByteArray());
        blockDto.bodyLength = ByteUtil.byteArrayToLong(header.getBodyLength().toByteArray());
        blockDto.signature = Hex.toHexString(block.getInstance().getSignature().toByteArray());
        blockDto.txSize = block.getBodySize();
        if (withBody) {
            blockDto.body = block.getBody().stream().map(TransactionDto::createBy)
                    .collect(Collectors.toList());
        }
        blockDto.author = block.getAddress().toString();
        blockDto.blockId = block.getHash().toString();
        return blockDto;
    }

}