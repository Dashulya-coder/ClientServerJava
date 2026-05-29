package protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
public class PackageBuilder {

    private static final int HEADER_SIZE = 16;
    private final CryptoService cryptoService;

    public PackageBuilder(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    public byte[] build(byte clientId, long packetId, Message message) throws Exception {
        byte[] plainBody = serializeMessage(message);

        byte[] encryptedBody = cryptoService.encrypt(plainBody);
        int bodyLen = encryptedBody.length;

        byte[] packet = new byte[HEADER_SIZE + bodyLen + 2];
        ByteBuffer buf = ByteBuffer.wrap(packet);


        buf.put(0, Package.MAGIC);
        buf.put(1, clientId);
        buf.putLong(2, packetId);
        buf.putInt(10, bodyLen);

        int headerCrc = Crc16.calculate(packet, 0, HEADER_SIZE - 2);
        packet[14] = (byte) ((headerCrc >> 8) & 0xFF);
        packet[15] = (byte) (headerCrc & 0xFF);


        System.arraycopy(encryptedBody, 0, packet, HEADER_SIZE, bodyLen);
        int bodyCrc = Crc16.calculate(packet, HEADER_SIZE, bodyLen);
        packet[HEADER_SIZE + bodyLen]     = (byte) ((bodyCrc >> 8) & 0xFF);
        packet[HEADER_SIZE + bodyLen + 1] = (byte) (bodyCrc & 0xFF);

        return packet;
    }

    private byte[] serializeMessage(Message message) {
        byte[] payloadBytes = message.getPayload().getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[8 + payloadBytes.length];

        ByteBuffer buf = ByteBuffer.wrap(body);
        buf.putInt(0, message.getCommandType());
        buf.putInt(4, message.getUserId());

        if (payloadBytes.length > 0) {
            System.arraycopy(payloadBytes, 0, body, 8, payloadBytes.length);
        }
        return body;
    }
}