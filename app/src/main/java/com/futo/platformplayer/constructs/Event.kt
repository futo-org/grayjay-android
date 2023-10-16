package com.futo.platformplayer.constructs

interface IEvent {

}
abstract class EventBase<Handler, ConditionalHandler>: IEvent {

    protected val _conditionalListeners = mutableListOf<TaggedHandler<ConditionalHandler>>();
    protected val _listeners = mutableListOf<TaggedHandler<Handler>>();

    fun hasListeners(): Boolean =
        synchronized(_listeners){_listeners.isNotEmpty()} ||
        synchronized(_conditionalListeners){_conditionalListeners.isNotEmpty()};

    fun subscribeConditional(listener: ConditionalHandler) {
        synchronized(_conditionalListeners) {
            _conditionalListeners.add(TaggedHandler(listener));
        }
    }

    fun subscribeConditional(tag: Any?, listener: ConditionalHandler) {
        synchronized(_conditionalListeners) {
            _conditionalListeners.add(TaggedHandler(listener, tag));
        }
    }

    fun subscribe(listener : Handler) {
        synchronized(_listeners) {
            _listeners.add(TaggedHandler(listener));
        }
    }

    fun subscribe(tag: Any?, listener: Handler) {
        synchronized(_listeners) {
            _listeners.add(TaggedHandler(listener, tag));
        }
    }

    fun remove(tag: Any) {
        synchronized(_conditionalListeners) {
            _conditionalListeners.removeIf { it.tag == tag };
        }

        synchronized(_listeners) {
            _listeners.removeIf { it.tag == tag };
        }
    }

    fun clear() {
        synchronized(_conditionalListeners) {
            _conditionalListeners.clear();
        }

        synchronized(_listeners) {
            _listeners.clear();
        }
    }

    class TaggedHandler<T> {
        val tag: Any?;
        val handler: T;

        constructor(handler: T, tag: Any? = null) {
            this.tag = tag;
            this.handler = handler;
        }
    }
}

class Event0() : EventBase<(()->Unit), (()->Boolean)>() {
    fun emit() : Boolean {
        var handled = false;

        synchronized(_conditionalListeners) {
            for (conditional in _conditionalListeners)
                handled = handled || conditional.handler.invoke();
        }

        synchronized(_listeners) {
            handled = handled || _listeners.isNotEmpty();
            for (handler in _listeners)
                handler.handler.invoke();
        }

        return handled;
    }
}
class Event1<T1>() : EventBase<((T1)->Unit), ((T1)->Boolean)>() {
    fun emit(value : T1): Boolean {
        var handled = false;
        synchronized(_conditionalListeners) {
            for (conditional in _conditionalListeners)
                handled = handled || conditional.handler.invoke(value);
        }

        synchronized(_listeners) {
            handled = handled || _listeners.isNotEmpty();
            for (handler in _listeners)
                handler.handler.invoke(value);
        }

        return handled;
    }
}
class Event2<T1, T2>() : EventBase<((T1, T2)->Unit), ((T1, T2)->Boolean)>() {
    fun emit(value1 : T1, value2 : T2): Boolean {
        var handled = false;

        synchronized(_conditionalListeners) {
            for (conditional in _conditionalListeners)
                handled = handled || conditional.handler.invoke(value1, value2);
        }

        synchronized(_listeners) {
            handled = handled || _listeners.isNotEmpty();
            for (handler in _listeners)
                handler.handler.invoke(value1, value2);
        }

        return handled;
    }
}

class Event3<T1, T2, T3>() : EventBase<((T1, T2, T3)->Unit), ((T1, T2, T3)->Boolean)>() {
    fun emit(value1 : T1, value2 : T2, value3 : T3): Boolean {
        var handled = false;

        synchronized(_conditionalListeners) {
            for (conditional in _conditionalListeners)
                handled = handled || conditional.handler.invoke(value1, value2, value3);
        }

        synchronized(_listeners) {
            handled = handled || _listeners.isNotEmpty();
            for (handler in _listeners)
                handler.handler.invoke(value1, value2, value3);
        }

        return handled;
    }
}