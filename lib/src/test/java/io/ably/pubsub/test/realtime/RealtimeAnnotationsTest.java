package io.ably.pubsub.test.realtime;

import io.ably.pubsub.realtime.RealtimeClient;
import io.ably.pubsub.realtime.RealtimeClientFactory;
import io.ably.pubsub.realtime.Channel;
import io.ably.pubsub.realtime.ChannelState;
import io.ably.pubsub.http.HttpClientFactory;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.test.common.Helpers;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.Annotation;
import io.ably.pubsub.types.AnnotationAction;
import io.ably.pubsub.types.ChannelMode;
import io.ably.pubsub.types.ChannelOptions;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.Message;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.types.PaginatedResult;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RealtimeAnnotationsTest extends ParameterizedTest {

    @Rule
    public Timeout testTimeout = Timeout.seconds(60);

    @Test
    public void publish_and_subscribe_annotations() throws Exception {

        String channelName = "mutable:publish_subscribe_annotation";

        TestChannel testChannel = new TestChannel(channelName);

        Channel channel = testChannel.realtimeChannel;

        final Message[] receivedMessage = new Message[1];
        channel.subscribe(message -> receivedMessage[0] = message);

        final Annotation[] receivedAnnotation = new Annotation[1];
        Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
        channel.annotations.subscribe(annotation -> {
            receivedAnnotation[0] = annotation;
            waiter.onSuccess();
        });

        Helpers.MessageWaiter messageWaiter = new Helpers.MessageWaiter(channel);
        channel.publish("message", "foobar");
        messageWaiter.waitFor(1);

        assertNotNull("Message should be received", receivedMessage[0]);

        Annotation emoji1Annotation = new Annotation();
        emoji1Annotation.type = "reaction:distinct.v1";
        emoji1Annotation.name = "👍";

        channel.annotations.publish(receivedMessage[0].serial, emoji1Annotation);
        waiter.waitFor();

        assertNotNull("Annotation should be received", receivedAnnotation[0]);
        assertEquals(AnnotationAction.ANNOTATION_CREATE, receivedAnnotation[0].action);
        assertEquals(receivedMessage[0].serial, receivedAnnotation[0].messageSerial);
        assertEquals("reaction:distinct.v1", receivedAnnotation[0].type);
        assertEquals("👍", receivedAnnotation[0].name);
        assertTrue(receivedAnnotation[0].serial.compareTo(receivedAnnotation[0].messageSerial) > 0);

        waiter.reset();

        receivedAnnotation[0] = null;
        Annotation emoji2Annotation = new Annotation();
        emoji2Annotation.type = "reaction:distinct.v1";
        emoji2Annotation.name = "😕";
        testChannel.httpChannel.annotations.publish(receivedMessage[0].serial, emoji2Annotation);

        waiter.waitFor();

        assertNotNull("HTTP-published annotation should be received", receivedAnnotation[0]);
        assertEquals(AnnotationAction.ANNOTATION_CREATE, receivedAnnotation[0].action);
        assertEquals(receivedMessage[0].serial, receivedAnnotation[0].messageSerial);
        assertEquals("reaction:distinct.v1", receivedAnnotation[0].type);
        assertEquals("😕", receivedAnnotation[0].name);
        assertTrue(receivedAnnotation[0].serial.compareTo(receivedAnnotation[0].messageSerial) > 0);

        testChannel.dispose();
    }

    @Test
    public void get_all_annotations() throws Exception {
        String channelName = "mutable:get_all_annotations_for_a_message";

        TestChannel testChannel = new TestChannel(channelName);
        Channel channel = testChannel.realtimeChannel;

        final Message[] receivedMessage = new Message[1];
        channel.subscribe(message -> receivedMessage[0] = message);

        Helpers.MessageWaiter messageWaiter = new Helpers.MessageWaiter(channel);
        channel.publish("message", "foobar");
        messageWaiter.waitFor(1);

        Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
        channel.annotations.subscribe(annotation -> waiter.onSuccess());

        String[] emojis = new String[]{"👍", "😕", "👎", "👍👍", "😕😕", "👎👎"};
        for (String emoji : emojis) {
            Annotation annotation = new Annotation();
            annotation.type = "reaction:distinct.v1";
            annotation.name = emoji;
            testChannel.httpChannel.annotations.publish(receivedMessage[0].serial, annotation);
        }

        waiter.waitFor(6);

        // There is a gap between receiving annotation messages and getting them in annotations
        Thread.sleep(1_000);

        PaginatedResult<Annotation> result = channel.annotations.get(receivedMessage[0].serial);
        assertEquals(6, result.items().length);

        assertEquals(AnnotationAction.ANNOTATION_CREATE, result.items()[0].action);
        assertEquals(receivedMessage[0].serial, result.items()[0].messageSerial);
        assertEquals("reaction:distinct.v1", result.items()[0].type);
        assertEquals("👍", result.items()[0].name);
        assertEquals("😕", result.items()[1].name);
        assertEquals("👎", result.items()[2].name);
        assertTrue(result.items()[1].serial.compareTo(result.items()[0].serial) > 0);
        assertTrue(result.items()[2].serial.compareTo(result.items()[1].serial) > 0);

        result = channel.annotations.get(receivedMessage[0].serial, new Param[]{new Param("limit", "2")});
        assertEquals(2, result.items().length);
        assertEquals("👍", result.items()[0].name);
        assertEquals("😕", result.items()[1].name);
        assertTrue(result.hasNext());

        result = result.next();
        assertNotNull(result);
        assertEquals(2, result.items().length);
        assertEquals("👎", result.items()[0].name);
        assertEquals("👍👍", result.items()[1].name);
        assertTrue(result.hasNext());

        result = result.next();
        assertNotNull(result);
        assertEquals(2, result.items().length);
        assertEquals("😕😕", result.items()[0].name);
        assertEquals("👎👎", result.items()[1].name);
        assertTrue(!result.hasNext());
    }


    private class TestChannel {
        TestChannel(String channelName) throws AblyException {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.clientId = UUID.randomUUID().toString();
            rest = HttpClientFactory.create(opts);
            httpChannel = rest.channels.get(channelName);
            realtime = RealtimeClientFactory.create(opts);
            ChannelOptions channelOptions = new ChannelOptions();
            channelOptions.modes = new ChannelMode[] {
                ChannelMode.publish, ChannelMode.subscribe, ChannelMode.annotation_publish, ChannelMode.annotation_subscribe
            };

            realtimeChannel = realtime.channels.get(channelName, channelOptions);
            realtimeChannel.attach();
            (new Helpers.ChannelWaiter(realtimeChannel)).waitFor(ChannelState.attached);
        }

        void dispose() throws Exception {
            realtime.close();
            rest.close();
        }

        PubSubHttpClient rest;
        RealtimeClient realtime;
        io.ably.pubsub.http.Channel httpChannel;
        io.ably.pubsub.realtime.Channel realtimeChannel;
    }

}
