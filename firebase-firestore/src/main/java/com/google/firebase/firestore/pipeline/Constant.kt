package com.google.firebase.firestore.pipeline

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.VectorValue
import com.google.firebase.firestore.model.Values
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firestore.v1.Value
import java.util.Date

class Constant internal constructor(val value: Value) : Expr() {

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
        else -> throw IllegalArgumentException("Unknown type: $value")
      }
    }

    @JvmStatic
    fun of(value: String): Constant {
      return Constant(encodeValue(value))
    }

    @JvmStatic
    fun of(value: Number): Constant {
      return Constant(encodeValue(value))
    }

    @JvmStatic
    fun of(value: Date): Constant {
      return Constant(encodeValue(value))
    }

    @JvmStatic
    fun of(value: Timestamp): Constant {
      return Constant(encodeValue(value))
    }

    @JvmStatic
    fun of(value: Boolean): Constant {
      return Constant(encodeValue(value))
    }

    @JvmStatic
    fun of(value: GeoPoint): Constant {
      return Constant(encodeValue(value))
    }

    @JvmStatic
    fun of(value: Blob): Constant {
      return Constant(encodeValue(value))
    }

    @JvmStatic
    fun of(value: DocumentReference): Constant {
      return Constant(encodeValue(value))
    }

    @JvmStatic
    fun of(value: VectorValue): Constant {
      return Constant(encodeValue(value))
    }

    @JvmStatic
    fun nullValue(): Constant {
      return Constant(Values.NULL_VALUE)
    }

    @JvmStatic
    fun vector(value: DoubleArray): Constant {
      return Constant(Values.encodeVectorValue(value))
    }

    @JvmStatic
    fun vector(value: VectorValue): Constant {
      return Constant(encodeValue(value))
    }
  }

  override fun toProto(): Value {
    return value
  }
}
