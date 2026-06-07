package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "host_rules")
data class HostRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hostname: String,
    val ipAddress: String,
    val isEnabled: Boolean = true,
    val description: String = ""
)
