package com.google.firebase.ai.type

import kotlinx.serialization.Serializable

/**
 * A tool that allows a Gemini model to connect to Google Maps to access and incorporate
 * location-based information into its responses.
 *
 * Important: If using Grounding with Google Maps, you are required to comply with the "Grounding
 * with Google Maps" usage requirements for your chosen API provider: {@link
 * https://ai.google.dev/gemini-api/terms#grounding-with-google-maps | Gemini Developer API} or
 * Vertex AI Gemini API (see {@link https://cloud.google.com/terms/service-terms | Service Terms}
 * section within the Service Specific Terms).
 */
public class GoogleMaps {
  @Serializable internal class Internal()

  internal fun toInternal() = Internal()
}
