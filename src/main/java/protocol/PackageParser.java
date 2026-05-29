package protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class PackageParser {

    private static final int HEADER_SIZE = 16;
    private static final int BODY_OFFSET = 16;

    private final CryptoService cryptoService;

    public PackageParser(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    public Package parse(byte[] raw) throws Exception {
        if (raw == null || raw.length < HEADER_SIZE + 2) {
            throw new IllegalArgumentException(
                    "protocol.Package too short: " + (raw == null ? 0 : raw.length));
        }

        if (raw[0] != Package.MAGIC) {
            throw new IllegalArgumentException(
                    String.format("Invalid magic byte: 0x%02X", raw[0]));
        }

        byte clientId = raw[1];
        ByteBuffer buf = ByteBuffer.wrap(raw);
        long packetId = buf.getLong(2);
        int  bodyLen  = buf.getInt(10);

        if (bodyLen < 0) {
            throw new IllegalArgumentException("Negative body length: " + bodyLen);
        }

        int expectedTotal = BODY_OFFSET + bodyLen + 2;
        if (raw.length < expectedTotal) {
            throw new IllegalArgumentException(
                    "protocol.Package too short: got " + raw.length + ", expected " + expectedTotal);
        }

        int storedHeaderCrc = ((raw[14] & 0xFF) << 8) | (raw[15] & 0xFF);
        int calcHeaderCrc   = Crc16.calculate(raw, 0, HEADER_SIZE - 2);
        if (storedHeaderCrc != calcHeaderCrc) {
            throw new IllegalArgumentException(String.format(
                    "Header CRC mismatch: stored=0x%04X, calculated=0x%04X",
                    storedHeaderCrc, calcHeaderCrc));
        }

        int bodyCrcOffset = BODY_OFFSET + bodyLen;
        int storedBodyCrc = ((raw[bodyCrcOffset] & 0xFF) << 8)
                | (raw[bodyCrcOffset + 1] & 0xFF);
        int calcBodyCrc   = Crc16.calculate(raw, BODY_OFFSET, bodyLen);
        if (storedBodyCrc != calcBodyCrc) {
            throw new IllegalArgumentException(String.format(
                    "Body CRC mismatch: stored=0x%04X, calculated=0x%04X",
                    storedBodyCrc, calcBodyCrc));
        }

        byte[] encryptedBody = new byte[bodyLen];
        System.arraycopy(raw, BODY_OFFSET, encryptedBody, 0, bodyLen);
        byte[] plainBody = cryptoService.decrypt(encryptedBody);

        Message message = parseMessage(plainBody);

        return new Package(clientId, packetId, message);
    }

    private Message parseMessage(byte[] body) {
        if (body.length < 8) {
            throw new IllegalArgumentException("protocol.Message body too short: " + body.length);
        }
        ByteBuffer buf = ByteBuffer.wrap(body);
        int commandType = buf.getInt(0);
        int userId      = buf.getInt(4);

        String payload = "";
        if (body.length > 8) {
            payload = new String(body, 8, body.length - 8, StandardCharsets.UTF_8);
        }
        return new Message(commandType, userId, payload);
    }
}