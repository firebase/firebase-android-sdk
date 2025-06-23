package com.google.firebase.ai.type

import kotlinx.serialization.Serializable

/**
 * A tool that allows the generative model to connect to Google Search to access and incorporate
 * up-to-date information from the web into its responses.
 *
 * When this tool is used, the model's responses may include "Grounded Results" which are subject to
 * the Grounding with Google Search terms outlined in the
 * [Service Specific Terms](https://cloud.google.com/terms/service-terms).
 */
@Serializable public class GoogleSearch {}
