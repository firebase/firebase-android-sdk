//[firebase-dataconnect](../../../../index.md)/[com.google.firebase.dataconnect](../../index.md)/[BaseRef](../index.md)/[Codec](index.md)

# Codec

[androidJvm]\
interface [Codec](index.md)&lt;[VariablesType](index.md), [ResultType](index.md)&gt;

## Functions

| Name | Summary |
|---|---|
| [decodeResult](decode-result.md) | [androidJvm]<br>abstract fun [decodeResult](decode-result.md)(map: [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), [Any](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)?&gt;): [ResultType](index.md) |
| [encodeVariables](encode-variables.md) | [androidJvm]<br>abstract fun [encodeVariables](encode-variables.md)(variables: [VariablesType](index.md)): [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), [Any](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)?&gt; |
