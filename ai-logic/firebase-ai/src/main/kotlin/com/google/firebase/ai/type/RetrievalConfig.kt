package com.google.firebase.ai.type

import kotlinx.serialization.Serializable

/**
 * The configuration that specifies information which can be used by tools during inference calls.
 *
 * See the static methods in the `companion object` for the list of available behaviors.
 */
public class RetrievalConfig
internal constructor(
  internal val latLng: LatLng? = null,
  internal val languageCode: String? = null
) {

  internal fun toInternal() = Internal(latLng, languageCode)

  /**
   * Builder for creating a [RetrievalConfig].
   *
   * Mainly intended for Java interop. Kotlin consumers should use [retrievalConfig] for a more
   * idiomatic experience.
   *
   * @property latLng See [RetrievalConfig.latLng].
   *
   * @property languageCode See [RetrievalConfig.languageCode].
   */
  public class Builder {
    @JvmField public var latLng: LatLng? = null
    @JvmField public var languageCode: String? = null

    public fun setLatLng(latLng: LatLng?): Builder = apply { this.latLng = latLng }
    public fun setLanguageCode(languageCode: String?): Builder = apply {
      this.languageCode = languageCode
    }

    /** Create a new [RetrievalConfig] with the attached arguments. */
    public fun build(): RetrievalConfig {
      return RetrievalConfig(latLng, languageCode)
    }
  }

  @Serializable
  internal class Internal(val latLng: LatLng? = null, val languageCode: String? = null) {}

  public companion object {
    /**
     * Alternative casing for [RetrievalConfig.Builder]:
     * ```
     * val config = RetrievalConfig.builder()
     * ```
     */
    public fun builder(): Builder = Builder()
  }

  /**
   * Helper method to construct a [RetrievalConfig] in a DSL-like manner.
   *
   * Example Usage:
   * ```
   * retrievalConfig {
   *   latLng = aLatLng
   *   languageCode = "en_US"
   *  }
   * ```
   */
  public fun retrievalConfig(init: RetrievalConfig.Builder.() -> Unit): RetrievalConfig {
    val builder = Builder()
    init(builder)
    return builder.build()
  }
}
