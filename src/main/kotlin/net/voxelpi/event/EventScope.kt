package net.voxelpi.event

import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * An event scope. Its purpose is to handle the event bus logic.
 * It allows to register event handlers, sub scopes, and to post events.
 */
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
    fun createSubScope(): EventScope

    /**
     * Adds all methods of the given [instance] that are annotated with the [net.voxelpi.event.annotation.Subscribe] annotation
     * as event handlers to a new sub scope of this scope.
     *
     * Only methods that are annotated with the [net.voxelpi.event.annotation.Subscribe] annotation,
     * take exactly one parameter and return void.
     *
     * @return the created sub scope.
     */
    fun registerAnnotated(instance: Any): EventScope

    /**
     * Unregisters the sub scope associated with the given [instance] from this event scope.
     * If this scope has no sub scope that is associated with the given [instance] nothing happens.
     */
    fun unregisterAnnotated(instance: Any)

    /**
     * Returns the sub scope associated with the given [instance], or null if no such sub scope exists.
     */
    fun annotatedSubScope(instance: Any): EventScope?
}

/**
 * Creates a new event scope.
 */
fun eventScope(): EventScope {
    return EventScopeImpl(null)
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
