package com.google.firebase.firestore.pipeline

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.VectorValue
import com.google.firestore.v1.Value
import java.util.Date

class Constant internal constructor(val value: Any?) : Expr() {

  companion object {
    fun of(value: Any): Constant {
      return when (value) {
        is String -> of(value)
        is Number -> of(value)
        is Date -> of(value)
        is Timestamp -> of(value)
        is Boolean -> of(value)
        is GeoPoint -> of(value)
        is Blob -> of(value)
        is DocumentReference -> of(value)
        is Value -> of(value)
        is Iterable<*> -> of(value)
        is Map<*, *> -> of(value)
        else -> throw IllegalArgumentException("Unknown type: $value")
      }
    }

    fun of(value: String): Constant {
      return Constant(value)
    }

    fun of(value: Number): Constant {
      return Constant(value)
    }

    fun of(value: Date): Constant {
      return Constant(value)
    }

    fun of(value: Timestamp): Constant {
      return Constant(value)
    }

    fun of(value: Boolean): Constant {
      return Constant(value)
    }

    fun of(value: GeoPoint): Constant {
      return Constant(value)
    }

    fun of(value: Blob): Constant {
      return Constant(value)
    }

    fun of(value: DocumentReference): Constant {
      return Constant(value)
    }

    fun of(value: Value): Constant {
      return Constant(value)
    }

    fun of(value: VectorValue): Constant {
      return Constant(value)
    }

    fun nullValue(): Constant {
      return Constant(null)
    }

    fun vector(value: DoubleArray): Constant {
      return of(FieldValue.vector(value))
    }

    fun vector(value: VectorValue): Constant {
      return of(value)
    }
  }
}
