package net.voxelpi.event

/**
 * The result of a posted event.
 */
public sealed interface PostResult {

    /**
     * No exceptions occurred whilst posting the event.
     */
    public data object Success : PostResult {

        override fun wasSuccessful(): Boolean {
            return true
        }

        override fun throwOnFailure() {
            Result.success(Unit).getOrThrow()
        }
    }

    /**
     * Some subscribers threw events whilst posting the event.
     */
    public data class Failure(
        val exceptions: Map<EventSubscription<*>, Throwable>,
    ) : PostResult {

        override fun wasSuccessful(): Boolean {
            return false
        }

        override fun throwOnFailure() {
            throw CombinedException(this)
        }

        public data class CombinedException(
            val failure: Failure,
        ) : Exception("An exception occurred whilst posting an event") {

            public fun printAllStackTraces() {
                printStackTrace()
                for ((_, exception) in failure.exceptions) {
                    exception.printStackTrace()
                }
            }
        }
    }

    /**
     * Returns if posting the event was successful.
     */
    public fun wasSuccessful(): Boolean

    /**
     * Throws an exception if posting an event was not successful.
     */
    public fun throwOnFailure()
}
