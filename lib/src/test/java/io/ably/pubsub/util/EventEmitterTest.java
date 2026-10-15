package io.ably.pubsub.util;

import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EventEmitterTest {

    private enum MyEvents {
        event_0,
        event_1
    }

    private static class MyEventPayload {
        public MyEvents event;
        public String message;
    }

    private interface MyListener {
        void onMyThingHappened(MyEventPayload theThing);
    }

    private static class CountingListener implements MyListener {
        @Override
        public void onMyThingHappened(MyEventPayload theThing) {
            Integer count = counts.get(theThing.event); if(count == null) count = 0;
            counts.put(theThing.event, count + 1);
        }
        HashMap<MyEvents, Integer> counts = new HashMap<>();
    }

    private static class MyEmitter extends EventEmitter<MyEvents, MyListener> {
        @Override
        protected void apply(MyListener listener, final MyEvents ev, final Object... args) {
            listener.onMyThingHappened(new MyEventPayload() {{ event = ev; message = (String)args[0]; }});
        }
    }

    /**
     * Register a listener, and verify it is called
     * when the event is emitted
     */
    @Test
    public void on_simple() {
        MyEmitter emitter = new MyEmitter();
        emitter.on(new MyListener() {
            @Override
            public void onMyThingHappened(MyEventPayload theThing) {
                assertEquals(theThing.event, MyEvents.event_0);
                assertEquals(theThing.message, "on_simple");
            }
        });
        emitter.emit(MyEvents.event_0, "on_simple");
    }

    /**
     * Register a listener, and verify it is called
     * when the event is emitted more than once
     */
    @Test
    public void on_multiple() {
        MyEmitter emitter = new MyEmitter();
        CountingListener listener = new CountingListener();
        emitter.on(listener);
        emitter.emit(MyEvents.event_0, "on_multiple_0");
        emitter.emit(MyEvents.event_0, "on_multiple_0");
        emitter.emit(MyEvents.event_1, "on_multiple_1");
        assertEquals(listener.counts.get(MyEvents.event_0), Integer.valueOf(2));
        assertEquals(listener.counts.get(MyEvents.event_1), Integer.valueOf(1));
    }

    /**
     * Register and unregister listener, and verify it
     * is not called when the event is emitted
     */
    @Test
    public void off_simple() {
        MyEmitter emitter = new MyEmitter();
        CountingListener listener = new CountingListener();
        emitter.on(listener);
        emitter.off(listener);
        emitter.emit(MyEvents.event_0, "on_multiple_0");
        emitter.emit(MyEvents.event_1, "on_multiple_1");
        assertNull(listener.counts.get(MyEvents.event_0));
        assertNull(listener.counts.get(MyEvents.event_1));
    }

    /**
     * Register and unregister multiple listeners, and verify they
     * are not called when the event is emitted
     */
    @Test
    public void off_all() {
        MyEmitter emitter = new MyEmitter();
        CountingListener listener1 = new CountingListener();
        CountingListener listener2 = new CountingListener();
        emitter.on(listener1);
        emitter.on(listener2);
        emitter.off();
        emitter.emit(MyEvents.event_0, "on_multiple_0");
        emitter.emit(MyEvents.event_1, "on_multiple_1");
        assertNull(listener1.counts.get(MyEvents.event_0));
        assertNull(listener1.counts.get(MyEvents.event_1));
        assertNull(listener2.counts.get(MyEvents.event_0));
        assertNull(listener2.counts.get(MyEvents.event_1));
    }

    /**
     * Register a listener for a specific event, and verify it is called
     * only when that event is emitted
     */
    @Test
    public void on_event_simple() {
        MyEmitter emitter = new MyEmitter();
        CountingListener listener = new CountingListener();
        emitter.on(MyEvents.event_0, listener);
        emitter.emit(MyEvents.event_0, "on_event_simple_0");
        emitter.emit(MyEvents.event_0, "on_event_simple_0");
        emitter.emit(MyEvents.event_1, "on_event_simple_1");
        assertEquals(listener.counts.get(MyEvents.event_0), Integer.valueOf(2));
        assertNull(listener.counts.get(MyEvents.event_1));
    }

    /**
     * Register a listener for a specific event, and verify
     * it is no longer called after it has been removed
     */
    @Test
    public void off_event_simple() {
        MyEmitter emitter = new MyEmitter();
        CountingListener listener = new CountingListener();
        emitter.on(MyEvents.event_0, listener);
        emitter.emit(MyEvents.event_0, "off_event_simple_0");
        emitter.emit(MyEvents.event_1, "off_event_simple_1");
        emitter.off(MyEvents.event_0, listener);
        emitter.emit(MyEvents.event_0, "off_event_simple_0");
        assertEquals(listener.counts.get(MyEvents.event_0), Integer.valueOf(1));
        assertNull(listener.counts.get(MyEvents.event_1));
    }

    /**
     * Register a "once" listener for a specific event, and
     * verify it is called only once when that event is emitted
     */
    @Test
    public void once_event_simple() {
        MyEmitter emitter = new MyEmitter();
        CountingListener listener = new CountingListener();
        emitter.once(MyEvents.event_0, listener);
        emitter.emit(MyEvents.event_0, "once_event_simple_0");
        emitter.emit(MyEvents.event_0, "once_event_simple_0");
        emitter.emit(MyEvents.event_1, "once_event_simple_1");
        assertEquals(listener.counts.get(MyEvents.event_0), Integer.valueOf(1));
        assertNull(listener.counts.get(MyEvents.event_1));
    }

    /**
     * Register a "once" listener for a specific event, then
     * remove it, and verify it is not called when that event is emitted
     */
    @Test
    public void once_off_event_simple() {
        MyEmitter emitter = new MyEmitter();
        CountingListener listener = new CountingListener();
        emitter.once(MyEvents.event_0, listener);
        emitter.emit(MyEvents.event_1, "once_event_simple_1");
        emitter.off(MyEvents.event_0, listener);
        emitter.emit(MyEvents.event_0, "once_event_simple_0");
        assertNull(listener.counts.get(MyEvents.event_0));
        assertNull(listener.counts.get(MyEvents.event_1));
    }

    /**
     * Register event listeners inside the listener and ensure they are not called during that event.
     */
    @Test
    public void on_all_events_listener_in_listener() {
        final MyEmitter emitter = new MyEmitter();

        final CountingListener allEventsListener = new CountingListener();

        emitter.on(MyEvents.event_0, new MyListener() {
            @Override
            public void onMyThingHappened(MyEventPayload theThing) {
                //Add a listener here.
                emitter.on(allEventsListener);
                assertTrue(theThing.message.contains("fireEvent"));

            }
        });

        emitter.emit(MyEvents.event_0, "fireEvent");
        emitter.emit(MyEvents.event_0, "fireEvent");

        assertEquals(allEventsListener.counts.get(MyEvents.event_0), Integer.valueOf(1));
    }

    /**
     * Register event listener "once" inside the listener and ensure they are not called during that event.
     */
    @Test
    public void once_all_events_listener_in_listener() {
        final MyEmitter emitter = new MyEmitter();

        final CountingListener allEventsListener = new CountingListener();

        final CountingListener event0Listener = new CountingListener() {
            @Override
            public void onMyThingHappened(MyEventPayload theThing) {
                super.onMyThingHappened(theThing);
                //Add a "once" listener in event0. We ensure it's added only once.
                if ("event_0_first".equalsIgnoreCase(theThing.message)) {
                    emitter.once(allEventsListener);
                }

                assertTrue(theThing.message.contains("event_0"));
            }
        };

        emitter.on(MyEvents.event_0, event0Listener);

        emitter.emit(MyEvents.event_0, "event_0_first");
        //Let's fire "once" added listener
        emitter.emit(MyEvents.event_0, "event_0_second");
        //We fire once again.
        emitter.emit(MyEvents.event_0, "event_0_third");

        assertEquals(allEventsListener.counts.get(MyEvents.event_0), Integer.valueOf(1));
    }

    /**
     * Register event listener "once" inside the listener and ensure they are not called during that event.
     */
    @Test
    public void once_event_specific_listener_in_listener() {
        final MyEmitter emitter = new MyEmitter();

        final CountingListener event0Listener = new CountingListener();
        final CountingListener event1Listener = new CountingListener();

        emitter.once(MyEvents.event_0, new MyListener() {
            @Override
            public void onMyThingHappened(MyEventPayload theThing) {
                //Add a "once" listener here.
                emitter.once(MyEvents.event_0, event0Listener);
                emitter.once(MyEvents.event_1, event1Listener);
                assertTrue(theThing.message.contains("fireEvent"));
            }
        });

        emitter.emit(MyEvents.event_0, "fireEvent1");
        //Let's fire "once" added listener
        emitter.emit(MyEvents.event_0, "fireEvent2");
        //Once listener should not called again.
        emitter.emit(MyEvents.event_0, "fireEvent3");

        assertEquals(event0Listener.counts.get(MyEvents.event_0), Integer.valueOf(1));
        assertNull(event1Listener.counts.get(MyEvents.event_1));
    }

    /**
     * Register a specific event listener inside the listener and ensure they are not called during that event.
     */
    @Test
    public void on_specific_event_listener_in_listener() {
        final MyEmitter emitter = new MyEmitter();

        final CountingListener event0Listener = new CountingListener();
        final CountingListener event1Listener = new CountingListener();

        emitter.on(MyEvents.event_0, new MyListener() {
            @Override
            public void onMyThingHappened(MyEventPayload theThing) {
                emitter.on(MyEvents.event_0, event0Listener);
                emitter.on(MyEvents.event_1, event1Listener);
                assertTrue(theThing.message.contains("fireEvent"));
            }
        });

        emitter.emit(MyEvents.event_0, "fireEvent");
        emitter.emit(MyEvents.event_0, "fireEvent");

        assertEquals(event0Listener.counts.get(MyEvents.event_0), Integer.valueOf(1));
        assertNull(event1Listener.counts.get(MyEvents.event_1));
    }

    /**
     * Remove "off" an all events listener inside the listener and ensure they are not called during that event.
     */
    @Test
    public void off_all_events_listener_in_listener() {
        final MyEmitter emitter = new MyEmitter();

        final CountingListener allEventsListener = new CountingListener();

        //This is called once.
        emitter.on(allEventsListener);

        emitter.on(MyEvents.event_0, new MyListener() {
            @Override
            public void onMyThingHappened(MyEventPayload theThing) {
                //Turn off here.
                emitter.off(allEventsListener);
                assertTrue(theThing.message.contains("fireEvent"));
            }
        });

        emitter.emit(MyEvents.event_0, "fireEvent");
        emitter.emit(MyEvents.event_0, "fireEvent");

        assertEquals(allEventsListener.counts.get(MyEvents.event_0), Integer.valueOf(1));
    }

    /**
     * "off" event specific listeners inside the listener and ensure they are not called during that event.
     */
    @Test
    public void off_specific_event_listener_in_listener() {
        final MyEmitter emitter = new MyEmitter();

        final CountingListener event0Listener = new CountingListener();
        final CountingListener event1Listener = new CountingListener();

        //This is called once.
        emitter.on(MyEvents.event_0, event0Listener);

        emitter.on(MyEvents.event_0, new MyListener() {
            @Override
            public void onMyThingHappened(MyEventPayload theThing) {
                //Turn off here.
                emitter.off(MyEvents.event_0, event0Listener);
                emitter.off(MyEvents.event_1, event1Listener);
                assertTrue(theThing.message.contains("fireEvent"));
            }
        });

        emitter.emit(MyEvents.event_0, "fireEvent");
        emitter.emit(MyEvents.event_0, "fireEvent");

        assertEquals(event0Listener.counts.get(MyEvents.event_0), Integer.valueOf(1));
        assertNull(event1Listener.counts.get(MyEvents.event_1));
    }
}
