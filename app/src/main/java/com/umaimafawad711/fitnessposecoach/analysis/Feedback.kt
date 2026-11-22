package com.umaimafawad711.fitnessposecoach.analysis

import com.umaimafawad711.fitnessposecoach.pose.BodyPart

enum class Severity { INFO, WARNING, ERROR }

data class Feedback(
    val message: String,
    val severity: Severity,
    /** Joints that `OverlayView` paints red. */
    val faultyParts: Set<BodyPart> = emptySet()
)