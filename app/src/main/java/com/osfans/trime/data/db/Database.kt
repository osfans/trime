package com.osfans.trime.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [DatabaseBean::class], version = 4)
@TypeConverters(DatabaseBean.Converters::class)
abstract class Database : RoomDatabase() {
    abstract fun databaseDao(): DatabaseDao
}
