package net.voxelpi.event

import kotlin.reflect.KType

internal data class EventSubscriberImpl<T : Any>(
    override val type: KType,
    override val postOrder: Int,
    override val callback: (event: T) -> Unit,
) : EventSubscriber<T>
