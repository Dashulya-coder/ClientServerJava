package pipeline;

import pipeline.decryptor.DecryptorWorker;
import pipeline.encryptor.EncryptorWorker;
import pipeline.processor.ProcessorWorker;
import pipeline.receiver.MockMessageGenerator;
import pipeline.receiver.MockReceiver;
import pipeline.sender.MockSender;
import protocol.CryptoService;
import protocol.Message;
import protocol.Package;
import protocol.PackageBuilder;
import protocol.PackageParser;
import warehouse.Warehouse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Pipeline {

    private final ExecutorService executor;
    private final List<MockReceiver> receivers;
    private final List<DecryptorWorker> decryptors;
    private final List<ProcessorWorker> processors;
    private final List<EncryptorWorker> encryptors;
    private final List<MockSender> senders;

    public Pipeline(Warehouse warehouse, byte[] key) {
        this(warehouse, key, 1, 1, 1, 1, 1);
    }

    public Pipeline(Warehouse warehouse, byte[] key,
                    int receiverCount, int decryptorCount, int processorCount,
                    int encryptorCount, int senderCount) {

        BlockingQueue<byte[]> rawQueue = new LinkedBlockingQueue<>(100);
        BlockingQueue<Package> parsedQueue = new LinkedBlockingQueue<>(100);
        BlockingQueue<Message> processedQueue = new LinkedBlockingQueue<>(100);
        BlockingQueue<byte[]> encryptedQueue = new LinkedBlockingQueue<>(100);

        CryptoService crypto = new CryptoService(key);
        PackageBuilder builder = new PackageBuilder(crypto);
        PackageParser parser = new PackageParser(crypto);
        MockMessageGenerator generator = new MockMessageGenerator(builder);

        receivers = new ArrayList<>();
        for (int i = 0; i < receiverCount; i++) {
            receivers.add(new MockReceiver(generator, rawQueue));
        }

        decryptors = new ArrayList<>();
        for (int i = 0; i < decryptorCount; i++) {
            decryptors.add(new DecryptorWorker(parser, rawQueue, parsedQueue));
        }

        processors = new ArrayList<>();
        for (int i = 0; i < processorCount; i++) {
            processors.add(new ProcessorWorker(warehouse, parsedQueue, processedQueue));
        }

        encryptors = new ArrayList<>();
        for (int i = 0; i < encryptorCount; i++) {
            encryptors.add(new EncryptorWorker(builder, processedQueue, encryptedQueue));
        }

        senders = new ArrayList<>();
        for (int i = 0; i < senderCount; i++) {
            senders.add(new MockSender(encryptedQueue));
        }

        int total = receiverCount + decryptorCount + processorCount + encryptorCount + senderCount;
        executor = Executors.newFixedThreadPool(total);
    }

    public void start() {
        receivers.forEach(executor::submit);
        decryptors.forEach(executor::submit);
        processors.forEach(executor::submit);
        encryptors.forEach(executor::submit);
        senders.forEach(executor::submit);
    }

    public void stop() throws InterruptedException {
        receivers.forEach(MockReceiver::stop);
        decryptors.forEach(DecryptorWorker::stop);
        processors.forEach(ProcessorWorker::stop);
        encryptors.forEach(EncryptorWorker::stop);
        senders.forEach(MockSender::stop);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}