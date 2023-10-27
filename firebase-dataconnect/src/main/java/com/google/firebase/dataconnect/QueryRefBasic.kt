package com.google.firebase.dataconnect

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import com.google.protobuf.Struct
import kotlinx.coroutines.channels.ReceiveChannel

// QueryResponse cannot be marked as `out` since we need `remove(listener : Channel<QueryResponse>)`
abstract class QueryRefBasic<in QueryRequest : Request, QueryResponse : Response> constructor(
    val revision: String,
    val operationSet: String,
    val operationName: String
    ) {

    val dataConnect = FirebaseDataConnect.getInstance("", "")

     val listeners = mutableListOf<Channel<QueryResponse>>()

     val testQuery = QueryRef(revision, operationSet, operationName)

    // use suspend to work with grpc call
    suspend fun get(request : QueryRequest) : Struct {
        return dataConnect.executeQuery(testQuery, request.encode())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun listen() : ReceiveChannel<QueryResponse> {
        val channel = Channel<QueryResponse>()
        listeners.add(channel)
        // invoked once the channel is closed or the receiving side of this channel is cancel.
        channel.invokeOnClose { listeners.remove(channel) }
        return channel
    }

    suspend fun reload(request : QueryRequest) {
        pushNotifications(get(request))
    }

    // The reason why not cancel the channel is the developer can reuse it for other things
    fun remove(listener : Channel<QueryResponse>) {
        listeners.remove(listener)
    }

    fun removeAllListeners() {
        listeners.clear()
    }

    private suspend fun realtimeStreaming() {
        while (true) {
            //TODO: wait for backend changes, another channel talks to backend?
            pushNotifications("realtime" as Struct)
        }
    }

    private suspend fun pushNotifications(value : Struct) {
        for (receiver in listeners) {
            receiver.send(value as QueryResponse)
        }
    }
}
