package com.google.firebase.dataconnect

class GetPost
    : QueryRefBasic<GetPostRequest, GetPostResponse>("", "", "")
{
    lateinit var request : GetPostRequest
    suspend fun get(id : String): GetPostResponse {
        request = GetPostRequest(id)
        return GetPostResponse(super.get(request))
    }

    suspend fun reload() {
        super.reload(request)
    }
}

class ListPosts : QueryRefBasic<ListPostsRequest, ListPostsResponse>("", "", "") {
    suspend fun get(): GetPostResponse {
        return GetPostResponse(super.get(ListPostsRequest()))
    }
}

class ListPostsOnlyId : QueryRefBasic<ListPostsOnlyIdRequest, ListPostsOnlyIdResponse>("", "", "") {
    suspend fun get(): GetPostResponse {
        return GetPostResponse(super.get(ListPostsOnlyIdRequest()))
    }
}
