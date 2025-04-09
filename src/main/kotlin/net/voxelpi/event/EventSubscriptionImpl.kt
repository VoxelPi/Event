package net.voxelpi.event

import java.util.UUID
import kotlin.reflect.KType

internal data class EventSubscriptionImpl<T : Any>(
    val eventScope: EventScopeImpl,
    override val type: KType,
    override val postOrder: Int,
    val uniqueId: UUID = UUID.randomUUID(),
    override val callback: (event: T) -> Unit,
) : EventSubscription<T> {

    override fun cancel() {
        eventScope.unregisterSubscription(this)
    }
}
