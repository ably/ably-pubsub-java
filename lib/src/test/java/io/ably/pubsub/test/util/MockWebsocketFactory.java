package io.ably.pubsub.test.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.ably.pubsub.transport.ConnectionManager;
import io.ably.pubsub.transport.ITransport;
import io.ably.pubsub.transport.WebSocketTransport;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.PresenceMessage;
import io.ably.pubsub.types.ProtocolMessage;

/**
 * Websocket factory that creates transport with capability of modifying the behaviour of send() and other calls
 */
public class MockWebsocketFactory implements ITransport.Factory {

    enum SendBehaviour {
        allow,
        block,
        fail
    }

    enum ReceiveBehaviour {
        allow,
        block,
        blockAndQueue,
        fail
    }

    enum ConnectBehaviour {
        allow,
        fail
    }

    public interface MessageFilter {
        boolean matches(ProtocolMessage message);
    }

    public interface HostFilter {
        boolean matches(String hostname);
    }

    public interface HostTransform {
        String transformHost(String givenHost);
    }

    SendBehaviour sendBehaviour = SendBehaviour.allow;
    ReceiveBehaviour receiveBehaviour = ReceiveBehaviour.allow;
    ConnectBehaviour connectBehaviour = ConnectBehaviour.allow;
    MessageFilter sendMessageFilter = null;
    MessageFilter receiveMessageFilter = null;
    HostFilter hostFilter = null;
    HostTransform hostTransform = null;

    final List<ProtocolMessage> blockedReceiveQueue = new ArrayList<>();

    public ITransport lastCreatedTransport =  null;

    public static class TransformParams extends ITransport.TransportParams {
        private HostTransform hostTransform;

        TransformParams(ITransport.TransportParams src, HostTransform hostTransform) {
            super(src.getClientOptions(), new EmptyPlatformAgentProvider());
            this.hostTransform = hostTransform;
            this.host = hostTransform.transformHost(src.getHost());
            this.port = src.getPort();
        }
    }

    @Override
    public ITransport getTransport(final ITransport.TransportParams transportParams, ConnectionManager connectionManager) {
        ITransport.TransportParams transformParams = transportParams;
        if(hostTransform != null) {
            transformParams = new TransformParams(transportParams, hostTransform);
        }
        lastCreatedTransport = new MockWebsocketTransport(transportParams, transformParams, connectionManager);
        return lastCreatedTransport;
    }

    //only use this when you know when transport is created - just for tests
    public MockWebsocketTransport getCreatedTransport() {
        return (MockWebsocketTransport) lastCreatedTransport;
    }

    public void blockSend(MessageFilter filter) {
        sendMessageFilter = filter;
        sendBehaviour = SendBehaviour.block;
    }

    /*
    We cannot prevent server sending us messages from here so instead, this will block processing messages from this
    point. That is they will not be triggering connection manager's onMessage which will help simulate some conditions
    * */
    public void blockReceiveProcessing(MessageFilter filter) {
        receiveMessageFilter = filter;
        receiveBehaviour = ReceiveBehaviour.block;
    }

    /*
  We cannot prevent server sending us messages from here so instead, this will block processing messages from this
  point. That is they will not be triggering connection manager's onMessage which will help simulate some conditions
  * */
    public void blockReceiveProcessingAndQueueBlockedMessages(MessageFilter filter) {
        receiveMessageFilter = filter;
        receiveBehaviour = ReceiveBehaviour.blockAndQueue;
    }

    public void allowReceiveProcessing(MessageFilter filter) {
        receiveMessageFilter = filter;
        receiveBehaviour = ReceiveBehaviour.allow;
    }

    public void blockSend() { blockSend(null); }

    public void allowSend(MessageFilter filter) {
        sendMessageFilter = filter;
        sendBehaviour = SendBehaviour.allow;
    }
    public void allowSend() { allowSend(null);}

    public void failSend(MessageFilter filter) {
        sendMessageFilter = filter;
        sendBehaviour = SendBehaviour.fail;
    }
    public void failSend() { failSend(null); }

    public void setSendMessageFilter(MessageFilter filter) {
        sendMessageFilter = filter;
    }

    public void failConnect(HostFilter filter) {
        hostFilter = filter;
        connectBehaviour = ConnectBehaviour.fail;
    }
    public void failConnect() { failConnect(null); }

    public void setHostTransform(HostTransform transform) {
        hostTransform = transform;
    }

    /*
     * Special transport class that allows blocking send() and other operations
     */
    public class MockWebsocketTransport extends WebSocketTransport {
        private final TransportParams givenTransportParams;
        private final TransportParams transformedTransportParams;
        //Sent presence or normal messages
        private final List<ProtocolMessage> sentMessages = new ArrayList<>();

        private MockWebsocketTransport(TransportParams givenTransportParams, TransportParams transformedTransportParams, ConnectionManager connectionManager) {
            super(transformedTransportParams, connectionManager);
            this.givenTransportParams = givenTransportParams;
            this.transformedTransportParams = transformedTransportParams;
            turnOffActivityCheckIfPingListenerIsNotSupported();
        }

        public List<ProtocolMessage> getSentMessages() {
            return sentMessages;
        }

        public List<ProtocolMessage> getPublishedMessages() {
            return sentMessages.stream().filter(protocolMessage -> protocolMessage.action == ProtocolMessage.Action.message).collect(Collectors.toList());
        }

        public List<PresenceMessage> getSentPresenceMessages() {
            final List<ProtocolMessage> protocolMessages = sentMessages.stream()
                .filter(protocolMessage -> protocolMessage.action == ProtocolMessage.Action.presence)
                .collect(Collectors.toList());
            final List<PresenceMessage> presenceMessages = new ArrayList<>();
            protocolMessages.forEach(protocolMessage -> {
                Collections.addAll(presenceMessages, protocolMessage.presence);
            });
            return presenceMessages;
        }

        public void clearPublishedMessages() {
            sentMessages.clear();
        }

        @Override
        public void send(ProtocolMessage msg) throws AblyException {
            if (msg.action == ProtocolMessage.Action.message || msg.action == ProtocolMessage.Action.presence){
                sentMessages.add(msg);
            }
            switch (sendBehaviour) {
                case allow:
                    if (sendMessageFilter == null || sendMessageFilter.matches(msg)) {
                        super.send(msg);
                    }
                    break;
                case block:
                    if (sendMessageFilter == null || sendMessageFilter.matches(msg)) {
                        /* do nothing */
                    } else {
                        super.send(msg);
                    }
                    break;
                case fail:
                    if (sendMessageFilter == null || sendMessageFilter.matches(msg)) {
                        throw AblyException.fromErrorInfo(new ErrorInfo("Mock", 40000));
                    } else {
                        super.send(msg);
                    }
                    break;
            }
        }

        @Override
        public void receive(ProtocolMessage msg) throws AblyException {

            switch (receiveBehaviour) {
                case allow:
                    for (ProtocolMessage queuedMessage: blockedReceiveQueue) {
                        if (receiveMessageFilter == null || receiveMessageFilter.matches(queuedMessage)) {
                            super.receive(queuedMessage);
                        }
                    }
                    if (receiveMessageFilter == null || receiveMessageFilter.matches(msg)) {
                        super.receive(msg);
                    }
                    break;
                case block:
                    if (receiveMessageFilter == null || receiveMessageFilter.matches(msg)) {
                        //process queued messages
                    } else {
                        super.receive(msg);
                    }
                    break;
                case blockAndQueue:
                    if (receiveMessageFilter == null || receiveMessageFilter.matches(msg)) {
                        blockedReceiveQueue.add(msg);
                    } else {
                        super.receive(msg);
                    }
                    break;
                case fail:
                    if (receiveMessageFilter == null || receiveMessageFilter.matches(msg)) {
                        throw AblyException.fromErrorInfo(new ErrorInfo("Mock", 40000));
                    } else {
                        super.receive(msg);
                    }
                    break;
            }
        }


        @Override
        public void connect(ConnectListener connectListener) {
            String host = givenTransportParams.getHost();
            switch (connectBehaviour) {
                case allow:
                    if (hostFilter == null || hostFilter.matches(host)) {
                        System.out.println("MockWebsocketTransport: allowing " + host);
                        super.connect(connectListener);
                    } else {
                        System.out.println("MockWebsocketTransport: disallowing " + host);
                        connectListener.onTransportUnavailable(this, new ErrorInfo("MockWebsocketTransport: connection disallowed by hostFilter", 500, 50000));
                    }
                    break;
                case fail:
                    if (hostFilter == null || hostFilter.matches(host)) {
                        System.out.println("MockWebsocketTransport: failing " + host);
                        connectListener.onTransportUnavailable(this, new ErrorInfo("MockWebsocketTransport: connection failed by hostFilter", 500, 50000));
                    } else {
                        System.out.println("MockWebsocketTransport: not failing " + host);
                        super.connect(connectListener);
                    }
                    break;
            }
        }
    }
}
