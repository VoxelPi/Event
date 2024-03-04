package net.voxelpi.event

import net.voxelpi.event.annotation.Subscribe
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.typeOf

internal class EventScopeImpl(
    private val annotatedInstance: Any?,
) : EventScope {

    private val subScopes: MutableList<EventScopeImpl> = mutableListOf()
    private val subscribers: MutableList<EventSubscriberImpl<*>> = mutableListOf()

    override fun subscribedEventTypes(): Set<KType> {
        val types = mutableSetOf<KType>()
        types.addAll(subscribers.map(EventSubscriberImpl<*>::type))
        for (subScope in subScopes) {
            types.addAll(subScope.subscribedEventTypes())
        }
        return types
    }

    override fun postEvent(event: Any, eventType: KType) {
        // Collect all relevant subscribers. // TODO: This should not happen every time an event is posted.
        val subscribers = mutableListOf<EventSubscriberImpl<*>>()
        subscribers.addAll(this.subscribers)
        for (subScope in subScopes) {
            subscribers.addAll(subScope.subscribers)
        }

        // Filter subscribers
        val applicableSubscribers = subscribers
            .filter { handler ->
                eventType.isSubtypeOf(handler.type)
            }
            .sortedByDescending { it.postOrder }

        // Post event to subscribers.
        for (subscriber in applicableSubscribers) {
            @Suppress("UNCHECKED_CAST")
            (subscriber as EventSubscriberImpl<Any>).callback.invoke(event)
        }
    }

    override fun <T : Any> handleEvent(type: KType, priority: Int, callback: (T) -> Unit): EventSubscriberImpl<T> {
        val subscriber = EventSubscriberImpl(type, priority, callback)
        subscribers.add(subscriber)
        return subscriber
    }

    override fun registerAnnotated(instance: Any): EventScope {
        val typeClass = instance::class
        val scope = EventScopeImpl(instance)
        register(scope)

        // Get all functions that are annotated by Subscribe, take one parameter (plus implicit this parameter) and return Unit.
        val functions = typeClass.memberFunctions.filter { function ->
            function.findAnnotation<Subscribe>() != null && function.parameters.size == 2 && function.returnType == typeOf<Unit>()
        }

        // Generate subscribers
        for (function in functions) {
            val subscription = function.findAnnotation<Subscribe>()!!
            val priority = subscription.priority
            val type = function.parameters[1].type
            val subscriber = EventSubscriberImpl<Any>(type, priority) { event ->
                function.call(instance, event)
            }
            scope.subscribers.add(subscriber)
        }

        return scope
    }

    override fun unregisterAnnotated(instance: Any) {
        val scope = annotatedSubScope(instance) ?: return
        unregister(scope)
    }

    override fun annotatedSubScope(instance: Any): EventScope? {
        return subScopes.find { it.annotatedInstance == instance }
    }

    override fun unregister(subscriber: EventSubscriber<*>) {
        subscribers.remove(subscriber)
    }

    override fun register(scope: EventScope) {
        subScopes.add(scope as EventScopeImpl)
    }

    override fun unregister(scope: EventScope) {
        subScopes.remove(scope as EventScopeImpl)
    }

    override fun createSubScope(): EventScopeImpl {
        val scope = EventScopeImpl(null)
        register(scope)
        return scope
    }
}
