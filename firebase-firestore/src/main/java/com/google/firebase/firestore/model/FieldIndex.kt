// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.firestore.model

import com.google.firebase.Timestamp
import java.util.ArrayList
import java.util.Comparator

/**
 * An index definition for field indices in Firestore.
 *
 *
 * Every index is associated with a collection. The definition contains a list of fields and
 * their indexkind (which can be [Segment.Kind.ASCENDING], [Segment.Kind.DESCENDING] or
 * [Segment.Kind.CONTAINS]) for ArrayContains/ArrayContainsAny queries.
 *
 *
 * Unlike the backend, the SDK does not differentiate between collection or collection
 * group-scoped indices. Every index can be used for both single collection and collection group
 * queries.
 */
data class FieldIndex(
    /**
     * The index ID. Returns -1 if the index ID is not available (e.g. the index has not yet been
     * persisted).
     */
    val indexId: Int,

    /** The collection ID this index applies to.  */
    val collectionGroup: String,

    /** Returns all field segments for this index.  */
    val segments: List<Segment>,

    /** Returns how up-to-date the index is for the current user.  */
    val indexState: IndexState

) {
    /** An index component consisting of field path and index type.  */
    data class Segment(
        /** The field path of the component.  */
        val fieldPath: FieldPath,
        /** The indexes sorting order.  */
        val kind: Kind
    ) : Comparable<Segment> {

        /** The type of the index, e.g. for which type of query it can be used.  */
        enum class Kind {
            /** Ordered index. Can be used for <, <=, ==, >=, >, !=, IN and NOT IN queries.  */
            ASCENDING,

            /** Ordered index. Can be used for <, <=, ==, >=, >, !=, IN and NOT IN queries.  */
            DESCENDING,

            /** Contains index. Can be used for ArrayContains and ArrayContainsAny  */
            CONTAINS
        }

        override fun compareTo(other: Segment): Int {
            val cmp = fieldPath.compareTo(other.fieldPath)
            return if (cmp != 0) cmp else kind.compareTo(other.kind)
        }
    }

    /** Stores the "high water mark" that indicates how updated the Index is for the current user.  */
    data class IndexState(
        /**
         * Returns a number that indicates when the index was last updated (relative to other indexes).
         */
        val sequenceNumber: Long,

        /** Returns the latest indexed read time and document.  */
        val offset: IndexOffset
    ) {
        constructor(
            sequenceNumber: Long,
            readTime: SnapshotVersion,
            documentKey: DocumentKey
        ) : this(sequenceNumber, IndexOffset(readTime, documentKey))
    }

    /** Stores the latest read time and document that were processed for an index.  */
    data class IndexOffset(
        /**
         * The latest read time version that has been indexed by Firestore for this field index.
         */
        val readTime: SnapshotVersion,
        /**
         * The last document that has been indexed by Firestore for this field index.
         */
        val documentKey: DocumentKey
    ) : Comparable<IndexOffset> {

        override fun compareTo(other: IndexOffset): Int {
            val cmp = readTime.compareTo(other.readTime)
            return if (cmp != 0) cmp else documentKey.compareTo(other.documentKey)
        }

        companion object {
            /**
             * Creates an offset that matches all documents with a read time higher than {@code readTime}.
             */
            @JvmStatic
            fun create(readTime: SnapshotVersion): IndexOffset {
                // We want to create an offset that matches all documents with a read time greater than
                // the provided read time. To do so, we technically need to create an offset for
                // `(readTime, MAX_DOCUMENT_KEY)`. While we could use Unicode codepoints to generate
                // MAX_DOCUMENT_KEY, it is much easier to use `(readTime + 1, DocumentKey.empty())` since
                // `> DocumentKey.empty()` matches all valid document IDs.
                val successorSeconds = readTime.timestamp.seconds
                val successorNanos = readTime.timestamp.nanoseconds + 1
                val successor = SnapshotVersion(
                    if (successorNanos.toDouble() == 1e9) Timestamp(
                        successorSeconds + 1,
                        0
                    ) else Timestamp(successorSeconds, successorNanos)
                )
                return IndexOffset(successor, DocumentKey.empty())
            }

            @JvmStatic
            fun fromDocument(document: Document): IndexOffset {
                return IndexOffset(document.readTime, document.key)
            }

            @JvmField
            val DOCUMENT_COMPARATOR =
                Comparator<MutableDocument> { l: MutableDocument, r: MutableDocument ->
                    fromDocument(
                        l
                    ).compareTo(fromDocument(r))
                }

            @JvmField
            var NONE: IndexOffset = IndexOffset(SnapshotVersion.NONE, DocumentKey.empty())
        }
    }

    /** Returns all directional (ascending/descending) segments for this index.  */
    val directionalSegments: List<Segment>
        get() {
            val filteredSegments: MutableList<Segment> = ArrayList()
            for (segment in segments) {
                if (segment.kind != Segment.Kind.CONTAINS) {
                    filteredSegments.add(segment)
                }
            }
            return filteredSegments
        }

    /** Returns the ArrayContains/ArrayContainsAny segment for this index.  */
    val arraySegment: Segment?
        get() {
            for (segment in segments) {
                if (segment.kind == Segment.Kind.CONTAINS) {
                    // Firestore queries can only have a single ArrayContains/ArrayContainsAny statements.
                    return segment
                }
            }
            return null
        }

    companion object {

        /** Compares indexes by collection group and segments. Ignores update time and index ID. */
        @JvmField
        var SEMANTIC_COMPARATOR = Comparator { left: FieldIndex, right: FieldIndex ->
            var cmp: Int = left.collectionGroup.compareTo(right.collectionGroup)
            if (cmp != 0) return@Comparator cmp

                val leftIt: Iterator<Segment> =
                    left.segments.iterator()
                val rightIt: Iterator<Segment> =
                    right.segments.iterator()
                while (leftIt.hasNext() && rightIt.hasNext()) {
                     cmp = leftIt.next().compareTo(rightIt.next())
                    if (cmp != 0) return@Comparator cmp
                }
           return@Comparator compareValues(leftIt.hasNext(), rightIt.hasNext())
            }

    /** An ID for an index that has not yet been added to persistence. */
    const val UNKNOWN_ID: Int = -1

        /** The initial sequence number for each index. Gets updated during index backfill.  */
        private const val INITIAL_SEQUENCE_NUMBER: Long = 0

                /** The state of an index that has not yet been backfilled. */
                @JvmField
        var INITIAL_STATE: IndexState =
            IndexState(
                INITIAL_SEQUENCE_NUMBER,
                SnapshotVersion.NONE,
                DocumentKey.empty()
            )
    }
}
