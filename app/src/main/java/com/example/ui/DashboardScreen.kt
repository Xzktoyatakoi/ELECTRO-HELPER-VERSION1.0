package com.example.ui

import android.widget.Toast
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.net.Uri
import android.graphics.Bitmap
import android.provider.MediaStore
import android.os.Build
import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Report
import com.example.data.ReportSerializer
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: AppViewModel) {
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()
    val hasPin by viewModel.hasPin.collectAsState()
    val lang by viewModel.language.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (!isAuthenticated) {
            PinKeypadScreen(
                hasPin = hasPin,
                lang = lang,
                viewModel = viewModel
            )
        } else {
            MainWorkspace(viewModel = viewModel, lang = lang)
        }
    }
}

@Composable
fun PinKeypadScreen(
    hasPin: Boolean,
    lang: String,
    viewModel: AppViewModel
) {
    var codeText by remember { mutableStateOf("") }
    val errorMsg by viewModel.pinError.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Engineering Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Text(
                text = "⚡ " + Localization.get("pin_title", lang) + " ⚡",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("pin_title_tag")
            )
            Text(
                text = if (hasPin) Localization.get("pin_enter", lang) else Localization.get("pin_create", lang),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (!hasPin) {
                Text(
                    text = Localization.get("pin_confirm", lang),
                    color = TerminalGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Indicator dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(vertical = 24.dp)
        ) {
            for (i in 0 until 4) {
                val isActive = i < codeText.length
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(
                            BorderStroke(2.dp, if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            RoundedCornerShape(4.dp)
                        )
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else Color.Transparent
                        )
                )
            }
        }

        // Error message space
        Box(
            modifier = Modifier
                .height(30.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (errorMsg != null) {
                Text(
                    text = errorMsg!!,
                    color = SafetyRed,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // KEYPAD Grid (3 x 4)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "OK")
            )

            keys.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row.forEach { char ->
                        Button(
                            onClick = {
                                when (char) {
                                    "C" -> {
                                        codeText = ""
                                    }
                                    "OK" -> {
                                        if (codeText.length == 4) {
                                            if (hasPin) {
                                                val success = viewModel.loginWithPin(codeText)
                                                if (!success) {
                                                    codeText = ""
                                                }
                                            } else {
                                                viewModel.createPin(codeText)
                                                Toast.makeText(context, "PIN Saved", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    else -> {
                                        if (codeText.length < 4) {
                                            codeText += char
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .testTag("keypad_btn_$char"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (char == "OK") TerminalGreen else if (char == "C") MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                                contentColor = if (char == "OK") Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = char,
                                fontWeight = FontWeight.Bold,
                                color = if (char == "OK") Color.White else if (char == "C") SafetyRed else MaterialTheme.colorScheme.primary,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainWorkspace(viewModel: AppViewModel, lang: String) {
    val activeTab by viewModel.currentTab.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // App Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outline)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "⚙️ " + Localization.get("app_title", lang),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = Localization.get("app_sub", lang),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Language & Logout Area
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { viewModel.toggleLanguage() },
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("lang_toggle_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = if (lang == Localization.LANG_RU) "RU 🌐" else "UZ 🌐",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { viewModel.logout() },
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                        .testTag("logout_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock App",
                        tint = SafetyRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Horizontal line separator
        Spacer(modifier = Modifier.height(2.dp).fillMaxWidth().background(MaterialTheme.colorScheme.primary))

        // Workspace Screens switching area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (activeTab) {
                "CALCULATOR" -> CalculatorTab(viewModel = viewModel, lang = lang)
                "REPORTS" -> ReportsArchiveTab(viewModel = viewModel, lang = lang)
                "AI_CIVIL" -> AiEngineerTab(viewModel = viewModel, lang = lang)
            }
        }

        // Simplified Bottom Menu Bar (3 tabs only: Smeta, Archive, AI Assistant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outline)
        ) {
            val tabs = listOf(
                Triple("CALCULATOR", Localization.get("menu_home", lang), "⚡"),
                Triple("REPORTS", Localization.get("menu_reports", lang), "📁"),
                Triple("AI_CIVIL", Localization.get("menu_ai", lang), "🤖")
            )

            tabs.forEach { (tabId, label, symbol) ->
                val isSelected = activeTab == tabId
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.setTab(tabId) }
                        .padding(vertical = 12.dp)
                        .testTag("tab_btn_$tabId"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = symbol,
                        fontSize = 18.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun CalculatorTab(viewModel: AppViewModel, lang: String) {
    val context = LocalContext.current
    val textInput by viewModel.calculationInput.collectAsState()
    val parsedItems by viewModel.parsedItemsList.collectAsState()
    val totalSum by viewModel.totalSum.collectAsState()
    val masterName by viewModel.masterName.collectAsState()
    val masterGrade by viewModel.masterGrade.collectAsState()
    val usdRate by viewModel.usdRate.collectAsState()

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.getOrNull(0) ?: ""
            if (spokenText.isNotEmpty()) {
                // Normalize voice recognition: "сом" -> "сум", "сомов" -> "сумов", "сома" -> "сума"
                val normalizedSpokenText = spokenText
                    .replace(Regex("""(?i)\bсом\b"""), "сум")
                    .replace(Regex("""(?i)\bсомов\b"""), "сумов")
                    .replace(Regex("""(?i)\bсома\b"""), "сума")
                val currentText = textInput
                val newText = if (currentText.isBlank()) normalizedSpokenText else "$currentText\n$normalizedSpokenText"
                viewModel.calculationInput.value = newText
            }
        }
    }

    var showProfileConfig by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Collapsible/Inline configuration block inside Calculator - removes the profile tab entirely!
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showProfileConfig = !showProfileConfig },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👷", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                val displayName = masterName.ifBlank { if (lang == Localization.LANG_RU) "Мастер" else "Usta" }
                                val displayRank = masterGrade.ifBlank { if (lang == Localization.LANG_RU) "Электрик" else "Elektrik" }
                                Text(
                                    text = displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "${Localization.get("excel_grade", lang)}: $displayRank",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Icon(
                            imageVector = if (showProfileConfig) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Config toggle",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    AnimatedVisibility(visible = showProfileConfig) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            
                            OutlinedTextField(
                                value = masterName,
                                onValueChange = { viewModel.saveMasterProfile(it, masterGrade) },
                                label = { Text(Localization.get("profile_name", lang), fontSize = 11.sp) },
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("inline_name_input"),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )

                            OutlinedTextField(
                                value = masterGrade,
                                onValueChange = { viewModel.saveMasterProfile(masterName, it) },
                                label = { Text(Localization.get("profile_grade", lang), fontSize = 11.sp) },
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("inline_grade_input"),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 4.dp))
                            
                            val googleEmail by viewModel.userGoogleEmail.collectAsState()
                            val googleLinked by viewModel.isGoogleLinked.collectAsState()
                            var showGoogleDialog by remember { mutableStateOf(false) }

                            Text(
                                text = Localization.get("bind_account_title", lang),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = Localization.get("bind_account_desc", lang),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (!googleLinked) {
                                Button(
                                    onClick = { showGoogleDialog = true },
                                    modifier = Modifier.fillMaxWidth().height(40.dp).testTag("google_link_btn"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = Localization.get("bind_btn", lang),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .border(BorderStroke(1.dp, TerminalGreen), RoundedCornerShape(4.dp))
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("👤", fontSize = 16.sp)
                                        Text(
                                            text = googleEmail,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = TerminalGreen,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "✅ Active",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = TerminalGreen,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Text(
                                        text = "⚡ " + Localization.get("cloud_sync_active", lang),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Button(
                                        onClick = { viewModel.unlinkGoogleAccount() },
                                        modifier = Modifier.fillMaxWidth().height(36.dp).testTag("google_unlink_btn"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Transparent,
                                            contentColor = SafetyRed
                                        ),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, SafetyRed.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = Localization.get("unbind_btn", lang),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            if (showGoogleDialog) {
                                var emailInput by remember { mutableStateOf("arasevumid832@gmail.com") }
                                AlertDialog(
                                    onDismissRequest = { showGoogleDialog = false },
                                    title = {
                                        Text(
                                            text = Localization.get("bind_account_title", lang),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text(
                                                text = Localization.get("bind_account_desc", lang),
                                                fontSize = 12.sp
                                            )
                                            OutlinedTextField(
                                                value = emailInput,
                                                onValueChange = { emailInput = it },
                                                label = { Text("Google Email", fontSize = 11.sp) },
                                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                if (emailInput.isNotBlank() && emailInput.contains("@")) {
                                                    viewModel.linkGoogleAccount(emailInput.trim())
                                                    showGoogleDialog = false
                                                    Toast.makeText(context, Localization.get("account_linked", lang), Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Please enter a valid Google Account!", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = "OK",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showGoogleDialog = false }) {
                                            Text(text = Localization.get("photo_cancel", lang), color = SafetyRed)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Photo Scanner AI Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "📷 " + Localization.get("photo_scanner_title", lang),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Localization.get("photo_scanner_desc", lang),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val isScanning by viewModel.isImageScanning.collectAsState()
                    val scanResult by viewModel.imageScanResult.collectAsState()
                    val scanError by viewModel.imageScanError.collectAsState()

                    val cameraLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.TakePicturePreview()
                    ) { bitmap: Bitmap? ->
                        if (bitmap != null) {
                            viewModel.scanImageWithGemini(bitmap, context)
                        }
                    }

                    val galleryLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        if (uri != null) {
                            try {
                                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION.SDK_INT) {
                                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                                    ImageDecoder.decodeBitmap(source)
                                } else {
                                    @Suppress("DEPRECATION")
                                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                                }
                                viewModel.scanImageWithGemini(bitmap, context)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    if (!isScanning && scanResult == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { cameraLauncher.launch(null) },
                                modifier = Modifier.weight(1f).height(40.dp).testTag("camera_scan_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = Localization.get("photo_camera", lang),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Button(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f).height(40.dp).testTag("gallery_scan_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = Localization.get("photo_gallery", lang),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    } else if (isScanning) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "⚡ " + Localization.get("photo_processing", lang),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    scanError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = err,
                            color = SafetyRed,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    scanResult?.let { resultText ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                                .border(BorderStroke(1.dp, TerminalGreen), RoundedCornerShape(4.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "✅ " + Localization.get("photo_scan_status", lang),
                                color = TerminalGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 120.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = resultText,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val prev = viewModel.calculationInput.value
                                        viewModel.calculationInput.value = if (prev.isBlank()) resultText else "$prev\n$resultText"
                                        viewModel.imageScanResult.value = null
                                    },
                                    modifier = Modifier.weight(1.2f).height(36.dp).testTag("apply_scan_btn"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TerminalGreen,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = Localization.get("photo_apply", lang),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Button(
                                    onClick = { viewModel.imageScanResult.value = null },
                                    modifier = Modifier.weight(0.8f).height(36.dp).testTag("discard_scan_btn"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = SafetyRed
                                    ),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, SafetyRed),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = Localization.get("photo_cancel", lang),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live text field input area for materials / description list
        item {
            Text(
                text = "📝 " + Localization.get("main_input_label", lang),
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = textInput,
                onValueChange = { viewModel.calculationInput.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .testTag("equipment_text_input"),
                placeholder = {
                    Text(
                        text = Localization.get("main_input_hint", lang),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == Localization.LANG_RU) "ru-RU" else "uz-UZ")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, if (lang == Localization.LANG_RU) "Говорите список материалов..." else "Materiallar ro'yxatini ayting...")
                            }
                            try {
                                speechRecognizerLauncher.launch(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Speech recognition is not supported on this device", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("voice_input_btn")
                    ) {
                        Text(
                            text = "🎙️",
                            fontSize = 20.sp
                        )
                    }
                },
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }

        // Live calculation list / parsed items
        item {
            Text(
                text = "📊 ДЕТАЛИЗАЦИЯ И РАСЧЁТ (LIVE REPORT)",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = LightBlueGlow,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (parsedItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(4.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = Localization.get("main_empty_error", lang),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Наименование / Name",
                                modifier = Modifier.weight(2f),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "Кол / Qty",
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.End
                            )
                            Text(
                                "Сумма / Total",
                                modifier = Modifier.weight(1.2f),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.End
                            )
                        }

                        parsedItems.forEach { item ->
                            Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(MaterialTheme.colorScheme.outline))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.name,
                                    modifier = Modifier.weight(2f),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                val displayQty = if (item.quantity % 1.0 == 0.0) item.quantity.toLong().toString() else item.quantity.toString()
                                Text(
                                    text = "$displayQty ${item.unit} x ${item.price.toInt()}",
                                    modifier = Modifier.weight(1.1f),
                                    color = VoltYellow,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.End
                                )
                                Text(
                                    text = "${item.total.toInt()} sum",
                                    modifier = Modifier.weight(1.1f),
                                    color = TerminalGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.End
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(MaterialTheme.colorScheme.primary))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 2.dp, start = 6.dp, end = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = Localization.get("report_total", lang),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${totalSum.toInt()} СУМ / UZS",
                                fontWeight = FontWeight.Bold,
                                color = TerminalGreen,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        val totalInUsd = if (usdRate > 0.0) totalSum / usdRate else 0.0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 4.dp, start = 6.dp, end = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${Localization.get("rate_usd_title", lang)}: 1$ = ${usdRate.toInt()} UZS",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = String.format(Locale.US, "$ %.2f USD", totalInUsd),
                                fontWeight = FontWeight.Bold,
                                color = VoltYellow,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Custom Excel Filename Input Field
        item {
            val fileNameVal by viewModel.customFileName.collectAsState()
            
            OutlinedTextField(
                value = fileNameVal,
                onValueChange = { viewModel.customFileName.value = it },
                label = { Text("📁 " + Localization.get("excel_filename_title", lang), fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                placeholder = { Text("Замин", fontSize = 13.sp, fontFamily = FontFamily.Monospace) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("excel_filename_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Action Trigger Button
        item {
            Button(
                onClick = {
                    if (parsedItems.isEmpty()) {
                        Toast.makeText(context, Localization.get("main_empty_error", lang), Toast.LENGTH_LONG).show()
                    } else {
                        viewModel.generateAndShareExcel(context)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("submit_excel_generation_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share Excel Share Icon",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = Localization.get("main_btn_generate", lang),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun ReportsArchiveTab(viewModel: AppViewModel, lang: String) {
    val context = LocalContext.current
    val reportsList by viewModel.allReports.collectAsState()
    val selectedReport by viewModel.selectedReportForPreview.collectAsState()
    val showDeleteConfirm by viewModel.showDeleteConfirm.collectAsState()
    val usdRate by viewModel.usdRate.collectAsState()

    // Confirm Delete Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteReport() },
            title = {
                Text(
                    text = Localization.get("delete_confirm_title", lang),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = SafetyRed
                )
            },
            text = {
                Text(
                    text = Localization.get("delete_confirm_msg", lang),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDeleteReport() },
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyRed, contentColor = Color.White)
                ) {
                    Text(Localization.get("delete_yes", lang), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.cancelDeleteReport() }
                ) {
                    Text(Localization.get("delete_no", lang), color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (selectedReport != null) {
            DetailedReportPreview(
                report = selectedReport!!,
                lang = lang,
                usdRate = usdRate,
                onDismiss = { viewModel.selectReportForPreview(null) },
                onResend = { viewModel.resendReportExcel(context, selectedReport!!) },
                onDelete = { viewModel.requestDeleteReport(selectedReport!!.id) }
            )
        } else {
            Text(
                text = "⚡ " + Localization.get("reports_title", lang),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (reportsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📁", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = Localization.get("reports_empty", lang),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(reportsList) { report ->
                        ReportItemRow(report = report, lang = lang, onClick = {
                            viewModel.selectReportForPreview(report)
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun ReportItemRow(report: Report, lang: String, onClick: () -> Unit) {
    val items = remember(report.serializedItems) { ReportSerializer.decode(report.serializedItems) }
    val displayDate = remember(report.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(report.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("report_item_card_${report.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${Localization.get("excel_executor", lang)}: ${report.masterName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${Localization.get("report_date", lang)} $displayDate",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = "${Localization.get("report_usd_rate", lang)} ${report.usdRate.toInt()} UZS",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = "Items: ${items.size} приборов / позиций",
                    fontSize = 12.sp,
                    color = LightBlueGlow,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${report.totalSum.toInt()} сум",
                    fontWeight = FontWeight.Bold,
                    color = TerminalGreen,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .background(
                            if (report.language == Localization.LANG_RU) CopperOrange.copy(alpha = 0.15f) else TerminalGreen.copy(alpha = 0.15f),
                            RoundedCornerShape(2.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = report.language,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (report.language == Localization.LANG_RU) CopperOrange else TerminalGreen
                    )
                }
            }
        }
    }
}

@Composable
fun DetailedReportPreview(
    report: Report,
    lang: String,
    usdRate: Double,
    onDismiss: () -> Unit,
    onResend: () -> Unit,
    onDelete: () -> Unit
) {
    val items = remember(report.serializedItems) { ReportSerializer.decode(report.serializedItems) }
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("report_preview_screen")
    ) {
        // Preview Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.testTag("back_to_archive_btn")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = Localization.get("report_detail", lang),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp
            )
            IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_report_btn")) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = SafetyRed)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Executor details card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "💼 ДАННЫЕ О РАСЧЕТЕ",
                            fontWeight = FontWeight.Bold,
                            color = VoltYellow,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${Localization.get("excel_executor", lang)}: ${report.masterName}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${Localization.get("excel_grade", lang)}: ${report.masterGrade}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${Localization.get("report_date", lang)} ${formatter.format(Date(report.timestamp))}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${Localization.get("report_usd_rate", lang)} ${report.usdRate.toInt()} UZS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Raw input text log
            item {
                Text(
                    text = "📝 ОРИГИНАЛЬНЫЙ ТЕКСТ:",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = LightBlueGlow
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline))
                        .padding(10.dp)
                ) {
                    Text(
                        text = report.inputText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Local generated items table
            item {
                Text(
                    text = "⚙️ СВОДНАЯ СМЕТА:",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = TerminalGreen
                )
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Материал / Прибор",
                                modifier = Modifier.weight(2f),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Кол",
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.End
                            )
                            Text(
                                text = "Итого",
                                modifier = Modifier.weight(1.2f),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.End
                            )
                        }

                        items.forEach { item ->
                            Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(MaterialTheme.colorScheme.outline))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.name,
                                    modifier = Modifier.weight(2f),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                val displayQty = if (item.quantity % 1.0 == 0.0) item.quantity.toLong().toString() else item.quantity.toString()
                                Text(
                                    text = "$displayQty ${item.unit} x ${item.price.toInt()}",
                                    modifier = Modifier.weight(1.1f),
                                    color = VoltYellow,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.End
                                )
                                Text(
                                    text = "${item.total.toInt()} sum",
                                    modifier = Modifier.weight(1.1f),
                                    color = TerminalGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.End
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(MaterialTheme.colorScheme.primary))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 2.dp, start = 6.dp, end = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = Localization.get("report_total", lang),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${report.totalSum.toInt()} UZS",
                                fontWeight = FontWeight.Bold,
                                color = TerminalGreen,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        val totalInUsd = if (report.usdRate > 0.0) report.totalSum / report.usdRate else 0.0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 4.dp, start = 6.dp, end = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${Localization.get("rate_usd_title", lang)}: 1$ = ${report.usdRate.toInt()} UZS",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = String.format(Locale.US, "$ %.2f USD", totalInUsd),
                                fontWeight = FontWeight.Bold,
                                color = VoltYellow,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Resend Excel button
        Button(
            onClick = onResend,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(vertical = 4.dp)
                .testTag("resend_excel_btn"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Re-Share File")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = Localization.get("report_resend", lang),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun AiEngineerTab(viewModel: AppViewModel, lang: String) {
    val promptText by viewModel.aiQueryString.collectAsState()
    val aiResponseText by viewModel.aiResponseText.collectAsState()
    val isAiRunning by viewModel.isAiRunning.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // AI Assistant Card (Simplified, friendly, high-contrast mode)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "🤖 " + Localization.get("ai_title", lang),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = Localization.get("ai_sub", lang),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(SafetyRed.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "⚡ ПУЭ / ГОСТ",
                            fontSize = 10.sp,
                            color = SafetyRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (lang == Localization.LANG_RU) "Максимальная помощь в расчетах!" else "Hisob-kitobda maksimal yordam!",
                        fontSize = 11.sp,
                        color = TerminalGreen,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Active Chat Content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline))
                .padding(12.dp)
        ) {
            if (aiResponseText.isEmpty() && !isAiRunning) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = Localization.get("ai_hint", lang),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        if (isAiRunning) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = Localization.get("ai_btn_loading", lang),
                                    color = VoltYellow,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Safety recommendation line
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SafetyRed.copy(alpha = 0.1f))
                                        .border(BorderStroke(1.dp, SafetyRed))
                                        .padding(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("⚠️", fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = Localization.get("ai_safety_alert", lang),
                                            color = SafetyRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(14.dp))

                                // Render response message lines nicely
                                val lines = aiResponseText.split("\n")
                                lines.forEach { line ->
                                    if (line.startsWith("###")) {
                                        Text(
                                            line.replace("###", "").trim(),
                                            color = LightBlueGlow,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    } else if (line.startsWith("##") || line.startsWith("#")) {
                                        Text(
                                            line.replace(Regex("[#]"), "").trim(),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    } else {
                                        Text(
                                            text = line,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Ask button and TextField prompt input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = promptText,
                onValueChange = { viewModel.aiQueryString.value = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_chef_engineer_prompt_field"),
                placeholder = {
                    Text(
                        text = if (lang == Localization.LANG_RU) "Например: Кабель на розетки..." else "Masalan: Rozetkalar uchun kabel...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { viewModel.askAiEngineer() },
                modifier = Modifier
                    .height(52.dp)
                    .testTag("ask_ai_engineer_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(4.dp),
                enabled = !isAiRunning && promptText.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Ask"
                )
            }
        }
    }
}
