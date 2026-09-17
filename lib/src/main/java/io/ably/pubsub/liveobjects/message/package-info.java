/**
 * User-facing object message metadata, delivered to subscription listeners so
 * that user code can inspect the operation that triggered an object change.
 *
 * <p>{@link io.ably.pubsub.liveobjects.message.ObjectMessage} is the single entry point
 * of this package; every other type is reached by walking its properties:
 *
 * <pre>{@code
 * ObjectMessage                          (delivered in subscription events)
 * └── getOperation()  → ObjectOperation
 *     ├── getAction()        → ObjectOperationAction (enum)
 *     ├── getMapCreate()     → MapCreate
 *     │   ├── getSemantics() → ObjectsMapSemantics (enum)
 *     │   └── getEntries()   → Map<String, ObjectsMapEntry>
 *     │                          └── getData() → ObjectData
 *     ├── getMapSet()        → MapSet ── getValue() → ObjectData
 *     ├── getMapRemove()     → MapRemove
 *     ├── getCounterCreate() → CounterCreate
 *     ├── getCounterInc()    → CounterInc
 *     ├── getObjectDelete()  → ObjectDelete (empty)
 *     └── getMapClear()      → MapClear (empty)
 * }</pre>
 *
 * <p>Spec: PAOM1-PAOM3, PAOOP1-PAOOP3
 */
package io.ably.pubsub.liveobjects.message;
