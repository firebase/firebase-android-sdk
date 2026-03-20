package com.google.firebase.ai.type

import kotlinx.serialization.Serializable

/**
 * An object that represents a latitude/longitude pair.
 *
 * @param latitude The latitude in degrees. It must be in the range [-90.0, +90.0].
 * @param longitude The longitude in degrees. It must be in the range [-180.0, +180.0].
 */
@Serializable public data class LatLng(public val latitude: Double, public val longitude: Double)
