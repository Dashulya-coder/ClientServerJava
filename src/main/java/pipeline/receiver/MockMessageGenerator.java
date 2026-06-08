package pipeline.receiver;

import pipeline.CommandType;
import protocol.Message;
import protocol.PackageBuilder;

import java.util.Random;

//fake messages
public class MockMessageGenerator implements MessageSource {
    private static final String[] PRODUCTS = {"Apple", "Banana", "Orange", "Pineapple"};
    private final Random random = new Random();
    private final PackageBuilder builder;

    public MockMessageGenerator(PackageBuilder builder) {
        this.builder = builder;

    }
    public byte[] generate() {
        String randomProduct = PRODUCTS[random.nextInt(PRODUCTS.length)];
        int randomCommand = CommandType.values()[random.nextInt(CommandType.values().length)].ordinal();
        int randomAmount = random.nextInt(20);
        String payload = randomProduct + ":" + randomAmount;

        byte clientId = (byte) random.nextInt(256);
        long packetId = random.nextLong();
        int userId = random.nextInt(256);
        Message message = new Message(randomCommand,userId,payload);
        try {
            return builder.build(clientId, packetId, message);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }


}
