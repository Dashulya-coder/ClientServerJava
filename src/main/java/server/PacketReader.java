package server;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

// Reads one full framed packet from a TCP stream
public final class PacketReader {

    public static final int HEADER_SIZE = 16;
    public static final int BODY_LEN_OFFSET = 10;
    public static final int BODY_CRC_SIZE = 2;

    private PacketReader() {
    }

    // Throws exception when the peer closes the connection to detect disconnect
    public static byte[] readPacket(DataInputStream in) throws IOException {
        byte[] header = new byte[HEADER_SIZE];
        in.readFully(header);

        // read header first to learn body length, then read exactly the rest
        int bodyLen = ByteBuffer.wrap(header).getInt(BODY_LEN_OFFSET);
        if (bodyLen < 0 || bodyLen > 16 * 1024 * 1024) {
            throw new IOException("Invalid body length: " + bodyLen);
        }

        byte[] packet = new byte[HEADER_SIZE + bodyLen + BODY_CRC_SIZE];
        System.arraycopy(header, 0, packet, 0, HEADER_SIZE);
        in.readFully(packet, HEADER_SIZE, bodyLen + BODY_CRC_SIZE);
        return packet;
    }
}
