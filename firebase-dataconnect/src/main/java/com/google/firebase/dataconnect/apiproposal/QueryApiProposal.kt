package com.google.firebase.dataconnect.apiproposal

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

////////////////////////////////////////////////////////////////////////////////////////////////////
// CORE SDK
////////////////////////////////////////////////////////////////////////////////////////////////////

class FirebaseDataConnect {
  class Queries internal constructor() {
    val dataConnect: FirebaseDataConnect
      get() = TODO()
  }
  val queries: Queries = TODO()
}

open class DataConnectException internal constructor(message: String, cause: Throwable? = null) :
  Exception(message, cause)

open class NetworkTransportException internal constructor(message: String, cause: Throwable) :
  DataConnectException(message, cause)

open class ExecutionException internal constructor(message: String) : DataConnectException(message)

open class ResultDecodeException internal constructor(message: String) :
  DataConnectException(message)

abstract class BaseRef<VariablesType, ResultType> internal constructor() {
  val dataConnect: FirebaseDataConnect
    get() = TODO()

  abstract suspend fun execute(variables: VariablesType): ResultType

  protected abstract fun encodeVariables(variables: VariablesType): Map<String, Any?>
  protected abstract fun decodeResult(map: Map<String, Any?>): ResultType
}

abstract class QueryRef<VariablesType, ResultType>(
  dataConnect: FirebaseDataConnect,
  operationName: String,
  operationSet: String,
  revision: String
) : BaseRef<VariablesType, ResultType>() {
  override suspend fun execute(variables: VariablesType): ResultType = TODO()
  fun subscribe(variables: VariablesType): QuerySubscription<VariablesType, ResultType> = TODO()
}

class QuerySubscription<VariablesType, ResultType> internal constructor() {
  val query: QueryRef<VariablesType, ResultType>
    get() = TODO()
  val variables: VariablesType
    get() = TODO()

  // Alternative considered: add `lastResult`. The problem is, what do we do with this value if the
  // variables are changed via a call to update()? Do we clear it? Or do we leave it there even
  // though it came from a request with potentially-different variables?
  val lastResult: Result<ResultType>?
    get() = TODO()

  // Alternative considered: Return `Deferred<Result<T>>` so that customer knows when the reload
  // completes. For example, suppose a UI has a "Reload" button and when the customer clicks it they
  // get a spinner. The app then awaits the returned "Deferred" object to change the spinner to a
  // check mark or red "X". Note that simply waiting for a result to be delivered to a Flow isn't
  // sufficient because it's not clear that the result was from the specific call to reload() or
  // some previous call to reload() by some other unrelated operation.
  fun reload(): Unit = TODO()

  val flow: Flow<Result<ResultType>> = TODO()
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// GENERATED SDK
////////////////////////////////////////////////////////////////////////////////////////////////////

class GetPostQuery(dataConnect: FirebaseDataConnect) :
  QueryRef<GetPostQuery.Variables, GetPostQuery.Result>(
    dataConnect,
    operationName = "getPost",
    operationSet = "crud",
    revision = "1234567890abcdef"
  ) {

  data class Variables(val id: String) {
    val builder = Builder(id = id)
    fun build(block: Builder.() -> Unit): Variables = builder.apply(block).build()
    class Builder(var id: String) {
      fun build() = Variables(id = id)
    }
  }

  data class Result(val post: Post) {
    data class Post(val content: String, val comments: List<Comment>) {
      data class Comment(val id: String, val content: String)
    }
  }

  override fun encodeVariables(variables: Variables) = TODO()

  override fun decodeResult(map: Map<String, Any?>) = TODO()
}

typealias GetPostQuerySubscription = QuerySubscription<GetPostQuery.Variables, GetPostQuery.Result>

val FirebaseDataConnect.Queries.getPost: GetPostQuery
  get() = TODO()

suspend fun GetPostQuery.execute(id: String): GetPostQuery.Result = TODO()

fun GetPostQuery.subscribe(id: String): GetPostQuerySubscription = TODO()

////////////////////////////////////////////////////////////////////////////////////////////////////
// CUSTOMER CODE
////////////////////////////////////////////////////////////////////////////////////////////////////

private class MainActivity : Activity() {

  private lateinit var dataConnect: FirebaseDataConnect
  private lateinit var activityCoroutineScope: CoroutineScope
  private var querySubscription: GetPostQuerySubscription? = null
  private var querySubscriptionFlow: Job? = null

  fun onLiveUpdateButtonClick() {
    if (querySubscription == null) {
      querySubscription =
        dataConnect.queries.getPost.subscribe(id = getIdFromTextView()).also {
          querySubscriptionFlow =
            activityCoroutineScope.launch {
              it.flow.collect {
                if (it.isFailure) {
                  showError(it.exceptionOrNull().toString())
                } else {
                  showPostContent(it.getOrThrow().post.content)
                }
              }
            }
        }
    }
  }

  fun onReloadButtonClick() {
    querySubscription?.reload()
  }

  fun onLoadButtonClick() {
    activityCoroutineScope.launch {
      val result = dataConnect.queries.getPost.execute(id = getIdFromTextView())
      showPostContent(result.post.content)
    }
  }

  override fun onDestroy() {
    querySubscriptionFlow?.cancel()
    super.onDestroy()
  }

  fun getIdFromTextView(): String = TODO()
  fun showError(errorMessage: String): Unit = TODO()
  fun showPostContent(content: String): Unit = TODO()
}
