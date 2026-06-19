package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "monitored_pages")
data class MonitoredPage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val name: String,
    val keyword: String? = null, // keyword to detect, e.g. a specific notification keyword
    val lastContentHash: String? = null,
    val lastText: String? = null,
    val lastChecked: Long = 0L,
    val status: String = "Not Checked Yet",
    val isActive: Boolean = true,
    val frequencyMinutes: Int = 15
)

@Entity(
    tableName = "alert_logs",
    foreignKeys = [
        ForeignKey(
            entity = MonitoredPage::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["pageId"])]
)
data class AlertLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val pageId: Int,
    val pageName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val changeDescription: String
)

@Dao
interface PageDao {
    @Query("SELECT * FROM monitored_pages ORDER BY id DESC")
    fun getAllPagesFlow(): Flow<List<MonitoredPage>>

    @Query("SELECT * FROM monitored_pages")
    suspend fun getAllPages(): List<MonitoredPage>

    @Query("SELECT * FROM monitored_pages WHERE id = :id LIMIT 1")
    suspend fun getPageById(id: Int): MonitoredPage?

    @Query("SELECT * FROM monitored_pages WHERE isActive = 1")
    suspend fun getActivePages(): List<MonitoredPage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: MonitoredPage): Long

    @Update
    suspend fun updatePage(page: MonitoredPage)

    @Delete
    suspend fun deletePage(page: MonitoredPage)

    @Query("DELETE FROM monitored_pages WHERE id = :id")
    suspend fun deletePageById(id: Int)
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alert_logs ORDER BY timestamp DESC")
    fun getAllAlertsFlow(): Flow<List<AlertLog>>

    @Query("SELECT * FROM alert_logs WHERE pageId = :pageId ORDER BY timestamp DESC")
    fun getAlertsForPageFlow(pageId: Int): Flow<List<AlertLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertLog): Long

    @Query("DELETE FROM alert_logs WHERE id = :id")
    suspend fun deleteAlertById(id: Int)

    @Query("DELETE FROM alert_logs WHERE pageId = :pageId")
    suspend fun deleteAlertsForPage(pageId: Int)

    @Query("DELETE FROM alert_logs")
    suspend fun clearAllAlerts()
}
