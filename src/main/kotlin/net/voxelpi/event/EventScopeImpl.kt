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
    private val subscriptions: SubscriptionsCache = mutableMapOf()

    private val childSubscriptionsCache: SubscriptionsCache = mutableMapOf()
    private val parentSubscriptionsCache: SubscriptionsCache = mutableMapOf()
    private val eventTypeCache: SubscriptionsCache = mutableMapOf()

    /**
     * Returns all subscriptions that should be used by this scope.
     */
    fun subscriptionsForCurrentScope(): List<EventSubscriptionImpl<*>> {
        val subscriptions = subscriptions.values.flatten().toMutableList()
        subscriptions.addAll(parentSubscriptionsCache.values.flatten())
        subscriptions.addAll(childSubscriptionsCache.values.flatten())
        return subscriptions
    }

    /**
     * Returns all subscribes that should be used by the sub scopes.
     */
    fun subscriptionsForSubScopes(): List<EventSubscriptionImpl<*>> {
        val subscriptions = subscriptions.values.flatten().toMutableList()
        subscriptions.addAll(parentSubscriptionsCache.values.flatten())
        return subscriptions
    }

    /**
     * Returns all subscriptions that should be used by the parent scopes.
     */
    fun subscriptionsForParentScope(): List<EventSubscriptionImpl<*>> {
        val subscriptions = subscriptions.values.flatten().toMutableList()
        subscriptions.addAll(childSubscriptionsCache.values.flatten())
        return subscriptions
    }

    override fun subscribedEventTypes(): Set<KType> {
        val types = subscriptions.keys.toMutableSet()
        types.addAll(childSubscriptionsCache.keys)
        types.addAll(parentSubscriptionsCache.keys)
        return types
    }

    /**
     * Returns all subscribed event types that should be seen by the parent scopes.
     */
    fun subscribedEventTypesForParentScope(): Set<KType> {
        val types = subscriptions.keys.toMutableSet()
        types.addAll(childSubscriptionsCache.keys)
        return types
    }

    /**
     * Returns all subscribed event types that should be seen by the child scopes.
     */
    fun subscribedEventTypesForChildScope(): Set<KType> {
        val types = subscriptions.keys.toMutableSet()
        types.addAll(parentSubscriptionsCache.keys)
        return types
    }

    override fun collectiveSubscriptions(): Collection<EventSubscription<*>> {
        return subscriptionsForCurrentScope()
    }

    override fun collectiveSubscriptionsForType(eventType: KType): TreeSet<EventSubscriptionImpl<*>> {
        if (eventType in eventTypeCache) {
            return eventTypeCache[eventType]!!
        }

        val subscriptions = subscriptionTreeSet()
        subscriptions.addAll(this.subscriptions.filterKeys { type -> eventType.isSubtypeOf(type) }.values.flatten())
        subscriptions.addAll(parentSubscriptionsCache.filterKeys { type -> eventType.isSubtypeOf(type) }.values.flatten())
        subscriptions.addAll(childSubscriptionsCache.filterKeys { type -> eventType.isSubtypeOf(type) }.values.flatten())
        eventTypeCache[eventType] = subscriptions
        return subscriptions
    }

    @Suppress("UNCHECKED_CAST")
    override fun postEvent(event: Any, eventType: KType): PostResult {
        // Get relevant subscriptions from the cache.
        val eventSubscriptions = collectiveSubscriptionsForType(eventType)

        // Post event to subscribers.
        val exceptions = mutableMapOf<EventSubscription<*>, Throwable>()
        for (subscription in eventSubscriptions) {
            try {
                (subscription as EventSubscriptionImpl<Any>).callback.invoke(event)
            } catch (exception: Exception) {
                exceptions[subscription] = exception
            }
        }

        // Return result
        return if (exceptions.isEmpty()) {
            PostResult.Success
        } else {
            PostResult.Failure(exceptions)
        }
    }

    override fun <T : Any> handleEvent(type: KType, postOrder: Int, callback: (T) -> Unit): EventSubscriptionImpl<T> {
        val subscription = EventSubscriptionImpl<T>(this, type, postOrder, UUID.randomUUID(), callback)

        if (subscription.type in subscriptions) {
            subscriptions[subscription.type]!!.add(subscription)
        } else {
            subscriptions[subscription.type] = sortedSetOf(EventSubscriptionComparator, subscription)
        }
        cacheRegisterSubscription(subscription)

        return subscription
    }

    override fun registerAnnotated(instance: Any): EventScope {
        // Return existing sub scope if the instance was already used to create a sub scope of this scope.
        val existing = annotatedSubScope(instance)
        if (existing != null) {
            return existing
        }

        val typeClass = instance::class
        val scope = EventScopeImpl(instance)

        // Get all functions that are annotated by Subscribe, take one parameter (plus implicit this parameter) and return Unit.
        val functions = typeClass.memberFunctions.filter { function ->
            function.findAnnotation<Subscribe>() != null && function.parameters.size == 2 && function.returnType == typeOf<Unit>()
        }

        // Generate subscriptions
        for (function in functions) {
            val subscription = function.findAnnotation<Subscribe>()!!
            val postOrder = subscription.postOrder
            val type = function.parameters[1].type
            scope.handleEvent<Any>(type, postOrder) { event ->
                function.call(instance, event)
            }
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

    /**
     * Unregisters the given [subscription] from this scope.
     */
    fun unregisterSubscription(subscription: EventSubscription<*>) {
        require(subscription is EventSubscriptionImpl)

        // Unregister subscription.
        subscriptions[subscription.type]?.remove(subscription)
        if (subscriptions[subscription.type]?.isEmpty() == true) {
            subscriptions.remove(subscription.type)
        }

        cacheUnregisterSubscription(subscription)
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

    private fun cacheRegisterSubscription(subscription: EventSubscriptionImpl<*>) {
        eventTypeCache.keys.removeAll { it.isSubtypeOf(subscription.type) }

        for (childScope in subScopes) {
            childScope.cacheRegisterSubscriptionByParent(subscription)
        }
        for (parentScope in parentScopes) {
            parentScope.cacheRegisterSubscriptionByChild(subscription)
        }
    }

    private fun cacheRegisterSubscriptionByParent(subscription: EventSubscriptionImpl<*>) {
        eventTypeCache.keys.removeAll { it.isSubtypeOf(subscription.type) }

        if (subscription.type in parentSubscriptionsCache) {
            parentSubscriptionsCache[subscription.type]!!.add(subscription)
        } else {
            parentSubscriptionsCache[subscription.type] = sortedSetOf(EventSubscriptionComparator, subscription)
        }

        for (childScope in subScopes) {
            childScope.cacheRegisterSubscriptionByParent(subscription)
        }
    }

    private fun cacheRegisterSubscriptionByChild(subscription: EventSubscriptionImpl<*>) {
        eventTypeCache.keys.removeAll { it.isSubtypeOf(subscription.type) }

        if (subscription.type in childSubscriptionsCache) {
            childSubscriptionsCache[subscription.type]!!.add(subscription)
        } else {
            childSubscriptionsCache[subscription.type] = sortedSetOf(EventSubscriptionComparator, subscription)
        }

        for (parentScope in parentScopes) {
            parentScope.cacheRegisterSubscriptionByChild(subscription)
        }
    }

    private fun cacheUnregisterSubscription(subscription: EventSubscriptionImpl<*>) {
        eventTypeCache.keys.removeAll { it.isSubtypeOf(subscription.type) }

        for (childScope in subScopes) {
            childScope.cacheUnregisterSubscriptionByParent(subscription)
        }
        for (parentScope in parentScopes) {
            parentScope.cacheUnregisterSubscriptionByChild(subscription)
        }
    }

    private fun cacheUnregisterSubscriptionByParent(subscription: EventSubscriptionImpl<*>) {
        eventTypeCache.keys.removeAll { it.isSubtypeOf(subscription.type) }

        parentSubscriptionsCache[subscription.type]?.remove(subscription)
        if (parentSubscriptionsCache[subscription.type]?.isEmpty() == true) {
            parentSubscriptionsCache.remove(subscription.type)
        }

        for (childScope in subScopes) {
            childScope.cacheUnregisterSubscriptionByParent(subscription)
        }
    }

    private fun cacheUnregisterSubscriptionByChild(subscription: EventSubscriptionImpl<*>) {
        eventTypeCache.keys.removeAll { it.isSubtypeOf(subscription.type) }

        childSubscriptionsCache[subscription.type]?.remove(subscription)
        if (childSubscriptionsCache[subscription.type]?.isEmpty() == true) {
            childSubscriptionsCache.remove(subscription.type)
        }

        for (parentScope in parentScopes) {
            parentScope.cacheUnregisterSubscriptionByChild(subscription)
        }
    }

    private fun cacheRegisterParentScope(parentScope: EventScopeImpl) {
        val parentTypes = parentScope.subscribedEventTypesForChildScope()
        eventTypeCache.keys.removeAll { eventType -> parentTypes.any { eventType.isSubtypeOf(it) } }

        // Merge into parent subscription cache.
        parentSubscriptionsCache.mergeAll(parentScope.subscriptions)
        parentSubscriptionsCache.mergeAll(parentScope.parentSubscriptionsCache)

        // Forward cache update to all child scopes.
        for (childScope in subScopes) {
            childScope.cacheRegisterParentScope(parentScope)
        }
    }

    private fun cacheRegisterChildScope(childScope: EventScopeImpl) {
        val childTypes = childScope.subscribedEventTypesForParentScope()
        eventTypeCache.keys.removeAll { eventType -> childTypes.any { eventType.isSubtypeOf(it) } }

        // Merge into child subscription cache.
        childSubscriptionsCache.mergeAll(childScope.subscriptions)
        childSubscriptionsCache.mergeAll(childScope.childSubscriptionsCache)

        // Forward cache update to all parent scopes.
        for (parentScope in parentScopes) {
            parentScope.cacheRegisterChildScope(childScope)
        }
    }

    private fun cacheUnregisterParentScope(parentScope: EventScopeImpl) {
        val parentTypes = parentScope.subscribedEventTypesForChildScope()
        eventTypeCache.keys.removeAll { eventType -> parentTypes.any { eventType.isSubtypeOf(it) } }

        // Rebuild parent subscription cache.
        parentSubscriptionsCache.clear()
        for (scope in parentScopes) {
            parentSubscriptionsCache.mergeAll(scope.subscriptions)
            parentSubscriptionsCache.mergeAll(scope.parentSubscriptionsCache)
        }

        // Forward cache update to all child scopes.
        for (childScope in subScopes) {
            childScope.cacheUnregisterParentScope(parentScope)
        }
    }

    private fun cacheUnregisterChildScope(childScope: EventScopeImpl) {
        val childTypes = childScope.subscribedEventTypesForParentScope()
        eventTypeCache.keys.removeAll { eventType -> childTypes.any { eventType.isSubtypeOf(it) } }

        // Rebuild child subscription cache.
        childSubscriptionsCache.clear()
        for (scope in subScopes) {
            childSubscriptionsCache.mergeAll(scope.subscriptions)
            childSubscriptionsCache.mergeAll(scope.childSubscriptionsCache)
        }

        // Forward cache update to all parent scopes.
        for (parentScope in parentScopes) {
            parentScope.cacheUnregisterChildScope(childScope)
        }
    }

    object EventSubscriptionComparator : Comparator<EventSubscriptionImpl<*>> {
        override fun compare(a: EventSubscriptionImpl<*>, b: EventSubscriptionImpl<*>): Int {
            if (a.postOrder != b.postOrder) {
                return a.postOrder.compareTo(b.postOrder)
            }
            return a.uniqueId.compareTo(b.uniqueId)
        }
    }
}

internal fun subscriptionTreeSet(): TreeSet<EventSubscriptionImpl<*>> {
    return sortedSetOf(EventScopeImpl.EventSubscriptionComparator)
}

internal typealias SubscriptionsCache = MutableMap<KType, TreeSet<EventSubscriptionImpl<*>>>

internal fun SubscriptionsCache.mergeAll(cache2: SubscriptionsCache) {
    for ((type, subscriptions) in cache2) {
        if (type in this) {
            this[type]!!.addAll(subscriptions)
        } else {
            val set = subscriptionTreeSet()
            set.addAll(subscriptions)
            this[type] = set
        }
    }
}
