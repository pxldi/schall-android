package io.github.pxldi.schall.domain

import java.time.Duration
import java.time.Instant
import java.util.Locale

fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", value, units[unit])
}

fun formatDuration(ms: Long?): String {
    if (ms == null || ms <= 0) return ""
    val total = ms / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%d:%02d", minutes, seconds)
}

/** "in 5 min", "2 h ago", "in 3 d": how far an instant is from now, in the
 * coarsest unit that still says something. */
fun relative(iso: String?, now: Instant = Instant.now()): String {
    if (iso.isNullOrEmpty()) return ""
    val then = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val minutes = Duration.between(now, then).toMinutes()
    fun say(n: Long, unit: String) = if (n >= 0) "in $n $unit" else "${-n} $unit ago"
    if (kotlin.math.abs(minutes) < 60) return say(minutes, "min")
    val hours = Math.round(minutes / 60.0)
    if (kotlin.math.abs(hours) < 48) return say(hours, "h")
    return say(Math.round(hours / 24.0), "d")
}
