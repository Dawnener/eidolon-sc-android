package sc.eidolon.nativeapp.core

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** 后端的时间是带 +00:00 的 ISO 字符串；这里统一转成 Instant，展示时用设备时区。 */
object Time {
    fun parseInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(value).toInstant() }
            .recoverCatching { Instant.parse(value) }
            .getOrNull()
    }

    fun zone(zoneId: String? = ZoneId.systemDefault().id): ZoneId =
        runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.of("Asia/Shanghai"))

    fun localDate(instant: Instant, zone: ZoneId): LocalDate =
        instant.atZone(zone).toLocalDate()

    fun formatClock(instant: Instant, zone: ZoneId): String =
        instant.atZone(zone).format(DateTimeFormatter.ofPattern("H:mm"))

    fun formatListTime(instant: Instant, now: Instant, zone: ZoneId): String {
        val date = localDate(instant, zone)
        val today = localDate(now, zone)
        return when {
            date == today -> formatClock(instant, zone)
            date == today.minusDays(1) -> "昨天"
            date.year == today.year -> "${date.monthValue}月${date.dayOfMonth}日"
            else -> "${date.year}/${date.monthValue}/${date.dayOfMonth}"
        }
    }

    fun formatDaySeparator(instant: Instant, now: Instant, zone: ZoneId): String {
        val date = localDate(instant, zone)
        val today = localDate(now, zone)
        return when {
            date == today -> "今天"
            date == today.minusDays(1) -> "昨天"
            date.year == today.year -> "${date.monthValue}月${date.dayOfMonth}日"
            else -> "${date.year}/${date.monthValue}/${date.dayOfMonth}"
        }
    }

    fun formatMemoryDate(instant: Instant, zone: ZoneId): String =
        runCatching {
            ZonedDateTime.ofInstant(instant, zone)
                .format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
        }.getOrDefault("")

    fun isSameDay(a: Instant, b: Instant, zone: ZoneId): Boolean =
        localDate(a, zone) == localDate(b, zone)
}
