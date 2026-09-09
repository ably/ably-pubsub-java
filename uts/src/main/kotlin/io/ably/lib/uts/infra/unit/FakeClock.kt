package io.ably.lib.uts.infra.unit

import io.ably.lib.util.Clock
import io.ably.lib.util.AblyTimer
import io.ably.lib.util.TimerInstance
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/**
 * Virtual clock for deterministic time control in unit tests.
 *
 * Install via `enableFakeTimers(fakeClock)` inside a `TestRealtimeClient` or `TestRestClient` block.
 * Time only advances when [advance] is called; timer callbacks fire synchronously within that call.
 */
class FakeClock(initialTimeMs: Long = 0L) : Clock {
    @Volatile private var time = initialTimeMs
    // SDK transport/channel threads call newTimer/schedule/cancel concurrently with the test thread's
    // advance(), so the timer registry and each timer's task list must be thread-safe (a plain
    // HashMap/ArrayList here races into ConcurrentModificationException / lost tasks on a loaded runner).
    private val timers = ConcurrentHashMap<String, FakeAblyTimer>()
    private val waiters = mutableListOf<Waiter>()

    override fun currentTimeMillis() = time

    override fun nanoTime() = time * 1_000_000L

    override fun newTimer(name: String): AblyTimer {
        val t = FakeAblyTimer(name)
        timers[name] = t
        return t
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    override fun waitOn(target: Any, timeout: Long) {
        synchronized(waiters) {
            waiters.add(Waiter(target as Object, time + timeout))
        }
        (target as Object).wait(timeout)
    }

    /** Advance virtual time by [ms] milliseconds, firing any timers that become due. */
    fun advance(ms: Long) {
        time += ms
        // Run to quiescence (the spec run to quiescence Guarantee): re-scan all timers —
        // snapshotting each round so a timer registered mid-advance is picked up — until a full pass
        // fires nothing, so cascades due within the interval (a zero-delay reschedule, or a timer
        // created by fired work) also run. The map{}.any{} form is deliberately non-short-circuiting:
        // every timer gets a fireDue pass each round before we decide whether to loop again.
        do {
            val firedAny = timers.values.toList().map { it.fireDue(time) }.any { it }
        } while (firedAny)
        val due = synchronized(waiters) {
            waiters.filter { it.fireAt <= time }.also {
                waiters.removeIf { it.fireAt <= time }
            }
        }
        // notifyAll() requires holding the target's monitor.
        due.forEach { waiter ->
            synchronized(waiter.target) {
                waiter.target.notifyAll()
            }
        }
    }

    /** Advance virtual time by [time], firing any timers that become due. */
    fun advance(time: Duration) = advance(time.inWholeMilliseconds)

    /** Number of tasks currently scheduled on the named timer — useful for asserting retry state. */
    fun pendingTaskCount(timerName: String) = timers[timerName]?.pendingCount ?: 0

    inner class FakeAblyTimer(val name: String) : AblyTimer {
        // Guarded by `synchronized(pending)`: schedule/cancel run on SDK threads while fireDue runs on
        // the advancing test thread.
        private val pending = mutableListOf<Scheduled>()
        val pendingCount get() = synchronized(pending) { pending.size }

        override fun schedule(task: TimerTask, delayMs: Long): TimerInstance {
            val s = Scheduled(task, time + delayMs)
            synchronized(pending) {
                pending += s
                pending.sortBy { it.fireAt }
            }
            return TimerInstance {
                task.cancel()
                synchronized(pending) { pending -= s }
            }
        }

        override fun cancel() {
            synchronized(pending) {
                pending.forEach { it.task.cancel() }
                pending.clear()
            }
        }

        /** Fires all tasks now due; returns whether it fired anything (drives advance's quiescence loop). */
        fun fireDue(now: Long): Boolean {
            // Select + remove under the lock, then run tasks OUTSIDE it: a task may re-enter
            // schedule/cancel (e.g. the activity timer rescheduling itself), which would otherwise
            // deadlock or race.
            val due = synchronized(pending) {
                val d = pending.filter { it.fireAt <= now }
                pending -= d.toSet()
                d
            }
            due.forEach { it.task.run() }
            return due.isNotEmpty()
        }
    }

    class Scheduled(val task: TimerTask, val fireAt: Long)

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    class Waiter(val target: Object, val fireAt: Long)
}
