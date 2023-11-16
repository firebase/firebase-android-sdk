package com.google.firebase.dataconnect.apiproposal

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy

////////////////////////////////////////////////////////////////////////////////////////////////////
// CORE SDK
////////////////////////////////////////////////////////////////////////////////////////////////////

class FirebaseDataConnect {

  fun <VariablesType, ResultType> query(
    operationName: String,
    operationSet: String,
    revision: String,
    codec: BaseRef.Codec<ResultType>,
    variablesSerializer: SerializationStrategy<VariablesType>,
  ): QueryRef<VariablesType, ResultType> = TODO()

  class Queries internal constructor() {
    val dataConnect: FirebaseDataConnect
      get() = TODO()
  }
  val queries: Queries = TODO()
}

open class DataConnectException internal constructor() : Exception()

open class NetworkTransportException internal constructor() : DataConnectException()

open class GraphQLException internal constructor() : DataConnectException() {
  val errors: List<String>
    get() = TODO()
}

open class ResultDecodeException internal constructor() : DataConnectException()

abstract class BaseRef<VariablesType, ResultType> internal constructor() {
  val dataConnect: FirebaseDataConnect
    get() = TODO()

  abstract suspend fun execute(variables: VariablesType): ResultType

  interface Codec<ResultType> {
    fun decodeResult(map: Map<String, Any?>): ResultType
  }
}

class QueryRef<VariablesType, ResultType> internal constructor() :
  BaseRef<VariablesType, ResultType>() {
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
  val lastResult: Message<VariablesType, ResultType>?
    get() = TODO()

  // Alternative considered: Return `Deferred<Result<T>>` so that customer knows when the reload
  // completes. For example, suppose a UI has a "Reload" button and when the customer clicks it they
  // get a spinner. The app then awaits the returned "Deferred" object to change the spinner to a
  // check mark or red "X". Note that simply waiting for a result to be delivered to a Flow isn't
  // sufficient because it's not clear that the result was from the specific call to reload() or
  // some previous call to reload() by some other unrelated operation.
  fun reload(): Unit = TODO()

  val flow: Flow<Message<VariablesType, ResultType>> = TODO()

  class Message<VariablesType, ResultType>(
    val variables: VariablesType,
    val result: Result<ResultType>
  )
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// GENERATED SDK
////////////////////////////////////////////////////////////////////////////////////////////////////

class GetPostQuery private constructor() {

  @Serializable data class Variables(val id: String)

  data class Result(val post: Post) {
    data class Post(val content: String, val comments: List<Comment>) {
      data class Comment(val id: String, val content: String)
    }
  }

  companion object {
    fun query(dataConnect: FirebaseDataConnect): QueryRef<Variables, Result?> = TODO()
  }
}

val FirebaseDataConnect.Queries.getPost: QueryRef<GetPostQuery.Variables, GetPostQuery.Result?>
  get() = TODO()

suspend fun QueryRef<GetPostQuery.Variables, GetPostQuery.Result?>.execute(
  id: String
): GetPostQuery.Result? = TODO()

fun QueryRef<GetPostQuery.Variables, GetPostQuery.Result?>.subscribe(
  id: String
): QuerySubscription<GetPostQuery.Variables, GetPostQuery.Result?> = TODO()

typealias GetPostQueryRef = QueryRef<GetPostQuery.Variables, GetPostQuery.Result?>

typealias GetPostQuerySubscription = QuerySubscription<GetPostQuery.Variables, GetPostQuery.Result?>

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
                if (it.result.isSuccess) {
                  showPostContent(it.variables.id, it.result.getOrThrow())
                } else {
                  showError(it.variables.id, it.result.exceptionOrNull()!!)
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
      val id = getIdFromTextView()
      try {
        showPostContent(id, dataConnect.queries.getPost.execute(id = id))
      } catch (e: Exception) {
        showError(id, e)
      }
    }
  }

  override fun onDestroy() {
    querySubscriptionFlow?.cancel()
    super.onDestroy()
  }

  fun getIdFromTextView(): String = TODO()
  fun showError(postId: String, exception: Throwable): Unit = TODO()
  fun showPostContent(postId: String, post: GetPostQuery.Result?): Unit = TODO()
}
