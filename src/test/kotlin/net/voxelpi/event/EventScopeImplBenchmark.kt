package net.voxelpi.event

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.measureTime

class EventScopeImplBenchmark {

    @Test
    fun `benchmark the post method`() {
        val numberOfSubscriptions = 1_000
        val numberOfEvents = 1_000

        val scope = EventScopeImpl()
        val time1 = measureTime {
            for (i in 1..numberOfSubscriptions) {
                scope.on<Any> { }
                scope.on<String> { }
                scope.on<Int> { }
                scope.on<Double> { }
            }
        }
        assertEquals(numberOfSubscriptions * 4, scope.subscriptionsForCurrentScope().size)
        val time2 = measureTime {
            scope.on<Any> { }
        }
        println(
            "Creating ${scope.subscriptionsForCurrentScope().size} subscriptions took $time1 (${time1 / numberOfSubscriptions} " +
                "per subscription on average, $time2 at the end)"
        )

        val time = measureTime {
            for (i in 1..numberOfEvents) {
                scope.post("test")
            }
        }
        println("Posting $numberOfEvents events took $time (${time / numberOfEvents} per event on average)")
    }
}
