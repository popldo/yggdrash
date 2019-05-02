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

package io.yggdrash.core.blockchain;

import com.google.gson.JsonObject;
import io.yggdrash.common.utils.SerializationUtil;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;


public class TransactionBodyTest {

    private static final Logger log = LoggerFactory.getLogger(TransactionBodyTest.class);

    @Test
    public void testTransactionBody() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("test", "00");

        log.debug("JsonObject=" + jsonObject.toString());
        log.debug("JsonObjectSize=" + jsonObject.size());
        log.debug("JsonObjectStringSize=" + jsonObject.toString().length());

        TransactionBody txBody1 = new TransactionBody(jsonObject);

        log.debug("txBody={}", txBody1.getBody());
        assertEquals(jsonObject.toString(), txBody1.getBody().toString());

        log.debug("txBody length={}", txBody1.getLength());
        assertEquals(txBody1.toBinary().length, txBody1.getLength());

        log.debug("txBody Binary={}", Hex.toHexString(txBody1.toBinary()));

        assertArrayEquals(SerializationUtil.serializeString(txBody1.toString()), txBody1.toBinary());

        log.debug("txBody count={}", txBody1.getCount());

        assertEquals(1, txBody1.getCount());

        TransactionBody txBody2 = new TransactionBody(txBody1.getBody());

        assertEquals(txBody1, txBody2);

        assertEquals(txBody1.toString(), txBody2.toString());

        TransactionBody txBody3 = new TransactionBody(txBody1.toString());
        log.debug("txBody1 String={}", txBody1);
        log.debug("txBody3 String={}", txBody3);
        assertEquals(txBody1.toString(), txBody3.toString());

        byte[] data = SerializationUtil.serializeString(jsonObject.toString());
        TransactionBody txBody4 = new TransactionBody(SerializationUtil.deserializeString(data));
        log.debug("txBody1 String={}", txBody1);
        log.debug("txBody4 String={}", txBody4);
        assertEquals(txBody1, txBody4);
    }

}
