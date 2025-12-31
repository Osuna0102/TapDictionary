package com.godtap.dictionary

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.godtap.dictionary.database.AppDatabase
import com.godtap.dictionary.databinding.ActivityDictionaryDebugBinding
import com.godtap.dictionary.deinflection.JapaneseDeinflector
import com.godtap.dictionary.repository.DictionaryRepository
import kotlinx.coroutines.launch

/**
 * Debug screen for testing dictionary lookups
 * Allows direct search and shows all deinflected forms
 */
class DictionaryDebugActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityDictionaryDebugBinding
    private lateinit var repository: DictionaryRepository
    private val deinflector = JapaneseDeinflector()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDictionaryDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize repository
        val database = AppDatabase.getDatabase(this)
        repository = DictionaryRepository(database)
        
        setupUI()
    }
    
    private fun setupUI() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                if (query.isNotEmpty()) {
                    searchDictionary(query)
                } else {
                    binding.resultsText.text = ""
                    binding.statsText.text = ""
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        binding.exactSearchButton.setOnClickListener {
            val query = binding.searchInput.text.toString()
            if (query.isNotEmpty()) {
                searchExact(query)
            }
        }
        
        binding.fuzzySearchButton.setOnClickListener {
            val query = binding.searchInput.text.toString()
            if (query.isNotEmpty()) {
                searchFuzzy(query)
            }
        }
        
        binding.deinflectButton.setOnClickListener {
            val query = binding.searchInput.text.toString()
            if (query.isNotEmpty()) {
                showDeinflections(query)
            }
        }
    }
    
    private fun searchDictionary(query: String) {
        lifecycleScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                val entry = repository.search(query)
                val elapsed = System.currentTimeMillis() - startTime
                
                if (entry != null) {
                    binding.resultsText.text = buildString {
                        append("✓ FOUND\n\n")
                        append("Entry ID: ${entry.entryId}\n")
                        append("Expression: ${entry.primaryExpression ?: "(none)"}\n")
                        append("Reading: ${entry.primaryReading}\n")
                        append("Frequency: ${entry.frequency}\n")
                        append("JLPT: ${entry.jlptLevel ?: "N/A"}\n")
                        append("Common: ${if (entry.isCommon) "Yes" else "No"}\n\n")
                        append("Kanji forms:\n")
                        entry.kanjiElements.forEach {
                            append("  ${it.kanji}\n")
                        }
                        append("\nReadings:\n")
                        entry.readingElements.forEach {
                            append("  ${it.reading}\n")
                        }
                        append("\nMeanings:\n")
                        entry.senses.forEachIndexed { idx, sense ->
                            append("  ${idx + 1}. ${sense.glosses.joinToString("; ")}\n")
                            if (sense.partsOfSpeech.isNotEmpty()) {
                                append("     [${sense.partsOfSpeech.joinToString(", ")}]\n")
                            }
                        }
                    }
                    binding.statsText.text = "Query time: ${elapsed}ms"
                } else {
                    binding.resultsText.text = "⊘ NOT FOUND\n\nNo entry found for: '$query'"
                    binding.statsText.text = "Query time: ${elapsed}ms"
                }
            } catch (e: Exception) {
                binding.resultsText.text = "ERROR\n\n${e.message}"
                binding.statsText.text = ""
            }
        }
    }
    
    private fun searchExact(query: String) {
        lifecycleScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                val byExpression = repository.searchByExpression(query)
                val byReading = repository.searchByReading(query)
                val elapsed = System.currentTimeMillis() - startTime
                
                binding.resultsText.text = buildString {
                    append("EXACT SEARCH RESULTS\n\n")
                    
                    if (byExpression != null) {
                        append("✓ Found by Expression:\n")
                        append("  ${byExpression.primaryExpression} [${byExpression.primaryReading}]\n")
                        append("  ${byExpression.getAllGlosses().take(3).joinToString("; ")}\n\n")
                    } else {
                        append("⊘ Not found by expression\n\n")
                    }
                    
                    if (byReading != null) {
                        append("✓ Found by Reading:\n")
                        append("  ${byReading.primaryExpression ?: byReading.primaryReading} [${byReading.primaryReading}]\n")
                        append("  ${byReading.getAllGlosses().take(3).joinToString("; ")}\n\n")
                    } else {
                        append("⊘ Not found by reading\n\n")
                    }
                }
                binding.statsText.text = "Query time: ${elapsed}ms"
            } catch (e: Exception) {
                binding.resultsText.text = "ERROR\n\n${e.message}"
            }
        }
    }
    
    private fun searchFuzzy(query: String) {
        lifecycleScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                val entries = repository.searchFuzzy(query, limit = 20)
                val elapsed = System.currentTimeMillis() - startTime
                
                if (entries.isNotEmpty()) {
                    binding.resultsText.text = buildString {
                        append("FUZZY SEARCH (${entries.size} results)\n\n")
                        entries.forEachIndexed { idx, entry ->
                            append("${idx + 1}. ${entry.primaryExpression ?: entry.primaryReading} ")
                            append("[${entry.primaryReading}]\n")
                            append("   ${entry.getAllGlosses().take(2).joinToString("; ")}\n\n")
                        }
                    }
                } else {
                    binding.resultsText.text = "⊘ NO RESULTS\n\nNo similar entries found for: '$query'"
                }
                binding.statsText.text = "${entries.size} results in ${elapsed}ms"
            } catch (e: Exception) {
                binding.resultsText.text = "ERROR\n\n${e.message}"
            }
        }
    }
    
    private fun showDeinflections(query: String) {
        val deinflections = deinflector.deinflect(query)
        
        binding.resultsText.text = buildString {
            append("DEINFLECTION ANALYSIS\n\n")
            append("Input: $query\n")
            append("Generated ${deinflections.size} forms:\n\n")
            
            deinflections.forEachIndexed { idx, deinf ->
                append("${idx + 1}. ${deinf.term}\n")
                if (deinf.rules.isNotEmpty()) {
                    append("   Rules: ${deinf.rules.joinToString(", ")}\n")
                }
                append("\n")
            }
        }
        binding.statsText.text = "${deinflections.size} forms generated"
        
        // Try to find each form in the dictionary
        lifecycleScope.launch {
            val found = mutableListOf<String>()
            for (deinf in deinflections) {
                val entry = repository.search(deinf.term)
                if (entry != null) {
                    found.add("✓ ${deinf.term} → ${entry.primaryExpression ?: entry.primaryReading}")
                }
            }
            
            if (found.isNotEmpty()) {
                binding.statsText.text = buildString {
                    append("Found ${found.size} matches:\n")
                    found.forEach { append("$it\n") }
                }
            }
        }
    }
}
