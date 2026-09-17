package io.ably.pubsub.types;

import java.io.IOException;

import io.ably.pubsub.transport.Defaults;
import io.ably.pubsub.util.Log;

import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessageUnpacker;

/**
 * Contains any constraints a client should adhere to and provides additional metadata about a {@link io.ably.pubsub.realtime.Connection},
 * such as if a request to {@link io.ably.pubsub.realtime.Channel#publish} a message that exceeds the maximum message size should
 * be rejected immediately without communicating with Ably.
 */
public class ConnectionDetails {
    /**
     * Contains the client ID assigned to the token.
     * If clientId is null or omitted, then the client is prohibited from assuming a clientId in any operations,
     * however if clientId is a wildcard string *, then the client is permitted to assume any clientId.
     * Any other string value for clientId implies that the clientId is both enforced and assumed for all operations from this client.
     * <p>
     * Spec: RSA12a, CD2a
     */
    public String clientId;
    /**
     * The connection secret key string that is used to resume a connection and its state.
     * <p>
     * Spec: RTN15e, CD2b
     */
    public String connectionKey;
    /**
     * A unique identifier for the front-end server that the client has connected to.
     * This server ID is only used for the purposes of debugging.
     * <p>
     * Spec: CD2g
     */
    public String serverId;
    /**
     * The maximum message size is an attribute of an Ably account and enforced by Ably servers.
     * maxMessageSize indicates the maximum message size allowed by the Ably account this connection is using.
     * <p>
     * Spec: CD2c
     */
    public int maxMessageSize;
    /**
     * The maximum allowable number of requests per second from a client or Ably.
     * In the case of a realtime connection, this restriction applies to the number of messages sent,
     * whereas in the case of REST, it is the total number of REST requests per second.
     * <p>
     * Spec: CD2e
     */
    public Long maxInboundRate;
    public Long maxOutboundRate;

    /**
     * Overrides the default maxFrameSize.
     * <p>
     * Spec: CD2d
     */
    public Long maxFrameSize;
    /**
     * The maximum length of time in milliseconds that the server will allow no activity to occur in the server to client direction.
     * After such a period of inactivity, the server will send a HEARTBEAT or transport-level ping to the client.
     * If the value is 0, the server will allow arbitrarily-long levels of inactivity.
     * <p>
     * Spec: CD2h
     */
    public Long maxIdleInterval;
    /**
     * The duration that Ably will persist the connection state for when a Realtime client is abruptly disconnected.
     * <p>
     * Spec: CD2f, RTN14e, DF1a
     */
    public Long connectionStateTtl;
    /**
     * An opaque string identifying the server instance that the client is connected to.
     * Used as a key in siteTimeserials maps for LiveObjects operations.
     * <p>
     * Spec: CD2j
     */
    public String siteCode;

    /**
     * The duration in milliseconds used to retain tombstoned objects at client side.
     */
    public Long objectsGCGracePeriod;

    ConnectionDetails() {
        maxIdleInterval = Defaults.maxIdleInterval;
        connectionStateTtl = Defaults.connectionStateTtl;
    }

    ConnectionDetails readMsgpack(MessageUnpacker unpacker) throws IOException {
        int fieldCount = unpacker.unpackMapHeader();
        for(int i = 0; i < fieldCount; i++) {
            String fieldName = unpacker.unpackString().intern();
            MessageFormat fieldFormat = unpacker.getNextFormat();
            if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }

            switch(fieldName) {
                case "clientId":
                    clientId = unpacker.unpackString();
                    break;
                case "connectionKey":
                    connectionKey = unpacker.unpackString();
                    break;
                case "serverId":
                    serverId = unpacker.unpackString();
                    break;
                case "maxMessageSize":
                    maxMessageSize = unpacker.unpackInt();
                    break;
                case "maxInboundRate":
                    maxInboundRate = unpacker.unpackLong();
                    break;
                case "maxOutboundRate":
                    maxOutboundRate = unpacker.unpackLong();
                    break;
                case "maxFrameSize":
                    maxFrameSize = unpacker.unpackLong();
                    break;
                case "maxIdleInterval":
                    maxIdleInterval = unpacker.unpackLong();
                    break;
                case "connectionStateTtl":
                    connectionStateTtl = unpacker.unpackLong();
                    break;
                case "siteCode":
                    siteCode = unpacker.unpackString();
                    break;
                case "objectsGCGracePeriod":
                    objectsGCGracePeriod = unpacker.unpackLong();
                    break;
                default:
                    Log.v(TAG, "Unexpected field: " + fieldName);
                    unpacker.skipValue();
            }
        }
        return this;
    }

    static ConnectionDetails fromMsgpack(MessageUnpacker unpacker) throws IOException {
        return (new ConnectionDetails()).readMsgpack(unpacker);
    }

    private static final String TAG = ConnectionDetails.class.getName();
}
