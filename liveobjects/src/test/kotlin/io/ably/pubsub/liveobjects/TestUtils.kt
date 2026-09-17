package io.ably.pubsub.liveobjects

import java.lang.reflect.Field
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.lang.reflect.Method

suspend fun assertWaiter(timeoutInMs: Long = 10_000, block: suspend () -> Boolean) {
  withContext(Dispatchers.Default) {
    withTimeout(timeoutInMs) {
      do {
        val success = block()
        delay(100)
      } while (!success)
    }
  }
}

fun Any.setPrivateField(name: String, value: Any?) {
  val valueField = javaClass.findField(name)
  valueField.isAccessible = true
  valueField.set(this, value)
}

fun <T>Any.getPrivateField(name: String): T {
  val valueField = javaClass.findField(name)
  valueField.isAccessible = true
  @Suppress("UNCHECKED_CAST")
  return valueField.get(this) as T
}

private fun Class<*>.findField(name: String): Field {
  var result = kotlin.runCatching { getDeclaredField(name) }
  var currentClass = this
  while (result.isFailure && currentClass.superclass != null) // stop when we got field or reached top of class hierarchy
  {
    currentClass = currentClass.superclass!!
    result = kotlin.runCatching { currentClass.getDeclaredField(name) }
  }
  if (result.isFailure) {
    throw result.exceptionOrNull() as Exception
  }
  return result.getOrNull() as Field
}

suspend fun <T> Any.invokePrivateSuspendMethod(methodName: String, vararg args: Any?): T = suspendCancellableCoroutine { cont ->
  val suspendMethod = javaClass.declaredMethods.find { it.name == methodName }
    ?: error("Method '$methodName' not found")
  suspendMethod.isAccessible = true
  suspendMethod.invoke(this, *args, cont)
}

fun <T> Any.invokePrivateMethod(methodName: String, vararg args: Any?): T {
  val method = javaClass.declaredMethods.find { it.name == methodName } ?: error("Method '$methodName' not found")
  method.isAccessible = true
  @Suppress("UNCHECKED_CAST")
  return method.invoke(this, *args) as T
}

fun Class<*>.findMethod(methodName: String): Method {
  return methods.find { it.name.contains(methodName) } ?: error("Method '$methodName' not found")
}
