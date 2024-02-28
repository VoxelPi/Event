package net.voxelpi.event

/**
 * Default event priority levels.
 */
public object EventPriorities {

    public const val EARLIEST: Int = 1000

    public const val EARLY: Int = 100

    public const val NORMAL: Int = 0

    public const val LATE: Int = -100

    public const val LATEST: Int = -1000
}
