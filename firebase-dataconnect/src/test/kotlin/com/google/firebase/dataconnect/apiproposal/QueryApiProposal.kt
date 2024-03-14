package com.google.firebase.dataconnect.apiproposal

import android.app.Activity
import com.google.firebase.FirebaseApp
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy

////////////////////////////////////////////////////////////////////////////////////////////////////
// CORE SDK INIT
////////////////////////////////////////////////////////////////////////////////////////////////////
class ConnectorConfig
constructor(val connector: String, val location: String, val service: String) {

  fun copy(
    connector: String = this.connector,
    location: String = this.location,
    service: String = this.location
  ): ConnectorConfig = TODO()

  override fun equals(other: Any?): Boolean = TODO()

  override fun hashCode(): Int = TODO()

  override fun toString(): String = TODO()
}

class DataConnectSettings
constructor(val host: String = "dataconnect.googleapis.com", val sslEnabled: Boolean = true) {

  fun copy(host: String = this.host, sslEnabled: Boolean = this.sslEnabled): DataConnectSettings =
    TODO()

  override fun equals(other: Any?): Boolean = TODO()

  override fun hashCode(): Int = TODO()

  override fun toString(): String = TODO()
}

enum class LoggerLevel {
  DEBUG,
  WARN,
  NONE
}

class FirebaseDataConnect internal constructor() : AutoCloseable {

  val app: FirebaseApp
    get() = TODO()

  val config: ConnectorConfig
    get() = TODO()

  val settings: DataConnectSettings
    get() = TODO()

  fun useEmulator(host: String = "10.0.2.2", port: Int = 9510): Unit = TODO()

  override fun close() = TODO()

  override fun toString(): String = TODO()

  companion object {
    // Gets the instance associated with the default FirebaseApp and the given
    // config, creating it with the given settings if it does not already exist.
    // If the instance *does* already exist and its settings are *not* equal to the
    // given settings, then an exception is thrown.
    fun getInstance(
      config: ConnectorConfig,
      settings: DataConnectSettings = DataConnectSettings()
    ): FirebaseDataConnect = TODO()

    // Gets the instance associated with the *given* FirebaseApp and the given
    // config, creating it with the given settings if it does not already exist.
    // If the instance *does* already exist and its settings are *not* equal to the
    // given settings, then an exception is thrown.
    fun getInstance(
      app: FirebaseApp,
      config: ConnectorConfig,
      settings: DataConnectSettings = DataConnectSettings()
    ): FirebaseDataConnect = TODO()

    var logLevel: LoggerLevel
      get() = TODO()
      set(level: LoggerLevel) = TODO()
  }
}

fun Firebase.dataConnect(
  config: ConnectorConfig,
  app: FirebaseApp = Firebase.app
): FirebaseDataConnect = TODO()

fun Firebase.dataConnect(
  app: FirebaseApp = Firebase.app,
  config: ConnectorConfig,
  settings: DataConnectSettings
): FirebaseDataConnect = TODO()

abstract class OperationRef<Data, Variables> internal constructor() {
  val dataConnect: FirebaseDataConnect = TODO()

  val operationName: String = TODO()

  val variables: Variables = TODO()

  val responseDeserializer: DeserializationStrategy<Data> = TODO()

  val variablesSerializer: SerializationStrategy<Variables> = TODO()

  abstract suspend fun execute(): DataConnectResult<Data, Variables>
}

class QueryRef<Data, Variables> internal constructor() : OperationRef<Data, Variables>() {
  override suspend fun execute(): DataConnectQueryResult<Data, Variables> = TODO()

  fun subscribe(): QuerySubscription<Data, Variables> = TODO()
}

class QuerySubscription<Data, Variables> internal constructor() {
  val query: QueryRef<Data, Variables>
    get() = TODO()

  // Alternative considered: add `lastResult`. The problem is, what do we do with this value if the
  // variables are changed via a call to update()? Do we clear it? Or do we leave it there even
  // though it came from a request with potentially-different variables?
  val lastResult: DataConnectResult<Data, Variables>
    get() = TODO()

  // Alternative considered: Return `Deferred<Result<T>>` so that customer knows when the reload
  // completes. For example, suppose a UI has a "Reload" button and when the customer clicks it they
  // get a spinner. The app then awaits the returned "Deferred" object to change the spinner to a
  // check mark or red "X". Note that simply waiting for a result to be delivered to a Flow isn't
  // sufficient because it's not clear that the result was from the specific call to reload() or
  // some previous call to reload() by some other unrelated operation.
  fun reload(): Unit = TODO()

  val flow: Flow<QuerySubscriptionResult<Data, Variables>> = TODO()
}

// This extension function on `FirebaseDataConnect` is the mechanism for the
// generated SDK to create instances of `QueryRef`.
fun <Data, Variables> FirebaseDataConnect.query(
  operationName: String,
  responseDeserializer: DeserializationStrategy<Data>,
  variablesSerializer: SerializationStrategy<Variables>
): QueryRef<Data, Variables> = TODO()

open class DataConnectException internal constructor(message: String) : Exception(message)

sealed class DataConnectResult<Data, Variables> {
  val data: Data
    get() = TODO()

  open val ref: OperationRef<Data, Variables>
    get() = TODO()

  override fun hashCode(): Int = TODO()
  override fun equals(other: Any?): Boolean = TODO()
  override fun toString(): String = TODO()
}

class DataConnectQueryResult<Data, Variables> internal constructor() :
  DataConnectResult<Data, Variables>() {
  // Type of `ref` is narrowed from `Reference` to `QueryRef`.
  override val ref: QueryRef<Data, Variables> = TODO()
}

sealed class QuerySubscriptionResult<Data, Variables> protected constructor() {
  val subscription: QuerySubscription<Data, Variables> = TODO()

  override fun hashCode(): Int = TODO()

  override fun equals(other: Any?): Boolean = TODO()

  override fun toString(): String = TODO()

  class Success<Data, Variables> internal constructor() :
    QuerySubscriptionResult<Data, Variables>() {
    val result: DataConnectQueryResult<Data, Variables> = TODO()

    override fun hashCode(): Int = TODO()

    override fun equals(other: Any?): Boolean = TODO()

    override fun toString(): String = TODO()
  }

  class Failure<Data, Variables> internal constructor() :
    QuerySubscriptionResult<Data, Variables>() {
    val exception: DataConnectException = TODO()

    override fun hashCode(): Int = TODO()

    override fun equals(other: Any?): Boolean = TODO()

    override fun toString(): String = TODO()
  }
}

// See https://spec.graphql.org/October2021/#sec-Errors
// Future Add-ons
class DataConnectError private constructor() {
  val message: String
    get() = TODO()
  val path: List<PathSegment>
    get() = TODO()
  val extensions: Map<String, Any?>
    get() = TODO()

  override fun hashCode() = TODO()
  override fun equals(other: Any?) = TODO()
  override fun toString(): String = TODO()

  sealed interface PathSegment {
    @JvmInline value class Field(val field: String) : PathSegment
    @JvmInline value class ListIndex(val index: Int) : PathSegment
  }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// GEN SDK INIT
////////////////////////////////////////////////////////////////////////////////////////////////////

class PostsConnector {
  val dataConnect: FirebaseDataConnect = TODO()
  val getPost: GetPostQuery = TODO()
  companion object {
    val CONFIG: ConnectorConfig = TODO()
  }
}

val FirebaseDataConnect.PostsConnector: PostsConnector
  get() = TODO()

typealias GetPostQueryRef = QueryRef<GetPostQuery.Data, GetPostQuery.Variables>

typealias GetPostQuerySubscription = QuerySubscription<GetPostQuery.Data, GetPostQuery.Variables>

typealias GetPostQueryResult = DataConnectQueryResult<GetPostQuery.Data, GetPostQuery.Variables>

class GetPostQuery internal constructor() {

  @Serializable data class Variables(val id: String)

  data class Data(val post: Post) {
    data class Post(val content: String, val comments: List<Comment>) {
      data class Comment(val id: String, val content: String)
    }
  }
}

suspend fun GetPostQuery.ref(id: String): GetPostQueryRef = TODO()

suspend fun GetPostQuery.ref(variables: GetPostQuery.Variables): GetPostQueryRef = TODO()

suspend fun GetPostQuery.execute(id: String): GetPostQueryResult = TODO()

fun GetPostQuery.subscribe(id: String): GetPostQuerySubscription = TODO()

////////////////////////////////////////////////////////////////////////////////////////////////////
// Third Party Examples
////////////////////////////////////////////////////////////////////////////////////////////////////
suspend fun thirdPartyAppInit() {
  FirebaseDataConnect.logLevel = LoggerLevel.DEBUG

  val app = Firebase.app

  val config = PostsConnector.CONFIG

  val settings = DataConnectSettings(sslEnabled = false)

  val dataConnect = FirebaseDataConnect.getInstance(app, config)
  val dataConnectWithSetting = FirebaseDataConnect.getInstance(app, config, settings)

  dataConnect.useEmulator("10.0.2.2", 9000)

  var ref: GetPostQueryRef =
    dataConnect.PostsConnector.getPost.ref(GetPostQuery.Variables(id = "id"))
  var anotherRef: GetPostQueryRef = dataConnect.PostsConnector.getPost.ref(id = "id")
  var oneTimeFetch: GetPostQueryResult = ref.execute()
  var listerner: GetPostQuerySubscription = ref.subscribe()

  var anotherOneTimeFetch: GetPostQueryResult =
    dataConnect.PostsConnector.getPost.execute(id = "id")
  var anotherListerner: GetPostQuerySubscription =
    dataConnect.PostsConnector.getPost.subscribe(id = "id")
}

suspend fun thirdPartyAppQueryOne() {
  val dataConnect = Firebase.dataConnect(PostsConnector.CONFIG)
  val PostsConnector = dataConnect.PostsConnector

  // NOTE: Code below runs in a coroutine and, therefore, can invoke suspend
  // functions, like QueryRef.execute() and Flow.collect().
  try {
    // One time fetch
    val result1 = PostsConnector.getPost.execute(id = "TestUniqueId1")

    // One more way to execute the query, which supports the reuse/passing of
    // Variables as a group.
    val result2 = PostsConnector.getPost.ref(GetPostQuery.Variables(id = "TestUniqueId2")).execute()

    val postContent = result1.data.post.content
    result1.data.post.comments.forEach { println(it.content) }
  } catch (e: DataConnectException) {
    println("Caught DataConnectException")
  }

  // Realtime update
  val querySubscription: GetPostQuerySubscription =
    PostsConnector.getPost.subscribe(id = "TestUniqueId3")

  // Or, can specify GetPostQuery.Variables as an argument instead of the convenience overload
  // extension function that just takes an "id" argument.
  val querySubscriptionAnother = PostsConnector.getPost.subscribe(id = "id")

  querySubscription.reload()
}

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
        dataConnect.PostsConnector.getPost.subscribe(id = getIdFromTextView()).also { subscriber ->
          querySubscriptionFlow =
            activityCoroutineScope.launch {
              subscriber.flow.collect {
                when (it) {
                  is QuerySubscriptionResult.Success ->
                    showPostContent(subscriber.query.variables.id, it.result)
                  is QuerySubscriptionResult.Failure ->
                    showError(subscriber.query.variables.id, it.exception)
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
        showPostContent(id, dataConnect.PostsConnector.getPost.execute(id = id))
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
  fun showPostContent(postId: String, post: GetPostQueryResult): Unit = TODO()
}
