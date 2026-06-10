package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.data.GeminiClient
import com.example.data.GeminiContent
import com.example.data.GeminiPart
import com.example.data.GeminiRequest
import com.example.data.ItemParser
import com.example.data.ParsedItem
import com.example.data.Report
import com.example.data.ReportRepository
import com.example.data.ReportSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("electro_prefs", Context.MODE_PRIVATE)

    // Room Database Setup
    private val database = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "electro_assistant_v2.db"
    ).fallbackToDestructiveMigration().build()

    private val repository = ReportRepository(database.reportDao())

    // List of reports
    val allReports: StateFlow<List<Report>> = repository.allReports
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Language state: defaults to Russian
    private val _language = MutableStateFlow(sharedPrefs.getString("app_lang", Localization.LANG_RU) ?: Localization.LANG_RU)
    val language: StateFlow<String> = _language.asStateFlow()

    // PIN Authentication States
    private val _hasPin = MutableStateFlow(sharedPrefs.contains("user_pin"))
    val hasPin: StateFlow<Boolean> = _hasPin.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(!sharedPrefs.contains("user_pin"))
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    // Constructor Injection / States for Profile Form
    private val _masterName = MutableStateFlow(sharedPrefs.getString("master_name", "") ?: "")
    val masterName: StateFlow<String> = _masterName.asStateFlow()

    private val _masterGrade = MutableStateFlow(sharedPrefs.getString("master_grade", "") ?: "")
    val masterGrade: StateFlow<String> = _masterGrade.asStateFlow()

    // Input Calculator State
    val calculationInput = MutableStateFlow("")
    
    private val _parsedItemsList = MutableStateFlow<List<ParsedItem>>(emptyList())
    val parsedItemsList = _parsedItemsList.asStateFlow()

    private val _totalSum = MutableStateFlow(0.0)
    val totalSum = _totalSum.asStateFlow()

    private val _usdRate = MutableStateFlow(12850.0)
    val usdRate = _usdRate.asStateFlow()

    fun fetchUsdRate() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://open.er-api.com/v6/latest/USD")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val regex = """"UZS"\s*:\s*(\d+(?:\.\d+)?)""".toRegex()
                    val match = regex.find(response)
                    if (match != null) {
                        val rate = match.groupValues[1].toDoubleOrNull()
                        if (rate != null && rate > 0.0) {
                            _usdRate.value = rate
                            android.util.Log.d("AppViewModel", "Successfully fetched live USD rate: $rate")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "Failed to fetch live USD rate, using fallback: ${e.message}")
            }
        }
    }

    // Screen navigation state (simple tabs in single-screen layout to follow constraints)
    private val _currentTab = MutableStateFlow("CALCULATOR") // "CALCULATOR", "REPORTS", "AI_CIVIL", "PROFILE"
    val currentTab = _currentTab.asStateFlow()

    // Chosen Report inside Archive for Drawer/Expanded Details
    private val _selectedReportForPreview = MutableStateFlow<Report?>(null)
    val selectedReportForPreview = _selectedReportForPreview.asStateFlow()

    // Delete dialog confirmation states
    private val _showDeleteConfirm = MutableStateFlow(false)
    val showDeleteConfirm = _showDeleteConfirm.asStateFlow()
    private var reportIdToDelete: Int? = null

    // AI Chief Electrical Engineer States
    val aiQueryString = MutableStateFlow("")
    
    private val _aiResponseText = MutableStateFlow("")
    val aiResponseText = _aiResponseText.asStateFlow()

    private val _isAiRunning = MutableStateFlow(false)
    val isAiRunning = _isAiRunning.asStateFlow()

    // Google Account Linking States
    val userGoogleEmail = MutableStateFlow(sharedPrefs.getString("user_google_email", "") ?: "")
    val isGoogleLinked = MutableStateFlow(sharedPrefs.getBoolean("is_google_linked", false))

    fun linkGoogleAccount(email: String) {
        sharedPrefs.edit().putString("user_google_email", email).putBoolean("is_google_linked", true).apply()
        isGoogleLinked.value = true
        userGoogleEmail.value = email
    }

    fun unlinkGoogleAccount() {
        sharedPrefs.edit().remove("user_google_email").remove("is_google_linked").apply()
        isGoogleLinked.value = false
        userGoogleEmail.value = ""
    }

    // Custom Excel File Name
    val customFileName = MutableStateFlow("Замин")

    // Image-Parsing with Gemini States
    val isImageScanning = MutableStateFlow(false)
    val imageScanResult = MutableStateFlow<String?>(null)
    val imageScanError = MutableStateFlow<String?>(null)

    fun scanImageWithGemini(bitmap: Bitmap, context: Context) {
        isImageScanning.value = true
        imageScanError.value = null
        imageScanResult.value = null
        
        viewModelScope.launch {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                
                // Compress and convert to base64
                val base64Image = withContext(Dispatchers.IO) {
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                }

                // High accuracy prompt for parsing list items including "м" unit
                val prompt = """
                    Вы — ИИ Помощник Электрика. Распознайте рукописный или напечатанный текст на этой фотографии. Это список электромонтажных работ, материалов, кабелей или приборов с их количеством и ценами.
                    Распознайте каждую позицию и переведите в структурированный текстовый формат, где каждая позиция записана на новой строке в следующем формате:
                    [Название] [Количество] [Цена, например: по 15000 или просто 15000]

                    ВАЖНЫЕ ПРАВИЛА:
                    1. Если в строке указаны метры (например, 150 метров, 150 м, 150м), обязательно пишите единицу измерения "м" слитно или раздельно после количества. КРАЙНЕ ВАЖНО: для метража пишите именно букву "м", а не "миллион" или "млн"! Например: "Кабель 3х2.5 150м по 14000".
                    2. Выдайте ТОЛЬКО распознанный список приборов/материалов в таком строчном формате, без каких-либо заголовков, пояснений, вводных слов или разметки markdown (без ```). Каждая позиция на отдельной строке.
                    Пример вывода:
                    Кабель ВВГнг-LS 3х2.5 150м по 14000
                    Автомат ABB 25A 3шт по 45000
                    УЗО Schneider 2шт по 180000
                    Монтаж щитка 1шт 250000
                """.trimIndent()

                val request = GeminiRequest(
                    contents = listOf(
                        com.example.data.GeminiContent(
                            parts = listOf(
                                com.example.data.GeminiPart(text = prompt),
                                com.example.data.GeminiPart(inlineData = com.example.data.GeminiInlineData(mimeType = "image/jpeg", data = base64Image))
                            )
                        )
                    )
                )

                val response = withContext(Dispatchers.IO) {
                    GeminiClient.service.generateContent(apiKey, request)
                }

                val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!textResponse.isNullOrBlank()) {
                    imageScanResult.value = textResponse
                } else {
                    imageScanError.value = "Не удалось распознать текст. Попробуйте сделать более чёткое фото."
                }
            } catch (e: Exception) {
                imageScanError.value = "Ошибка распознавания: ${e.localizedMessage ?: e.message}"
            } finally {
                isImageScanning.value = false
            }
        }
    }

    init {
        fetchUsdRate()
        // Automatically calculate totals whenever input text changes
        viewModelScope.launch {
            calculationInput.collect { text ->
                val items = ItemParser.parseBulkText(text)
                _parsedItemsList.value = items
                _totalSum.value = items.sumOf { it.total }
            }
        }
    }

    // Toggle app language
    fun toggleLanguage() {
        val nextLang = if (_language.value == Localization.LANG_RU) Localization.LANG_UZ else Localization.LANG_RU
        _language.value = nextLang
        sharedPrefs.edit().putString("app_lang", nextLang).apply()
    }

    // Authentication Logic & PIN Actions
    fun createPin(pin: String) {
        if (pin.length == 4 && pin.all { it.isDigit() }) {
            sharedPrefs.edit().putString("user_pin", pin).apply()
            _hasPin.value = true
            _isAuthenticated.value = true
            _pinError.value = null
        } else {
            _pinError.value = "PIN must be 4 digits!"
        }
    }

    fun loginWithPin(pin: String): Boolean {
        val saved = sharedPrefs.getString("user_pin", null)
        return if (saved == pin) {
            _isAuthenticated.value = true
            _pinError.value = null
            true
        } else {
            _pinError.value = Localization.get("pin_wrong", _language.value)
            false
        }
    }

    fun logout() {
        _isAuthenticated.value = false
    }

    // Save master details
    fun saveMasterProfile(name: String, grade: String) {
        _masterName.value = name
        _masterGrade.value = grade
        sharedPrefs.edit()
            .putString("master_name", name)
            .putString("master_grade", grade)
            .apply()
    }

    // Navigate to local tabs
    fun setTab(tab: String) {
        _currentTab.value = tab
    }

    // Parse and Export Excel-CSV & Trigger Share Dialog
    fun generateAndShareExcel(context: Context) {
        val items = _parsedItemsList.value
        val rawInput = calculationInput.value
        if (items.isEmpty() || rawInput.trim().isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val name = _masterName.value.ifBlank { "Мастер-Электрик" }
            val grade = _masterGrade.value.ifBlank { "N/A" }
            val lang = _language.value

            // 1. Generate real CSV File
            val csvFile = writeCsvToStorage(context, items, name, grade, lang)
            
            // 2. Save calculation to Room Offline DB
            val serialized = ReportSerializer.encode(items)
            val total = items.sumOf { it.total }
            val newReport = Report(
                inputText = rawInput,
                serializedItems = serialized,
                totalSum = total,
                masterName = name,
                masterGrade = grade,
                language = lang,
                usdRate = _usdRate.value
            )
            repository.insert(newReport)

            // 3. Open Android Share Intent with FileProvider
            shareCsvFile(context, csvFile)
        }
    }

    // Re-share existing report from reports archive
    fun resendReportExcel(context: Context, report: Report) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = ReportSerializer.decode(report.serializedItems)
            val csvFile = writeCsvToStorage(
                context, 
                items, 
                report.masterName, 
                report.masterGrade, 
                report.language,
                report.usdRate
            )
            shareCsvFile(context, csvFile)
        }
    }

    private fun formatNum(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }

    private fun writeCsvToStorage(
        context: Context,
        items: List<ParsedItem>,
        masterName: String,
        masterGrade: String,
        lang: String,
        customUsdRate: Double? = null
    ): File {
        val rawFileName = customFileName.value.trim().ifBlank {
            "electro_invoice_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
        }
        val fileName = "$rawFileName.csv"
        val file = File(context.cacheDir, fileName)
        
        BufferedWriter(OutputStreamWriter(FileOutputStream(file), "UTF-8")).use { writer ->
            // Excel UTF-8 BOM
            writer.write("\uFEFF")

            // Executor details
            writer.write("${Localization.get("excel_executor", lang)}:;$masterName\n")
            writer.write("${Localization.get("excel_grade", lang)}:;$masterGrade\n")
            writer.write("\n")

            // Column Header
            writer.write("${Localization.get("col_no", lang)};${Localization.get("col_name", lang)};${Localization.get("col_qty", lang)};${Localization.get("col_unit", lang)};${Localization.get("col_price", lang)};${Localization.get("col_total", lang)}\n")

            // Line items
            items.forEachIndexed { idx, item ->
                writer.write("${idx + 1};${item.name};${formatNum(item.quantity)};${item.unit};${formatNum(item.price)};${formatNum(item.total)}\n")
            }

            // Total row
            writer.write("\n")
            val total = items.sumOf { it.total }
            val rateValue = customUsdRate ?: usdRate.value
            val totalInUsd = if (rateValue > 0.0) total / rateValue else 0.0

            writer.write(";;;;${Localization.get("excel_total_row", lang)}:;${formatNum(total)}\n")
            writer.write(";;;;${Localization.get("rate_usd_title", lang)}:;${formatNum(rateValue)}\n")
            writer.write(";;;;${Localization.get("total_usd", lang)}:;${formatNum(totalInUsd)}\n")
        }
        return file
    }

    private fun shareCsvFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "com.example.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/comma-separated-values"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Electro Invoice - Electro Assistant")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Share Electrical Estimation Invoice").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    // Reports Archiving actions
    fun selectReportForPreview(report: Report?) {
        _selectedReportForPreview.value = report
    }

    fun requestDeleteReport(id: Int) {
        reportIdToDelete = id
        _showDeleteConfirm.value = true
    }

    fun confirmDeleteReport() {
        val id = reportIdToDelete
        if (id != null) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.deleteById(id)
                withContext(Dispatchers.Main) {
                    _showDeleteConfirm.value = false
                    _selectedReportForPreview.value = null
                    reportIdToDelete = null
                }
            }
        }
    }

    fun cancelDeleteReport() {
        _showDeleteConfirm.value = false
        reportIdToDelete = null
    }

    // Gemini API Request call as Chief Electrical Engineer
    fun askAiEngineer() {
        val query = aiQueryString.value.trim()
        if (query.isEmpty()) return

        _isAiRunning.value = true
        _aiResponseText.value = ""

        viewModelScope.launch {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                
                // 30 years expert electrical engineer system prompt
                val sysPrompt = """
                    Вы — интеллектуальный ИИ Помощник Электрика. Вы помогаете писать ответы на вопросы, делать расчеты сечений кабелей, нагрузок и помогать в работе со сметами.
                    Вы идеально знаете ПУЭ (Правила устройства электроустановок), ГОСТы, СНиП и все правила техники безопасности.
                    
                    ПРАВИЛА ОТВЕТА:
                    1. Говорите прямо и строго по делу, помогая пользователю в работе. Переходите сразу к расчетам, советам или спецификациям.
                    2. Каждый расчет должен включать точные цифры: сечение проводника в кв.мм, рабочее напряжение (U в Вольтах), нагрузочный ток (I в Амперах), потребляемая мощность (P в Ваттах/кВт), типы автоматических выключателей.
                    3. Обязательно приводите физические формулы при расчетах:
                       - Однофазная сеть (220 В): I = P / U.
                       - Трехфазная сеть (380 В): P = √3 * I * U * cosφ.
                    4. Обязательно делайте акцент на безопасность (риск перегрузки, КЗ и пожара).
                    
                    Отвечайте на текущем языке приложения: если язык ${Localization.LANG_UZ} - пишите на узбекском, если ${Localization.LANG_RU} - пишите на русском.
                """.trimIndent()

                val request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(GeminiPart(text = query))
                        )
                    ),
                    systemInstruction = GeminiContent(
                        parts = listOf(GeminiPart(text = sysPrompt))
                    )
                )

                val response = withContext(Dispatchers.IO) {
                    GeminiClient.service.generateContent(apiKey, request)
                }

                val aiText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                _aiResponseText.value = aiText ?: "Ошибка: ответ не получен от ИИ."
            } catch (e: Exception) {
                _aiResponseText.value = "Ошибка связи с ИИ: ${e.localizedMessage ?: e.message}. Убедитесь в подключении к интернету."
            } finally {
                _isAiRunning.value = false
            }
        }
    }
}
