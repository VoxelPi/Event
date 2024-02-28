package net.voxelpi.event

import net.voxelpi.event.annotation.Subscribe
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.typeOf

class EventScopeImpl : EventScope {

    private val childScopes: MutableList<EventScopeImpl> = mutableListOf()
    private val handlers: MutableList<EventHandlerImpl<*>> = mutableListOf()

    override fun postEvent(event: Any, eventType: KType) {
        // Collect all relevant handlers. // TODO: This should not happen every time an event is posted.
        val handlers = mutableListOf<EventHandlerImpl<*>>()
        handlers.addAll(this.handlers)
        for (scope in childScopes) {
            handlers.addAll(scope.handlers)
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

    override fun registerSubscriptions(subscriptions: Any) {
        val typeClass = subscriptions::class

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
                function.call(subscriptions, event)
            }
            handlers.add(handler)
        }
    }

    override fun unregister(handler: EventHandler<*>) {
        handlers.remove(handler)
    }

    override fun register(scope: EventScope) {
        childScopes.add(scope as EventScopeImpl)
    }

    override fun unregister(scope: EventScope) {
        childScopes.remove(scope as EventScopeImpl)
    }
}
