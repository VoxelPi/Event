package net.voxelpi.event

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.measureTime

class EventScopeImplBenchmark {

    @Test
    fun `benchmark the post method`() {
        val numberOfSubscribers = 1_000
        val numberOfEvents = 1_000

        val scope = EventScopeImpl()
        val time1 = measureTime {
            for (i in 1..numberOfSubscribers) {
                scope.on<Any> { }
                scope.on<String> { }
                scope.on<Int> { }
                scope.on<Double> { }
            }
        }
        assertEquals(numberOfSubscribers * 4, scope.subscribersForCurrentScope().size)
        val time2 = measureTime {
            scope.on<Any> { }
        }
        println(
            "Creating ${scope.subscribersForCurrentScope().size} subscribers took $time1 (${time1 / numberOfSubscribers} " +
                "per subscriber on average, $time2 at the end)"
        )

        val time = measureTime {
            for (i in 1..numberOfEvents) {
                scope.post("test")
            }
        }
        println("Posting $numberOfEvents events took $time (${time / numberOfEvents} per event on average)")
    }
}
