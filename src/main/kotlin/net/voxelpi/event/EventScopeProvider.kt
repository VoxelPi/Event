package net.voxelpi.event

import kotlin.reflect.typeOf

public interface EventScopeProvider {

    public val eventScope: EventScope
}

/**
 * Registers an event subscription for events of the given type [T] with the given [postOrder] and [callback].
 */
public inline fun <reified T : Any> EventScopeProvider.on(postOrder: Int = 0, noinline callback: (T) -> Unit): EventSubscription<T> {
    return eventScope.handleEvent(typeOf<T>(), postOrder, callback)
}
