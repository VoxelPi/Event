package net.voxelpi.event.annotation

/**
 * Annotates a function to be used as an event subscriber.
 * The function must have exactly one parameter that is used as the event type and must return [Unit].
 */
@Target(AnnotationTarget.FUNCTION)
public annotation class Subscribe(val postOrder: Int = 0)
