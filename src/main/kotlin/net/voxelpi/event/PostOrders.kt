package net.voxelpi.event

/**
 * Default post orders.
 * A higher value means, that the subscriber will be called later in the subscriber chain.
 */
public object PostOrders {

    public const val EARLIEST: Int = -10000

    public const val EARLY: Int = -1000

    public const val NORMAL: Int = 0

    public const val LATE: Int = 1000

    public const val LATEST: Int = 10000
}
