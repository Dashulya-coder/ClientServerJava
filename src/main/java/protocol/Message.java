package protocol;

public class Message {

    private final int commandType;
    private final int userId;
    private final String payload;

    public Message(int commandType, int userId, String payload) {
        this.commandType = commandType;
        this.userId = userId;
        this.payload = payload != null ? payload : "";
    }

    public int getCommandType() { return commandType; }
    public int getUserId()      { return userId; }
    public String getPayload()  { return payload; }

    @Override
    public String toString() {
        return "protocol.Message{commandType=0x" + String.format("%08X", commandType) +
                ", userId=" + userId + ", payload='" + payload + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message other)) return false;
        return commandType == other.commandType
                && userId == other.userId
                && payload.equals(other.payload);
    }

    @Override
    public int hashCode() {
        int r = commandType;
        r = 31 * r + userId;
        r = 31 * r + payload.hashCode();
        return r;
    }
}