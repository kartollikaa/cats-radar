package dev.catsradar.data.db

import androidx.room3.ColumnTypeConverter
import kotlin.time.Instant

internal object InstantConverters {
    @ColumnTypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilliseconds()

    @ColumnTypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::fromEpochMilliseconds)
}
