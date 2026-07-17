package com.example.appblacklist

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface AppDao {

    @Query("SELECT * FROM app_records ORDER BY appName COLLATE NOCASE ASC")
    fun getAll(): LiveData<List<AppEntity>>

    @Query("SELECT * FROM app_records")
    suspend fun getAllSync(): List<AppEntity>

    @Query("SELECT * FROM app_records WHERE isBlacklisted = 1")
    suspend fun getBlacklistedSync(): List<AppEntity>

    @Query("SELECT * FROM app_records WHERE packageName = :pkg LIMIT 1")
    suspend fun getByPackage(pkg: String): AppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: AppEntity)

    @Update
    suspend fun update(app: AppEntity)

    @Query("UPDATE app_records SET isBlacklisted = :blacklisted WHERE packageName = :pkg")
    suspend fun setBlacklisted(pkg: String, blacklisted: Boolean)

    @Query("UPDATE app_records SET isInstalled = :installed WHERE packageName = :pkg")
    suspend fun setInstalled(pkg: String, installed: Boolean)

    @Query("DELETE FROM app_records WHERE packageName = :pkg")
    suspend fun delete(pkg: String)
}
