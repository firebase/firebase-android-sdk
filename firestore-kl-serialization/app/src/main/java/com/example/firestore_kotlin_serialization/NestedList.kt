package com.example.firestore_kotlin_serialization

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer


class NestedList(
    val map: MutableMap<Int, MutableList<Any>> = mutableMapOf<Int, MutableList<Any>>(),
    var depth: Int = 0
    ):AbstractEncoder(){

    init {
        map[0] = mutableListOf<Any>()
    }
    override val serializersModule: SerializersModule = EmptySerializersModule

    override fun encodeValue(value: Any){
        map[depth]?.add(value)
        println("result is====----"+ map[depth])
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        when (descriptor.kind){
            StructureKind.CLASS -> {
                println("=======begin structure with depth level ${depth}")
                println("the current map is: ${map}")
                println("the current 0 level is ${map[0]}")
                println("the current 0 level is ${map[0]?.size}")
                depth ++
                map[depth] = mutableListOf<Any>()
                map[depth]?.let { map[depth-1]!!.add(it) }
                return this
            }
            else -> {
                println("this is a permitive type")
                return this
            }
        }
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        map.remove(depth--)
//        depth--
    }
}



fun <T> NencodeToList(serializer: SerializationStrategy<T>, value: T): List<Any> {
    val encoder = NestedList()
    encoder.encodeSerializableValue(serializer, value)
    val result: List<Any> = encoder.map[0]?.get(0) as List<Any>
    return result
}

inline fun <reified T> NencodeToList(value: T) = NencodeToList(serializer(), value)

fun main() {
    @Serializable
    data class PlainProject(val name: String, val ownerName: String)

    val obj = PlainProject("NAME", "OWNER")
    println("the data class is    ${obj}")
    val encodelist1 = NencodeToList<PlainProject>(obj)
    println(encodelist1)
    println("//////////////////////////////////////////////////////////////////")

    @Serializable
    data class Owner(val name: String, val age: String)

    @Serializable
    data class MapInsideOfMapProject(val name: String, val owner: Owner, val title: String)

    val nestedObj = MapInsideOfMapProject("nestedMap", Owner("mayson", "1000"), "Doctor")
    println("the data class is    ${nestedObj}")

    val encodelist2 = NencodeToList<MapInsideOfMapProject>(nestedObj)

    println(encodelist2)
    println("//////////////////////////////////////////////////////////////////")

    @Serializable
    data class MoreMapInsideOfMapProject(val name: String, val owner: Owner, val title: String, val student: Owner)

    val moreNestedObj = MoreMapInsideOfMapProject("nestedMap", Owner("mayson", "1000"), "Doctor",student = Owner("Daddy","3"))
    println("the data class is    ${moreNestedObj}")
    println(NencodeToList<MoreMapInsideOfMapProject>(moreNestedObj))
    println("//////////////////////////////////////////////////////////////////")


    @Serializable
    data class DeeperMapInsideOfMapProject(val name: String, val owner: Owner, val title: String, val student: MoreMapInsideOfMapProject)
    var deeperMapInsideOfMapProject = DeeperMapInsideOfMapProject("deeperName", Owner("daddy", "3"), "SDE", moreNestedObj)

    println("the data class is    ${deeperMapInsideOfMapProject}")
    println(NencodeToList<DeeperMapInsideOfMapProject>(deeperMapInsideOfMapProject))
    println("//////////////////////////////////////////////////////////////////")


}