package io.yggdrash.validator.store;

import io.yggdrash.common.util.ByteUtil;
import io.yggdrash.core.exception.NonExistObjectException;
import io.yggdrash.core.store.Store;
import io.yggdrash.core.store.datasource.DbSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;

public class BlockConKeyStore implements Store<Long, byte[]> {
    private static final Logger log = LoggerFactory.getLogger(BlockConKeyStore.class);

    private final DbSource<byte[], byte[]> db;

    public BlockConKeyStore(DbSource<byte[], byte[]> dbSource) {
        this.db = dbSource.init();
    }

    @Override
    public void put(Long key, byte[] value) {
        log.trace("BlockConKeyStore put "
                + "(key: " + key + ")"
                + "(value : " + Hex.toHexString(value) + ")");
        db.put(ByteUtil.longToBytes(key), value);
    }

    @Override
    public byte[] get(Long key) {
        log.trace("BlockConKeyStore get " + "(" + key + ")");

        if (key > -1) {
            byte[] foundValue = db.get(ByteUtil.longToBytes(key));
            if (foundValue != null) {
                log.trace("BlockConKeyStore value: " + Hex.toHexString(foundValue));

                return foundValue;
            }
        }
        throw new NonExistObjectException("Not Found [" + key + "]");
    }

    @Override
    public boolean contains(Long key) {
        if (key > -1) {
            return db.get(ByteUtil.longToBytes(key)) != null;
        }

        return false;
    }

    public int size() {
        try {
            return db.getAll().size();
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        return 0;
    }

    public void close() {
        this.db.close();
    }
}