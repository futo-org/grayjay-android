package com.futo.platformplayer.constructs

import android.provider.Settings.Global
import com.futo.platformplayer.states.StateApp
import kotlinx.coroutines.*

class BatchedTaskHandler<TParameter, TResult> {

    private val _batchLock = Object();
    //TODO: Determine Deferred/Async vs CompletableFuture (JVM8)
    private val _batchRequest = HashMap<TParameter, Deferred<TResult>>();

    private val _scope: CoroutineScope;
    private val _task: suspend ((parameter: TParameter) -> TResult);
    private val _taskGetCache: ((parameter: TParameter) -> TResult?)?;
    private val _taskSetCache: ((para: TParameter, result: TResult) -> Unit)?;

    constructor(scope: CoroutineScope, task: suspend ((parameter: TParameter) -> TResult), taskGetCache: ((parameter: TParameter) -> TResult?)? = null, taskSetCache: ((para: TParameter, result: TResult) -> Unit)? = null) {
        _task = task;
        _scope = scope;
        _taskGetCache = taskGetCache;
        _taskSetCache = taskSetCache;
        if((_taskGetCache != null) != (_taskSetCache != null))
            throw IllegalArgumentException("Neither or both getCache/setCache need to be provided");
    }

    fun execute(para : TParameter) : Deferred<TResult> {
        var result: TResult? = null;
        var taskResult: Deferred<TResult>? = null;

        synchronized(_batchLock) {
            result = _taskGetCache?.invoke(para);
            if(result == null) {
                taskResult = _batchRequest[para];
                if(taskResult?.isCancelled ?: false) {
                    _batchRequest.remove(para);
                    taskResult = null;
                }
            }

            //Cached
            if(result != null)
                return CompletableDeferred(result as TResult);
            //Already requesting
            if(taskResult != null)
                return taskResult as Deferred<TResult>;

            //No ongoing task, then execute the search
            //TODO: Replace GlobalScope with _scope after preventing cancel on exception
            val task = GlobalScope.async {
                val res: TResult;
                try {
                    res = _task.invoke(para);
                    result = res;
                } catch(ex : Throwable) {
                    synchronized (_batchLock) {
                        _batchRequest.remove(para);
                    }

                    throw ex.fillInStackTrace();
                }

                synchronized(_batchLock) {
                    _batchRequest.remove(para);
                    _taskSetCache?.invoke(para, res);
                    return@async res;
                }
            };
            _batchRequest[para] = task;
            return task;
        }
    }
}