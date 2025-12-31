package com.godtap.dictionary.downloader

import android.content.Context
import android.util.Log
import com.godtap.dictionary.database.AppDatabase
import com.godtap.dictionary.parser.JMdictParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Manages dictionary download and import for Yomichan JMdict format
 */
class DictionaryDownloader(private val context: Context) {
    
    private val httpClient = OkHttpClient()
    private val parser = JMdictParser()
    
    companion object {
        private const val TAG = "DictionaryDownloader"
        
        // Yomichan JMdict from yomidevs
        private const val JMDICT_URL = "https://github.com/yomidevs/jmdict-yomitan/releases/latest/download/JMdict_english_with_examples.zip"
        
        // Local cache
        private const val CACHE_FILE = "jmdict.zip"
        private const val EXTRACTED_DIR = "jmdict_extracted"
        
        // Preferences
        private const val PREF_NAME = "dictionary_prefs"
        private const val KEY_DICTIONARY_VERSION = "dictionary_version"
        private const val KEY_DICTIONARY_IMPORTED = "dictionary_imported"
        private const val CURRENT_VERSION = "yomichan_latest"
    }
    
    fun isDictionaryImported(): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val imported = prefs.getBoolean(KEY_DICTIONARY_IMPORTED, false)
        val version = prefs.getString(KEY_DICTIONARY_VERSION, "")
        return imported && version == CURRENT_VERSION
    }
    
    interface DownloadProgressListener {
        fun onProgress(bytesDownloaded: Long, totalBytes: Long, stage: String)
        fun onComplete()
        fun onError(error: Exception)
    }
    
    suspend fun downloadAndImport(
        listener: DownloadProgressListener? = null
    ) = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting Yomichan JMdict download")
            
            listener?.onProgress(0, 100, "Downloading dictionary...")
            val downloadedFile = downloadDictionary { bytes, total ->
                listener?.onProgress(bytes, total, "Downloading...")
            }
            
            listener?.onProgress(0, 100, "Extracting...")
            val extractedDir = extractZipFile(downloadedFile)
            
            listener?.onProgress(0, 100, "Importing entries...")
            importYomichanDictionary(extractedDir) { current, total ->
                listener?.onProgress(current.toLong(), total.toLong(), "Importing...")
            }
            
            downloadedFile.delete()
            extractedDir.deleteRecursively()
            
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_DICTIONARY_IMPORTED, true)
                .putString(KEY_DICTIONARY_VERSION, CURRENT_VERSION)
                .apply()
            
            Log.i(TAG, "Dictionary import complete")
            listener?.onComplete()
            
        } catch (e: Exception) {
            Log.e(TAG, "Dictionary download/import failed", e)
            listener?.onError(e)
            throw e
        }
    }
    
    private fun downloadDictionary(
        progressCallback: (Long, Long) -> Unit
    ): File {
        Log.i(TAG, "Downloading from: $JMDICT_URL")
        
        val request = Request.Builder()
            .url(JMDICT_URL)
            .build()
        
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Download failed: ${response.code}")
        }
        
        val cacheFile = File(context.cacheDir, CACHE_FILE)
        val totalBytes = response.body?.contentLength() ?: -1L
        var downloadedBytes = 0L
        
        response.body?.byteStream()?.use { input ->
            FileOutputStream(cacheFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    
                    if (totalBytes > 0) {
                        progressCallback(downloadedBytes, totalBytes)
                    }
                }
            }
        }
        
        Log.i(TAG, "Download complete: ${cacheFile.length()} bytes")
        return cacheFile
    }
    
    private fun extractZipFile(zipFile: File): File {
        Log.i(TAG, "Extracting ZIP file...")
        
        val extractedDir = File(context.cacheDir, EXTRACTED_DIR)
        extractedDir.mkdirs()
        
        java.util.zip.ZipInputStream(FileInputStream(zipFile)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val file = File(extractedDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { output ->
                        zip.copyTo(output)
                    }
                }
                entry = zip.nextEntry
            }
        }
        
        Log.i(TAG, "Extraction complete")
        return extractedDir
    }
    
    private suspend fun importYomichanDictionary(
        extractedDir: File,
        progressCallback: (Int, Int) -> Unit
    ) {
        Log.i(TAG, "Importing Yomichan dictionary...")
        
        val database = AppDatabase.getDatabase(context)
        val dao = database.dictionaryDao()
        
        dao.deleteAll()
        
        val termBankFiles = extractedDir.listFiles { file ->
            file.name.startsWith("term_bank_") && file.name.endsWith(".json")
        }?.sortedBy { it.name } ?: emptyList()
        
        Log.i(TAG, "Found ${termBankFiles.size} term bank files")
        
        var totalImported = 0
        val estimatedTotal = termBankFiles.size * 1000
        
        termBankFiles.forEach { file ->
            Log.d(TAG, "Processing ${file.name}")
            val entries = parser.parseYomichanTermBank(file)
            
            entries.chunked(500).forEach { batch ->
                dao.insertAll(batch)
                totalImported += batch.size
                progressCallback(totalImported, estimatedTotal)
            }
        }
        
        Log.i(TAG, "Import complete: $totalImported entries")
    }
    
    suspend fun getDictionaryStatus(): DictionaryStatus = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val imported = prefs.getBoolean(KEY_DICTIONARY_IMPORTED, false)
        val version = prefs.getString(KEY_DICTIONARY_VERSION, "unknown")
        
        val database = AppDatabase.getDatabase(context)
        val entryCount = database.dictionaryDao().getCount()
        
        DictionaryStatus(
            isImported = imported,
            version = version ?: "unknown",
            entryCount = entryCount,
            isUpToDate = version == CURRENT_VERSION
        )
    }
    
    suspend fun deleteDictionary() = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(context)
        database.dictionaryDao().deleteAll()
        
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_DICTIONARY_IMPORTED, false)
            .remove(KEY_DICTIONARY_VERSION)
            .apply()
        
        Log.i(TAG, "Dictionary deleted")
    }
}

data class DictionaryStatus(
    val isImported: Boolean,
    val version: String,
    val entryCount: Int,
    val isUpToDate: Boolean
)
