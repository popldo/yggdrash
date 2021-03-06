package io.yggdrash.validator.data.pbft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import io.yggdrash.common.crypto.HashUtil;
import io.yggdrash.common.util.TimeUtils;
import io.yggdrash.common.utils.ByteUtil;
import io.yggdrash.common.utils.JsonUtil;
import io.yggdrash.common.utils.SerializationUtil;
import io.yggdrash.core.exception.NotValidateException;
import io.yggdrash.core.wallet.Wallet;
import io.yggdrash.proto.PbftProto;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

public class PbftStatus {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(PbftStatus.class);

    private final long index;
    private final Map<String, PbftMessage> unConfirmedPbftMessageMap = new TreeMap<>();
    private final long timestamp;
    private final byte[] signature;

    public PbftStatus(long index,
                      Map<String, PbftMessage> unConfirmedPbftMessageMap,
                      long timestamp,
                      byte[] signature) {
        this.index = index;
        this.unConfirmedPbftMessageMap.putAll(unConfirmedPbftMessageMap);
        if (timestamp == 0L) {
            this.timestamp = TimeUtils.time();
        } else {
            this.timestamp = timestamp;
        }

        this.signature = signature;
    }

    public PbftStatus(long index,
                      Map<String, PbftMessage> unConfirmedPbftMessageMap,
                      long timestamp,
                      Wallet wallet) {
        this.index = index;
        this.unConfirmedPbftMessageMap.putAll(unConfirmedPbftMessageMap);
        if (timestamp == 0L) {
            this.timestamp = TimeUtils.time();
        } else {
            this.timestamp = timestamp;
        }

        this.signature = this.sign(wallet);
    }

    public PbftStatus(PbftProto.PbftStatus pbftStatus) {
        this.index = pbftStatus.getIndex();
        for (PbftProto.PbftMessage protoPbftMessage :
                pbftStatus.getPbftMessageList().getPbftMessageListList()) {
            String key = Hex.toHexString(protoPbftMessage.getSignature().toByteArray());
            if (!this.unConfirmedPbftMessageMap.containsKey(key)) {
                this.unConfirmedPbftMessageMap.put(key, new PbftMessage(protoPbftMessage));
            }
        }
        this.timestamp = pbftStatus.getTimestamp();
        this.signature = pbftStatus.getSignature().toByteArray();
    }

    private PbftStatus(JsonObject jsonObject) {
        this.index = jsonObject.get("index").getAsLong();
        if (jsonObject.get("unConfirmedList") != null) {
            for (JsonElement pbftMessageJsonElement : jsonObject.get("unConfirmedList").getAsJsonArray()) {
                PbftMessage pbftMessage = new PbftMessage(pbftMessageJsonElement.getAsJsonObject());
                this.unConfirmedPbftMessageMap.put(pbftMessage.getSignatureHex(), pbftMessage);
            }
        }
        this.timestamp = jsonObject.get("timestamp").getAsLong();
        this.signature = Hex.decode(jsonObject.get("signature").getAsString());
    }

    public PbftStatus(byte[] bytes) {
        this(JsonUtil.parseJsonObject(SerializationUtil.deserializeString(bytes)));
    }

    public long getIndex() {
        return index;
    }

    public Map<String, PbftMessage> getUnConfirmedPbftMessageMap() {
        return unConfirmedPbftMessageMap;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public byte[] getSignature() {
        return signature;
    }

    public byte[] getHashForSigning() {
        ByteArrayOutputStream dataForSigning = new ByteArrayOutputStream();

        try {
            dataForSigning.write(ByteUtil.longToBytes(index));
            for (Map.Entry<String, PbftMessage> entry : this.unConfirmedPbftMessageMap.entrySet()) {
                dataForSigning.write(entry.getValue().toBinary());
            }
            dataForSigning.write(ByteUtil.longToBytes(timestamp));
        } catch (Exception e) {
            log.debug(e.getMessage());
            return null;
        }

        return HashUtil.sha3(dataForSigning.toByteArray());
    }

    private byte[] sign(Wallet wallet) {
        if (wallet == null) {
            throw new NotValidateException("wallet is null");
        }

        return wallet.sign(getHashForSigning(), true);
    }

    public static boolean verify(PbftStatus status) {
        if (status == null || status.getSignature() == null) {
            log.debug("PbftStatus is null.");
            return false;
        }

        byte[] hashData = status.getHashForSigning();
        byte[] signature = status.getSignature();
        if (hashData == null
                || !Wallet.verify(hashData, signature, true)) {
            log.debug("PbftStatus is not verified.");
            return false;
        }

        for (PbftMessage pbftMessage : status.unConfirmedPbftMessageMap.values()) {
            if (!PbftVerifier.INSTANCE.verify(pbftMessage)) {
                log.debug("PbftMessage is not verified. {}", pbftMessage.toJsonObject().toString());
                return false;
            }
        }

        return true;
    }

    public static PbftProto.PbftStatus toProto(PbftStatus pbftStatus) {
        if (pbftStatus == null) {
            return null;
        }

        PbftProto.PbftMessageList.Builder protoPbftMessageListBuilder = PbftProto.PbftMessageList.newBuilder();
        for (String key : pbftStatus.getUnConfirmedPbftMessageMap().keySet()) {
            PbftMessage pbftMessage = pbftStatus.getUnConfirmedPbftMessageMap().get(key);
            protoPbftMessageListBuilder.addPbftMessageList(PbftMessage.toProto(pbftMessage));
        }

        PbftProto.PbftStatus.Builder protoPbftStatusBuilder = PbftProto.PbftStatus.newBuilder()
                .setIndex(pbftStatus.getIndex())
                .setPbftMessageList(protoPbftMessageListBuilder.build())
                .setTimestamp(pbftStatus.getTimestamp())
                .setSignature(ByteString.copyFrom(pbftStatus.getSignature()));
        return protoPbftStatusBuilder.build();
    }

    public JsonObject toJsonObject() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("index", this.index);

        JsonArray unConfirmedMessageMapJsonArray = new JsonArray();
        for (Map.Entry<String, PbftMessage> entry : unConfirmedPbftMessageMap.entrySet()) {
            unConfirmedMessageMapJsonArray.add(entry.getValue().toJsonObject());
        }
        if (unConfirmedMessageMapJsonArray.size() > 0) {
            jsonObject.add("unConfirmedList", unConfirmedMessageMapJsonArray);
        }

        jsonObject.addProperty("timestamp", this.timestamp);
        jsonObject.addProperty("signature", Hex.toHexString(this.signature));

        return jsonObject;
    }

    public byte[] toBinary() {
        return SerializationUtil.serializeJson(toJsonObject());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PbftStatus other = (PbftStatus) o;
        return Arrays.equals(toBinary(), other.toBinary());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(toBinary());
    }

    public void clear() {
        for (PbftMessage pbftMessage : this.unConfirmedPbftMessageMap.values()) {
            if (pbftMessage != null) {
                pbftMessage.clear();
            }
        }
        this.unConfirmedPbftMessageMap.clear();
    }


}