package net.voxelpi.event

import kotlin.reflect.KType

/**
 * An event subscriber instance. Stores the callback and information on about what events should be processed and the post order.
 */
public sealed interface EventSubscriber<T : Any> {

    /**
     * The type of the events the handler should listen to.
     */
    public val type: KType

    /**
     * The post order of the subscriber.
     * A higher value means that the subscriber will be invoked later in the subscriber chain.
     */
    public val postOrder: Int

    /**
     * The callback of the event subscriber.
     * Is called for all events whose type is either [type] or a subtype of it.
     */
    public val callback: (event: T) -> Unit
}
