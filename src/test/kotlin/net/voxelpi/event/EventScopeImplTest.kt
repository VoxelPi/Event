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
    fun `test sub scopes`() {
        val scope = eventScope()
        val subScope = scope.createSubScope()
        val subSubScope = subScope.createSubScope()

        var handledMain = false
        scope.on<Any> { _ ->
            handledMain = true
        }
        var handledSub = false
        subScope.on<Any> { _ ->
            handledSub = true
        }
        var handledSubSub = false
        subSubScope.on<Any> { _ ->
            handledSubSub = true
        }

        scope.post(Unit)
        assertEquals(true, handledMain)
        assertEquals(true, handledSub)
        assertEquals(true, handledSubSub)

        handledMain = false
        handledSub = false
        handledSubSub = false
        subScope.post("Test")
        assertEquals(false, handledMain)
        assertEquals(true, handledSub)
        assertEquals(true, handledSubSub)
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

    @Test
    fun `test subscriber collection`() {
        val parentScope = EventScopeImpl(null)
        val scope = parentScope.createSubScope()
        val childScope = scope.createSubScope()
        val siblingScope = parentScope.createSubScope()

        val parentScopeSubscriber = parentScope.on<Any> { }
        val scopeSubscriber = scope.on<Number> { }
        val childScopeSubscriber = childScope.on<Int> { }
        val siblingScopeSubscriber = siblingScope.on<String> {  }

        // Check that the event types are correctly collected.
        assertEquals(setOf(typeOf<Any>(), typeOf<Number>(), typeOf<Int>(), typeOf<String>()), parentScope.subscribedEventTypes())
        assertEquals(setOf(typeOf<Any>(), typeOf<Number>(), typeOf<Int>()), scope.subscribedEventTypes())
        assertEquals(setOf(typeOf<Any>(), typeOf<Number>(), typeOf<Int>()), childScope.subscribedEventTypes())
        assertEquals(setOf(typeOf<Any>(), typeOf<String>()), siblingScope.subscribedEventTypes())

        // Check that the event subscribers are correctly collected.
        assertEquals(setOf<EventSubscriber<*>>(scopeSubscriber, childScopeSubscriber), scope.subscribersForParentScope().toSet())
        assertEquals(setOf(parentScopeSubscriber, scopeSubscriber, childScopeSubscriber), scope.subscribersForCurrentScope().toSet())
        assertEquals(setOf(parentScopeSubscriber, scopeSubscriber), scope.subscribersForSubScopes().toSet())
        assertEquals(setOf(parentScopeSubscriber, siblingScopeSubscriber), siblingScope.subscribersForCurrentScope().toSet())
    }
}
