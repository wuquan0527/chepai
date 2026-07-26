package com.vzlpr.controller.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** 白名单车辆 */
@Entity(tableName = "whitelist")
data class WhitelistEntity(
    @PrimaryKey val plate: String,      // 车牌号（主键，天然去重）
    val owner: String = "",             // 车主/备注
    val enabled: Boolean = true,        // 是否启用
    val expireAt: Long = 0L,            // 过期时间(毫秒)，0=永久
    val createdAt: Long = System.currentTimeMillis()
)

/** 通行记录 */
@Entity(tableName = "pass_record")
data class PassRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plate: String,
    val colorName: String,
    val confidence: Int,
    val deviceIp: String,
    val allowed: Boolean,               // 是否放行
    val time: Long,
    val imagePath: String? = null       // 落盘的大图路径
)

@Dao
interface VzDao {
    // ---- 白名单 ----
    @Query("SELECT * FROM whitelist ORDER BY createdAt DESC")
    fun observeWhitelist(): Flow<List<WhitelistEntity>>

    @Query("SELECT * FROM whitelist WHERE plate = :plate LIMIT 1")
    suspend fun findPlate(plate: String): WhitelistEntity?

    @Query("SELECT * FROM whitelist")
    suspend fun getAllWhitelist(): List<WhitelistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WhitelistEntity)

    @Query("DELETE FROM whitelist WHERE plate = :plate")
    suspend fun deletePlate(plate: String)

    @Query("SELECT COUNT(*) FROM whitelist")
    suspend fun whitelistCount(): Int

    // ---- 通行记录 ----
    @Query("SELECT * FROM pass_record ORDER BY time DESC LIMIT 500")
    fun observeRecords(): Flow<List<PassRecordEntity>>

    @Insert
    suspend fun insertRecord(record: PassRecordEntity)

    @Query("DELETE FROM pass_record")
    suspend fun clearRecords()
}

@Database(
    entities = [WhitelistEntity::class, PassRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): VzDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vzlpr.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
