package com.mohdshayan.rainfold.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** A saved mix. Layers live in [MixLayer]; the order on the Mixes screen is [sortOrder]. */
@Entity(tableName = "mixes")
data class Mix(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int,
    val evolving: Boolean = false,
    val driftRange: Float = 0.3f,
    val createdAt: Long,
    val updatedAt: Long,
    val lastPlayedAt: Long? = null,
)

/** One layer of a saved mix. [engine] is an EngineKind id such as BROWN or RAIN. */
@Entity(
    tableName = "mix_layers",
    foreignKeys = [ForeignKey(entity = Mix::class, parentColumns = ["id"], childColumns = ["mixId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("mixId")],
)
data class MixLayer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mixId: Long,
    val position: Int,
    val engine: String,
    val volume: Float,
    val tone: Float,
    val intensity: Float,
)

data class MixWithLayers(
    @Embedded val mix: Mix,
    @Relation(parentColumn = "id", entityColumn = "mixId") val layers: List<MixLayer>,
)

@Dao
interface MixDao {
    @Transaction
    @Query("SELECT * FROM mixes ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<MixWithLayers>>

    @Transaction
    @Query("SELECT * FROM mixes ORDER BY sortOrder, id")
    suspend fun all(): List<MixWithLayers>

    @Transaction
    @Query("SELECT * FROM mixes WHERE id = :id")
    suspend fun byId(id: Long): MixWithLayers?

    @Query("SELECT COUNT(*) FROM mixes")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM mixes")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insertMix(mix: Mix): Long

    @Insert
    suspend fun insertLayers(layers: List<MixLayer>)

    @Query("DELETE FROM mix_layers WHERE mixId = :mixId")
    suspend fun deleteLayers(mixId: Long)

    @Query("UPDATE mixes SET name = :name, evolving = :evolving, driftRange = :driftRange, updatedAt = :now WHERE id = :id")
    suspend fun updateMix(id: Long, name: String, evolving: Boolean, driftRange: Float, now: Long)

    @Query("UPDATE mixes SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE mixes SET sortOrder = :order WHERE id = :id")
    suspend fun setOrder(id: Long, order: Int)

    @Query("UPDATE mixes SET lastPlayedAt = :now WHERE id = :id")
    suspend fun markPlayed(id: Long, now: Long)

    @Query("DELETE FROM mixes WHERE id = :id")
    suspend fun delete(id: Long)
}

/**
 * Schema export is off because version 1 has nothing to migrate from; turn it on and write a
 * Migration before shipping version 2.
 */
@Database(entities = [Mix::class, MixLayer::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mixDao(): MixDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rainfold.db",
                ).build().also { instance = it }
            }
    }
}
