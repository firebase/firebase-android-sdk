package com.google.firebase.firestore

import com.google.firebase.firestore.encoding.JavaGeoPointSerializer
import com.google.firebase.firestore.util.Util
import kotlinx.serialization.Serializable
import java.lang.Double

/** Immutable class representing a `GeoPoint` in Cloud Firestore  */
@Serializable (with = JavaGeoPointSerializer::class)
class JavaGeoPoint(val latitude: kotlin.Double, val longitude: kotlin.Double) : Comparable<JavaGeoPoint> {
    /** @return The latitude value of this `GeoPoint`.
     */
//    val latitude: kotlin.Double

    /** @return The longitude value of this `GeoPoint`.
     */
//    val longitude: kotlin.Double
    override fun compareTo(other: JavaGeoPoint): Int {
        val comparison = Util.compareDoubles(latitude, other.latitude)
        return if (comparison == 0) {
            Util.compareDoubles(longitude, other.longitude)
        } else {
            comparison
        }
    }

    override fun toString(): String {
        return "GeoPoint { latitude=$latitude, longitude=$longitude }"
    }

    override fun equals(o: Any?): Boolean {
        if (o !is JavaGeoPoint) {
            return false
        }
        val geoPoint = o

        // We can do exact comparison here, because we only expect finite numbers for latitude and
        // longitude
        return latitude == geoPoint.latitude && longitude == geoPoint.longitude
    }

    override fun hashCode(): Int {
        var result: Int
        var temp: Long
        temp = java.lang.Double.doubleToLongBits(latitude)
        result = (temp xor (temp ushr 32)).toInt()
        temp = java.lang.Double.doubleToLongBits(longitude)
        result = 31 * result + (temp xor (temp ushr 32)).toInt()
        return result
    }

    /**
     * Construct a new `GeoPoint` using the provided latitude and longitude values.
     *
     * @param latitude The latitude of this `GeoPoint` in the range [-90, 90].
     * @param longitude The longitude of this `GeoPoint` in the range [-180, 180].
     */
    init {
        require(!(Double.isNaN(latitude) || latitude < -90 || latitude > 90)) { "Latitude must be in the range of [-90, 90]" }
        require(!(Double.isNaN(longitude) || longitude < -180 || longitude > 180)) { "Longitude must be in the range of [-180, 180]" }
//        this.latitude = latitude
//        this.longitude = longitude
    }
}