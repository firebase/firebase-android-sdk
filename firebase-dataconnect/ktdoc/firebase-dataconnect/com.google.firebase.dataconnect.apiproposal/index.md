//[firebase-dataconnect](../../index.md)/[com.google.firebase.dataconnect.apiproposal](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [BaseRef](-base-ref/index.md) | [androidJvm]<br>abstract class [BaseRef](-base-ref/index.md)&lt;[VariablesType](-base-ref/index.md), [ResultType](-base-ref/index.md)&gt; |
| [DataConnectException](-data-connect-exception/index.md) | [androidJvm]<br>open class [DataConnectException](-data-connect-exception/index.md) : [Exception](https://developer.android.com/reference/kotlin/java/lang/Exception.html) |
| [ExecutionException](-execution-exception/index.md) | [androidJvm]<br>open class [ExecutionException](-execution-exception/index.md) : [DataConnectException](-data-connect-exception/index.md) |
| [FirebaseDataConnect](-firebase-data-connect/index.md) | [androidJvm]<br>class [FirebaseDataConnect](-firebase-data-connect/index.md) |
| [GetPostQuery](-get-post-query/index.md) | [androidJvm]<br>class [GetPostQuery](-get-post-query/index.md) |
| [GetPostQueryRef](-get-post-query-ref/index.md) | [androidJvm]<br>typealias [GetPostQueryRef](-get-post-query-ref/index.md) = [QueryRef](-query-ref/index.md)&lt;[GetPostQuery.Variables](-get-post-query/-variables/index.md), [GetPostQuery.Result](-get-post-query/-result/index.md)&gt; |
| [GetPostQuerySubscription](-get-post-query-subscription/index.md) | [androidJvm]<br>typealias [GetPostQuerySubscription](-get-post-query-subscription/index.md) = [QuerySubscription](-query-subscription/index.md)&lt;[GetPostQuery.Variables](-get-post-query/-variables/index.md), [GetPostQuery.Result](-get-post-query/-result/index.md)&gt; |
| [NetworkTransportException](-network-transport-exception/index.md) | [androidJvm]<br>open class [NetworkTransportException](-network-transport-exception/index.md) : [DataConnectException](-data-connect-exception/index.md) |
| [QueryRef](-query-ref/index.md) | [androidJvm]<br>class [QueryRef](-query-ref/index.md)&lt;[VariablesType](-query-ref/index.md), [ResultType](-query-ref/index.md)&gt;(dataConnect: [FirebaseDataConnect](-firebase-data-connect/index.md), operationName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), operationSet: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), revision: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), codec: [BaseRef.Codec](-base-ref/-codec/index.md)&lt;[VariablesType](-query-ref/index.md), [ResultType](-query-ref/index.md)&gt;) : [BaseRef](-base-ref/index.md)&lt;[VariablesType](-query-ref/index.md), [ResultType](-query-ref/index.md)&gt; |
| [QuerySubscription](-query-subscription/index.md) | [androidJvm]<br>class [QuerySubscription](-query-subscription/index.md)&lt;[VariablesType](-query-subscription/index.md), [ResultType](-query-subscription/index.md)&gt; |
| [ResultDecodeException](-result-decode-exception/index.md) | [androidJvm]<br>open class [ResultDecodeException](-result-decode-exception/index.md) : [DataConnectException](-data-connect-exception/index.md) |

## Properties

| Name | Summary |
|---|---|
| [getPost](get-post.md) | [androidJvm]<br>val [FirebaseDataConnect.Queries](-firebase-data-connect/-queries/index.md).[getPost](get-post.md): [QueryRef](-query-ref/index.md)&lt;[GetPostQuery.Variables](-get-post-query/-variables/index.md), [GetPostQuery.Result](-get-post-query/-result/index.md)&gt; |

## Functions

| Name | Summary |
|---|---|
| [execute](execute.md) | [androidJvm]<br>suspend fun [QueryRef](-query-ref/index.md)&lt;[GetPostQuery.Variables](-get-post-query/-variables/index.md), [GetPostQuery.Result](-get-post-query/-result/index.md)&gt;.[execute](execute.md)(id: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [GetPostQuery.Result](-get-post-query/-result/index.md) |
| [subscribe](subscribe.md) | [androidJvm]<br>fun [QueryRef](-query-ref/index.md)&lt;[GetPostQuery.Variables](-get-post-query/-variables/index.md), [GetPostQuery.Result](-get-post-query/-result/index.md)&gt;.[subscribe](subscribe.md)(id: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [QuerySubscription](-query-subscription/index.md)&lt;[GetPostQuery.Variables](-get-post-query/-variables/index.md), [GetPostQuery.Result](-get-post-query/-result/index.md)&gt; |
