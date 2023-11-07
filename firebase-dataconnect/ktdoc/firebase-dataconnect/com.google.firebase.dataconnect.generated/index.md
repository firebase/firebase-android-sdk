//[firebase-dataconnect](../../index.md)/[com.google.firebase.dataconnect.generated](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [CreatePostMutation](-create-post-mutation/index.md) | [androidJvm]<br>class [CreatePostMutation](-create-post-mutation/index.md) |
| [GetPostQuery](-get-post-query/index.md) | [androidJvm]<br>class [GetPostQuery](-get-post-query/index.md) |
| [GetPostQuerySubscription](-get-post-query-subscription/index.md) | [androidJvm]<br>typealias [GetPostQuerySubscription](-get-post-query-subscription/index.md) = [QuerySubscription](../com.google.firebase.dataconnect/-query-subscription/index.md)&lt;[GetPostQuery.Variables](-get-post-query/-variables/index.md), [GetPostQuery.Result](-get-post-query/-result/index.md)&gt; |

## Properties

| Name | Summary |
|---|---|
| [createPost](create-post.md) | [androidJvm]<br>val [FirebaseDataConnect.Mutations](../com.google.firebase.dataconnect/-firebase-data-connect/-mutations/index.md).[createPost](create-post.md): [MutationRef](../com.google.firebase.dataconnect/-mutation-ref/index.md)&lt;[CreatePostMutation.Variables](-create-post-mutation/-variables/index.md), [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)&gt; |
| [getPost](get-post.md) | [androidJvm]<br>val [FirebaseDataConnect.Queries](../com.google.firebase.dataconnect/-firebase-data-connect/-queries/index.md).[getPost](get-post.md): [QueryRef](../com.google.firebase.dataconnect/-query-ref/index.md)&lt;[GetPostQuery.Variables](-get-post-query/-variables/index.md), [GetPostQuery.Result](-get-post-query/-result/index.md)&gt; |

## Functions

| Name | Summary |
|---|---|
| [execute](execute.md) | [androidJvm]<br>suspend fun [QueryRef](../com.google.firebase.dataconnect/-query-ref/index.md)&lt;[GetPostQuery.Variables](-get-post-query/-variables/index.md), [GetPostQuery.Result](-get-post-query/-result/index.md)&gt;.[execute](execute.md)(id: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [GetPostQuery.Result](-get-post-query/-result/index.md)<br>suspend fun [MutationRef](../com.google.firebase.dataconnect/-mutation-ref/index.md)&lt;[CreatePostMutation.Variables](-create-post-mutation/-variables/index.md), [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)&gt;.[execute](execute.md)(id: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), content: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) |
| [subscribe](subscribe.md) | [androidJvm]<br>fun [QueryRef](../com.google.firebase.dataconnect/-query-ref/index.md)&lt;[GetPostQuery.Variables](-get-post-query/-variables/index.md), [GetPostQuery.Result](-get-post-query/-result/index.md)&gt;.[subscribe](subscribe.md)(id: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [QuerySubscription](../com.google.firebase.dataconnect/-query-subscription/index.md)&lt;[GetPostQuery.Variables](-get-post-query/-variables/index.md), [GetPostQuery.Result](-get-post-query/-result/index.md)&gt; |
| [update](update.md) | [androidJvm]<br>fun [QuerySubscription](../com.google.firebase.dataconnect/-query-subscription/index.md)&lt;[GetPostQuery.Variables](-get-post-query/-variables/index.md), [GetPostQuery.Result](-get-post-query/-result/index.md)&gt;.[update](update.md)(block: [GetPostQuery.Variables.Builder](-get-post-query/-variables/-builder/index.md).() -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)) |
