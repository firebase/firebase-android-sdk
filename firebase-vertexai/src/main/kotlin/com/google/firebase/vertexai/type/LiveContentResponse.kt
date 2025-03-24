package com.google.firebase.vertexai.type

public enum class Status {
  NORMAL,
  INTERRUPTED,
  TURNCOMPLETE
}

public class LiveContentResponse
internal constructor(
  public val data: Content?,
  public val status: Status = Status.NORMAL,
  public val functionCalls: List<FunctionCallPart>?
) {
  /**
   * Convenience field representing all the text parts in the response as a single string, if they
   * exists.
   */
  public val text: String? =
    data?.parts?.filterIsInstance<TextPart>()?.joinToString(" ") { it.text }
}
