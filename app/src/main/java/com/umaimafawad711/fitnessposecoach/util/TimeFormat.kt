package com.umaimafawad711.fitnessposecoach.util

import java.util.Locale

// Locale.US: default locale could render Arabic-Indic digits on some devices.
fun formatDuration(totalSeconds: Int): String =
    String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)