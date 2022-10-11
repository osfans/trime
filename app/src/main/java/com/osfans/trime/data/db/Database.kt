package com.osfans.trime.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DatabaseBean::class], version = 4)
@TypeConverters(DatabaseBean.Converters::class)
abstract class Database : RoomDatabase() {
    abstract fun databaseDao(): DatabaseDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE ${DatabaseBean.TABLE_NAME} RENAME TO _t_data")
                database.execSQL(
                    """
                    CREATE TABLE ${DatabaseBean.TABLE_NAME} (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        text TEXT,
                        html TEXT,
                        type INTEGER NOT NULL,
                        time INTEGER NOT NULL,
                        pinned INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO ${DatabaseBean.TABLE_NAME} (id, text, html, type, time)
                    SELECT id, text, html, type, time FROM _t_data
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE _t_data")
            }
        }
    }
}
