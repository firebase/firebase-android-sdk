package com.google.firebase.dataconnect.apiproposal

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

////////////////////////////////////////////////////////////////////////////////////////////////////
// CORE SDK
////////////////////////////////////////////////////////////////////////////////////////////////////

class FirebaseDataConnect {

  // NOTE: This interface and val are only needed if the alternative about adding a
  // getPost() method by the generated SDK below is accepted.
  class Queries internal constructor(val dataConnect: FirebaseDataConnect)
  val queries: Queries = TODO()
}

open class DataConnectException(message: String) : Exception(message)
open class ResultDecodeError(message: String) : DataConnectException(message)

abstract class BaseRef<VariablesType, ResultType>(
  val dataConnect: FirebaseDataConnect,
  operationName: String,
  operationSet: String,
  revision: String,
  variables: VariablesType
) {
  val variables: VariablesType = TODO()

  abstract suspend fun execute(): ResultType

  protected open fun onUpdate() {}
  
  protected interface Codec<VariablesType, ResultType> {
    fun encodeVariables(variables: VariablesType): Map<String, Any?>
    fun decodeResult(map: Map<String, Any?>): ResultType
  }

  protected abstract val codec: Codec<VariablesType, ResultType>
}

abstract class QueryRef<VariablesType, ResultType>(
  dataConnect: FirebaseDataConnect,
  operationName: String,
  operationSet: String,
  revision: String,
  variables: VariablesType
) :
  BaseRef<VariablesType, ResultType>(
    dataConnect = dataConnect,
    operationName = operationName,
    operationSet = operationSet,
    revision = revision,
    variables = variables
  ) {

  // QUESTION: Should the result of execute() be delivered to the listeners that are subscribed?
  // The current implementation does _not_ because it's difficult to know which is the "latest"
  // result from concurrent calls to execute(). One workaround would be to serialize the calls to
  // execute, but that could introduce unacceptable lag for the invocations at the end of the queue.
  override suspend fun execute(): ResultType = TODO()

  fun subscribe(): Flow<Result<ResultType>> = TODO()

  // Alternative considered: Return `Deferred<Result<T>>` so that customer knows when the reload
  // completes. For example, suppose a UI has a "Reload" button and when the customer clicks it they
  // get a spinner. The app then awaits the returned "Deferred" object to change the spinner to a
  // check mark or red "X". Note that simply waiting for a result to be delivered to a Flow isn't
  // sufficient because it's not clear that the result was from the specific call to reload() or
  // some previous call to reload() by some other unrelated operation.
  fun reload(): Unit = TODO()

  // Alternative considered: add `lastResult`. The problem is, what do we do with this value if the
  // variables are changed? Do we clear it? Or do we leave it there even though it came from a
  // request with different variables?
  val lastResult: Result<ResultType>? get() = TODO()
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// GENERATED SDK
////////////////////////////////////////////////////////////////////////////////////////////////////

class GetPostQuery(dataConnect: FirebaseDataConnect, variables: Variables) :
  QueryRef<GetPostQuery.Variables, GetPostQuery.Result>(
    dataConnect = dataConnect,
    operationName = "getPost",
    operationSet = "crud",
    revision = "1234567890abcdef",
    variables = variables,
  ) {

  data class Variables(val id: String)

  data class Result(val post: Post) {
    data class Post(val content: String, val comments: List<Comment>) {
      data class Comment(val id: String, val content: String)
    }
  }

  override val codec = object : Codec<Variables, Result> {
    override fun encodeVariables(variables: Variables) = TODO()
    override fun decodeResult(map: Map<String, Any?>) = TODO()
  }
}

fun FirebaseDataConnect.query(variables: GetPostQuery.Variables): GetPostQuery =
  GetPostQuery(dataConnect = this, variables = variables)

fun FirebaseDataConnect.Queries.getPost(id: String): GetPostQuery =
  dataConnect.query(GetPostQuery.Variables(id = id))

////////////////////////////////////////////////////////////////////////////////////////////////////
// CUSTOMER CODE
////////////////////////////////////////////////////////////////////////////////////////////////////

fun demo(dataConnect: FirebaseDataConnect) = runBlocking {
  val query = dataConnect.query(GetPostQuery.Variables(id = "abc"))

  println(query.execute())

  val subscribeJob1 = launch { query.subscribe().collect { println("Got result in job 1: $it") } }
  val subscribeJob2 = launch { query.subscribe().collect { println("Got result in job 2: $it") } }

  delay(5000)
  query.reload()
  delay(5000)
  subscribeJob1.cancel()
  subscribeJob2.cancel()
}
