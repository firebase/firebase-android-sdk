package com.google.firebase.dataconnect

import com.google.protobuf.Struct
import kotlinx.coroutines.channels.ReceiveChannel

class GetPostQuery(private var id : String) {
    private val queryRef = QueryRef("revision", "operationSet", "GetPostQuery")
    private val dataConnect = FirebaseDataConnect.getInstance("", "")
    private val subscribeQuery : QuerySubscription
        get() {
            return dataConnect.subscribeQuery(queryRef, encode())
        }

    fun update(newId : String) {
        id = newId
    }

    suspend fun get() : GetPostResponse {
        return decode(dataConnect.executeQuery(queryRef, encode()))
    }

    fun listen() : ReceiveChannel<QueryResult> {
        return subscribeQuery.channel
    }

    fun reload() {
        subscribeQuery.reload()
    }

    private fun encode() : Map<String, Any?> {
        return mapOf(id to null)
    }

    private fun decode(response : Struct) : GetPostResponse {
        return GetPostResponse("", emptyList())
    }
}

class ListPostsQuery {
    private val queryRef = QueryRef("revision", "operationSet", "ListPostsQuery")
    private val dataConnect = FirebaseDataConnect.getInstance("", "")
    private val subscribeQuery : QuerySubscription
        get() {
            return dataConnect.subscribeQuery(queryRef, encode())
        }

    suspend fun get() : ListPostsResponse {
        return decode(dataConnect.executeQuery(queryRef, encode()))
    }

    fun listen() : ReceiveChannel<QueryResult> {
        return subscribeQuery.channel
    }

    fun reload() {
        subscribeQuery.reload()
    }

    private fun encode() : Map<String, Any?> {
        return emptyMap()
    }

    private fun decode(response : Struct) : ListPostsResponse {
        return ListPostsResponse(emptyList())
    }
}

class ListPostsOnlyIdQuery {
    private val queryRef = QueryRef("revision", "operationSet", "ListPostsOnlyIdQuery")
    private val dataConnect = FirebaseDataConnect.getInstance("", "")
    private val subscribeQuery : QuerySubscription
        get() {
            return dataConnect.subscribeQuery(queryRef, encode())
        }

    suspend fun get() : ListPostsOnlyIdResponse {
        return decode(dataConnect.executeQuery(queryRef, encode()))
    }

    fun listen() : ReceiveChannel<QueryResult> {
        return subscribeQuery.channel
    }

    fun reload() {
        subscribeQuery.reload()
    }

    private fun encode() : Map<String, Any?> {
        return emptyMap()
    }

    private fun decode(response : Struct) : ListPostsOnlyIdResponse {
        return ListPostsOnlyIdResponse(emptyList())
    }
}
