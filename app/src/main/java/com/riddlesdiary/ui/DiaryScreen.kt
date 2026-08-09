package com.riddlesdiary.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.riddlesdiary.ai.AIProvider
import com.riddlesdiary.viewmodel.DiaryStatus
import com.riddlesdiary.viewmodel.DiaryViewModel
import com.riddlesdiary.ui.theme.DiaryCover
import com.riddlesdiary.ui.theme.DiaryCoverLight
import com.riddlesdiary.ui.theme.DiarySpine
import com.riddlesdiary.ui.theme.GoldAccent
import com.riddlesdiary.ui.theme.GoldDim
import com.riddlesdiary.ui.theme.InkGlow
import com.riddlesdiary.ui.theme.InkGreen
import com.riddlesdiary.ui.theme.Parchment
import com.riddlesdiary.ui.theme.ParchmentAged
import com.riddlesdiary.ui.theme.ParchmentDark
import com.riddlesdiary.ui.theme.TextDark
import com.riddlesdiary.ui.theme.TextMuted

/**
 * Main screen for Riddle's Notebook.
 */
@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DiaryCover)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp)
        ) {
            // Diary cover frame
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(3.dp, GoldDim, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
            ) {
                ParchmentBackground()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Top bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusIndicator(status = uiState.status)
                        Spacer(modifier = Modifier.weight(1f))

                        // Provider badge
                        val provider = uiState.selectedProvider
                        Text(
                            text = provider.displayName,
                            fontFamily = FontFamily.Serif,
                            fontSize = 10.sp,
                            color = if (provider.isFree) InkGreen.copy(alpha = 0.7f) else GoldAccent.copy(alpha = 0.7f)
                        )

                        IconButton(onClick = { viewModel.clearPage() },
                            enabled = uiState.status !is DiaryStatus.Thinking) {
                            Icon(Icons.Default.Clear, "Clear page",
                                tint = TextMuted.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, "Settings",
                                tint = GoldAccent.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Main content
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        // Drawing canvas
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                            DrawingCanvas(
                                isEnabled = uiState.isDrawingEnabled,
                                inkAlpha = uiState.inkAlpha,
                                canvasClearToken = uiState.canvasClearToken,
                                onStrokeStarted = { viewModel.onStrokeStarted() },
                                onStrokeEnded = { viewModel.onStrokeEnded(it) },
                                onWritingPaused = { viewModel.onWritingPaused() }
                            )
                        }

                        // AI Response
                        androidx.compose.animation.AnimatedVisibility(
                            visible = uiState.displayedResponse.isNotEmpty(),
                            enter = fadeIn(tween(300)),
                            exit = fadeOut(tween(200)),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.TopStart) {
                                Text(
                                    text = uiState.displayedResponse,
                                    fontFamily = FontFamily.Cursive,
                                    fontSize = 20.sp,
                                    color = InkGreen,
                                    lineHeight = 32.sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Thinking
                        androidx.compose.animation.AnimatedVisibility(
                            visible = uiState.status is DiaryStatus.Thinking,
                            enter = fadeIn(tween(500)),
                            exit = fadeOut(tween(300)),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = GoldAccent, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("The diary is thinking…", fontFamily = FontFamily.Serif, fontSize = 14.sp, color = TextMuted)
                                    Text("via ${uiState.selectedProvider.displayName}", fontFamily = FontFamily.Serif, fontSize = 11.sp, color = TextMuted.copy(alpha = 0.5f))
                                }
                            }
                        }

                        // Text input fallback (when handwriting recognition is unavailable)
                        if (uiState.recognitionUnavailable && uiState.status is DiaryStatus.Idle && uiState.displayedResponse.isEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = uiState.typedText,
                                    onValueChange = { viewModel.onTypedTextChanged(it) },
                                    placeholder = {
                                        Text(
                                            "Type your message to the diary…",
                                            fontFamily = FontFamily.Serif,
                                            fontSize = 14.sp,
                                            color = TextMuted.copy(alpha = 0.5f)
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = InkGreen,
                                        unfocusedTextColor = InkGreen,
                                        focusedBorderColor = GoldAccent,
                                        unfocusedBorderColor = GoldDim.copy(alpha = 0.3f),
                                        cursorColor = InkGreen,
                                        focusedContainerColor = ParchmentDark.copy(alpha = 0.2f),
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Cursive,
                                        fontSize = 16.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { viewModel.submitTypedText(uiState.typedText) },
                                    enabled = uiState.typedText.isNotBlank()
                                ) {
                                    Icon(
                                        Icons.Default.Send,
                                        contentDescription = "Send",
                                        tint = if (uiState.typedText.isNotBlank()) GoldAccent else GoldDim.copy(alpha = 0.3f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Empty state
                        if (uiState.status is DiaryStatus.Idle && uiState.displayedResponse.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Write something in the notebook…",
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 16.sp,
                                        color = TextMuted.copy(alpha = 0.4f),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Using ${uiState.selectedProvider.displayName}",
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 11.sp,
                                        color = TextMuted.copy(alpha = 0.3f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom spine
            Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(DiarySpine))
        }

        // Error message
        AnimatedVisibility(
            visible = uiState.errorMessage != null && uiState.status is DiaryStatus.Error,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.dismissError() },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF4A1818)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = uiState.errorMessage ?: "",
                    color = ParchmentDark,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // Settings dialog
    if (showSettings) {
        SettingsDialog(
            currentProvider = uiState.selectedProvider,
            currentModelId = uiState.selectedModelId,
            currentApiKey = uiState.apiKey,
            availableProviders = AIProvider.entries,
            getModels = { viewModel.getModels(it) },
            onProviderSelected = { viewModel.setProvider(it) },
            onModelSelected = { viewModel.setModel(it) },
            onApiKeySaved = { viewModel.setApiKey(it) },
            onDismiss = { showSettings = false }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Settings Dialog — User-Friendly Design
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDialog(
    currentProvider: AIProvider,
    currentModelId: String,
    currentApiKey: String,
    availableProviders: List<AIProvider>,
    getModels: (AIProvider) -> List<com.riddlesdiary.ai.AIModel>,
    onProviderSelected: (AIProvider) -> Unit,
    onModelSelected: (String) -> Unit,
    onApiKeySaved: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedProvider by remember { mutableStateOf(currentProvider) }
    var selectedModelId by remember { mutableStateOf(currentModelId) }
    var apiKey by remember { mutableStateOf(currentApiKey) }
    var showAdvanced by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    val models = remember(selectedProvider) { getModels(selectedProvider) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(if (showAdvanced) 560.dp else 380.dp),
            colors = CardDefaults.cardColors(containerColor = ParchmentAged),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // ── Title ───────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, null, tint = GoldAccent, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Settings",
                        fontFamily = FontFamily.Serif,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── AI Status Card ──────────────────────────────
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = InkGreen.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Star, null,
                                tint = if (currentApiKey.isNotBlank()) InkGreen else GoldAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (currentApiKey.isNotBlank())
                                    "AI Ready — ${selectedProvider.displayName}"
                                else
                                    "AI Setup Required",
                                fontFamily = FontFamily.Serif,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextDark
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            when {
                                currentApiKey.isNotBlank() ->
                                    "The diary's magic is active. Write something and the AI will respond using ${models.firstOrNull { it.id == selectedModelId }?.displayName ?: selectedModelId}."
                                else ->
                                    "The diary needs a connection to the magical realm. Tap below to set it up — it's free and takes 30 seconds."
                            },
                            fontFamily = FontFamily.Serif,
                            fontSize = 12.sp,
                            color = TextMuted,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Quick Setup (if no key) ─────────────────────
                if (currentApiKey.isBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))
                                context.startActivity(intent)
                            },
                        colors = CardDefaults.cardColors(containerColor = GoldAccent.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.OpenInNew, null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Get Free AI Access",
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextDark
                                )
                                Text(
                                    "Sign up on Groq Cloud → Copy your free API key → Paste it below",
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("Your API Key", fontFamily = FontFamily.Serif) },
                        placeholder = {
                            Text("Paste your gsk_... key here", fontFamily = FontFamily.Serif, fontSize = 12.sp, color = TextMuted)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextDark,
                            unfocusedTextColor = TextDark,
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = GoldDim.copy(alpha = 0.5f),
                            cursorColor = InkGreen,
                            focusedContainerColor = ParchmentDark.copy(alpha = 0.3f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                }

                // ── Advanced Settings Toggle ────────────────────
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = GoldDim.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (showAdvanced) "▾ Hide Advanced Settings" else "▸ Advanced Settings",
                        fontFamily = FontFamily.Serif,
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }

                // ── Advanced: Provider & Model Selection ─────────
                if (showAdvanced) {
                    Text(
                        "AI Provider",
                        fontFamily = FontFamily.Serif,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    availableProviders.forEach { provider ->
                        val isSelected = provider == selectedProvider
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable {
                                    selectedProvider = provider
                                    selectedModelId = provider.defaultModel
                                    apiKey = ""
                                    onProviderSelected(provider)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) InkGreen.copy(alpha = 0.1f)
                                    else Color.White.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            provider.displayName,
                                            fontFamily = FontFamily.Serif,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = TextDark
                                        )
                                        if (provider.isFree) {
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                "FREE",
                                                fontFamily = FontFamily.Serif,
                                                fontSize = 8.sp,
                                                color = InkGreen,
                                                modifier = Modifier
                                                    .background(InkGreen.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, "Selected", tint = InkGreen, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Model picker
                    ExposedDropdownMenuBox(
                        expanded = modelDropdownExpanded,
                        onExpandedChange = { modelDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = models.firstOrNull { it.id == selectedModelId }?.displayName ?: selectedModelId,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Model", fontFamily = FontFamily.Serif, fontSize = 12.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextDark, unfocusedTextColor = TextDark,
                                focusedBorderColor = GoldAccent, unfocusedBorderColor = GoldDim.copy(alpha = 0.5f),
                                focusedContainerColor = ParchmentDark.copy(alpha = 0.3f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.3f)
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif, fontSize = 13.sp)
                        )
                        ExposedDropdownMenu(
                            expanded = modelDropdownExpanded,
                            onDismissRequest = { modelDropdownExpanded = false }
                        ) {
                            models.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model.displayName, fontFamily = FontFamily.Serif, fontSize = 13.sp) },
                                    onClick = {
                                        selectedModelId = model.id
                                        modelDropdownExpanded = false
                                        onModelSelected(model.id)
                                    }
                                )
                            }
                        }
                    }

                    // API key for providers that need one
                    if (selectedProvider.requiresApiKey) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("${selectedProvider.displayName} API Key", fontFamily = FontFamily.Serif, fontSize = 11.sp) },
                            placeholder = { Text("Enter API key...", fontFamily = FontFamily.Serif, fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextDark, unfocusedTextColor = TextDark,
                                focusedBorderColor = GoldAccent, unfocusedBorderColor = GoldDim.copy(alpha = 0.5f),
                                focusedContainerColor = ParchmentDark.copy(alpha = 0.3f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Done Button ─────────────────────────────────
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", fontFamily = FontFamily.Serif, color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (apiKey.isNotBlank()) {
                                onApiKeySaved(apiKey)
                            }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = InkGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Done", fontFamily = FontFamily.Serif)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Parchment Background
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ParchmentBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(color = Parchment, size = size)

        val random = java.util.Random(42)
        val edgeWidth = size.width * 0.08f

        // Vignette edges
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(ParchmentDark.copy(alpha = 0.6f), ParchmentDark.copy(alpha = 0.2f), Color.Transparent),
                startX = 0f, endX = edgeWidth
            ),
            size = Size(edgeWidth, size.height)
        )
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, ParchmentDark.copy(alpha = 0.2f), ParchmentDark.copy(alpha = 0.6f)),
                startX = size.width - edgeWidth, endX = size.width
            ),
            topLeft = Offset(size.width - edgeWidth, 0f),
            size = Size(edgeWidth, size.height)
        )
        drawRect(
            brush = Brush.verticalGradient(
                listOf(ParchmentDark.copy(alpha = 0.5f), ParchmentDark.copy(alpha = 0.1f), Color.Transparent),
                startY = 0f, endY = edgeWidth
            ),
            size = Size(size.width, edgeWidth)
        )
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, ParchmentDark.copy(alpha = 0.1f), ParchmentDark.copy(alpha = 0.5f)),
                startY = size.height - edgeWidth, endY = size.height
            ),
            topLeft = Offset(0f, size.height - edgeWidth),
            size = Size(size.width, edgeWidth)
        )

        // Paper texture lines
        var y = 8f
        while (y < size.height) {
            drawLine(TextMuted.copy(alpha = 0.03f), Offset(0f, y), Offset(size.width, y), 0.5f)
            y += 8f
        }

        // Subtle stains
        for (i in 0..5) {
            drawCircle(
                ParchmentDark.copy(alpha = 0.08f),
                radius = 40f + random.nextFloat() * 60f,
                center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Status Indicator
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun StatusIndicator(status: DiaryStatus) {
    val color = when (status) {
        is DiaryStatus.Idle -> TextMuted.copy(alpha = 0.3f)
        is DiaryStatus.Writing -> InkGlow.copy(alpha = 0.8f)
        is DiaryStatus.Fading -> InkGlow.copy(alpha = 0.5f)
        is DiaryStatus.Recognizing -> GoldAccent.copy(alpha = 0.7f)
        is DiaryStatus.Thinking -> GoldAccent.copy(alpha = 0.9f)
        is DiaryStatus.Responding -> InkGreen.copy(alpha = 0.7f)
        is DiaryStatus.Error -> Color(0xFFCC3333)
    }
    val label = when (status) {
        is DiaryStatus.Idle -> ""
        is DiaryStatus.Writing -> "Writing…"
        is DiaryStatus.Fading -> "Vanishing…"
        is DiaryStatus.Recognizing -> "Reading…"
        is DiaryStatus.Thinking -> "Thinking…"
        is DiaryStatus.Responding -> "Responding…"
        is DiaryStatus.Error -> ""
    }
    if (label.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(modifier = Modifier.size(8.dp)) { drawCircle(color, radius = 4f) }
            Text(label, fontFamily = FontFamily.Serif, fontSize = 12.sp, color = color)
        }
    }
}
