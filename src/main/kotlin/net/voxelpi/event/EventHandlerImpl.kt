package net.voxelpi.event

import kotlin.reflect.KType

internal data class EventHandlerImpl<T : Any>(
    override val type: KType,
    override val priority: Int,
    override val callback: (event: T) -> Unit,
) : EventHandler<T>
