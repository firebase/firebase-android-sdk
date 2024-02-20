package com.google.firebase.dataconnect.apiproposal

import android.app.Activity
import com.google.firebase.FirebaseApp
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
class ConnectorConfig constructor(
  val connector: String,
  val location: String,
  val service: String
)

class DataConnectSetting internal constructor()

enum class LoggerLevel {
  DEBUG, WARN, NONE
}

class FirebaseDataConnect internal constructor() : AutoCloseable {

  class Queries internal constructor() {
    val dataConnect: FirebaseDataConnect
      get() = TODO()
  }
  val queries: Queries = TODO()

  fun useEmulator(host : String,  port : Int) : Unit = TODO()

  override fun close() = TODO()

  override fun toString(): String = TODO()

  companion object {
    fun getInstance(app : FirebaseApp, connectorConfig : ConnectorConfig) : FirebaseDataConnect = TODO()

    // Future Add On
    fun getInstance(app : FirebaseApp, connectorConfig : ConnectorConfig, setting : DataConnectSetting) : FirebaseDataConnect = TODO()

    var logLevel : LoggerLevel
      get() = TODO()
      set(level : LoggerLevel) = TODO()
  }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// GEN SDK INIT
////////////////////////////////////////////////////////////////////////////////////////////////////

class PostConnector internal constructor () {
  companion object {
    fun getConfig(location: String = "default.location", service: String = "default.service"): ConnectorConfig = TODO()
  }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
// Third Party Examples
////////////////////////////////////////////////////////////////////////////////////////////////////

fun thirdPartyApp() {
  FirebaseDataConnect.logLevel = LoggerLevel.DEBUG

  val app = FirebaseApp.getInstance()

  val config = PostConnector.getConfig()
  val configOverrideLocation = PostConnector.getConfig(location = "new.location")
  val configOverrideAllVariables = PostConnector.getConfig(location = "new.location", service = "new.service")

  val dataConnect = FirebaseDataConnect.getInstance(app, config)

  dataConnect.useEmulator("10.0.2.2", 9000)

  val settingFuture = DataConnectSetting()
  val dataConnectFuture = FirebaseDataConnect.getInstance(app, PostConnector.getConfig(), settingFuture)
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// CORE SDK Query
////////////////////////////////////////////////////////////////////////////////////////////////////


fun <VariablesType, DataType> FirebaseDataConnect.query(
  operationName: String,
  operationSet: String,
  revision: String,
  variablesSerializer: SerializationStrategy<VariablesType>,
  dataDeserializer: DeserializationStrategy<DataType>
): QueryRef<VariablesType, DataType> = TODO()

inline fun <reified VariablesType, reified DataType> FirebaseDataConnect.query(
  operationName: String,
  operationSet: String,
  revision: String
): QueryRef<VariablesType, DataType> = TODO()

open class DataConnectException internal constructor() : Exception()

open class NetworkTransportException internal constructor() : DataConnectException()

open class GraphQLException internal constructor() : DataConnectException() {
  val errors: List<String>
    get() = TODO()
}

class DataConnectResult<VariablesType, DataType> private constructor() {
  val variables: VariablesType
    get() = TODO()
  val data: DataType?
    get() = TODO()
  val errors: List<DataConnectError>
    get() = TODO()

  override fun hashCode() = TODO()
  override fun equals(other: Any?) = TODO()
  override fun toString() = TODO()
}

// See https://spec.graphql.org/October2021/#sec-Errors
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
  val lastResult: Message<VariablesType, DataType>?
    get() = TODO()

  // Alternative considered: Return `Deferred<Result<T>>` so that customer knows when the reload
  // completes. For example, suppose a UI has a "Reload" button and when the customer clicks it they
  // get a spinner. The app then awaits the returned "Deferred" object to change the spinner to a
  // check mark or red "X". Note that simply waiting for a result to be delivered to a Flow isn't
  // sufficient because it's not clear that the result was from the specific call to reload() or
  // some previous call to reload() by some other unrelated operation.
  fun reload(): Unit = TODO()

  val flow: Flow<Message<VariablesType, DataType>> = TODO()

  class Message<VariablesType, DataType>(val variables: VariablesType, val data: Result<DataType>)
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// GENERATED SDK
////////////////////////////////////////////////////////////////////////////////////////////////////

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

val FirebaseDataConnect.Queries.getPost: QueryRef<GetPostQuery.Variables, GetPostQuery.Data>
  get() = TODO()

suspend fun QueryRef<GetPostQuery.Variables, GetPostQuery.Data>.execute(
  id: String
): GetPostQuery.Data = TODO()

fun QueryRef<GetPostQuery.Variables, GetPostQuery.Data>.subscribe(
  id: String
): QuerySubscription<GetPostQuery.Variables, GetPostQuery.Data> = TODO()

typealias GetPostQueryRef = QueryRef<GetPostQuery.Variables, GetPostQuery.Data>

typealias GetPostQuerySubscription = QuerySubscription<GetPostQuery.Variables, GetPostQuery.Data>

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
                if (it.data.isSuccess) {
                  showPostContent(it.variables.id, it.data.getOrThrow())
                } else {
                  showError(it.variables.id, it.data.exceptionOrNull()!!)
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
  fun showPostContent(postId: String, post: GetPostQuery.Data?): Unit = TODO()
}
