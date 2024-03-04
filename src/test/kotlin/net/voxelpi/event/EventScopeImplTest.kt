package net.voxelpi.event

import net.voxelpi.event.annotation.Subscribe
import org.junit.jupiter.api.Test
import kotlin.reflect.typeOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EventScopeImplTest {

    @Test
    fun `test that events are handled`() {
        val scope = eventScope()

        var handled = false
        scope.on<Any> { _ ->
            handled = true
        }

        scope.post(Unit)
        assertEquals(true, handled)
    }

    @Test
    fun `test that the value of an event is correct`() {
        val scope = eventScope()

        var counter = 0
        scope.on<Int> { value ->
            counter += value
        }

        val n = 10
        repeat(n) { i ->
            scope.post(i)
        }

        assertEquals(n * (n - 1) / 2, counter)
    }

    @Test
    fun `test invalid types`() {
        class TypeA

        class TypeB

        val scope = eventScope()

        var handled = false
        scope.on<TypeA> { _ ->
            handled = true
        }

        scope.post(TypeB())
        assertEquals(false, handled)
    }

    @Test
    fun `test subtypes`() {
        open class TypeA

        class TypeB : TypeA()

        val scope = eventScope()

        var handled = false
        scope.on<TypeA> { _ ->
            handled = true
        }

        scope.post(TypeB())
        assertEquals(true, handled)
    }

    @Test
    fun `test generics`() {
        val scope = eventScope()

        var handledInt = false
        scope.on<List<Int>> { _ ->
            handledInt = true
        }
        var handledLong = false
        scope.on<List<Long>> { _ ->
            handledLong = true
        }

        scope.post(emptyList<Long>())
        assertEquals(false, handledInt)
        assertEquals(true, handledLong)
    }

    @Test
    fun `test child scopes`() {
        val scope = eventScope()
        val subScope = scope.createSubScope()

        var handledMain = false
        scope.on<Any> { _ ->
            handledMain = true
        }
        var handledSub = false
        subScope.on<Any> { _ ->
            handledSub = true
        }

        scope.post(Unit)
        assertEquals(true, handledMain)
        assertEquals(true, handledSub)

        handledMain = false
        handledSub = false
        subScope.post("Test")
        assertEquals(false, handledMain)
        assertEquals(true, handledSub)
    }

    @Test
    fun `test subscribed to`() {
        val scope = eventScope()
        val subScope = scope.createSubScope()

        scope.on<Int> {  }
        scope.on<Float> {  }
        scope.on<Any> {  }
        subScope.on<String> {  }
        subScope.on<Float> {  }

        assertEquals(setOf(typeOf<Int>(), typeOf<Float>(), typeOf<Any>(), typeOf<String>()), scope.subscribedEventTypes())
        assertEquals(setOf(typeOf<Float>(), typeOf<String>()), subScope.subscribedEventTypes())
    }

    @Test
    fun `test annotations`() {
        val scope = eventScope()

        class Test {
            var counterA = 0
            var counterB = 0

            @Subscribe
            @Suppress("UNUSED_PARAMETER")
            fun handleA(event: Any) {
                counterA += 1
            }

            @Subscribe
            @Suppress("UNUSED_PARAMETER")
            fun handleB(event: String) {
                counterB += 1
            }
        }

        val test = Test()
        val subScope = scope.registerAnnotated(test)
        assertNotNull(subScope)

        val scope2 = scope.annotatedSubScope(test)
        assertEquals(subScope, scope2)

        scope.post(Unit)
        assertEquals(1, test.counterA)
        assertEquals(0, test.counterB)

        scope.post("Test")
        assertEquals(2, test.counterA)
        assertEquals(1, test.counterB)

        // Check unregister
        scope.unregisterAnnotated(test)
        scope.post("Test")
        assertEquals(2, test.counterA)
        assertEquals(1, test.counterB)
    }
}
