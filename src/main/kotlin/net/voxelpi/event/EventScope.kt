package net.voxelpi.event

import kotlin.reflect.KType
import kotlin.reflect.typeOf

sealed interface EventScope {

    /**
     * Post the given [event] to all handlers of this event scope and its sub scopes.
     */
    fun postEvent(event: Any, eventType: KType)

    /**
     * Registers an event handler for events of the given [type] with the given [priority] and [callback].
     */
    fun <T : Any> handleEvent(type: KType, priority: Int = 0, callback: (T) -> Unit): EventHandler<T>

    /**
     * Registers all annotated event handlers in a sub scope and returns the generated event scope.
     */
    fun registerSubscriptions(subscriptions: Any): EventScope

    /**
     * Unregisters the given [handler] from this scope.
     */
    fun unregister(handler: EventHandler<*>)

    /**
     * Registers the given [scope] as a sub scope.
     * All events fired in this scope will also be fired in the sub scope.
     */
    fun register(scope: EventScope)

    /**
     * Unregisters the given [scope] from the sub scopes.
     */
    fun unregister(scope: EventScope)

    /**
     * Creates a new event scope and registers it is a sub scope.
     */
    fun subScope(): EventScope
}

/**
 * Creates a new event scope.
 */
fun eventScope(): EventScope {
    return EventScopeImpl()
}

/**
 * Post the given [event] to all handlers of this event scope and its sub scopes.
 */
inline fun <reified T : Any> EventScope.post(event: T) {
    return postEvent(event, typeOf<T>())
}

/**
 * Registers an event handler for events of the given type [T] with the given [priority] and [callback].
 */
inline fun <reified T : Any> EventScope.on(priority: Int = 0, noinline callback: (T) -> Unit): EventHandler<T> {
    return handleEvent(typeOf<T>(), priority, callback)
}
