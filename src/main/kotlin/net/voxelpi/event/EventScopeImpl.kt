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

    private val parentScopes: MutableList<EventScopeImpl> = mutableListOf()
    private val subScopes: MutableList<EventScopeImpl> = mutableListOf()
    private val subscribers: MutableList<EventSubscriberImpl<*>> = mutableListOf()

    /**
     * Returns all subscribers that should be used by this scope.
     */
    fun subscribersForCurrentScope(): List<EventSubscriberImpl<*>> {
        val subscribers = this.subscribers.toMutableList()
        for (parentScope in parentScopes) {
            subscribers.addAll(parentScope.subscribersForSubScopes())
        }
        for (childScope in subScopes) {
            subscribers.addAll(childScope.subscribersForParentScope())
        }
        return subscribers
    }

    /**
     * Returns all subscribes that should be used by the sub scopes.
     */
    fun subscribersForSubScopes(): List<EventSubscriberImpl<*>> {
        val subscribers = this.subscribers.toMutableList()
        for (parentScope in parentScopes) {
            subscribers.addAll(parentScope.subscribersForSubScopes())
        }
        return subscribers
    }

    /**
     * Returns all subscribers that should be used by the parent scopes.
     */
    fun subscribersForParentScope(): List<EventSubscriberImpl<*>> {
        val subscribers = this.subscribers.toMutableList()
        for (childScope in subScopes) {
            subscribers.addAll(childScope.subscribersForParentScope())
        }
        return subscribers
    }

    override fun subscribedEventTypes(): Set<KType> {
        val subscribers = subscribersForCurrentScope()
        val types = subscribers.map(EventSubscriberImpl<*>::type).toSet()
        return types
    }

    override fun postEvent(event: Any, eventType: KType) {
        // Collect all relevant subscribers. // TODO: This should not happen every time an event is posted.
        val subscribers = mutableListOf<EventSubscriberImpl<*>>()
        subscribers.addAll(this.subscribers)
        for (subScope in subScopes) {
            subscribers.addAll(subScope.subscribersForParentScope())
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
        require(scope is EventScopeImpl)
        this.subScopes.add(scope)
        scope.parentScopes.add(this)
    }

    override fun unregister(scope: EventScope) {
        require(scope is EventScopeImpl)
        this.subScopes.remove(scope)
        scope.parentScopes.remove(this)
    }

    override fun createSubScope(): EventScopeImpl {
        val scope = EventScopeImpl(null)
        register(scope)
        return scope
    }
}
