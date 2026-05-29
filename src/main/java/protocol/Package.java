public class Package {

    public static final byte MAGIC = 0x13;

    private final byte clientId;
    private final long packetId;
    private final Message message;

    public Package(byte clientId, long packetId, Message message) {
        this.clientId = clientId;
        this.packetId = packetId;
        this.message = message;
    }

    public byte getClientId()   { return clientId; }
    public long getPacketId()   { return packetId; }
    public Message getMessage() { return message; }

    @Override
    public String toString() {
        return "Package{clientId=" + (clientId & 0xFF) +
                ", packageId=" + packetId +
                ", message=" + message + "}";
    }
}