package com.google.firebase.firestore.pipeline

import com.google.firebase.firestore.DocumentReference
import com.google.firestore.v1.Pipeline
import com.google.firestore.v1.Value

abstract class Stage(protected val name: String) {
    internal fun toProtoStage(): Pipeline.Stage {
        val builder = Pipeline.Stage.newBuilder()
        builder.setName(name)
        args().forEach { arg -> builder.addArgs(arg) }
        return builder.build();
    }
    protected abstract fun args(): Sequence<Value>
}

class DatabaseSource : Stage("database") {
    override fun args(): Sequence<Value> {
        return emptySequence()
    }
}

class CollectionSource internal constructor(path: String) : Stage("collection") {
    private val path: String = if (path.startsWith("/")) path else "/" + path
    override fun args(): Sequence<Value> {
        return sequenceOf(Value.newBuilder().setReferenceValue(path).build())
    }
}

class CollectionGroupSource internal constructor(val collectionId: String): Stage("collection_group") {
    override fun args(): Sequence<Value> {
        return sequenceOf(
            Value.newBuilder().setReferenceValue("").build(),
            Value.newBuilder().setStringValue(collectionId).build()
        )
    }
}

class DocumentsSource private constructor(private val documents: List<String>) : Stage("documents") {
    internal constructor(documents: Array<out DocumentReference>) : this(documents.map { docRef -> "/" + docRef.path })
    override fun args(): Sequence<Value> {
        return documents.asSequence().map { doc -> Value.newBuilder().setStringValue(doc).build() }
    }
}
