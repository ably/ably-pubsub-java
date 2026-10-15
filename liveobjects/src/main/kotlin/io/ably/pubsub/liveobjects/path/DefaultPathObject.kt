package io.ably.pubsub.liveobjects.path

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import io.ably.pubsub.liveobjects.DefaultRealtimeObject
import io.ably.pubsub.liveobjects.ROOT_OBJECT_ID
import io.ably.pubsub.liveobjects.Subscription
import io.ably.pubsub.liveobjects.ValueType
import io.ably.pubsub.liveobjects.instance.Instance
import io.ably.pubsub.liveobjects.instance.toInstance
import io.ably.pubsub.liveobjects.path.types.BinaryPathObject
import io.ably.pubsub.liveobjects.path.types.BooleanPathObject
import io.ably.pubsub.liveobjects.path.types.DefaultBinaryPathObject
import io.ably.pubsub.liveobjects.path.types.DefaultBooleanPathObject
import io.ably.pubsub.liveobjects.path.types.DefaultJsonArrayPathObject
import io.ably.pubsub.liveobjects.path.types.DefaultJsonObjectPathObject
import io.ably.pubsub.liveobjects.path.types.DefaultLiveCounterPathObject
import io.ably.pubsub.liveobjects.path.types.DefaultLiveMapPathObject
import io.ably.pubsub.liveobjects.path.types.DefaultNumberPathObject
import io.ably.pubsub.liveobjects.path.types.DefaultStringPathObject
import io.ably.pubsub.liveobjects.path.types.JsonArrayPathObject
import io.ably.pubsub.liveobjects.path.types.JsonObjectPathObject
import io.ably.pubsub.liveobjects.path.types.LiveCounterPathObject
import io.ably.pubsub.liveobjects.path.types.LiveMapPathObject
import io.ably.pubsub.liveobjects.path.types.NumberPathObject
import io.ably.pubsub.liveobjects.path.types.StringPathObject
import io.ably.pubsub.liveobjects.value.ResolvedValue
import io.ably.pubsub.liveobjects.value.livemap.InternalLiveMap
import io.ably.pubsub.liveobjects.message.toCompactJsonElement
import io.ably.pubsub.liveobjects.value.valueType

/**
 * Default implementation of [PathObject], the untyped node in the path-addressed view of
 * the LiveObjects graph.
 *
 * The `as*` casts return a typed view of the same position without resolving it; operations
 * that need a value re-resolve the stored path against the live objects graph on every call
 * via [resolveValueAtCurrentPath], so a PathObject never holds a stale reference.
 *
 * Spec: RTPO1, RTPO2, RTTS3
 */
internal open class DefaultPathObject(
  internal val channelObject: DefaultRealtimeObject,
  internal val path: String
) : PathObject {

  override fun path(): String = path

  override fun getType(): ValueType? {
    channelObject.throwIfInvalidAccessApiConfiguration()
    return resolveValueAtCurrentPath()?.valueType()
  }

  override fun instance(): Instance? {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTPO8a
    val resolvedValue = resolveValueAtCurrentPath() ?: return null // RTPO8e - unresolved path -> no instance
    // RTPO8c/RTPO8f - wrap the resolved value (live object or primitive) in its typed Instance
    return resolvedValue.toInstance(channelObject)
  }

  override fun compactJson(): JsonElement? {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTPO14a / RTO25
    return when (val resolved = resolveValueAtCurrentPath()) {
      null -> null // RTPO3c1 - unresolved path
      is ResolvedValue.MapRef -> resolved.map.compactJson() // RTPO13c
      is ResolvedValue.CounterRef -> JsonPrimitive(resolved.counter.value()) // RTPO13d
      is ResolvedValue.Leaf -> resolved.data.toCompactJsonElement() // RTPO13e, RTPO14b1
    }
  }

  override fun exists(): Boolean {
    channelObject.throwIfInvalidAccessApiConfiguration()
    return resolveValueAtCurrentPath() != null
  }

  override fun asLiveMap(): LiveMapPathObject = DefaultLiveMapPathObject(channelObject, path)

  override fun asLiveCounter(): LiveCounterPathObject = DefaultLiveCounterPathObject(channelObject, path)

  override fun asNumber(): NumberPathObject = DefaultNumberPathObject(channelObject, path)

  override fun asString(): StringPathObject = DefaultStringPathObject(channelObject, path)

  override fun asBoolean(): BooleanPathObject = DefaultBooleanPathObject(channelObject, path)

  override fun asBinary(): BinaryPathObject = DefaultBinaryPathObject(channelObject, path)

  override fun asJsonObject(): JsonObjectPathObject = DefaultJsonObjectPathObject(channelObject, path)

  override fun asJsonArray(): JsonArrayPathObject = DefaultJsonArrayPathObject(channelObject, path)

  override fun subscribe(listener: PathObjectListener): Subscription = subscribe(listener, null)

  override fun subscribe(listener: PathObjectListener, options: PathObjectSubscriptionOptions?): Subscription {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTPO19b
    // depth validation happens in PathObjectSubscriptionOptions(int) - RTPO19c1a
    val segments = PathSegments.parseStored(path) // empty stored path = root = zero segments
    // RTPO19f - pure registration, no side effects on channel state (RTPO19g)
    return channelObject.pathObjectSubscriptionRegister.subscribe(segments, listener, options?.depth)
  }

  /**
   * RTPO3 path resolution against the local objects graph, evaluated freshly at call time.
   * Returns null on resolution failure; read callers degrade per RTPO3c1, write callers
   * throw 92005 per RTPO3c2.
   */
  protected fun resolveValueAtCurrentPath(): ResolvedValue? {
    // root is always present and always an InternalLiveMap (RTO3b); the pool never replaces
    // the root instance (RTO4b2, RTO5c2a), so looking it up per call is equivalent to
    // holding the RTPO2b root reference
    val root = channelObject.objectsPool.get(ROOT_OBJECT_ID) as InternalLiveMap
    var current: ResolvedValue = ResolvedValue.MapRef(root)
    // parseStored: an empty stored path is the root itself - zero segments (RTPO3b)
    for (segment in PathSegments.parseStored(path)) {
      val map = (current as? ResolvedValue.MapRef)?.map ?: return null // RTPO3a1 - non-map mid-path
      current = map.get(segment) ?: return null // RTPO3a2 - via RTLM5
    }
    return current // RTPO3a3
  }
}
