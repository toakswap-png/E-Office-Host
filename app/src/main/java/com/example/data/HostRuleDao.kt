package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HostRuleDao {
    @Query("SELECT * FROM host_rules ORDER BY hostname ASC")
    fun getAllRulesFlow(): Flow<List<HostRule>>

    @Query("SELECT * FROM host_rules WHERE isEnabled = 1")
    suspend fun getActiveRules(): List<HostRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: HostRule)

    @Update
    suspend fun updateRule(rule: HostRule)

    @Delete
    suspend fun deleteRule(rule: HostRule)

    @Query("DELETE FROM host_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Int)
}
