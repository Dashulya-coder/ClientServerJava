package pipeline.processor;

import protocol.Message;

public interface Processor {
    void process(Message message);
}
