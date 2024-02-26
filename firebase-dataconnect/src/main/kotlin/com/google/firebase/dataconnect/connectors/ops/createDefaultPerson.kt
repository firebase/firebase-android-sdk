package com.google.firebase.dataconnect.connectors.ops

import com.google.firebase.dataconnect.*
import kotlinx.serialization.Serializable

@Serializable data class createDefaultPersonData(val person_insert: MutationRef.InsertData)
