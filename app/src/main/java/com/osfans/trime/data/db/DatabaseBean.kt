package com.osfans.trime.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = DatabaseBean.TABLE_NAME)
data class DatabaseBean(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val text: String?,
    val html: String? = null,
    val type: BeanType,
    val time: Long = System.currentTimeMillis()
) {
    companion object {
        const val TABLE_NAME = "t_data"
    }

    enum class BeanType {
        TEXT, HTML;
    }

    class Converters {
        @TypeConverter
        fun BeanType?.toInt(): Int? = this?.ordinal

        @TypeConverter
        fun Int?.toBeanType(): BeanType? = this?.let { BeanType.values()[it] }
    }
}
