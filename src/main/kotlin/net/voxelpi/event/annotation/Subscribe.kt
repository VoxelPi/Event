package net.voxelpi.event.annotation

@Target(AnnotationTarget.FUNCTION)
public annotation class Subscribe(val priority: Int = 0)
