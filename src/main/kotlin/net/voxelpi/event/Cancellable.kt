package net.voxelpi.event

/**
 * An event that can be cancelled.
 */
interface Cancellable {

    /**
     * If the event should be cancelled.
     */
    var cancelled: Boolean
}
