package com.example.firestore_kotlin_serialization

import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.capturedKClass
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.internal.TaggedEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.encodeToString
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.encodeToMap
import kotlin.jvm.internal.Intrinsics
import kotlin.reflect.typeOf

class MyEncoder {

}

class MapEncoder(
    var key_value_list: List<String> = emptyList(),
    var elementIndex:Int = 0,
    var outermap: MutableMap<String, Any> = mutableMapOf<String, Any>()
): AbstractEncoder(){

    var map: MutableMap<String, Any> = mutableMapOf<String, Any>()

    override val serializersModule: SerializersModule = EmptySerializersModule

    // update the key_value_list at the beginning of each epoch inside of beginStructure() method
    override fun encodeValue(value: Any){
        println("--------///===///===///== ${key_value_list}")
        println("--------///===///===///== ${elementIndex}")
        map.put(key_value_list.get(elementIndex++), value)
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        val my_key_value_list = descriptor.elementNames.toList()
        println("///===///===///== ${my_key_value_list}")
        when(descriptor.kind){
            StructureKind.CLASS -> println("this is a class")
            else -> println("this is a permitive type")
        }
        return MapEncoder(my_key_value_list)

    }
}



fun <T> encodeToMap(serializer: SerializationStrategy<T>, value: T): Map<String, Any> {
    val encoder = MapEncoder()
    encoder.encodeSerializableValue(serializer, value)
    return encoder.map
}

inline fun <reified T> encodeToMap(value: T): Map<String, Any> = encodeToMap(serializer(), value)



class ListEncoder: AbstractEncoder(){
    val list = mutableListOf<Any>()
    var key_value_test = "key_value_test"

    override val serializersModule: SerializersModule = EmptySerializersModule

    override fun encodeValue(value: Any){
        println("==========  begin encodeValue ========== value is ${value}=======")
        list.add(value)
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        println("==========begin structure====================")
        println(descriptor.kind) // if this is CLASS, OR MAP, we will need another map, or this can be a LIST
        println(descriptor)
        println(descriptor.annotations)
        println(descriptor.elementNames.toList())
        println(descriptor.getElementName(0)) // this can be used to get name from descriptor
        println("-----------------------------")
        return this
    }
}

fun <T> encodeToList(serializer: SerializationStrategy<T>, value: T): List<Any> {
    val encoder = ListEncoder()
    encoder.encodeSerializableValue(serializer, value)
    return encoder.list
}

inline fun <reified T> encodeToList(value: T) = encodeToList(serializer(), value)


class ListDecoder(val list: ArrayDeque<Any>) : AbstractDecoder() {
    private var elementIndex = 0

    override val serializersModule: SerializersModule = EmptySerializersModule

    override fun decodeValue(): Any = list.removeFirst()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == descriptor.elementsCount) return CompositeDecoder.DECODE_DONE
        return elementIndex++
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        ListDecoder(list)
}

fun <T> decodeFromList(list: List<Any>, deserializer: DeserializationStrategy<T>): T {
    val decoder = ListDecoder(ArrayDeque(list))
    return decoder.decodeSerializableValue(deserializer)
}

inline fun <reified T> decodeFromList(list: List<Any>): T = decodeFromList(list, serializer())


fun main(){
    println("Hi, Encoder")

    @Serializable
    data class PlainProject(val name: String, val ownerName: String)

    //TODO: ENCODE this object to a map

    ///////////////////////////////////////////////////////////////////////////////
    val obj = PlainProject("NAME", "OWNER")
    val encodelist1 = encodeToList<PlainProject>(obj)
    val decoderesult1 = decodeFromList<PlainProject>(encodelist1)
    println(encodelist1)
    println(decoderesult1)

    val map1:Map<String,Any> = encodeToMap(obj)
    println("The first plain map is:" + map1)
    println("//////////////////////////////////////////////////////////////////")

//    val l = listOf<Int>(1,2,3,4,5)
//    println(encodeToList(l))

    @Serializable
    data class Owner(val name: String)

    @Serializable
    data class MapInsideOfMapProject(val name: String, val owner: Owner)

    val nestedObj = MapInsideOfMapProject("nestedMap", Owner("mayson"))
    val encodelist2 = encodeToList<MapInsideOfMapProject>(nestedObj)
    val decoderesult2 = decodeFromList<MapInsideOfMapProject>(encodelist2)

    println(encodelist2)
    println(decoderesult2)

    val map2:Map<String,Any> = encodeToMap(nestedObj)
    println("The second nested map is "+map2)
    println("//////////////////////////////////////////////////////////////////")

    @Serializable
    data class ListInsideOfMapProject(val name: String, val listOfOwner: List<Owner>)

    val listOfOwner = listOf<Owner>(Owner("a"), Owner("b"),Owner("c"))

    val listInsideMapObj = ListInsideOfMapProject("listMap", listOfOwner)

    val encodelist3 = encodeToList<ListInsideOfMapProject>(listInsideMapObj)
    val decoderesult3 = decodeFromList<ListInsideOfMapProject>(encodelist3)

    println(encodelist3)
    println(decoderesult3)
    println("//////////////////////////////////////////////////////////////////")

    val json = Json { allowStructuredMapKeys=true }
    val result = Json.encodeToString(listInsideMapObj)
    val result2 = Json.encodeToJsonElement(listInsideMapObj)
    val result3 = Properties.encodeToMap(listInsideMapObj)
    println(result)
    println(result2)
    println(result3)


}