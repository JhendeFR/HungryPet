package com.usfx.hungrypet.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedingLogDao {
    @Insert
    suspend fun insertLog(log: FeedingLog)

    @Query("SELECT * FROM feeding_logs ORDER BY id DESC")
    fun getAllLogs(): Flow<List<FeedingLog>>

    @Delete
    suspend fun deleteLog(log: FeedingLog)
}