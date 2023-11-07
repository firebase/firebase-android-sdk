//[firebase-dataconnect](../../index.md)/[com.google.firebase.dataconnect](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [BaseRef](-base-ref/index.md) | [androidJvm]<br>abstract class [BaseRef](-base-ref/index.md)&lt;[VariablesType](-base-ref/index.md), [ResultType](-base-ref/index.md)&gt; |
| [DataConnectException](-data-connect-exception/index.md) | [androidJvm]<br>open class [DataConnectException](-data-connect-exception/index.md) : [Exception](https://developer.android.com/reference/kotlin/java/lang/Exception.html) |
| [FirebaseDataConnect](-firebase-data-connect/index.md) | [androidJvm]<br>class [FirebaseDataConnect](-firebase-data-connect/index.md) : [Closeable](https://developer.android.com/reference/kotlin/java/io/Closeable.html) |
| [FirebaseDataConnectSettings](-firebase-data-connect-settings/index.md) | [androidJvm]<br>class [FirebaseDataConnectSettings](-firebase-data-connect-settings/index.md) |
| [GraphQLException](-graph-q-l-exception/index.md) | [androidJvm]<br>open class [GraphQLException](-graph-q-l-exception/index.md) : [DataConnectException](-data-connect-exception/index.md) |
| [LogLevel](-log-level/index.md) | [androidJvm]<br>enum [LogLevel](-log-level/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[LogLevel](-log-level/index.md)&gt; |
| [MutationRef](-mutation-ref/index.md) | [androidJvm]<br>class [MutationRef](-mutation-ref/index.md)&lt;[VariablesType](-mutation-ref/index.md), [ResultType](-mutation-ref/index.md)&gt;(dataConnect: [FirebaseDataConnect](-firebase-data-connect/index.md), operationName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), operationSet: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), revision: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), codec: [BaseRef.Codec](-base-ref/-codec/index.md)&lt;[VariablesType](-mutation-ref/index.md), [ResultType](-mutation-ref/index.md)&gt;) : [BaseRef](-base-ref/index.md)&lt;[VariablesType](-mutation-ref/index.md), [ResultType](-mutation-ref/index.md)&gt; |
| [NetworkTransportException](-network-transport-exception/index.md) | [androidJvm]<br>open class [NetworkTransportException](-network-transport-exception/index.md) : [DataConnectException](-data-connect-exception/index.md) |
| [QueryRef](-query-ref/index.md) | [androidJvm]<br>class [QueryRef](-query-ref/index.md)&lt;[VariablesType](-query-ref/index.md), [ResultType](-query-ref/index.md)&gt;(dataConnect: [FirebaseDataConnect](-firebase-data-connect/index.md), operationName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), operationSet: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), revision: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), codec: [BaseRef.Codec](-base-ref/-codec/index.md)&lt;[VariablesType](-query-ref/index.md), [ResultType](-query-ref/index.md)&gt;) : [BaseRef](-base-ref/index.md)&lt;[VariablesType](-query-ref/index.md), [ResultType](-query-ref/index.md)&gt; |
| [QuerySubscription](-query-subscription/index.md) | [androidJvm]<br>class [QuerySubscription](-query-subscription/index.md)&lt;[VariablesType](-query-subscription/index.md), [ResultType](-query-subscription/index.md)&gt; |
| [ResultDecodeException](-result-decode-exception/index.md) | [androidJvm]<br>open class [ResultDecodeException](-result-decode-exception/index.md) : [DataConnectException](-data-connect-exception/index.md) |

## Properties

| Name | Summary |
|---|---|
| [logLevel](log-level.md) | [androidJvm]<br>@[Volatile](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.jvm/-volatile/index.html)<br>var [logLevel](log-level.md): [LogLevel](-log-level/index.md) |
