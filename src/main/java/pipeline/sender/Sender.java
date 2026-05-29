package pipeline.sender;

import java.net.InetAddress;

public interface Sender {
    void send(byte[] message, InetAddress target);
}
