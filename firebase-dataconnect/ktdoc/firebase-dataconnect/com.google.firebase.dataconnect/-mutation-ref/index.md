//[firebase-dataconnect](../../../index.md)/[com.google.firebase.dataconnect](../index.md)/[MutationRef](index.md)

# MutationRef

[androidJvm]\
class [MutationRef](index.md)&lt;[VariablesType](index.md), [ResultType](index.md)&gt;(dataConnect: [FirebaseDataConnect](../-firebase-data-connect/index.md), operationName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), operationSet: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), revision: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), codec: [BaseRef.Codec](../-base-ref/-codec/index.md)&lt;[VariablesType](index.md), [ResultType](index.md)&gt;) : [BaseRef](../-base-ref/index.md)&lt;[VariablesType](index.md), [ResultType](index.md)&gt;

## Constructors

| | |
|---|---|
| [MutationRef](-mutation-ref.md) | [androidJvm]<br>constructor(dataConnect: [FirebaseDataConnect](../-firebase-data-connect/index.md), operationName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), operationSet: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), revision: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), codec: [BaseRef.Codec](../-base-ref/-codec/index.md)&lt;[VariablesType](index.md), [ResultType](index.md)&gt;) |

## Properties

| Name | Summary |
|---|---|
| [dataConnect](../-base-ref/data-connect.md) | [androidJvm]<br>val [dataConnect](../-base-ref/data-connect.md): [FirebaseDataConnect](../-firebase-data-connect/index.md) |

## Functions

| Name | Summary |
|---|---|
| [execute](execute.md) | [androidJvm]<br>open suspend override fun [execute](execute.md)(variables: [VariablesType](index.md)): [ResultType](index.md) |
| [execute](../../com.google.firebase.dataconnect.generated/execute.md) | [androidJvm]<br>suspend fun [MutationRef](index.md)&lt;[CreatePostMutation.Variables](../../com.google.firebase.dataconnect.generated/-create-post-mutation/-variables/index.md), [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)&gt;.[execute](../../com.google.firebase.dataconnect.generated/execute.md)(id: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), content: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) |
