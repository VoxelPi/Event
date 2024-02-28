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
    private val handlers: MutableList<EventHandlerImpl<*>> = mutableListOf()

    override fun postEvent(event: Any, eventType: KType) {
        // Collect all relevant handlers. // TODO: This should not happen every time an event is posted.
        val handlers = mutableListOf<EventHandlerImpl<*>>()
        handlers.addAll(this.handlers)
        for (subScope in subScopes) {
            handlers.addAll(subScope.handlers)
        }

        // Filter handlers
        val applicableHandlers = handlers
            .filter { handler ->
                eventType.isSubtypeOf(handler.type)
            }
            .sortedByDescending { it.priority }

        // Post event to handlers.
        for (handler in applicableHandlers) {
            @Suppress("UNCHECKED_CAST")
            (handler as EventHandlerImpl<Any>).callback.invoke(event)
        }
    }

    override fun <T : Any> handleEvent(type: KType, priority: Int, callback: (T) -> Unit): EventHandlerImpl<T> {
        val handler = EventHandlerImpl(type, priority, callback)
        handlers.add(handler)
        return handler
    }

    override fun registerAnnotated(instance: Any): EventScope {
        val typeClass = instance::class
        val scope = EventScopeImpl(instance)
        register(scope)

        // Get all functions that are annotated by Subscribe, take one parameter (plus implicit this parameter) and return Unit.
        val handlerFunctions = typeClass.memberFunctions.filter { function ->
            function.findAnnotation<Subscribe>() != null && function.parameters.size == 2 && function.returnType == typeOf<Unit>()
        }

        // Generate handlers
        for (function in handlerFunctions) {
            val subscription = function.findAnnotation<Subscribe>()!!
            val priority = subscription.priority
            val type = function.parameters[1].type
            val handler = EventHandlerImpl<Any>(type, priority) { event ->
                function.call(instance, event)
            }
            scope.handlers.add(handler)
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

    override fun unregister(handler: EventHandler<*>) {
        handlers.remove(handler)
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
