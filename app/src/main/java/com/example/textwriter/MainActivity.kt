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
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
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

    // Views for WPM and Timer
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

        loadInitialFile() // This function now handles loading asset/URI/default

        val prefs = getSharedPreferences("typing_prefs", Context.MODE_PRIVATE)
        val lastFile = prefs.getString("last_opened_file", null)

        if (lastFile != null) {
            currentFileName = lastFile

            if (assets.list("")?.contains(lastFile) == true) {
                loadTextFromAssets(lastFile)
            } else {
                // It's likely a file picker file, so we skip loading content
                // unless you want to cache the full file text yourself.
                loadTextFromRaw() // fallback
            }
            loadProgress()
        } else {
            currentFileName = "the_hobbit_j_r_r_tolkien.txt"
            loadProgress()
            loadTextFromRaw()
        }

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

        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

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
                R.id.load_file -> {
                    openFilePicker()
                    drawerLayout.closeDrawers()
                    true
                }
                else -> false
            }
        }

    }

    private fun loadTextFromRaw() {
        val inputStream = resources.openRawResource(R.raw.the_hobbit_j_r_r_tolkien)
        val text = inputStream.bufferedReader().use { it.readText() }
        words = text.split(" ", "\n").filter { it.isNotBlank() }
        updateDisplay("")
    }

    private fun checkWord(input: String) {
        if (currentWordIndex < words.size) {
            val correctWord = words[currentWordIndex]
            if (input == correctWord) {
                currentWordIndex++
                wordCount++  // Increase word count for WPM calculation
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
            if ((i + 1) % 10 == 0) start++ // for line break
        }
        return start
    }

    // These should now work correctly as currentFileName is set appropriately
    private fun saveProgress() {
        if (::currentFileName.isInitialized && currentFileName.isNotBlank()) { // Check if initialized
            val prefs = getSharedPreferences("typing_prefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("${currentFileName}_progress", currentWordIndex).apply()
            Log.d("MainActivity", "Saved progress $currentWordIndex for $currentFileName")
        } else {
            Log.w("MainActivity", "Cannot save progress, currentFileName not set.")
        }
    }

    private fun loadProgress() {
        if (::currentFileName.isInitialized && currentFileName.isNotBlank()) { // Check if initialized
            val prefs = getSharedPreferences("typing_prefs", Context.MODE_PRIVATE)
            currentWordIndex = prefs.getInt("${currentFileName}_progress", 0)
            Log.d("MainActivity", "Loaded progress $currentWordIndex for $currentFileName")
        } else {
            currentWordIndex = 0 // Default to 0 if no file name known
            Log.w("MainActivity", "Cannot load progress, currentFileName not set. Resetting index.")
        }
    }


    private fun startTimer() {
        startTime = System.currentTimeMillis()
        timerRunning = true

        // Update the timer every second
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

        handler.post(timerRunnable)  // Start the timer
    }
    private fun updateTimerView() {
        val seconds = (currentTime / 1000) % 60
        val minutes = (currentTime / 1000) / 60
        val formattedTime = String.format("%02d:%02d", minutes, seconds)
        timerView.text = formattedTime
    }

    private fun updateWPM() {
        // Calculate words per minute based on the current time and typed words
        val elapsedMinutes = (currentTime / 1000) / 60
        if (elapsedMinutes > 0) {
            wpm = (wordCount / elapsedMinutes).toInt()
        }
        wpmView.text = "WPM: $wpm"
    }
    private fun listRawResources(): List<Pair<String, Int>> {
        val resources = mutableListOf<Pair<String, Int>>()
        val rawClass: Class<R.raw> = R.raw::class.java // Get the R.raw class
        val fields: Array<Field> = rawClass.declaredFields // Get all fields declared in R.raw

        for (field in fields) {
            // Optional: Add checks if needed, e.g., ensure it's a static int field
            // We assume all fields in R.raw correspond to actual raw resources
            try {
                // Filter based on naming convention if you only want certain files
                // For example, if your text files in raw end with "_txt":
                // if (field.name.endsWith("_txt")) {
                val resourceId = field.getInt(null) // Get the integer value (resource ID)
                // You might want to remove the suffix if you used one for filtering:
                // val displayName = field.name.removeSuffix("_txt")
                val displayName = field.name // Use the field name directly
                resources.add(Pair(displayName, resourceId))
                // }
            } catch (e: Exception) {
                // Log error or handle cases where a field might not be a resource ID
                Log.e("MainActivity", "Error reflecting R.raw field: ${field.name}", e)
            }
        }
        return resources.sortedBy { it.first } // Sort alphabetically by name
    }
    private fun loadTextFromRawResource(resourceId: Int, resourceName: String) {
        try {
            val inputStream: InputStream = resources.openRawResource(resourceId)
            val text = inputStream.bufferedReader().use { it.readText() }
            words = text.split(" ", "\n").filter { it.isNotBlank() }
            currentWordIndex = 0 // Reset progress for new file
            // currentFileName = resourceName // Set by saveLastOpenedResource
            updateDisplay("")
            Log.d("MainActivity", "Loaded text from raw resource: $resourceName (ID: $resourceId)")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading raw resource: $resourceName (ID: $resourceId)", e)
            showErrorDialog("Error loading selected resource file.")
            // Reset state if loading fails
            words = emptyList()
            currentWordIndex = 0
            updateDisplay("")
        }
    }

    private fun showRawResourceList() {
        val rawFiles: List<Pair<String, Int>> = listRawResources() // Get name/ID pairs

        if (rawFiles.isEmpty()) {
            // Handle case where no raw resources are found (or match filter)
            showErrorDialog("No text resource files found in res/raw.")
            Log.w("MainActivity", "No files found via listRawResources()")
            return
        }

        // Extract just the names to show in the dialog
        val fileDisplayNames = rawFiles.map { it.first }.toTypedArray()

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Resource File") // Updated title
        builder.setItems(fileDisplayNames) { _, which ->
            // Get the selected Pair (name, id) using the index 'which'
            val selectedFile: Pair<String, Int> = rawFiles[which]
            val resourceName = selectedFile.first
            val resourceId = selectedFile.second

            Log.d("MainActivity", "Selected raw resource: $resourceName (ID: $resourceId)")

            // Save this selection as the last opened file (using the resource NAME as identifier)
            // Use a distinct type, e.g., "raw_resource"
            saveLastOpenedResource(resourceName, "raw_resource")

            // Load the text content using the resource ID
            loadTextFromRawResource(resourceId, resourceName)

            // Load progress associated with this resource NAME
            loadProgress() // Uses currentFileName which was set by saveLastOpenedResource
        }
        builder.show()
    }

    // Generic function to save last opened identifier and type
    private fun saveLastOpenedResource(identifier: String, type: String) {
        val prefs = getSharedPreferences("typing_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_opened_identifier", identifier)
            .putString("last_opened_type", type) // e.g., "raw_resource", "uri"
            .apply()
        currentFileName = identifier // Use the resource name or URI string as the key
        Log.d("MainActivity", "Saved last opened: Type=$type, ID=$identifier")
    }

    private fun loadTextFromAssets(fileName: String) {
        val inputStream = assets.open(fileName)
        val text = inputStream.bufferedReader().use { it.readText() }
        words = text.split(" ", "\n").filter { it.isNotBlank() }
        currentWordIndex = 0
        updateDisplay("")
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*" // Or "*/*" for any file type

        // *** Add these crucial flags ***
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

        try {
            startActivityForResult(intent, 100) // Use your request code
        } catch (e: ActivityNotFoundException) {
            // Handle case where no file picker app is available
            Log.e("MainActivity", "No activity found to handle ACTION_GET_CONTENT", e)
            showErrorDialog("Could not open file picker. No compatible app found.")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    // Ensure permission is granted to access the file
//                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

                    // Load text from the URI immediately
                    loadTextFromUri(uri) // This also sets words, resets index, updates display

// Save URI for future access (this now also persists permission)
                    saveLastOpenedUri(uri)

// Load progress associated with this URI string
                    loadProgress() // Load progress using the URI string as the key (currentFileName)

                } catch (e: SecurityException) {
                    e.printStackTrace()
                    showErrorDialog("Permission error: Unable to access the file.")
                }
            }
        }
    }



    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }



    private fun saveLastOpenedFile(fileName: Uri) {
        val prefs = getSharedPreferences("typing_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("last_opened_file", fileName.toString()).apply()
    }

    // Method to copy a file from assets to internal storage
    private fun copyFileToInternalStorage(fileName: String) {
        val file = File(filesDir, fileName)  // filesDir refers to the app's internal storage directory

        // Check if the file already exists
        if (!file.exists()) {
            try {
                val inputStream: InputStream = assets.open(fileName)
                val outputStream: OutputStream = FileOutputStream(file)

                // Copy the content of the asset file to the internal storage file
                val buffer = ByteArray(1024)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }

                inputStream.close()
                outputStream.close()

                Log.d("MainActivity", "File copied to internal storage: $fileName")
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("MainActivity", "Error copying file: $fileName")
            }
        } else {
            Log.d("MainActivity", "File already exists in internal storage: $fileName")
        }
    }

    // Method to load file from internal storage
    private fun loadTextFromInternalStorage(fileName: String) {
        val file = File(filesDir, fileName)
        if (file.exists()) {
            val text = file.readText()
            words = text.split(" ", "\n").filter { it.isNotBlank() }
            currentWordIndex = 0
            updateDisplay("")
            Log.d("MainActivity", "Loaded file from internal storage: $fileName")
        } else {
            Log.e("MainActivity", "File not found in internal storage: $fileName")
        }
    }

    // Remove the old loadLastOpenedFile() function entirely from onCreate initial checks

    // Call this revised function from onCreate
    private fun loadInitialFile() {
        val prefs = getSharedPreferences("typing_prefs", Context.MODE_PRIVATE)
        val identifier = prefs.getString("last_opened_identifier", null)
        val type = prefs.getString("last_opened_type", "raw_resource") // Default to asset if type isn't saved

        var loadedSuccessfully = false
        if (identifier != null) {
            currentFileName = identifier // Set the identifier (asset name or URI string)
            Log.d("MainActivity", "Attempting to load last opened: Type=$type, ID=$identifier")
            when (type) {
                "uri" -> {
                    // ... (Your existing logic for loading URIs) ...
                    try {
                        val uri = Uri.parse(identifier)
                        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        contentResolver.takePersistableUriPermission(uri, takeFlags)
                        loadTextFromUri(uri) // Make sure this function exists and works
                        loadProgress()
                        loadedSuccessfully = true
                        Log.d("MainActivity", "Successfully loaded last opened URI: $identifier")
                    } catch (e: SecurityException) {
                        Log.e("MainActivity", "SecurityException loading last URI: $identifier", e)
                        showErrorDialog("Permission error loading last file '$identifier'. Please re-select it. Loading default.")
                        clearLastOpenedFile()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error loading last URI: $identifier", e)
                        showErrorDialog("Error loading last file. Loading default.")
                        clearLastOpenedFile()
                    }
                }
                "raw_resource" -> { // **** Handle Raw Resource ****
                    try {
                        val resourceName = identifier
                        // Get the resource ID dynamically from its name
                        val resourceId = resources.getIdentifier(resourceName, "raw", packageName)

                        if (resourceId != 0) { // 0 means resource not found
                            loadTextFromRawResource(resourceId, resourceName)
                            loadProgress() // Load progress using the resource name
                            loadedSuccessfully = true
                            Log.d("MainActivity", "Successfully loaded last opened raw resource: $resourceName (ID: $resourceId)")
                        } else {
                            // Resource might have been renamed or removed
                            Log.e("MainActivity", "Raw resource not found for name: $resourceName. Clearing preference.")
                            showErrorDialog("Last selected resource '$resourceName' not found. Loading default.")
                            clearLastOpenedFile() // Clear the invalid preference
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error loading last raw resource: $identifier", e)
                        showErrorDialog("Error loading last selected resource file. Loading default.")
                        clearLastOpenedFile()
                    }
                }
                // You might have an "asset" type if you still support assets directly
                // case "asset": { ... }
                else -> {
                    Log.w("MainActivity", "Unknown last opened type: $type. Clearing preference.")
                    clearLastOpenedFile() // Clear unknown type
                }
            }
        }

        // Fallback to default if no file was saved or loading failed
        if (!loadedSuccessfully) {
            Log.d("MainActivity", "Loading default resource.")
            loadDefaultRawResource() // Load a specific default raw resource
        }
    }
    private fun loadDefaultRawResource() {
        // Replace with the actual NAME of your default file in res/raw
        val defaultResourceName = "the_hobbit_j_r_r_tolkien"
        val defaultResourceId = resources.getIdentifier(defaultResourceName, "raw", packageName)

        if (defaultResourceId != 0) {
            currentFileName = defaultResourceName // Set for progress tracking
            loadTextFromRawResource(defaultResourceId, defaultResourceName)
            saveLastOpenedResource(defaultResourceName, "raw_resource") // Save the default as the last opened
            loadProgress()
        } else {
            Log.e("MainActivity", "FATAL: Default raw resource '$defaultResourceName' not found!")
            showErrorDialog("Critical Error: Default resource file not found.")
            // Handle gracefully - display error, disable input?
            textDisplay.text = "Error: Default resource file not found."
            words = emptyList()
            currentWordIndex = 0
        }
    }
    // Make sure loadTextFromUri exists if you handle "uri" type
    private fun loadTextFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val text = inputStream.bufferedReader().readText()
                words = text.split(" ", "\n").filter { it.isNotBlank() }
                currentWordIndex = 0
                updateDisplay("")
                Log.d("MainActivity", "Loaded text from URI: $uri")
            } ?: run {
                Log.e("MainActivity", "Failed to open InputStream for URI: $uri")
                showErrorDialog("File not found or could not be opened.")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error reading file from URI: $uri", e)
            showErrorDialog("Error reading file from URI.")
            // Reset state
            words = emptyList()
            currentWordIndex = 0
            updateDisplay("")
        }
    }

    // Keep clearLastOpenedFile helper
    // Helper to load the default asset cleanly
    private fun loadDefaultAsset() {
        val defaultAssetName = "the_hobbit_j_r_r_tolkien.txt" // Your default
        currentFileName = defaultAssetName
        try {
            loadTextFromAssets(defaultAssetName)
            saveLastOpenedAsset(defaultAssetName) // Save the default as the last opened
            loadProgress() // Load progress for default
        } catch (e: Exception) {
            Log.e("MainActivity", "FATAL: Could not load default asset $defaultAssetName", e)
            showErrorDialog("Critical error: Cannot load the default text file.")
            // Handle this case gracefully - maybe disable input?
            textDisplay.text = "Error: Default text file not found."
            words = emptyList()
            currentWordIndex = 0
        }
    }

    // Helper to clear invalid preferences
    private fun clearLastOpenedFile() {
        val prefs = getSharedPreferences("typing_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("last_opened_identifier")
            .remove("last_opened_type")
            .apply()
        Log.w("MainActivity", "Cleared last opened file preferences.")
    }

    // Replace the single saveLastOpenedFile function with this logic
    private fun saveLastOpenedAsset(fileName: String) {
        val prefs = getSharedPreferences("typing_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_opened_identifier", fileName)
            .putString("last_opened_type", "asset")
            .apply()
        currentFileName = fileName // Keep track of the simple name for assets
        Log.d("MainActivity", "Saved last opened asset: $fileName")
    }

    private fun saveLastOpenedUri(uri: Uri) {
        val prefs = getSharedPreferences("typing_prefs", Context.MODE_PRIVATE)
        val uriString = uri.toString()
        prefs.edit()
            .putString("last_opened_identifier", uriString)
            .putString("last_opened_type", "uri")
            // Optionally save flags needed to re-take permission if necessary
            // prefs.edit().putInt("last_opened_uri_flags", Intent.FLAG_GRANT_READ_URI_PERMISSION).apply()
            .apply()
        // For URIs, currentFileName might be less useful, maybe store display name?
        // Or use the URI string itself as the key for progress saving.
        currentFileName = uriString // Use the URI string as the unique identifier
        Log.d("MainActivity", "Saved last opened URI: $uriString")

        // Persist permission (important!)
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Log.d("MainActivity", "Persisted read permission for URI: $uriString")
        } catch (e: SecurityException) {
            Log.e("MainActivity", "Failed to persist permission for URI: $uriString", e)
            showErrorDialog("Could not save access permission for the selected file. It might not load correctly next time.")
        }
    }

}

