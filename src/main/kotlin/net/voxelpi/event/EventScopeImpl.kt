package net.voxelpi.event

import net.voxelpi.event.annotation.Subscribe
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.isSupertypeOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.typeOf

internal class EventScopeImpl(
    private val annotatedInstance: Any?,
) : EventScope {

    private val parentScopes: MutableList<EventScopeImpl> = mutableListOf()
    private val subScopes: MutableList<EventScopeImpl> = mutableListOf()
    private val subscribers: MutableList<EventSubscriberImpl<*>> = mutableListOf()

    private val subscriberCache: MutableMap<KType, List<EventSubscriberImpl<*>>> = mutableMapOf()

    init {
        buildCache()
    }

    private fun buildCache() {
        val subscribers = subscribersForCurrentScope()
        subscribers.sortedBy(EventSubscriberImpl<*>::postOrder)
        val types = subscribers.map(EventSubscriberImpl<*>::type)

        subscriberCache.clear()
        subscriberCache.putAll(
            types.associateWith { type ->
                subscribers.filter { subscriber ->
                    type.isSubtypeOf(subscriber.type)
                }
            }
        )
    }

    private fun invalidateCache() {
        buildCache()
        for (parentScope in parentScopes) {
            parentScope.invalidateCacheByChild()
        }
        for (childScope in subScopes) {
            childScope.invalidateCacheByParent()
        }
    }

    private fun invalidateCacheByParent() {
        buildCache()
        for (childScope in subScopes) {
            childScope.invalidateCacheByParent()
        }
    }

    private fun invalidateCacheByChild() {
        buildCache()
        for (parentScope in parentScopes) {
            parentScope.invalidateCacheByChild()
        }
    }

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

    fun eventTypeSubscribers(eventType: KType): List<EventSubscriberImpl<*>> {
        val type = subscriberCache.keys.sortedWith { type1, type2 ->
            return@sortedWith when {
                type1.isSubtypeOf(type2) && type1.isSupertypeOf(type2) -> 0
                type1.isSubtypeOf(type2) -> 1
                type1.isSupertypeOf(type2) -> -1
                else -> 0
            }
        }.lastOrNull { eventType.isSubtypeOf(it) } ?: return emptyList()
        return subscriberCache[type] ?: emptyList()
    }

    override fun postEvent(event: Any, eventType: KType) {
        // Get relevant subscribers from the cache.
        val eventSubscribers = eventTypeSubscribers(eventType)

        // Post event to subscribers.
        for (subscriber in eventSubscribers) {
            @Suppress("UNCHECKED_CAST")
            (subscriber as EventSubscriberImpl<Any>).callback.invoke(event)
        }
    }

    override fun <T : Any> handleEvent(type: KType, priority: Int, callback: (T) -> Unit): EventSubscriberImpl<T> {
        val subscriber = EventSubscriberImpl(type, priority, callback)
        register(subscriber)
        return subscriber
    }

    override fun registerAnnotated(instance: Any): EventScope {
        val typeClass = instance::class
        val scope = EventScopeImpl(instance)

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
            scope.register(subscriber)
        }

        // Register scope.
        register(scope)
        return scope
    }

    override fun unregisterAnnotated(instance: Any) {
        val scope = annotatedSubScope(instance) ?: return
        unregister(scope)
    }

    override fun annotatedSubScope(instance: Any): EventScope? {
        return subScopes.find { it.annotatedInstance == instance }
    }

    fun register(subscriber: EventSubscriberImpl<*>) {
        subscribers.add(subscriber)
        invalidateCache()
    }

    override fun unregister(subscriber: EventSubscriber<*>) {
        subscribers.remove(subscriber)
        invalidateCache()
    }

    override fun register(scope: EventScope) {
        require(scope is EventScopeImpl)
        this.subScopes.add(scope)
        scope.parentScopes.add(this)

        // Update cache.
        invalidateCache()
    }

    override fun unregister(scope: EventScope) {
        require(scope is EventScopeImpl)
        this.subScopes.remove(scope)
        scope.parentScopes.remove(this)

        // Update cache.
        invalidateCache()
        scope.invalidateCacheByParent() // Also update cache of now disconnected sub scope.
    }

    override fun createSubScope(): EventScopeImpl {
        val scope = EventScopeImpl(null)
        register(scope)
        return scope
    }
}
