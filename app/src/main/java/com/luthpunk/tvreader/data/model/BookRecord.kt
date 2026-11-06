package com.luthpunk.tvreader.data.model

data class BookmarkRecord(
    val pageIndex: Int,
    val note: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class BookRecord(
    val id: String,
    val filePath: String,
    val uriString: String? = null,
    val title: String,
    var lastPage: Int = 0,
    var pageCount: Int = 1,
    var lastOpenedTimestamp: Long = System.currentTimeMillis(),
    var coverThumbnailPath: String? = null,
    val bookmarks: MutableList<BookmarkRecord> = mutableListOf()
) {
    val progressPercentage: Int
        get() = if (pageCount > 0) (((lastPage + 1).toFloat() / pageCount.toFloat()) * 100).toInt() else 0
}
