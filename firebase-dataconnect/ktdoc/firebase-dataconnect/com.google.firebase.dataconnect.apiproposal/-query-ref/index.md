//[firebase-dataconnect](../../../index.md)/[com.google.firebase.dataconnect.apiproposal](../index.md)/[QueryRef](index.md)

# QueryRef

[androidJvm]\
class [QueryRef](index.md)&lt;[VariablesType](index.md), [ResultType](index.md)&gt;(dataConnect: [FirebaseDataConnect](../-firebase-data-connect/index.md), operationName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), operationSet: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), revision: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), codec: [BaseRef.Codec](../-base-ref/-codec/index.md)&lt;[VariablesType](index.md), [ResultType](index.md)&gt;) : [BaseRef](../-base-ref/index.md)&lt;[VariablesType](index.md), [ResultType](index.md)&gt;

## Constructors

| | |
|---|---|
| [QueryRef](-query-ref.md) | [androidJvm]<br>constructor(dataConnect: [FirebaseDataConnect](../-firebase-data-connect/index.md), operationName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), operationSet: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), revision: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), codec: [BaseRef.Codec](../-base-ref/-codec/index.md)&lt;[VariablesType](index.md), [ResultType](index.md)&gt;) |

## Properties

| Name | Summary |
|---|---|
| [dataConnect](../-base-ref/data-connect.md) | [androidJvm]<br>val [dataConnect](../-base-ref/data-connect.md): [FirebaseDataConnect](../-firebase-data-connect/index.md) |

## Functions

| Name | Summary |
|---|---|
| [execute](execute.md) | [androidJvm]<br>open suspend override fun [execute](execute.md)(variables: [VariablesType](index.md)): [ResultType](index.md) |
| [execute](../execute.md) | [androidJvm]<br>suspend fun [QueryRef](index.md)&lt;[GetPostQuery.Variables](../-get-post-query/-variables/index.md), [GetPostQuery.Result](../-get-post-query/-result/index.md)&gt;.[execute](../execute.md)(id: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [GetPostQuery.Result](../-get-post-query/-result/index.md) |
| [subscribe](subscribe.md) | [androidJvm]<br>fun [subscribe](subscribe.md)(variables: [VariablesType](index.md)): [QuerySubscription](../-query-subscription/index.md)&lt;[VariablesType](index.md), [ResultType](index.md)&gt; |
| [subscribe](../subscribe.md) | [androidJvm]<br>fun [QueryRef](index.md)&lt;[GetPostQuery.Variables](../-get-post-query/-variables/index.md), [GetPostQuery.Result](../-get-post-query/-result/index.md)&gt;.[subscribe](../subscribe.md)(id: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [QuerySubscription](../-query-subscription/index.md)&lt;[GetPostQuery.Variables](../-get-post-query/-variables/index.md), [GetPostQuery.Result](../-get-post-query/-result/index.md)&gt; |
