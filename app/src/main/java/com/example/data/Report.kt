package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "reports")
data class Report(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val inputText: String,
    val serializedItems: String, // format: "name||quantity||price||total&&..."
    val totalSum: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val masterName: String,
    val masterGrade: String,
    val language: String, // "RU" or "UZ"
    val usdRate: Double = 12850.0
)

data class ParsedItem(
    val name: String,
    val quantity: Double,
    val price: Double,
    val total: Double,
    val unit: String = "шт"
)

object ReportSerializer {
    // Encodes a list of ParsedItem to string including unit
    fun encode(items: List<ParsedItem>): String {
        return items.joinToString("&&") { 
            "${it.name}||${it.quantity}||${it.price}||${it.total}||${it.unit}" 
        }
    }

    // Decodes a string to list of ParsedItem with backwards compatibility fallback for unit
    fun decode(encoded: String?): List<ParsedItem> {
        if (encoded.isNullOrBlank()) return emptyList()
        return try {
            encoded.split("&&").mapNotNull { itemStr ->
                val parts = itemStr.split("||")
                if (parts.size >= 4) {
                    val unitVal = if (parts.size >= 5) parts[4] else "шт"
                    ParsedItem(
                        name = parts[0],
                        quantity = parts[1].toDoubleOrNull() ?: 0.0,
                        price = parts[2].toDoubleOrNull() ?: 0.0,
                        total = parts[3].toDoubleOrNull() ?: 0.0,
                        unit = unitVal
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

@Dao
interface ReportDao {
    @Query("SELECT * FROM reports ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<Report>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: Report): Long

    @Query("DELETE FROM reports WHERE id = :id")
    suspend fun deleteReportById(id: Int)

    @Query("SELECT * FROM reports WHERE id = :id LIMIT 1")
    suspend fun getReportById(id: Int): Report?
}

@Database(entities = [Report::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reportDao(): ReportDao
}
