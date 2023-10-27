package com.google.firebase.dataconnect

import com.google.protobuf.Struct

// TODO: restrict Any access scope or replace it
abstract class Response(val response : Struct) {
    open fun decode() : Any {
        return "null"
    }
}

class GetPostResponse(response : Struct) : Response(response) {
    data class Post(
        val content : String,
        val comments : List<Comment>) {
        data class Comment(
            val id : Int,
            val content : String
        )
    }

    override fun decode() : Post {
            return Post("", emptyList())
    }
}

class ListPostsResponse(response : Struct) : Response(response) {
    data class Posts(
        val posts : List<Post>
        ) {
        data class Post(
            val id : String,
            val content : String)
    }

    override fun decode() : Posts {
        return Posts(emptyList())
    }
}

class ListPostsOnlyIdResponse(response : Struct) : Response(response) {
    data class Posts(
        val posts : List<Post>
    ) {
        data class Post(val id : String)
    }

    override fun decode() : Posts {
        return Posts(emptyList())
    }
}
