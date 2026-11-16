package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDocumentDao {
    @Query("SELECT * FROM pdf_documents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents ORDER BY lastOpenedAt DESC LIMIT 10")
    fun observeRecent(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PdfDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doc: PdfDocumentEntity): Long

    @Query("UPDATE pdf_documents SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE pdf_documents SET displayName = :newName, filePath = :newPath WHERE id = :id")
    suspend fun rename(id: Long, newName: String, newPath: String)

    @Query("UPDATE pdf_documents SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun touchLastOpened(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE pdf_documents SET fileSizeBytes = :size, pageCount = :pages, lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun updateStats(id: Long, size: Long, pages: Int, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM pdf_documents WHERE id = :id")
    suspend fun deleteById(id: Long)
}
