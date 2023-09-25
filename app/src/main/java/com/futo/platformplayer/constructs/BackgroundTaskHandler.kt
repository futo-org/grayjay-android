package com.futo.platformplayer.constructs

import com.futo.platformplayer.logging.Logger
import kotlinx.coroutines.*

class BackgroundTaskHandler {
    private val TAG = "BackgroundTaskHandler"

    var onError = Event1<Throwable>();

    private val _scope: CoroutineScope;
    private val _dispatcher: CoroutineDispatcher;
    private var _idGenerator = 0;
    private val _task: (() -> Unit);
    private val _lockObject = Object();

    constructor(scope: CoroutineScope, task: (() -> Unit), dispatcher: CoroutineDispatcher = Dispatchers.IO) {
        _task = task;
        _scope = scope;
        _dispatcher = dispatcher;
    }

    inline fun <reified T : Throwable>exception(noinline cb : (T)->Unit) : BackgroundTaskHandler {
        onError.subscribeConditional {
            if(it is T) {
                cb(it);
                return@subscribeConditional true;
            }

            return@subscribeConditional false;
        }
        return this;
    }

    @Synchronized
    fun run() {
        val id = ++_idGenerator;

        _scope.launch(_dispatcher) {
            synchronized (_lockObject) {
                if (id != _idGenerator)
                    return@launch;

                try {
                    _task.invoke();
                    if (id != _idGenerator)
                        return@launch;
                } catch (e: Throwable) {
                    if (id != _idGenerator)
                        return@launch;

                    if (!onError.emit(e)) {
                        Logger.e(TAG, "Uncaught exception handled by BackgroundTaskHandler.", e);
                    }
                }
            }
        }
    }

    @Synchronized
    fun cancel() {
        _idGenerator++;
    }

    @Synchronized
    fun dispose() {
        cancel();
        onError.clear();
    }
}