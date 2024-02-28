package net.voxelpi.event.annotation

@Target(AnnotationTarget.FUNCTION)
annotation class Subscribe(val priority: Int = 0)
