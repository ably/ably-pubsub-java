package io.ably.pubsub.util;

import java.util.TimerTask;

public interface AblyTimer {
    TimerInstance schedule(TimerTask task, long delayMs);
    void cancel();
}
