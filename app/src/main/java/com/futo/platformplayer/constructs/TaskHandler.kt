package com.futo.platformplayer.constructs

import android.util.Log
import com.futo.platformplayer.logging.Logger
import kotlinx.coroutines.*

class TaskHandler<TParameter, TResult> {
    private val TAG = "TaskHandler<TResult>"

    var onSuccess = Event1<TResult>();
    var onError = Event2<Throwable, TParameter>();

    private val _scope: ()->CoroutineScope;
    private val _dispatcher: CoroutineDispatcher;
    private var _idGenerator = 0;
    private val _task: suspend ((parameter: TParameter) -> TResult);

    constructor(claz : Class<TResult>, scope: ()->CoroutineScope) {
        _task = { claz.newInstance() };
        _scope = scope;
        _dispatcher = Dispatchers.IO;
    }
    constructor(scope: ()->CoroutineScope, task: suspend ((parameter: TParameter) -> TResult), dispatcher: CoroutineDispatcher = Dispatchers.IO) {
        _task = task;
        _scope = scope;
        _dispatcher = dispatcher;
    }

    inline fun success(noinline cb : (TResult)->Unit) : TaskHandler<TParameter, TResult> {
        onSuccess.subscribe(cb);
        return this;
    }

    inline fun <reified T : Throwable>exception(noinline cb : (T)->Unit) : TaskHandler<TParameter, TResult> {
        onError.subscribeConditional { ex, para ->
            if(ex is T) {
                cb(ex);
                return@subscribeConditional true;
            }
            return@subscribeConditional false;
        }
        return this;
    }
    inline fun <reified T : Throwable>exceptionWithParameter(noinline cb : (T, TParameter)->Unit) : TaskHandler<TParameter, TResult> {
        onError.subscribeConditional { ex, para ->
            if(ex is T) {
                cb(ex, para);
                return@subscribeConditional true;
            }

            return@subscribeConditional false;
        }
        return this;
    }

    @Synchronized
    fun run(parameter: TParameter) {
        val id = ++_idGenerator;

        _scope().launch(_dispatcher) {
            if (id != _idGenerator)
                return@launch;

            try {
                val result = _task.invoke(parameter);
                if (id != _idGenerator)
                    return@launch;

                withContext(Dispatchers.Main) {
                    if (id != _idGenerator)
                        return@withContext;

                    try {
                        onSuccess.emit(result);
                    }
                    catch (e: Throwable) {
                        Logger.w(TAG, "Handled exception in TaskHandler onSuccess.", e);
                        onError.emit(e, parameter);
                    }
                }
            }
            catch (e: Throwable) {
                Log.i("TaskHandler", "TaskHandler.run in exception: " + e.message);
                if (id != _idGenerator)
                    return@launch;

                withContext(Dispatchers.Main) {
                    if (id != _idGenerator)
                        return@withContext;

                    if (!onError.emit(e, parameter)) {
                        Logger.e(TAG, "Uncaught exception handled by TaskHandler.", e);
                    } else {
                        //Logger.w(TAG, "Handled exception in TaskHandler invoke.", e); (Prevents duplicate logs)
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
        onSuccess.clear();
        onError.clear();
    }
}