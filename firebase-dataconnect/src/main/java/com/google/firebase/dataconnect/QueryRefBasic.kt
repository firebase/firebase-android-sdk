package com.google.firebase.dataconnect

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel

abstract class QueryRefBasic<T> constructor(private var variables : Map<String, Any?>? = null) {
    // TODO: Make private in public API
    val listeners = mutableListOf<Channel<T>>()

    // TODO: Remove in public API
    var testResult : T = "placeholder" as T

    // use suspend to work with grpc call
    suspend fun get() : T {
        return testResult
    }

    protected fun updateVariables(newVariables : Map<String, Any?>) {
        variables = newVariables
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun listen() : Channel<T> {
        val channel = Channel<T>()
        listeners.add(channel)
        // invoked once the channel is closed or the receiving side of this channel is cancel.
        channel.invokeOnClose { listeners.remove(channel) }
        return channel
    }

    suspend fun reload() {
        pushNotifications(get())
    }

    // The reason why not cancel the channel is the developer can reuse it for other things
    fun remove(listener : Channel<T>) {
        listeners.remove(listener)
    }

    fun removeAllListeners() {
        listeners.clear()
    }

    private suspend fun realtimeStreaming() {
        while (true) {
            //TODO: wait for backend changes, another channel talks to backend?
            pushNotifications("realtime" as T)
        }
    }

    private suspend fun pushNotifications(value : T) {
        for (receiver in listeners) {
            receiver.send(value)
        }
    }
}

class TestBasicQuery : QueryRefBasic<String>()
