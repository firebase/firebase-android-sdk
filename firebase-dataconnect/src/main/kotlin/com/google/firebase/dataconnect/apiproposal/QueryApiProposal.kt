package com.google.firebase.dataconnect.apiproposal

import android.app.Activity
import com.google.firebase.FirebaseApp
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
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
) {

    fun copy(
        connector: String = this.connector,
        location: String = this.location,
        service: String = this.location
    )
            : ConnectorConfig = TODO()

    override fun equals(other: Any?): Boolean = TODO()

    override fun hashCode(): Int = TODO()

    override fun toString(): String = TODO()
}

class DataConnectSettings constructor(
    val host: String = "dataconnect.googleapis.com",
    val sslEnabled: Boolean = true
) {

    fun copy(host: String = this.host, sslEnabled: Boolean = this.sslEnabled): DataConnectSettings =
        TODO()

    override fun equals(other: Any?): Boolean = TODO()

    override fun hashCode(): Int = TODO()

    override fun toString(): String = TODO()
}

enum class LoggerLevel {
    DEBUG, WARN, NONE
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
        // Creates an instance with the default settings and caches it, if there is not a
        // cached instance for the given app/config pair.
        fun getInstance(
            app: FirebaseApp = Firebase.app,
            config: ConnectorConfig
        ): FirebaseDataConnect = TODO()

        // Throws if there is a cached instance for the given app/config pair AND
        // that instance has different settings that the provided settings, compared
        // using DataConnectSettings.equals() method. Otherwise, creates and caches
        // the instance if there is no cached instance.
        fun getInstance(
            app: FirebaseApp = Firebase.app,
            config: ConnectorConfig,
            settings: DataConnectSettings
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

abstract class Reference<Response, Variables> internal constructor() {
    val dataConnect: FirebaseDataConnect
        get() = TODO()

    val variables: Variables = TODO()
    abstract suspend fun execute(variables: Variables): DataConnectResult<Response, Variables>
}

class QueryRef<Response, Variables> internal constructor() :
    Reference<Response, Variables>() {
    override suspend fun execute(variables: Variables): DataConnectQueryResult<Response, Variables> =
        TODO()

    fun subscribe(variables: Variables): QuerySubscription<Response, Variables> = TODO()
}

class QuerySubscription<Response, Variables> internal constructor(variables: Variables) {
    val query: QueryRef<Response, Variables>
        get() = TODO()

    // Alternative considered: add `lastResult`. The problem is, what do we do with this value if the
    // variables are changed via a call to update()? Do we clear it? Or do we leave it there even
    // though it came from a request with potentially-different variables?
    val lastResult: DataConnectResult<Response, Variables>
        get() = TODO()

    // Alternative considered: Return `Deferred<Result<T>>` so that customer knows when the reload
    // completes. For example, suppose a UI has a "Reload" button and when the customer clicks it they
    // get a spinner. The app then awaits the returned "Deferred" object to change the spinner to a
    // check mark or red "X". Note that simply waiting for a result to be delivered to a Flow isn't
    // sufficient because it's not clear that the result was from the specific call to reload() or
    // some previous call to reload() by some other unrelated operation.
    fun reload(): Unit = TODO()

    val resultFlow: Flow<DataConnectResult<Response, Variables>> = TODO()
}

open class DataConnectException internal constructor(message: String) : Exception(message)

sealed class DataConnectResult<Response, Variables> {
    val data: Response
        get() = TODO()

    open val ref: Reference<Response, Variables>
        get() = TODO()

    override fun hashCode(): Int = TODO()
    override fun equals(other: Any?): Boolean = TODO()
    override fun toString(): String = TODO()
}

class DataConnectQueryResult<Response, Variables> internal constructor() :
    DataConnectResult<Response, Variables>() {
    override val ref: QueryRef<Response, Variables>
        get() = TODO()
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
        @JvmInline
        value class Field(val field: String) : PathSegment
        @JvmInline
        value class ListIndex(val index: Int) : PathSegment
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// GEN SDK INIT
////////////////////////////////////////////////////////////////////////////////////////////////////

class PostConnector internal constructor() {
    val getPost: QueryRef<GetPostQuery.Response, GetPostQuery.Variables> = TODO()

    companion object {
        val CONFIG: ConnectorConfig = TODO()
    }
}

val FirebaseDataConnect.postConnector: PostConnector
    get() = TODO()

suspend fun QueryRef<GetPostQuery.Response, GetPostQuery.Variables>.execute(
    id: String
): DataConnectQueryResult<GetPostQuery.Response, GetPostQuery.Variables> = TODO()

fun QueryRef<GetPostQuery.Response, GetPostQuery.Variables>.subscribe(
    id: String
): QuerySubscription<GetPostQuery.Response, GetPostQuery.Variables> = TODO()

typealias GetPostQueryRef = QueryRef<GetPostQuery.Response, GetPostQuery.Variables>

typealias GetPostQuerySubscription = QuerySubscription<GetPostQuery.Response, GetPostQuery.Variables>

class GetPostQuery private constructor() {

    @Serializable
    data class Variables(val id: String)

    data class Response(val post: Post) {
        data class Post(val content: String, val comments: List<Comment>) {
            data class Comment(val id: String, val content: String)
        }
    }

    companion object {
        fun query(dataConnect: FirebaseDataConnect): QueryRef<Response, Variables> = TODO()
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// Third Party Examples
////////////////////////////////////////////////////////////////////////////////////////////////////
suspend fun thirdPartyAppInit() {
    FirebaseDataConnect.logLevel = LoggerLevel.DEBUG

    val app = Firebase.app

    val config = PostConnector.CONFIG

    val settings = DataConnectSettings(sslEnabled = false)

    val dataConnect = FirebaseDataConnect.getInstance(app, config)
    val dataConnectWithSetting = FirebaseDataConnect.getInstance(app, config, settings)

    dataConnect.useEmulator("10.0.2.2", 9000)

    var getPostRef = dataConnect.postConnector.getPost.execute(id = "id")
    var queryRef12 = dataConnect.postConnector.getPost.execute(GetPostQuery.Variables(id = "id"))
}

suspend fun thirdPartyAppQueryOne() {
    val dataConnect = Firebase.dataConnect(PostConnector.CONFIG)
    val postConnector = dataConnect.postConnector

// NOTE: Code below runs in a coroutine and, therefore, can invoke suspend
// functions, like QueryRef.execute() and Flow.collect().
    try {
        // One time fetch
        val result1 = postConnector.getPost.execute(id = "TestUniqueId1")

        // One more way to execute the query, which supports the reuse/passing of
        // Variables as a group.
        val result2 = postConnector.getPost.execute(
            GetPostQuery.Variables(id = "TestUniqueId2")
        )

        val postContent = result1.data.post.content
        result1.data.post.comments.forEach {
            println(it.content)
        }
    } catch (e: DataConnectException) {
        println("Caught DataConnectException")
    }


// Realtime update
    val querySubscription: GetPostQuerySubscription =
        postConnector.getPost.subscribe(id = "TestUniqueId3")

// Or, can specify GetPostQuery.Variables as an argument instead of the convenience overload extension function that just takes an "id" argument.
    val querySubscriptionAnother = postConnector.getPost.subscribe(
        GetPostQuery.Variables(id = "id")
    )

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
                dataConnect.postConnector.getPost.subscribe(id = getIdFromTextView()).also { subscriber ->
                    querySubscriptionFlow =
                        activityCoroutineScope.launch {
                            subscriber.resultFlow.collect {
                                showPostContent(subscriber.query.variables.id, it.data)
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
                showPostContent(id, dataConnect.postConnector.getPost.execute(id = id).data)
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
    fun showPostContent(postId: String, post: GetPostQuery.Response?): Unit = TODO()
}
