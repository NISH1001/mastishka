package nishparadox.mastishka.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.ZoneId

/**
 * Thin wrapper over Health Connect for writing meditation sessions. All calls degrade
 * gracefully when Health Connect is unavailable or permission hasn't been granted.
 */
@OptIn(ExperimentalMindfulnessSessionApi::class)
class HealthConnectManager(private val context: Context) {

    /** Permissions we need: write our sessions (and read to confirm our own writes). */
    val permissions: Set<String> = setOf(
        HealthPermission.getWritePermission(MindfulnessSessionRecord::class),
        HealthPermission.getReadPermission(MindfulnessSessionRecord::class),
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
                metadata = Metadata.manualEntry(device = Device(type = Device.TYPE_PHONE)),
            )
            c.insertRecords(listOf(record))
            true
        }.getOrDefault(false)
    }
}
