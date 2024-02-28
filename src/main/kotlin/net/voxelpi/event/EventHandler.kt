package net.voxelpi.event

import kotlin.reflect.KType

/**
 * An event handler instance. Stores the callback and information on about what event should be processed and its priority.
 */
sealed interface EventHandler<T : Any> {

    /**
     * The type of the events the handler should listen to.
     */
    val type: KType

    /**
     * The priority of the handler
     */
    val priority: Int

    /**
     * The callback of the event handler
     */
    val callback: (event: T) -> Unit
}
