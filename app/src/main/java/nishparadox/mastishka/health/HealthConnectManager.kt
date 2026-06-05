package nishparadox.mastishka.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneId

/**
 * Thin wrapper over Health Connect for writing meditation sessions. All calls degrade
 * gracefully when Health Connect is unavailable or permission hasn't been granted.
 */
@OptIn(ExperimentalMindfulnessSessionApi::class)
class HealthConnectManager(private val context: Context) {

    /** Permissions we need: write our sessions, read them back, and read heart rate. */
    val permissions: Set<String> = setOf(
        HealthPermission.getWritePermission(MindfulnessSessionRecord::class),
        HealthPermission.getReadPermission(MindfulnessSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    private val client: HealthConnectClient? by lazy {
        if (status() == HealthConnectClient.SDK_AVAILABLE) {
            runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
        } else null
    }

    /** One of HealthConnectClient.SDK_AVAILABLE / SDK_UNAVAILABLE / SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED. */
    fun status(): Int = HealthConnectClient.getSdkStatus(context)

    suspend fun hasAllPermissions(): Boolean {
        val c = client ?: return false
        return runCatching {
            c.permissionController.getGrantedPermissions().containsAll(permissions)
        }.getOrDefault(false)
    }

    /** Write one meditation session. Returns true on success. */
    suspend fun writeMeditation(
        startMillis: Long,
        endMillis: Long,
        title: String,
        notes: String,
        clientRecordId: String,
    ): Boolean {
        val c = client ?: return false
        if (!hasAllPermissions()) return false
        return runCatching {
            val zone = ZoneId.systemDefault()
            val start = Instant.ofEpochMilli(startMillis)
            val end = Instant.ofEpochMilli(endMillis)
            val record = MindfulnessSessionRecord(
                startTime = start,
                startZoneOffset = zone.rules.getOffset(start),
                endTime = end,
                endZoneOffset = zone.rules.getOffset(end),
                mindfulnessSessionType = MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_MEDITATION,
                title = title,
                notes = notes.ifBlank { null },
                // Stable clientRecordId → re-syncing updates the same record instead of duplicating.
                metadata = Metadata.manualEntry(
                    device = Device(type = Device.TYPE_PHONE),
                    clientRecordId = clientRecordId,
                ),
            )
            c.insertRecords(listOf(record))
            true
        }.getOrDefault(false)
    }

    /** Read all heart-rate bpm samples (sorted by time) recorded within a window. */
    suspend fun readHeartRate(startMillis: Long, endMillis: Long): List<Long> {
        val c = client ?: return emptyList()
        return runCatching {
            c.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(startMillis),
                        Instant.ofEpochMilli(endMillis),
                    ),
                )
            ).records.flatMap { it.samples }.sortedBy { it.time }.map { it.beatsPerMinute }
        }.getOrDefault(emptyList())
    }
}
