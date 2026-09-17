package io.ably.pubsub.util;

import java.util.Timer;
import java.util.TimerTask;

import io.ably.pubsub.debug.DebugOptions;
import io.ably.pubsub.types.ClientOptions;

public class SystemClock implements Clock {
    public static final SystemClock INSTANCE = new SystemClock();

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public long nanoTime() {
        return System.nanoTime();
    }

    @Override
    public AblyTimer newTimer(String name) {
        Timer jTimer = new Timer(name, true);
        return new AblyTimer() {
            @Override
            public TimerInstance schedule(TimerTask task, long delayMs) {
                jTimer.schedule(task, delayMs);
                return task::cancel;
            }

            @Override
            public void cancel() {
                jTimer.cancel();
            }
        };
    }

    @Override
    public void waitOn(Object target, long timeout) throws InterruptedException {
        target.wait(timeout);
    }

    public static Clock clockFrom(ClientOptions opts) {
        if (opts instanceof DebugOptions) {
            Clock c = ((DebugOptions) opts).clock;
            if (c != null) return c;
        }
        return INSTANCE;
    }
}
