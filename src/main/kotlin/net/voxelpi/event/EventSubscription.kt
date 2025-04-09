package net.voxelpi.event

import kotlin.reflect.KType

/**
 * An event subscription. Stores the callback and information on about what events should be processed and the post order.
 */
public sealed interface EventSubscription<T : Any> : AutoCloseable {

    /**
     * The type of the events the handler should listen to.
     */
    public val type: KType

    /**
     * The post order of the subscription.
     * A higher value means that the subscriber will be invoked later in the subscriber chain.
     */
    public val postOrder: Int

    /**
     * The callback of the event subscription.
     * Is called for all events whose type is either [type] or a subtype of it.
     */
    public val callback: (event: T) -> Unit

    /**
     * Cancel the event subscription.
     */
    public fun cancel()

    override fun close() {
        cancel()
    }
}
