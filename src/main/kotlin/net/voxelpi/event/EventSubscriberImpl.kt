package net.voxelpi.event

import java.util.UUID
import kotlin.reflect.KType

internal data class EventSubscriberImpl<T : Any>(
    override val type: KType,
    override val postOrder: Int,
    val uniqueId: UUID = UUID.randomUUID(),
    override val callback: (event: T) -> Unit,
) : EventSubscriber<T>
