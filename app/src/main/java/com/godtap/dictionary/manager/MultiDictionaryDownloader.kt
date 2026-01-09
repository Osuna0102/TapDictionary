package com.godtap.dictionary.manager

import android.content.Context
import android.util.Log
import com.godtap.dictionary.database.AppDatabase
import com.godtap.dictionary.database.DictionaryEntry
import com.godtap.dictionary.database.DictionaryFormat
import com.godtap.dictionary.database.DictionaryMetadata
import com.godtap.dictionary.parser.JMdictParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Yomitan dictionary index.json structure
 */
@Serializable
data class DictionaryIndex(
    val title: String? = null,
    val revision: String? = null,
    val format: Int? = null,
    val author: String? = null,
    val url: String? = null,
    val description: String? = null,
    val attribution: String? = null,
    val sourceLanguage: String? = null,
    val targetLanguage: String? = null,
    val downloadUrl: String? = null,
    val sequenced: Boolean? = null
)

/**
 * Multi-dictionary downloader and importer
 * Supports downloading and importing dictionaries in various formats
 */
class MultiDictionaryDownloader(private val context: Context) {
    
    private val httpClient = OkHttpClient()
    private val parser = JMdictParser()
    private val dictionaryManager = DictionaryManager(context)
    private val database = AppDatabase.getDatabase(context)
    
    companion object {
        private const val TAG = "MultiDictDownloader"
        private const val CACHE_DIR = "dictionary_cache"
    }
    
    interface DownloadProgressListener {
        fun onProgress(bytesDownloaded: Long, totalBytes: Long, stage: String)
        fun onComplete()
        fun onError(error: Exception)
    }
    
    /**
     * Download and import a dictionary
     */
    suspend fun downloadAndImport(
        dictionaryId: String,
        listener: DownloadProgressListener? = null
    ) = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting download for dictionary: $dictionaryId")
            listener?.onProgress(0, 100, "Preparing...")
            
            // Get dictionary metadata
            val metadata = database.dictionaryMetadataDao().getByDictionaryId(dictionaryId)
                ?: throw IllegalArgumentException("Dictionary not found: $dictionaryId")
            
            val downloadUrl = metadata.downloadUrl
                ?: throw IllegalArgumentException("No download URL for dictionary: $dictionaryId")
            
            // Check if it's a local file path
            val zipFile = if (downloadUrl.startsWith("file://") || downloadUrl.startsWith("/")) {
                // Local file
                listener?.onProgress(0, 100, "Loading local file...")
                val localPath = downloadUrl.removePrefix("file://")
                File(localPath)
            } else {
                // Download from URL
                listener?.onProgress(0, 100, "Downloading...")
                downloadDictionary(downloadUrl, dictionaryId, listener)
            }
            
            if (!zipFile.exists()) {
                throw IllegalArgumentException("Dictionary file not found: ${zipFile.absolutePath}")
            }
            
            // Import based on format
            listener?.onProgress(0, 100, "Importing...")
            val entryCount = when (metadata.format) {
                DictionaryFormat.YOMICHAN -> importYomichanDictionary(zipFile, dictionaryId, listener)
                DictionaryFormat.DSL -> importDSLDictionary(zipFile, dictionaryId, listener)
                else -> throw UnsupportedOperationException("Format ${metadata.format} not yet supported")
            }
            
            // Mark as installed
            dictionaryManager.markDictionaryInstalled(dictionaryId, entryCount)
            
            // Enable by default
            dictionaryManager.setDictionaryEnabled(dictionaryId, true)
            
            // Clean up (only if downloaded, not local file)
            if (!downloadUrl.startsWith("file://") && !downloadUrl.startsWith("/")) {
                zipFile.delete()
            }
            
            Log.i(TAG, "Successfully imported $entryCount entries for $dictionaryId")
            listener?.onComplete()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading/importing dictionary", e)
            listener?.onError(e)
            throw e
        }
    }
    
    /**
     * Import from a local ZIP file (for bundled or user-provided dictionaries)
     */
    suspend fun importFromLocalFile(
        zipFile: File,
        dictionaryId: String,
        listener: DownloadProgressListener? = null
    ) = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Importing local dictionary: $dictionaryId from ${zipFile.absolutePath}")
            listener?.onProgress(0, 100, "Preparing...")
            
            if (!zipFile.exists()) {
                throw IllegalArgumentException("File not found: ${zipFile.absolutePath}")
            }
            
            // Get or create metadata
            var metadata = database.dictionaryMetadataDao().getByDictionaryId(dictionaryId)
            
            // Always update metadata from index.json to ensure latest info (especially language codes)
            val updatedMetadata = createMetadataFromZip(zipFile, dictionaryId)
            
            if (metadata == null) {
                // Create new metadata
                database.dictionaryMetadataDao().insert(updatedMetadata)
                metadata = updatedMetadata
            } else {
                // Update existing metadata with info from index.json
                val merged = metadata.copy(
                    name = updatedMetadata.name,
                    version = updatedMetadata.version,
                    sourceLanguage = updatedMetadata.sourceLanguage,
                    targetLanguage = updatedMetadata.targetLanguage,
                    description = updatedMetadata.description,
                    author = updatedMetadata.author,
                    website = updatedMetadata.website,
                    fileSize = updatedMetadata.fileSize
                )
                database.dictionaryMetadataDao().update(merged)
                metadata = merged
            }
            
            // Import based on format
            listener?.onProgress(0, 100, "Importing...")
            val entryCount = when (metadata.format) {
                DictionaryFormat.YOMICHAN -> importYomichanDictionary(zipFile, dictionaryId, listener)
                else -> throw UnsupportedOperationException("Format ${metadata.format} not yet supported")
            }
            
            // Mark as installed
            dictionaryManager.markDictionaryInstalled(dictionaryId, entryCount)
            dictionaryManager.setDictionaryEnabled(dictionaryId, true)
            
            Log.i(TAG, "Successfully imported $entryCount entries from local file")
            listener?.onComplete()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error importing local dictionary", e)
            listener?.onError(e)
            throw e
        }
    }
    
    /**
     * Create metadata from index.json in ZIP file
     */
    private fun createMetadataFromZip(zipFile: File, dictionaryId: String): DictionaryMetadata {
        var indexJson: String? = null
        
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "index.json") {
                    indexJson = zis.bufferedReader().readText()
                    break
                }
                entry = zis.nextEntry
            }
        }
        
        if (indexJson != null) {
            // Parse index.json
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val index = json.decodeFromString<DictionaryIndex>(indexJson!!)
            
            return DictionaryMetadata(
                name = index.title ?: dictionaryId,
                dictionaryId = dictionaryId,
                version = index.revision ?: "unknown",
                format = DictionaryFormat.YOMICHAN,
                sourceLanguage = index.sourceLanguage ?: "unknown",
                targetLanguage = index.targetLanguage ?: "unknown",
                downloadUrl = index.downloadUrl,
                fileSize = zipFile.length(),
                description = index.description,
                author = index.author,
                website = index.url
            )
        }
        
        // Fallback metadata
        return DictionaryMetadata(
            name = dictionaryId,
            dictionaryId = dictionaryId,
            version = "unknown",
            format = DictionaryFormat.YOMICHAN,
            sourceLanguage = "unknown",
            targetLanguage = "unknown",
            downloadUrl = null,
            fileSize = zipFile.length(),
            description = "Local dictionary"
        )
    }
    
    /**
     * Download dictionary ZIP file
     */
    private suspend fun downloadDictionary(
        url: String,
        dictionaryId: String,
        listener: DownloadProgressListener?
    ): File = withContext(Dispatchers.IO) {
        Log.i(TAG, "Downloading from: $url")
        
        val request = Request.Builder()
            .url(url)
            .build()
        
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Download failed: ${response.code}")
        }
        
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        cacheDir.mkdirs()
        
        val zipFile = File(cacheDir, "$dictionaryId.zip")
        val totalBytes = response.body?.contentLength() ?: -1
        
        response.body?.byteStream()?.use { input ->
            FileOutputStream(zipFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    listener?.onProgress(totalRead, totalBytes, "Downloading...")
                }
            }
        }
        
        Log.i(TAG, "Download complete: ${zipFile.length()} bytes")
        zipFile
    }
    
    /**
     * Import Yomichan/Yomitan format dictionary
     */
    private suspend fun importYomichanDictionary(
        zipFile: File,
        dictionaryId: String,
        listener: DownloadProgressListener?
    ): Long = withContext(Dispatchers.IO) {
        Log.i(TAG, "Importing Yomichan dictionary: $dictionaryId")
        Log.d(TAG, "ZIP file size: ${zipFile.length()} bytes")
        
        listener?.onProgress(0, 100, "Extracting ZIP file...")
        
        val extractDir = File(context.cacheDir, "${dictionaryId}_extracted")
        extractDir.mkdirs()
        
        // Extract ZIP
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            var extractedCount = 0
            while (entry != null) {
                extractedCount++
                if (extractedCount % 10 == 0) {
                    Log.d(TAG, "Extracted $extractedCount files...")
                }
                val file = File(extractDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { output ->
                        zis.copyTo(output)
                    }
                }
                entry = zis.nextEntry
            }
            Log.i(TAG, "Extracted $extractedCount files from ZIP")
        }
        
        listener?.onProgress(10, 100, "Finding dictionary files...")
        
        // Parse term banks
        val termBankFiles = extractDir.listFiles { _, name ->
            name.startsWith("term_bank_") && name.endsWith(".json")
        }?.sorted() ?: emptyList()
        
        Log.i(TAG, "Found ${termBankFiles.size} term bank files")
        
        if (termBankFiles.isEmpty()) {
            val allFiles = extractDir.listFiles()?.map { it.name }?.joinToString(", ") ?: "none"
            Log.e(TAG, "No term bank files found! Files in directory: $allFiles")
            throw IllegalStateException("No term bank files found in dictionary ZIP")
        }
        
        listener?.onProgress(20, 100, "Importing dictionary entries...")
        
        var totalEntries = 0L
        val dao = database.dictionaryDao()
        
        termBankFiles.forEachIndexed { index, file ->
            Log.d(TAG, "Processing term bank ${index + 1}/${termBankFiles.size}: ${file.name}")
            val entries = parser.parseYomichanTermBank(file)
            Log.d(TAG, "Parsed ${entries.size} entries from ${file.name}")
            
            // Add dictionaryId to each entry
            val entriesWithDictId = entries.map { it.copy(dictionaryId = dictionaryId) }
            
            dao.insertAll(entriesWithDictId)
            totalEntries += entriesWithDictId.size
            
            val progress = 20 + (index.toFloat() / termBankFiles.size * 70).toInt()
            listener?.onProgress(
                progress.toLong(),
                100,
                "Importing entries... (${index + 1}/${termBankFiles.size}) - $totalEntries entries"
            )
        }
        
        listener?.onProgress(95, 100, "Cleaning up...")
        
        // Clean up extracted files
        extractDir.deleteRecursively()
        
        Log.i(TAG, "Successfully imported $totalEntries entries for dictionary: $dictionaryId")
        listener?.onProgress(100, 100, "Complete!")
        totalEntries
    }
    
    /**
     * Import DSL format dictionary (ABBYY Lingvo)
     * TODO: Implement DSL parser
     */
    private suspend fun importDSLDictionary(
        zipFile: File,
        dictionaryId: String,
        listener: DownloadProgressListener?
    ): Long = withContext(Dispatchers.IO) {
        throw UnsupportedOperationException("DSL format not yet implemented")
    }
    
    /**
     * Check if dictionary is already downloaded
     */
    suspend fun isDictionaryDownloaded(dictionaryId: String): Boolean = withContext(Dispatchers.IO) {
        val metadata = database.dictionaryMetadataDao().getByDictionaryId(dictionaryId)
        metadata?.installed == true
    }
    
    /**
     * Delete downloaded dictionary
     */
    suspend fun deleteDictionary(dictionaryId: String) = withContext(Dispatchers.IO) {
        dictionaryManager.deleteDictionary(dictionaryId)
        
        // Clean up cache files
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        File(cacheDir, "$dictionaryId.zip").delete()
    }
}
