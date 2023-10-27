package com.google.firebase.dataconnect

interface Request {
    fun encode() :  Map<String, Any?>
}

class GetPostRequest(var id : String) : Request {
    fun update(newId : String) {
        id = newId
    }

    override fun encode() : Map<String, Any?> {
        return emptyMap()
    }
}

class ListPostsRequest : Request {
    override fun encode() : Map<String, Any?> {
        return emptyMap()
    }
}

class ListPostsOnlyIdRequest : Request {
    override fun encode() : Map<String, Any?> {
        return emptyMap()
    }
}
