package com.google.firebase.ai.type

import com.google.firebase.ai.common.util.FirstOrdinalSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** Represents the result of the code execution */
public class Outcome private constructor(public val ordinal: Int) {

  @Serializable(Internal.Serializer::class)
  internal enum class Internal {
    OUTCOME_UNSPECIFIED,
    OUTCOME_OK,
    OUTCOME_FAILED,
    OUTCOME_DEADLINE_EXCEEDED;

    internal object Serializer : KSerializer<Internal> by FirstOrdinalSerializer(Internal::class)

    internal fun toPublic() =
      when (this) {
        OUTCOME_UNSPECIFIED -> Outcome.OUTCOME_UNSPECIFIED
        OUTCOME_OK -> Outcome.OUTCOME_OK
        OUTCOME_FAILED -> Outcome.OUTCOME_FAILED
        OUTCOME_DEADLINE_EXCEEDED -> Outcome.OUTCOME_DEADLINE_EXCEEDED
      }
  }

  internal fun toInternal() =
    when (this) {
      OUTCOME_UNSPECIFIED -> Internal.OUTCOME_UNSPECIFIED
      OUTCOME_OK -> Internal.OUTCOME_OK
      OUTCOME_FAILED -> Internal.OUTCOME_FAILED
      else -> Internal.OUTCOME_DEADLINE_EXCEEDED
    }
  public companion object {

    /** Represents that the code execution outcome is unspecified */
    @JvmField public val OUTCOME_UNSPECIFIED: Outcome = Outcome(0)

    /** Represents that the code execution succeeded */
    @JvmField public val OUTCOME_OK: Outcome = Outcome(1)

    /** Represents that the code execution failed */
    @JvmField public val OUTCOME_FAILED: Outcome = Outcome(2)

    /** Represents that the code execution timed out */
    @JvmField public val OUTCOME_DEADLINE_EXCEEDED: Outcome = Outcome(3)
  }
}
