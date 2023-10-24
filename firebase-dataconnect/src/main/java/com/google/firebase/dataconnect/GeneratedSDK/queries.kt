package com.google.firebase.dataconnect

class GetPost(id : String) : QueryRefBasic<GetPostResponse>(mapOf(id to null)) {
    fun update(newId : String) {
        super.updateVariables(mapOf(newId to null))
    }
}

data class GetPostResponse(
    val content : String,
    val comments : List<Comment>
) {
     data class Comment(
        val id : Int,
        val content : String
    )
}

class ListPosts : QueryRefBasic<List<ListPostsResponse>>()

data class ListPostsResponse(
    val id : String,
    val content : String
)

class ListPostsOnlyId : QueryRefBasic<List<ListPostsOnlyIdResponse>>()

data class ListPostsOnlyIdResponse(val id : String)
