package net.voxelpi.event

import kotlin.reflect.KType

/**
 * An event handler instance. Stores the callback and information on about what event should be processed and its priority.
 */
public sealed interface EventHandler<T : Any> {

    /**
     * The type of the events the handler should listen to.
     */
    public val type: KType

    /**
     * The priority of the handler
     */
    public val priority: Int

    /**
     * The callback of the event handler
     */
    public val callback: (event: T) -> Unit
}
