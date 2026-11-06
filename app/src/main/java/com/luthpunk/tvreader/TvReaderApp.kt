package com.luthpunk.tvreader

import android.app.Application
import com.luthpunk.tvreader.data.storage.LibraryRepository

class TvReaderApp : Application() {
    lateinit var repository: LibraryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = LibraryRepository(this)
    }
}
