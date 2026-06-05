package nishparadox.mastishka.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import nishparadox.mastishka.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Selectable gong sounds, each backed by a synthesized clip in res/raw. */
enum class GongType(val key: String, val label: String, val rawResId: Int) {
    SMALL("small", "Small", R.raw.gong_small),
    MEDIUM("medium", "Medium", R.raw.gong_medium),
    LARGE("large", "Large", R.raw.gong_large);

    companion object {
        fun fromKey(key: String?): GongType = entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

// ---------- Entities ----------

/** A person you radiate metta toward; reusable across sits. */
@Entity(tableName = "people")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/** One completed meditation sit. */
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,          // epoch millis when the sit began
    val plannedMillis: Long,      // the duration you set
    val totalMillis: Long,        // actual time sat, including overtime
    val calmness: Int,            // 0..10 positivity / calmness level
    val people: List<String>,     // names you sent metta to this sit
    val notes: String,            // free-form notes / tags (often dictated)
) {
    val overtimeMillis: Long get() = (totalMillis - plannedMillis).coerceAtLeast(0)
}

// ---------- Converters ----------

class Converters {
    @TypeConverter
    fun fromList(value: List<String>): String = value.joinToString("||")

    @TypeConverter
    fun toList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split("||")
}

// ---------- DAOs ----------

@Dao
interface PersonDao {
    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Person>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(person: Person): Long

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<Session>>

    @Insert
    suspend fun insert(session: Session): Long

    @Query("DELETE FROM sessions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

// ---------- Database ----------

@Database(entities = [Person::class, Session::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mastishka.db"
                ).build().also { instance = it }
            }
    }
}

// ---------- Settings (DataStore) ----------

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val keyDurationMin = intPreferencesKey("duration_minutes")
    private val keyVolume = floatPreferencesKey("gong_volume")
    private val keyGongType = stringPreferencesKey("gong_type")
    private val keyDarkTheme = booleanPreferencesKey("dark_theme")

    val durationMinutes: Flow<Int> =
        context.dataStore.data.map { it[keyDurationMin] ?: 5 }

    /** Gong volume scalar, 0f..1f. */
    val gongVolume: Flow<Float> =
        context.dataStore.data.map { it[keyVolume] ?: 0.7f }

    suspend fun setDurationMinutes(minutes: Int) {
        context.dataStore.edit { it[keyDurationMin] = minutes }
    }

    suspend fun setGongVolume(volume: Float) {
        context.dataStore.edit { it[keyVolume] = volume.coerceIn(0f, 1f) }
    }

    /** Selected gong sound; defaults to MEDIUM. */
    val gongType: Flow<GongType> =
        context.dataStore.data.map { GongType.fromKey(it[keyGongType]) }

    suspend fun setGongType(type: GongType) {
        context.dataStore.edit { it[keyGongType] = type.key }
    }

    /** Dark theme on/off; defaults to true (dark). */
    val darkTheme: Flow<Boolean> =
        context.dataStore.data.map { it[keyDarkTheme] ?: true }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[keyDarkTheme] = enabled }
    }
}
