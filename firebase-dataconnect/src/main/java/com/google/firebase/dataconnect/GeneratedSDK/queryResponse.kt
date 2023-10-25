package com.google.firebase.dataconnect

data class GetPostResponse(
    val content : String,
    val comments : List<Comment>
) {
    data class Comment(
        val id : Int,
        val content : String
    )
}

data class ListPostsResponse(
    val posts : List<Post>
) {
    data class Post(
        val id : String,
        val content : String
    )
}

data class ListPostsOnlyIdResponse(
    val posts : List<Post>
) {
    data class Post(
        val id : String
    )
}
