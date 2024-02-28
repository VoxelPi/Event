package net.voxelpi.event

/**
 * An event that can be cancelled.
 */
public interface Cancellable {

    /**
     * If the event should be cancelled.
     */
    public var cancelled: Boolean
}
