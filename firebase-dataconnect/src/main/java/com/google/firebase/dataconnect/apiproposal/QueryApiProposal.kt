package com.google.firebase.dataconnect.apiproposal

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

////////////////////////////////////////////////////////////////////////////////////////////////////
// CORE SDK
////////////////////////////////////////////////////////////////////////////////////////////////////

interface FirebaseDataConnect {

  suspend fun executeQuery(
    operationName: String,
    operationSet: String,
    revision: String,
    variables: Map<String, Any?>
  ): Map<String, Any?>

  fun subscribe(
    operationName: String,
    operationSet: String,
    revision: String,
    variables: Map<String, Any?>
  ): QuerySubscription

  // NOTE: This interface and val are only needed if the alternative about adding a
  // getPost() method by the generated SDK below is accepted.
  interface Queries {
    val dataConnect: FirebaseDataConnect
  }
  val queries: Queries
}

interface QuerySubscription {
  fun reload()
  fun subscribe(): Flow<Result<Map<String, Any?>>>
}

// Implemented by the generated SDK for each `query` defined in graphql
// Alternative: Delete this interface altogether, because it's only real value is so that
// customers could have a List<QueryRef<*>> if, say, they wanted to reload() every one.
interface QueryRef<T> {
  // QUESTION: Should the result of execute() be delivered to the listeners that are subscribed?
  // The current implementation does _not_ because it's difficult to know which is the "latest"
  // result from concurrent calls to execute(). One workaround would be to serialize the calls to
  // execute, but that could introduce unacceptable lag for the invocations at the end of the queue.
  suspend fun execute(): T

  fun subscribe(): Flow<Result<T>>

  // Alternative considered: Return `Deferred<Result<T>>` so that customer knows when the reload
  // completes. For example, suppose a UI has a "Reload" button and when the customer clicks it they
  // get a spinner. The app then awaits the returned "Deferred" object to change the spinner to a
  // check mark or red "X". Note that simply waiting for a result to be delivered to a Flow isn't
  // sufficient because it's not clear that the result was from the specific call to reload() or
  // some previous call to reload() by some other unrelated operation.
  fun reload()

  // Alternative considered: add `lastResult`. The problem is, what do we do with this value if the
  // variables are changed? Do we clear it? Or do we leave it there even though it came from a
  // request with different variables?
  val lastResult: Result<T>?
}

// Alternative considered: Make QueryRef and abstract class
abstract class QueryRefAlt<T>(
  // Alternative considered: Make `dataConnect` public for convenience
  private val dataConnect: FirebaseDataConnect,
  private val operationName: String,
  private val operationSet: String,
  private val revision: String,
  variables: Map<String, Any?>
) {

  private val variables = variables.toMap()
  private val subscription: QuerySubscription by lazy {
    dataConnect.subscribe(
      operationName = operationName,
      operationSet = operationSet,
      revision = revision,
      variables = variables
    )
  }

  fun reload() = subscription.reload()

  protected suspend fun execute(): T =
    parseResult(
      dataConnect.executeQuery(
        operationName = operationName,
        operationSet = operationSet,
        revision = revision,
        variables = variables
      )
    )

  fun subscribe(): Flow<Result<T>> = subscription.subscribe().map { it.map(::parseResult) }

  protected abstract fun parseResult(map: Map<String, Any?>): T
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// GENERATED SDK
////////////////////////////////////////////////////////////////////////////////////////////////////

interface GetPostQuery : QueryRef<GetPostQuery.Result> {
  data class Variables(val id: String)

  data class Result(val post: Post) {
    data class Post(val content: String, val comments: List<Comment>) {
      data class Comment(val id: String, val content: String)
    }
  }
}

class GetPostQueryAlt
internal constructor(dataConnect: FirebaseDataConnect, variables: Map<String, Any?>) :
  QueryRefAlt<GetPostQueryAlt.Result>(
    dataConnect = dataConnect,
    operationName = "getPost",
    operationSet = "crud",
    revision = "abc123",
    variables = variables
  ) {
  data class Variables(val id: String)

  data class Result(val post: Post) {
    data class Post(val content: String, val comments: List<Comment>) {
      data class Comment(val id: String, val content: String)
    }
  }

  override fun parseResult(map: Map<String, Any?>): Result = TODO()
}

fun FirebaseDataConnect.query(variables: GetPostQuery.Variables): GetPostQuery = TODO()

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

////////////////////////////////////////////////////////////////////////////////////////////////////
// Alternatives Considered
////////////////////////////////////////////////////////////////////////////////////////////////////

fun FirebaseDataConnect.Queries.getPost(id: String): GetPostQuery = TODO()

suspend fun demoAlt(dataConnect: FirebaseDataConnect) {
  dataConnect.queries.getPost(id = "abc").execute()
}
