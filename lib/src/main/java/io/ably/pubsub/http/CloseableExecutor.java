package io.ably.pubsub.http;

import java.util.concurrent.Executor;

public interface CloseableExecutor extends Executor, AutoCloseable {
}
