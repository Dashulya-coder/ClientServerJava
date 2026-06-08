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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Pipeline {

    private final ExecutorService executor;
    private final MockReceiver receiver;
    private final DecryptorWorker decryptor;
    private final ProcessorWorker processor;
    private final EncryptorWorker encryptor;
    private final MockSender sender;

    public Pipeline(Warehouse warehouse, byte[] cryptoKey) {
        BlockingQueue<byte[]> rawQueue        = new LinkedBlockingQueue<>(100);
        BlockingQueue<Package> parsedQueue    = new LinkedBlockingQueue<>(100);
        BlockingQueue<Message> processedQueue = new LinkedBlockingQueue<>(100);
        BlockingQueue<byte[]> encryptedQueue  = new LinkedBlockingQueue<>(100);

        CryptoService crypto = new CryptoService(cryptoKey);
        PackageBuilder builder = new PackageBuilder(crypto);
        PackageParser parser = new PackageParser(crypto);

        MockMessageGenerator generator = new MockMessageGenerator(builder);

        this.receiver = new MockReceiver(generator, rawQueue);
        this.decryptor = new DecryptorWorker(parser, rawQueue, parsedQueue);
        this.processor = new ProcessorWorker(warehouse, parsedQueue, processedQueue);
        this.encryptor = new EncryptorWorker(builder, processedQueue, encryptedQueue);
        this.sender    = new MockSender(encryptedQueue);

        this.executor = Executors.newFixedThreadPool(5);
    }

    public void start() {
        executor.submit(receiver);
        executor.submit(decryptor);
        executor.submit(processor);
        executor.submit(encryptor);
        executor.submit(sender);
    }

    public void stop() throws InterruptedException {
        receiver.stop();
        decryptor.stop();
        processor.stop();
        encryptor.stop();
        sender.stop();

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}