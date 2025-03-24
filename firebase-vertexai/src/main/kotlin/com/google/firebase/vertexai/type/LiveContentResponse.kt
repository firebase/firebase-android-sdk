package com.google.firebase.vertexai.type

/* Public class representing the status of the server's response. */
public enum class Status {
  NORMAL,
  INTERRUPTED,
  TURNCOMPLETE
}

/* Class that represents the response from the server. */
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
