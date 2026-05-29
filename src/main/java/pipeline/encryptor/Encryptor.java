package pipeline.encryptor;

import protocol.Message;

public interface Encryptor {
    byte[] encrypt(Message message);
}
