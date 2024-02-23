package com.google.firebase.dataconnect.apiproposal

import android.app.Activity
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

////////////////////////////////////////////////////////////////////////////////////////////////////
// CORE SDK INIT
////////////////////////////////////////////////////////////////////////////////////////////////////
class ConnectorConfig constructor(
  val connector: String,
  val location: String,
  val service: String
)

class DataConnectSettings constructor(
  val host : String = DEFAULT_HOST,
  val sslEnabled : Boolean = true
) {
  override fun equals(other: Any?): Boolean = TODO()

  override fun hashCode() : Int = TODO()

  override fun toString() : String = TODO()

  companion object {
    val DEFAULT_HOST : String = "dataconnect.googleapis.com"
  }
}

enum class LoggerLevel {
  DEBUG, WARN, NONE
}

class FirebaseDataConnect internal constructor() : AutoCloseable {
  fun useEmulator(host : String = "10.0.2.2",  port : Int = 9399) : Unit = TODO()

  override fun close() = TODO()

  override fun toString(): String = TODO()

  companion object {
    fun getInstance(app : FirebaseApp = FirebaseApp.getInstance(),
                    config : ConnectorConfig,
                    settings : DataConnectSettings = DataConnectSettings()) : FirebaseDataConnect = TODO()

    var logLevel : LoggerLevel
      get() = TODO()
      set(level : LoggerLevel) = TODO()
  }
}

abstract class BaseRef<VariablesType, DataType> internal constructor() {
  val dataConnect: FirebaseDataConnect
    get() = TODO()

  abstract suspend fun execute(variables: VariablesType): DataType
}

class QueryRef<VariablesType, DataType> internal constructor() :
  BaseRef<VariablesType, DataType>() {
  override suspend fun execute(variables: VariablesType): DataType = TODO()

  fun subscribe(variables: VariablesType): QuerySubscription<VariablesType, DataType> = TODO()
}

class QuerySubscription<VariablesType, DataType> internal constructor() {
  val query: QueryRef<VariablesType, DataType>
    get() = TODO()
  val variables: VariablesType
    get() = TODO()

  // Alternative considered: add `lastResult`. The problem is, what do we do with this value if the
  // variables are changed via a call to update()? Do we clear it? Or do we leave it there even
  // though it came from a request with potentially-different variables?
  val lastResult: DataConnectResult<VariablesType, DataType>?
    get() = TODO()

  // Alternative considered: Return `Deferred<Result<T>>` so that customer knows when the reload
  // completes. For example, suppose a UI has a "Reload" button and when the customer clicks it they
  // get a spinner. The app then awaits the returned "Deferred" object to change the spinner to a
  // check mark or red "X". Note that simply waiting for a result to be delivered to a Flow isn't
  // sufficient because it's not clear that the result was from the specific call to reload() or
  // some previous call to reload() by some other unrelated operation.
  fun reload(): Unit = TODO()

  val flow: Flow<DataConnectResult<VariablesType, DataType>> = TODO()
}

open class DataConnectException internal constructor() : Exception()

open class NetworkTransportException internal constructor(
  val errorMessage : String
) : DataConnectException()

open class GraphQLException internal constructor(
  val errorMessages : List<String>
) : DataConnectException()

class DataConnectResult<VariablesType, DataType> internal constructor(
  val variables: VariablesType,
  val data: DataType
) {
  override fun hashCode(): Int = TODO()
  override fun equals(other: Any?): Boolean = TODO()
  override fun toString(): String = TODO()
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

class PostConnector internal constructor () {
  val getPost : QueryRef<GetPostQuery.Variables, GetPostQuery.Data> = TODO()
  companion object {
    val CONFIG : ConnectorConfig = TODO()
  }
}

val FirebaseDataConnect.postConnector: PostConnector
  get() = TODO()

suspend fun QueryRef<GetPostQuery.Variables, GetPostQuery.Data>.execute(
  id: String
): GetPostQuery.Data = TODO()

fun QueryRef<GetPostQuery.Variables, GetPostQuery.Data>.subscribe(
  id: String
): QuerySubscription<GetPostQuery.Variables, GetPostQuery.Data> = TODO()

typealias GetPostQueryRef = QueryRef<GetPostQuery.Variables, GetPostQuery.Data>

typealias GetPostQuerySubscription = QuerySubscription<GetPostQuery.Variables, GetPostQuery.Data>

class GetPostQuery private constructor() {

  @Serializable data class Variables(val id: String)

  data class Data(val post: Post?) {
    data class Post(val content: String, val comments: List<Comment>) {
      data class Comment(val id: String, val content: String)
    }
  }

  companion object {
    fun query(dataConnect: FirebaseDataConnect): QueryRef<Variables, Data> = TODO()
  }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// Third Party Examples
////////////////////////////////////////////////////////////////////////////////////////////////////
suspend fun thirdPartyApp() {
  FirebaseDataConnect.logLevel = LoggerLevel.DEBUG

  val app = FirebaseApp.getInstance()

  val config = PostConnector.CONFIG

  val settings = DataConnectSettings(sslEnabled = false)

  val dataConnect = FirebaseDataConnect.getInstance(app, config)
  val dataConnectWithSetting = FirebaseDataConnect.getInstance(app, config, settings)

  dataConnect.useEmulator("10.0.2.2", 9000)

  var getPostRef = dataConnect.postConnector.getPost.execute(id = "id")
  var queryRef12 = dataConnect.postConnector.getPost.execute(GetPostQuery.Variables(id = "id"))
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
        dataConnect.postConnector.getPost.subscribe(id = getIdFromTextView()).also {
          querySubscriptionFlow =
            activityCoroutineScope.launch {
              it.flow.collect {
                showPostContent(it.variables.id, it.data)
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
        showPostContent(id, dataConnect.postConnector.getPost.execute(id = id))
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
  fun showPostContent(postId: String, post: GetPostQuery.Data?): Unit = TODO()
}
