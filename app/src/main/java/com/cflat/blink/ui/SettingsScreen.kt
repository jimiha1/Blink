package com.cflat.blink.ui

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cflat.blink.R
import com.cflat.blink.util.PrefsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    context: Context,
    onBack: () -> Unit
) {
    // ── State ──────────────────────────────────────────────────────────
    var workMinutes by remember {
        mutableFloatStateOf(PrefsManager.getWorkDurationMs(context) / 60000f)
    }
    var restSeconds by remember {
        mutableFloatStateOf(PrefsManager.getRestDurationMs(context) / 1000f)
    }
    var alertMode by remember { mutableStateOf(PrefsManager.getAlertMode(context)) }
    var overlayDismissable by remember { mutableStateOf(PrefsManager.isOverlayDismissable(context)) }
    var overlayOpacity by remember { mutableFloatStateOf(PrefsManager.getOverlayOpacity(context)) }
    var overlayStyle by remember { mutableStateOf(PrefsManager.getOverlayStyle(context)) }
    var overlayTheme by remember { mutableStateOf(PrefsManager.getOverlayTheme(context)) }
    var autoStart by remember { mutableStateOf(PrefsManager.isAutoStartEnabled(context)) }
    var appLanguage by remember { mutableStateOf(PrefsManager.getAppLanguage(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            // ── Timers ───────────────────────────────────────────────
            SettingsSectionHeader(stringResource(R.string.settings_timers), Icons.Rounded.Timer)
            SettingsCard {
                SliderRow(
                    label = stringResource(R.string.settings_work_duration),
                    value = workMinutes,
                    displayText = stringResource(R.string.settings_unit_min, workMinutes.roundToInt()),
                    range = 1f..60f,
                    steps = 58,
                    onValueChange = { workMinutes = it },
                    onValueChangeFinished = {
                        PrefsManager.setWorkDurationMs(
                            context, workMinutes.roundToInt() * 60 * 1000L
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SliderRow(
                    label = stringResource(R.string.settings_rest_duration),
                    value = restSeconds,
                    displayText = stringResource(R.string.settings_unit_sec, restSeconds.roundToInt()),
                    range = 10f..60f,
                    steps = 49,
                    onValueChange = { restSeconds = it },
                    onValueChangeFinished = {
                        PrefsManager.setRestDurationMs(
                            context, restSeconds.roundToInt() * 1000L
                        )
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Alert Mode ───────────────────────────────────────────
            SettingsSectionHeader(stringResource(R.string.settings_alert), Icons.Rounded.Notifications)
            SettingsCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_alert_mode),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.settings_alert_mode_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    @OptIn(ExperimentalMaterial3Api::class)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            "both" to R.string.settings_alert_mode_both,
                            "overlay" to R.string.settings_alert_mode_overlay,
                            "notification" to R.string.settings_alert_mode_notif
                        ).forEachIndexed { index, (key, labelRes) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index, 3),
                                onClick = {
                                    alertMode = key
                                    PrefsManager.setAlertMode(context, key)
                                },
                                selected = alertMode == key,
                                label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Overlay Appearance ───────────────────────────────────
            SettingsSectionHeader(stringResource(R.string.settings_section_overlay), Icons.Rounded.Layers)
            SettingsCard {
                // Dismissable toggle
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dismissable), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(stringResource(R.string.settings_dismissable_desc)) },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = overlayDismissable,
                            onCheckedChange = {
                                overlayDismissable = it
                                PrefsManager.setOverlayDismissable(context, it)
                            }
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Opacity slider
                SliderRow(
                    label = stringResource(R.string.settings_opacity),
                    value = overlayOpacity,
                    displayText = stringResource(R.string.settings_unit_percent, (overlayOpacity * 100).roundToInt()),
                    range = 0.3f..1.0f,
                    steps = 13,
                    onValueChange = { overlayOpacity = it },
                    onValueChangeFinished = {
                        PrefsManager.setOverlayOpacity(context, overlayOpacity)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Style selector
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_overlay_style),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.settings_overlay_style_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    @OptIn(ExperimentalMaterial3Api::class)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            "expressive" to R.string.settings_overlay_style_expressive,
                            "calm" to R.string.settings_overlay_style_calm,
                            "minimal" to R.string.settings_overlay_style_minimal
                        ).forEachIndexed { index, (key, labelRes) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index, 3),
                                onClick = {
                                    overlayStyle = key
                                    PrefsManager.setOverlayStyle(context, key)
                                },
                                selected = overlayStyle == key,
                                label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Theme selector
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_overlay_theme),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.settings_overlay_theme_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    @OptIn(ExperimentalMaterial3Api::class)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            "system" to R.string.settings_overlay_theme_system,
                            "dark" to R.string.settings_overlay_theme_dark,
                            "light" to R.string.settings_overlay_theme_light
                        ).forEachIndexed { index, (key, labelRes) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index, 3),
                                onClick = {
                                    overlayTheme = key
                                    PrefsManager.setOverlayTheme(context, key)
                                },
                                selected = overlayTheme == key,
                                label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── System ───────────────────────────────────────────────
            SettingsSectionHeader(stringResource(R.string.settings_section_system), Icons.Rounded.PowerSettingsNew)
            SettingsCard {
                // Battery Optimization
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                var isIgnoring by remember {
                    mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName))
                }
                val scope = rememberCoroutineScope()

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_battery_optimization), fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            if (isIgnoring) stringResource(R.string.settings_battery_unrestricted)
                            else stringResource(R.string.settings_battery_restricted)
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = if (isIgnoring) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    },
                    trailingContent = {
                        if (!isIgnoring) {
                            FilledTonalButton(
                                onClick = {
                                    @Suppress("BatteryLife")
                                    val intent = Intent(
                                        AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                    scope.launch {
                                        delay(1500)
                                        isIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) { Text(stringResource(R.string.settings_disable)) }
                        } else {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_start_on_boot), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(stringResource(R.string.settings_start_on_boot_desc)) },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.PowerSettingsNew,
                            contentDescription = null,
                            tint = if (autoStart) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = autoStart,
                            onCheckedChange = {
                                autoStart = it
                                PrefsManager.setAutoStartEnabled(context, it)
                            }
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Language selector
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_language),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.settings_language_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    @OptIn(ExperimentalMaterial3Api::class)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            "system" to R.string.settings_lang_system,
                            "zh" to R.string.settings_lang_zh,
                            "en" to R.string.settings_lang_en
                        ).forEachIndexed { index, (key, labelRes) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index, 3),
                                onClick = {
                                    appLanguage = key
                                    PrefsManager.setAppLanguage(context, key)
                                    val lm = context.getSystemService(LocaleManager::class.java)
                                    lm?.applicationLocales = when (key) {
                                        "zh" -> LocaleList.forLanguageTags("zh-CN")
                                        "en" -> LocaleList.forLanguageTags("en")
                                        else -> LocaleList.getEmptyLocaleList()
                                    }
                                },
                                selected = appLanguage == key,
                                label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── OEM-specific guidance ────────────────────────────────
            val oemGuide = getOemBatteryGuide(context)
            if (oemGuide != null) {
                SettingsSectionHeader(stringResource(R.string.settings_section_device_specific), Icons.Rounded.PhoneAndroid)
                SettingsCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            oemGuide.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            oemGuide.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        oemGuide.steps.forEach { step ->
                            Text(
                                step,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Shared helpers ─────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(content = content)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    displayText: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                displayText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── OEM battery guidance ──────────────────────────────────────────────────────

private data class OemBatteryGuide(
    val title: String,
    val description: String,
    val steps: List<String>
)

private fun getOemBatteryGuide(context: Context): OemBatteryGuide? {
    val manufacturer = Build.MANUFACTURER.lowercase()
    return when {
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ->
            OemBatteryGuide(
                title = context.getString(R.string.oem_xiaomi_title),
                description = context.getString(R.string.oem_xiaomi_desc),
                steps = listOf(
                    context.getString(R.string.oem_xiaomi_step1),
                    context.getString(R.string.oem_xiaomi_step2),
                    context.getString(R.string.oem_xiaomi_step3),
                    context.getString(R.string.oem_xiaomi_step4),
                    "",
                    context.getString(R.string.oem_xiaomi_note)
                )
            )
        manufacturer.contains("samsung") ->
            OemBatteryGuide(
                title = context.getString(R.string.oem_samsung_title),
                description = context.getString(R.string.oem_samsung_desc),
                steps = listOf(
                    context.getString(R.string.oem_samsung_step1),
                    context.getString(R.string.oem_samsung_step2),
                    context.getString(R.string.oem_samsung_step3),
                    "",
                    context.getString(R.string.oem_samsung_note)
                )
            )
        manufacturer.contains("huawei") || manufacturer.contains("honor") ->
            OemBatteryGuide(
                title = context.getString(R.string.oem_huawei_title),
                description = context.getString(R.string.oem_huawei_desc),
                steps = listOf(
                    context.getString(R.string.oem_huawei_step1),
                    context.getString(R.string.oem_huawei_step2),
                    context.getString(R.string.oem_huawei_step3)
                )
            )
        manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") ->
            OemBatteryGuide(
                title = context.getString(R.string.oem_oppo_title),
                description = context.getString(R.string.oem_oppo_desc),
                steps = listOf(
                    context.getString(R.string.oem_oppo_step1),
                    context.getString(R.string.oem_oppo_step2),
                    context.getString(R.string.oem_oppo_step3),
                    context.getString(R.string.oem_oppo_step4),
                    "",
                    context.getString(R.string.oem_oppo_note)
                )
            )
        manufacturer.contains("vivo") || manufacturer.contains("iqoo") ->
            OemBatteryGuide(
                title = context.getString(R.string.oem_vivo_title),
                description = context.getString(R.string.oem_vivo_desc),
                steps = listOf(
                    context.getString(R.string.oem_vivo_step1),
                    context.getString(R.string.oem_vivo_step2),
                    context.getString(R.string.oem_vivo_step3),
                    context.getString(R.string.oem_vivo_step4),
                    "",
                    context.getString(R.string.oem_vivo_note)
                )
            )
        manufacturer.contains("motorola") || manufacturer.contains("lenovo") ->
            OemBatteryGuide(
                title = context.getString(R.string.oem_motorola_title),
                description = context.getString(R.string.oem_motorola_desc),
                steps = listOf(
                    context.getString(R.string.oem_motorola_step1),
                    context.getString(R.string.oem_motorola_step2),
                    context.getString(R.string.oem_motorola_step3),
                    "",
                    context.getString(R.string.oem_motorola_note)
                )
            )
        manufacturer.contains("asus") ->
            OemBatteryGuide(
                title = context.getString(R.string.oem_asus_title),
                description = context.getString(R.string.oem_asus_desc),
                steps = listOf(
                    context.getString(R.string.oem_asus_step1),
                    context.getString(R.string.oem_asus_step2)
                )
            )
        manufacturer.contains("nokia") ->
            OemBatteryGuide(
                title = context.getString(R.string.oem_nokia_title),
                description = context.getString(R.string.oem_nokia_desc),
                steps = listOf(
                    context.getString(R.string.oem_nokia_step1),
                    context.getString(R.string.oem_nokia_step2)
                )
            )
        else -> null
    }
}
