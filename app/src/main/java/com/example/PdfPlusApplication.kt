package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.pdf.PdfEngine
import com.example.data.prefs.SettingsRepository
import com.example.data.repository.PdfRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PdfPlusApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var pdfEngine: PdfEngine
        private set

    lateinit var pdfRepository: PdfRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "pdf_plus_vault.db"
        ).build()

        pdfEngine = PdfEngine(applicationContext)
        pdfRepository = PdfRepository(applicationContext, database.pdfDocumentDao(), pdfEngine)
        settingsRepository = SettingsRepository(applicationContext)
    }
}
