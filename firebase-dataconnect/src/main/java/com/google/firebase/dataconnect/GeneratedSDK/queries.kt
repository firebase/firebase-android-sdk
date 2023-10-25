package com.google.firebase.dataconnect

class GetPost(id : String) : QueryRefBasic<GetPostResponse>(mapOf(id to null)) {
    fun update(newId : String) {
        super.updateVariables(mapOf(newId to null))
    }
}

class ListPosts : QueryRefBasic<ListPostsResponse>()

class ListPostsOnlyId : QueryRefBasic<ListPostsOnlyIdResponse>()
