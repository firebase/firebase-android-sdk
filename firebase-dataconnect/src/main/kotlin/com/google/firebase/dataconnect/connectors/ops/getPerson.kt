package com.google.firebase.dataconnect.connectors.ops

import com.google.firebase.dataconnect.*
import kotlinx.serialization.Serializable

object getPerson {

@Serializable data class Variables(val id: String)

}