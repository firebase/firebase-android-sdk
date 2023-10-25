package com.google.firebase.dataconnect

import com.google.protobuf.Struct

class GetPostQuery(private var id : String) {
    private val queryRef = QueryRef("revision", "operationSet", "GetPostQuery")
    private val dataConnect = FirebaseDataConnect.getInstance("", "")
    fun update(newId : String) {
        id = newId
    }

    suspend fun get() : GetPostResponse {
        return decode(dataConnect.executeQuery(queryRef, encode()))
    }

    fun listen() : QuerySubscription {
        return dataConnect.subscribeQuery(queryRef, encode())
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

    suspend fun get() : ListPostsResponse {
        return decode(dataConnect.executeQuery(queryRef, encode()))
    }

    fun listen() : QuerySubscription {
        return dataConnect.subscribeQuery(queryRef, encode())
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

    suspend fun get() : ListPostsOnlyIdResponse {
        return decode(dataConnect.executeQuery(queryRef, encode()))
    }

    fun listen() : QuerySubscription {
        return dataConnect.subscribeQuery(queryRef, encode())
    }

    private fun encode() : Map<String, Any?> {
        return emptyMap()
    }

    private fun decode(response : Struct) : ListPostsOnlyIdResponse {
        return ListPostsOnlyIdResponse(emptyList())
    }
}
