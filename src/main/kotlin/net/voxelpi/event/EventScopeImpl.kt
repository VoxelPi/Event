package net.voxelpi.event

import net.voxelpi.event.annotation.Subscribe
import java.util.Comparator
import java.util.TreeSet
import java.util.UUID
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.typeOf

internal class EventScopeImpl(
    private val annotatedInstance: Any? = null,
) : EventScope {

    private val parentScopes: MutableList<EventScopeImpl> = mutableListOf()
    private val subScopes: MutableList<EventScopeImpl> = mutableListOf()
    private val subscribers: SubscriberCache = mutableMapOf()

    private val childSubscriberCache: SubscriberCache = mutableMapOf()
    private val parentSubscriberCache: SubscriberCache = mutableMapOf()
    private val eventTypeCache: SubscriberCache = mutableMapOf()

    /**
     * Returns all subscribers that should be used by this scope.
     */
    fun subscribersForCurrentScope(): List<EventSubscriberImpl<*>> {
        val subscribers = subscribers.values.flatten().toMutableList()
        subscribers.addAll(parentSubscriberCache.values.flatten())
        subscribers.addAll(childSubscriberCache.values.flatten())
        return subscribers
    }

    /**
     * Returns all subscribes that should be used by the sub scopes.
     */
    fun subscribersForSubScopes(): List<EventSubscriberImpl<*>> {
        val subscribers = subscribers.values.flatten().toMutableList()
        subscribers.addAll(parentSubscriberCache.values.flatten())
        return subscribers
    }

    /**
     * Returns all subscribers that should be used by the parent scopes.
     */
    fun subscribersForParentScope(): List<EventSubscriberImpl<*>> {
        val subscribers = subscribers.values.flatten().toMutableList()
        subscribers.addAll(childSubscriberCache.values.flatten())
        return subscribers
    }

    override fun subscribedEventTypes(): Set<KType> {
        val types = subscribers.keys.toMutableSet()
        types.addAll(childSubscriberCache.keys)
        types.addAll(parentSubscriberCache.keys)
        return types
    }

    fun subscribedEventTypesForParentScope(): Set<KType> {
        val types = subscribers.keys.toMutableSet()
        types.addAll(childSubscriberCache.keys)
        return types
    }

    fun subscribedEventTypesForChildScope(): Set<KType> {
        val types = subscribers.keys.toMutableSet()
        types.addAll(parentSubscriberCache.keys)
        return types
    }

    fun eventTypeSubscribers(eventType: KType): TreeSet<EventSubscriberImpl<*>> {
        if (eventType in eventTypeCache) {
            return eventTypeCache[eventType]!!
        }

        val subscribers = subscriberTreeSet()
        subscribers.addAll(this.subscribers.filterKeys { type -> eventType.isSubtypeOf(type) }.values.flatten())
        subscribers.addAll(parentSubscriberCache.filterKeys { type -> eventType.isSubtypeOf(type) }.values.flatten())
        subscribers.addAll(childSubscriberCache.filterKeys { type -> eventType.isSubtypeOf(type) }.values.flatten())
        eventTypeCache[eventType] = subscribers
        return subscribers
    }

    @Suppress("UNCHECKED_CAST")
    override fun postEvent(event: Any, eventType: KType): PostResult {
        // Get relevant subscribers from the cache.
        val eventSubscribers = eventTypeSubscribers(eventType)

        // Post event to subscribers.
        val exceptions = mutableMapOf<EventSubscriber<*>, Throwable>()
        for (subscriber in eventSubscribers) {
            try {
                (subscriber as EventSubscriberImpl<Any>).callback.invoke(event)
            } catch (exception: Exception) {
                exceptions[subscriber] = exception
            }
        }

        // Return result
        return if (exceptions.isEmpty()) {
            PostResult.Success
        } else {
            PostResult.Failure(exceptions)
        }
    }

    override fun <T : Any> handleEvent(type: KType, priority: Int, callback: (T) -> Unit): EventSubscriberImpl<T> {
        val subscriber = EventSubscriberImpl(type, priority, UUID.randomUUID(), callback)
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
        if (subscriber.type in subscribers) {
            subscribers[subscriber.type]!!.add(subscriber)
        } else {
            subscribers[subscriber.type] = sortedSetOf(EventSubscriberComparator, subscriber)
        }
        cacheRegisterSubscriber(subscriber)
    }

    override fun unregister(subscriber: EventSubscriber<*>) {
        require(subscriber is EventSubscriberImpl)

        // Unregister subscriber.
        subscribers[subscriber.type]?.remove(subscriber)
        if (subscribers[subscriber.type]?.isEmpty() == true) {
            subscribers.remove(subscriber.type)
        }

        cacheUnregisterSubscriber(subscriber)
    }

    override fun register(scope: EventScope) {
        require(scope is EventScopeImpl)
        this.subScopes.add(scope)
        scope.parentScopes.add(this)

        // Update cache.
        this.cacheRegisterChildScope(scope)
        scope.cacheRegisterParentScope(this)
    }

    override fun unregister(scope: EventScope) {
        require(scope is EventScopeImpl)
        this.subScopes.remove(scope)
        scope.parentScopes.remove(this)

        // Update cache.
        this.cacheUnregisterChildScope(scope)
        scope.cacheUnregisterParentScope(this)
    }

    override fun createSubScope(): EventScopeImpl {
        val scope = EventScopeImpl(null)
        register(scope)
        return scope
    }

    private fun cacheRegisterSubscriber(subscriber: EventSubscriberImpl<*>) {
        eventTypeCache.keys.removeAll { it.isSubtypeOf(subscriber.type) }

        for (childScope in subScopes) {
            childScope.cacheRegisterSubscriberByParent(subscriber)
        }
        for (parentScope in parentScopes) {
            parentScope.cacheRegisterSubscriberByChild(subscriber)
        }
    }

    private fun cacheRegisterSubscriberByParent(subscriber: EventSubscriberImpl<*>) {
        eventTypeCache.keys.removeAll { it.isSubtypeOf(subscriber.type) }

        if (subscriber.type in parentSubscriberCache) {
            parentSubscriberCache[subscriber.type]!!.add(subscriber)
        } else {
            parentSubscriberCache[subscriber.type] = sortedSetOf(EventSubscriberComparator, subscriber)
        }

        for (childScope in subScopes) {
            childScope.cacheRegisterSubscriberByParent(subscriber)
        }
    }

    private fun cacheRegisterSubscriberByChild(subscriber: EventSubscriberImpl<*>) {
        eventTypeCache.keys.removeAll { it.isSubtypeOf(subscriber.type) }

        if (subscriber.type in childSubscriberCache) {
            childSubscriberCache[subscriber.type]!!.add(subscriber)
        } else {
            childSubscriberCache[subscriber.type] = sortedSetOf(EventSubscriberComparator, subscriber)
        }

        for (parentScope in parentScopes) {
            parentScope.cacheRegisterSubscriberByChild(subscriber)
        }
    }

    private fun cacheUnregisterSubscriber(subscriber: EventSubscriberImpl<*>) {
        eventTypeCache.keys.removeAll { it.isSubtypeOf(subscriber.type) }

        for (childScope in subScopes) {
            childScope.cacheUnregisterSubscriberByParent(subscriber)
        }
        for (parentScope in parentScopes) {
            parentScope.cacheUnregisterSubscriberByChild(subscriber)
        }
    }

    private fun cacheUnregisterSubscriberByParent(subscriber: EventSubscriberImpl<*>) {
        eventTypeCache.keys.removeAll { it.isSubtypeOf(subscriber.type) }

        parentSubscriberCache[subscriber.type]?.remove(subscriber)
        if (parentSubscriberCache[subscriber.type]?.isEmpty() == true) {
            parentSubscriberCache.remove(subscriber.type)
        }

        for (childScope in subScopes) {
            childScope.cacheUnregisterSubscriberByParent(subscriber)
        }
    }

    private fun cacheUnregisterSubscriberByChild(subscriber: EventSubscriberImpl<*>) {
        eventTypeCache.keys.removeAll { it.isSubtypeOf(subscriber.type) }

        childSubscriberCache[subscriber.type]?.remove(subscriber)
        if (childSubscriberCache[subscriber.type]?.isEmpty() == true) {
            childSubscriberCache.remove(subscriber.type)
        }

        for (parentScope in parentScopes) {
            parentScope.cacheUnregisterSubscriberByChild(subscriber)
        }
    }

    private fun cacheRegisterParentScope(parentScope: EventScopeImpl) {
        val parentTypes = parentScope.subscribedEventTypesForChildScope()
        eventTypeCache.keys.removeAll { eventType -> parentTypes.any { eventType.isSubtypeOf(it) } }

        // Merge into parent subscriber cache.
        parentSubscriberCache.mergeAll(parentScope.subscribers)
        parentSubscriberCache.mergeAll(parentScope.parentSubscriberCache)

        // Forward cache update to all child scopes.
        for (childScope in subScopes) {
            childScope.cacheRegisterParentScope(parentScope)
        }
    }

    private fun cacheRegisterChildScope(childScope: EventScopeImpl) {
        val childTypes = childScope.subscribedEventTypesForParentScope()
        eventTypeCache.keys.removeAll { eventType -> childTypes.any { eventType.isSubtypeOf(it) } }

        // Merge into child subscriber cache.
        childSubscriberCache.mergeAll(childScope.subscribers)
        childSubscriberCache.mergeAll(childScope.childSubscriberCache)

        // Forward cache update to all parent scopes.
        for (parentScope in parentScopes) {
            parentScope.cacheRegisterChildScope(childScope)
        }
    }

    private fun cacheUnregisterParentScope(parentScope: EventScopeImpl) {
        val parentTypes = parentScope.subscribedEventTypesForChildScope()
        eventTypeCache.keys.removeAll { eventType -> parentTypes.any { eventType.isSubtypeOf(it) } }

        // Rebuild parent subscriber cache.
        parentSubscriberCache.clear()
        for (scope in parentScopes) {
            parentSubscriberCache.mergeAll(scope.subscribers)
            parentSubscriberCache.mergeAll(scope.parentSubscriberCache)
        }

        // Forward cache update to all child scopes.
        for (childScope in subScopes) {
            childScope.cacheUnregisterParentScope(parentScope)
        }
    }

    private fun cacheUnregisterChildScope(childScope: EventScopeImpl) {
        val childTypes = childScope.subscribedEventTypesForParentScope()
        eventTypeCache.keys.removeAll { eventType -> childTypes.any { eventType.isSubtypeOf(it) } }

        // Rebuild child subscriber cache.
        childSubscriberCache.clear()
        for (scope in subScopes) {
            childSubscriberCache.mergeAll(scope.subscribers)
            childSubscriberCache.mergeAll(scope.childSubscriberCache)
        }

        // Forward cache update to all parent scopes.
        for (parentScope in parentScopes) {
            parentScope.cacheUnregisterChildScope(childScope)
        }
    }

    object EventSubscriberComparator : Comparator<EventSubscriberImpl<*>> {
        override fun compare(a: EventSubscriberImpl<*>, b: EventSubscriberImpl<*>): Int {
            if (a.postOrder != b.postOrder) {
                return a.postOrder.compareTo(b.postOrder)
            }
            return a.uniqueId.compareTo(b.uniqueId)
        }
    }
}

internal fun subscriberTreeSet(): TreeSet<EventSubscriberImpl<*>> {
    return sortedSetOf(EventScopeImpl.EventSubscriberComparator)
}

internal typealias SubscriberCache = MutableMap<KType, TreeSet<EventSubscriberImpl<*>>>

internal fun SubscriberCache.mergeAll(cache2: SubscriberCache) {
    for ((type, subscribers) in cache2) {
        if (type in this) {
            this[type]!!.addAll(subscribers)
        } else {
            val set = subscriberTreeSet()
            set.addAll(subscribers)
            this[type] = set
        }
    }
}
