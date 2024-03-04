package net.voxelpi.event

import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * An event scope. Its purpose is to handle the event bus logic.
 * It allows to register event subscribers, sub scopes, and to post events.
 */
public sealed interface EventScope {

    /**
     * Post the given [event] to all subscribers of this event scope and its sub scopes.
     */
    public fun postEvent(event: Any, eventType: KType)

    /**
     * Registers an event subscribers for events of the given [type] with the given [priority] and [callback].
     */
    public fun <T : Any> handleEvent(type: KType, priority: Int = 0, callback: (T) -> Unit): EventSubscriber<T>

    /**
     * Unregisters the given [subscriber] from this scope.
     */
    public fun unregister(subscriber: EventSubscriber<*>)

    /**
     * Registers the given [scope] as a sub scope.
     * All events fired in this scope will also be fired in the sub scope.
     */
    public fun register(scope: EventScope)

    /**
     * Unregisters the given [scope] from the sub scopes.
     */
    public fun unregister(scope: EventScope)

    /**
     * Creates a new event scope and registers it is a sub scope.
     */
    public fun createSubScope(): EventScope

    /**
     * Adds all methods of the given [instance] that are annotated with the [net.voxelpi.event.annotation.Subscribe] annotation
     * as event subscribers to a new sub scope of this scope.
     *
     * Only methods that are annotated with the [net.voxelpi.event.annotation.Subscribe] annotation,
     * take exactly one parameter and return void.
     *
     * @return the created sub scope.
     */
    public fun registerAnnotated(instance: Any): EventScope

    /**
     * Unregisters the sub scope associated with the given [instance] from this event scope.
     * If this scope has no sub scope that is associated with the given [instance] nothing happens.
     */
    public fun unregisterAnnotated(instance: Any)

    /**
     * Returns the sub scope associated with the given [instance], or null if no such sub scope exists.
     */
    public fun annotatedSubScope(instance: Any): EventScope?

    /**
     * Returns all event types that have at least one subscriber registered in this scope or any of its sub scopes.
     */
    public fun subscribedEventTypes(): Set<KType>
}

/**
 * Creates a new event scope.
 */
public fun eventScope(): EventScope {
    return EventScopeImpl(null)
}

/**
 * Post the given [event] to all subscribers of this event scope and its sub scopes.
 */
public inline fun <reified T : Any> EventScope.post(event: T) {
    return postEvent(event, typeOf<T>())
}

/**
 * Registers an event subscriber for events of the given type [T] with the given [priority] and [callback].
 */
public inline fun <reified T : Any> EventScope.on(priority: Int = 0, noinline callback: (T) -> Unit): EventSubscriber<T> {
    return handleEvent(typeOf<T>(), priority, callback)
}
