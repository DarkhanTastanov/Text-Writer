package com.example.textwriter

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import android.util.Log
import java.io.InputStream
import java.lang.reflect.Field


class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var currentFileName: String

    private lateinit var textDisplay: TextView
    private lateinit var inputField: EditText
    private lateinit var scrollView: ScrollView

    private var words: List<String> = listOf()
    private var currentWordIndex = 0

    private var startTime: Long = 0
    private var currentTime: Long = 0
    private var wordCount: Int = 0
    private var timerRunning = false
    private var wpm = 0

    private lateinit var wpmView: TextView
    private lateinit var timerView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        wpmView = findViewById(R.id.wpmView)
        timerView = findViewById(R.id.timerView)
        textDisplay = findViewById(R.id.textDisplay)
        inputField = findViewById(R.id.inputField)
        scrollView = findViewById(R.id.scrollView)
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        loadInitialFile()
        loadProgress()
        updateDisplay("")

        inputField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                if (input.endsWith(" ")) {
                    val trimmedInput = input.trim()
                    checkWord(trimmedInput)
                    inputField.setText("")

                    if (!timerRunning) {
                        startTimer()
                    }
                } else {
                    updateDisplay(input)
                }
            }
        })

        val toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.app_name, R.string.app_name)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.load_asset -> {
                    showRawResourceList()
                    drawerLayout.closeDrawers()
                    true
                }

                else -> false
            }
        }

    }

    private fun checkWord(input: String) {
        if (currentWordIndex < words.size) {
            val correctWord = words[currentWordIndex]
            if (input == correctWord) {
                currentWordIndex++
                wordCount++
                saveProgress()
                updateDisplay(input)
            }
        }
    }


    private fun updateDisplay(currentInput: String) {
        val builder = SpannableStringBuilder()
        val windowSize = 30
        val startIdx = maxOf(0, currentWordIndex - 5)
        val endIdx = minOf(words.size, currentWordIndex + windowSize)

        for (i in startIdx until endIdx) {
            val word = words[i]
            val start = builder.length
            builder.append(word)

            when {
                i < currentWordIndex -> {
                    builder.setSpan(
                        ForegroundColorSpan(Color.GRAY),
                        start,
                        start + word.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                i == currentWordIndex -> {
                    if (currentInput.isNotEmpty()) {
                        val highlightColor = if (word.startsWith(currentInput)) Color.YELLOW else Color.RED
                        builder.setSpan(
                            BackgroundColorSpan(highlightColor),
                            start,
                            start + word.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    } else {
                        builder.setSpan(
                            BackgroundColorSpan(Color.YELLOW),
                            start,
                            start + word.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }

            builder.append(" ")
            if ((i + 1) % 10 == 0) builder.append("\n")
        }

        textDisplay.text = builder
        autoScrollToCurrentWord()
    }


    private fun autoScrollToCurrentWord() {
        textDisplay.post {
            val layout = textDisplay.layout ?: return@post
            val offset = getStartIndexOfCurrentWord()
            val line = layout.getLineForOffset(offset)
            val y = layout.getLineTop(line)
            scrollView.smoothScrollTo(0, y)
        }
    }

    private fun getStartIndexOfCurrentWord(): Int {
        var start = 0
        for (i in 0 until currentWordIndex) {
            start += words[i].length + 1
            if ((i + 1) % 10 == 0) start++
        }
        return start
    }

    private fun saveProgress() {
        if (::currentFileName.isInitialized && currentFileName.isNotBlank()) {
            val prefs = getSharedPreferences("typing_prefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("${currentFileName}_progress", currentWordIndex).apply()
            Log.d("MainActivity", "Saved progress $currentWordIndex for $currentFileName")
        } else {
            Log.w("MainActivity", "Cannot save progress, currentFileName not set.")
        }
    }

    private fun loadProgress() {
        if (::currentFileName.isInitialized && currentFileName.isNotBlank()) {
            val prefs = getSharedPreferences("typing_prefs", Context.MODE_PRIVATE)
            currentWordIndex = prefs.getInt("${currentFileName}_progress", 0)
            Log.d("MainActivity", "Loaded progress $currentWordIndex for $currentFileName")
        } else {
            currentWordIndex = 0
            Log.w("MainActivity", "Cannot load progress, currentFileName not set. Resetting index.")
        }
    }


    private fun startTimer() {
        startTime = System.currentTimeMillis()
        timerRunning = true

        val timerRunnable = object : Runnable {
            override fun run() {
                if (timerRunning) {
                    currentTime = System.currentTimeMillis() - startTime
                    updateTimerView()
                    updateWPM()

                    // Repeat the timer update every second
                    handler.postDelayed(this, 1000)
                }
            }
        }

        handler.post(timerRunnable)
    }
    private fun updateTimerView() {
        val seconds = (currentTime / 1000) % 60
        val minutes = (currentTime / 1000) / 60
        val formattedTime = String.format("%02d:%02d", minutes, seconds)
        timerView.text = formattedTime
    }

    private fun updateWPM() {
        val elapsedMinutes = (currentTime / 1000) / 60
        if (elapsedMinutes > 0) {
            wpm = (wordCount / elapsedMinutes).toInt()
        }
        wpmView.text = "WPM: $wpm"
    }
    private fun listRawResources(): List<Pair<String, Int>> {
        val resources = mutableListOf<Pair<String, Int>>()
        val rawClass: Class<R.raw> = R.raw::class.java
        val fields: Array<Field> = rawClass.declaredFields

        for (field in fields) {
            try {
                val resourceId = field.getInt(null)
                val displayName = field.name
                resources.add(Pair(displayName, resourceId))
            } catch (e: Exception) {
                Log.e("MainActivity", "Error reflecting R.raw field: ${field.name}", e)
            }
        }
        return resources.sortedBy { it.first }
    }

    private fun showRawResourceList() {
        val rawFiles: List<Pair<String, Int>> = listRawResources()

        if (rawFiles.isEmpty()) {
            showErrorDialog("No text resource files found in res/raw.")
            Log.w("MainActivity", "No files found via listRawResources()")
            return
        }

        val fileDisplayNames = rawFiles.map { it.first }.toTypedArray()

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Resource File")
        builder.setItems(fileDisplayNames) { _, which ->
            val selectedFile: Pair<String, Int> = rawFiles[which]
            val resourceName = selectedFile.first
            val resourceId = selectedFile.second

            Log.d("MainActivity", "Selected raw resource: $resourceName (ID: $resourceId)")

            saveLastOpenedResource(resourceName, "raw_resource")

            loadTextFromRawResource(resourceId, resourceName)

            loadProgress()
        }
        builder.show()
    }

    private fun saveLastOpenedResource(identifier: String, type: String) {
        val prefs = getSharedPreferences("typing_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_opened_identifier", identifier)
            .putString("last_opened_type", type)
            .apply()
        currentFileName = identifier
        Log.d("MainActivity", "Saved last opened: Type=$type, ID=$identifier")
    }


    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun loadInitialFile() {
        val prefs = getSharedPreferences("typing_prefs", Context.MODE_PRIVATE)
        val identifier = prefs.getString("last_opened_identifier", null)
        val type = prefs.getString("last_opened_type", null)

        var loadedSuccessfully = false
        if (identifier != null && type != null) {
            currentFileName = identifier
            Log.d("MainActivity", "Attempting to load last opened: Type=$type, ID=$identifier")

            if (type == "raw_resource") {
                try {
                    val resourceId = resources.getIdentifier(identifier, "raw", packageName)
                    if (resourceId != 0) {
                        loadTextFromRawResource(resourceId, identifier)
                        loadedSuccessfully = true
                        Log.d("MainActivity", "Successfully loaded last opened raw resource: $identifier (ID: $resourceId)")
                    } else {
                        Log.e("MainActivity", "Raw resource not found for name: $identifier")
                        showErrorDialog("Last selected resource '$identifier' not found. Loading default.")
                        clearLastOpenedFile()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error loading raw resource: $identifier", e)
                    showErrorDialog("Error loading last selected resource file. Loading default.")
                    clearLastOpenedFile()
                }
            }
            else {
                Log.w("MainActivity", "Unknown last opened type: $type. Loading default.")
                clearLastOpenedFile()
            }
        }
        else {
            Log.d("MainActivity", "No last opened file found in preferences.")
        }

        if (!loadedSuccessfully) {
            Log.d("MainActivity", "Loading default resource.")
            loadDefaultRawResource()
        }
    }

    private fun loadDefaultRawResource() {
        val defaultResourceName = "the_hobbit_j_r_r_tolkien"
        val defaultResourceId = resources.getIdentifier(defaultResourceName, "raw", packageName)

        if (defaultResourceId != 0) {
            loadTextFromRawResource(defaultResourceId, defaultResourceName)
            saveLastOpenedResource(defaultResourceName, "raw_resource")
        } else {
            Log.e("MainActivity", "FATAL: Default raw resource '$defaultResourceName' not found!")
            showErrorDialog("Critical Error: Default resource file not found. Cannot load text.")
            words = emptyList()
            currentWordIndex = 0
            currentFileName = ""
            updateDisplay("")
        }
    }


    private fun clearLastOpenedFile() {
        val prefs = getSharedPreferences("typing_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("last_opened_identifier")
            .remove("last_opened_type")
            .apply()
        Log.w("MainActivity", "Cleared last opened file preferences.")
    }


    private fun loadTextFromRawResource(resourceId: Int, resourceName: String) {
        try {
            val inputStream: InputStream = resources.openRawResource(resourceId)
            val text = inputStream.bufferedReader().use { it.readText() }
            words = text.split(" ", "\n").filter { it.isNotBlank() }
            currentWordIndex = 0
            wordCount = 0
            currentFileName = resourceName

            Log.d("MainActivity", "Loaded text from raw resource: $resourceName (ID: $resourceId)")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading raw resource: $resourceName (ID: $resourceId)", e)
            showErrorDialog("Error loading selected resource file.")
            words = emptyList()
            currentWordIndex = 0
            currentFileName = ""
            updateDisplay("")
        }
    }

}

