package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [HostRule::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hostRuleDao(): HostRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "eoffice_hosts_database"
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed default user rules in background thread securely
                        INSTANCE?.let { database ->
                            CoroutineScope(Dispatchers.IO).launch {
                                val dao = database.hostRuleDao()
                                dao.insertRule(
                                    HostRule(
                                        hostname = "districts.upeoffice.gov.in",
                                        ipAddress = "192.168.39.110",
                                        isEnabled = true,
                                        description = "UP District e-Office Intranet Portal"
                                    )
                                )
                                dao.insertRule(
                                    HostRule(
                                        hostname = "eoffsigner.eoffice.gov.in",
                                        ipAddress = "127.0.0.1",
                                        isEnabled = true,
                                        description = "Local e-Office Signing Engine Link"
                                    )
                                )
                            }
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
