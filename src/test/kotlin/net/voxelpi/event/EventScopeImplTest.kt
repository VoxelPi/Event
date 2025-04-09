package net.voxelpi.event

import net.voxelpi.event.annotation.Subscribe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
        assertEquals(1, scope.collectiveSubscriptions().size)
        assertEquals(1, scope.collectiveSubscriptionsForType<Any>().size)
        assertEquals(1, scope.collectiveSubscriptionsForType<Unit>().size)

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

        assertEquals(1, scope.collectiveSubscriptionsForType<TypeA>().size)
        assertEquals(1, scope.collectiveSubscriptionsForType<TypeB>().size)

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
        var handledNumber = false
        scope.on<List<Number>> { _ ->
            handledNumber = true
        }

        assertEquals(2, scope.collectiveSubscriptionsForType<List<Long>>().size)
        assertEquals(2, scope.collectiveSubscriptionsForType<List<Int>>().size)
        assertEquals(1, scope.collectiveSubscriptionsForType<List<Float>>().size)

        scope.post(emptyList<Long>())
        assertEquals(false, handledInt)
        assertEquals(true, handledLong)
        assertEquals(true, handledNumber)
    }

    @Test
    fun `test multiple subscriptions of same type`() {
        val scope = eventScope()

        var handledA = false
        var handledB = false
        scope.on<Any> { _ ->
            handledA = true
        }
        scope.on<Any> { _ ->
            handledB = true
        }

        assertEquals(2, scope.collectiveSubscriptionsForType<Any>().size)

        scope.post(Unit)
        assertEquals(true, handledA)
        assertEquals(true, handledB)
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

        assertEquals(3, scope.collectiveSubscriptionsForType<Any>().size)
        assertEquals(3, subScope.collectiveSubscriptionsForType<Any>().size)
        assertEquals(3, subSubScope.collectiveSubscriptionsForType<Any>().size)

        scope.post(Unit)
        assertEquals(true, handledMain)
        assertEquals(true, handledSub)
        assertEquals(true, handledSubSub)

        handledMain = false
        handledSub = false
        handledSubSub = false
        subScope.post("Test")
        assertEquals(true, handledMain)
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
        assertEquals(2, scope.collectiveSubscriptionsForType<String>().size)

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
    fun `test subscription collection`() {
        val parentScope = EventScopeImpl(null)
        val scope = parentScope.createSubScope()
        val childScope = scope.createSubScope()
        val siblingScope = parentScope.createSubScope()

        val parentScopeSub = parentScope.on<Any> { }
        val scopeSub = scope.on<Number> { }
        val childScopeSub = childScope.on<Int> { }
        val siblingScopeSub = siblingScope.on<String> { }

        // Check that the event types are correctly collected.
        assertEquals(setOf(typeOf<Any>(), typeOf<Number>(), typeOf<Int>(), typeOf<String>()), parentScope.subscribedEventTypes())
        assertEquals(setOf(typeOf<Any>(), typeOf<Number>(), typeOf<Int>()), scope.subscribedEventTypes())
        assertEquals(setOf(typeOf<Any>(), typeOf<Number>(), typeOf<Int>()), childScope.subscribedEventTypes())
        assertEquals(setOf(typeOf<Any>(), typeOf<String>()), siblingScope.subscribedEventTypes())

        assertEquals(
            setOf(typeOf<Any>()),
            parentScope.subscribedEventTypesForChildScope(),
        )
        assertEquals(
            setOf(typeOf<Any>(), typeOf<Number>(), typeOf<Int>(), typeOf<String>()),
            parentScope.subscribedEventTypesForParentScope(),
        )

        // Check that the event subscriptions are correctly collected.
        assertEquals(setOf<EventSubscription<*>>(scopeSub, childScopeSub), scope.subscriptionsForParentScope().toSet())
        assertEquals(setOf(parentScopeSub, scopeSub, childScopeSub), scope.subscriptionsForCurrentScope().toSet())
        assertEquals(setOf(parentScopeSub, scopeSub), scope.subscriptionsForSubScopes().toSet())
        assertEquals(setOf(parentScopeSub, siblingScopeSub), siblingScope.subscriptionsForCurrentScope().toSet())
    }

    @Test
    fun `diamond scope dependency`() {
        //   C
        //  / \
        // A   B
        //  \ /
        //   R
        val scopeR = eventScope()
        val scopeA = scopeR.createSubScope()
        val scopeB = scopeR.createSubScope()
        val scopeC = scopeA.createSubScope()
        scopeB.register(scopeC)

        var handledCCounter = 0
        scopeC.on<Any> { ++handledCCounter }

        scopeR.post(Unit)
        assertEquals(1, handledCCounter)

        scopeA.unregister(scopeC)
        handledCCounter = 0
        scopeR.post(Unit)
        assertEquals(1, handledCCounter)

        scopeB.unregister(scopeC)
        handledCCounter = 0
        scopeR.post(Unit)
        assertEquals(0, handledCCounter)
    }

    @Test
    fun `test post result`() {
        val scope = eventScope()

        scope.on<Any> { }
        assertEquals(true, scope.post(Unit).wasSuccessful())

        scope.on<Any> {
            throw Exception("Test")
        }
        assertEquals(false, scope.post(Unit).wasSuccessful())
        assertThrows<PostResult.Failure.CombinedException> { scope.post(Unit).throwOnFailure() }
    }

    @Test
    fun `test cancel subscription`() {
        val scope = eventScope()

        var counter = 0

        val subscription = scope.on<Any> { counter++ }

        scope.post(Unit)
        assertEquals(1, counter)

        subscription.cancel()
        scope.post(Unit)
        assertEquals(1, counter)
    }
}
