package com.google.firebase.perf.session

import com.google.firebase.perf.util.Constants
import java.util.UUID

/**
 * Identifies whether the [PerfSession] is an AQS or not.
 */
fun PerfSession.isAQS(): Boolean {
    return !this.sessionId().startsWith(Constants.UNDEFINED_AQS_ID_PREFIX)
}

@JvmOverloads fun createSessionId(aqsId: String = Constants.UNDEFINED_AQS_ID_PREFIX): String {
if (aqsId == Constants.UNDEFINED_AQS_ID_PREFIX) {
    val uuid = UUID.randomUUID().toString().replace("-", "");
    return uuid.replaceRange(0, aqsId.length, aqsId)
}

return aqsId;
}
