package com.pulse.market.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.PumpAiBackup
import com.pulse.market.data.PumpAiConfig
import com.pulse.market.data.PumpAiReviewer

/**
 * بخش «سرویس‌های پشتیبان هوش مصنوعی».
 *
 * چرا: یک کلید به‌تنهایی خیلی وقت‌ها جواب نمی‌دهد — سهمیه تمام می‌شود (۴۲۹)، کلید
 * باطل می‌شود (۴۰۱) یا سرویس از ایران مسدود است. با تعریف چند سرویس، برنامه بدون
 * دخالت کاربر همان درخواست را از سرویس بعدی می‌گیرد.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AiBackupProvidersSection(
    config: PumpAiConfig,
    onChange: (PumpAiConfig) -> Unit
) {
    InnerRow {
        Text(
            "سرویس‌های پشتیبان (در صورت خطای سرویس اصلی)",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Hint(
            "ترتیب تلاش: اول سرویس اصلی بالا، بعد پشتیبان ۱، ۲ و ۳. اگر سرویسی خطای کلید، " +
                "سهمیه، فیلتر جغرافیایی یا قطعی شبکه بدهد، همان درخواست خودکار به سرویس بعدی " +
                "فرستاده می‌شود؛ پس می‌توانی چند هوش مصنوعی مختلف (مثلاً Gemini + OpenRouter + Claude) " +
                "را کنار هم بگذاری."
        )

        config.backups.forEachIndexed { index, backup ->
            key(index) {
                BackupProviderCard(
                    index = index,
                    backup = backup,
                    onChange = { updated ->
                        onChange(
                            config.copy(
                                backups = config.backups.toMutableList().also { it[index] = updated }
                            )
                        )
                    },
                    onRemove = {
                        onChange(
                            config.copy(
                                backups = config.backups.filterIndexed { position, _ -> position != index }
                            )
                        )
                    }
                )
            }
        }

        if (config.backups.size < PumpAiConfig.MAX_BACKUPS) {
            OutlinedButton(
                onClick = { onChange(config.copy(backups = config.backups + PumpAiBackup())) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "افزودن سرویس پشتیبان (${config.backups.size + 1} از ${PumpAiConfig.MAX_BACKUPS})",
                    fontSize = 11.5.sp
                )
            }
        } else {
            Hint("سقف ${PumpAiConfig.MAX_BACKUPS} سرویس پشتیبان پر شده است.")
        }

        Hint(
            if (config.anyReady) {
                "سرویس‌های آمادهٔ زنجیره: ${config.chain.size} " +
                    "(${if (config.primary.isReady) "اصلی" else "بدون اصلی"} + " +
                    "${config.readyBackupCount} پشتیبان)"
            } else {
                "هنوز هیچ سرویس کاملی تنظیم نشده است؛ آدرس API و نام مدل حداقل یک سرویس را پر کن."
            }
        )
    }
}

/** کارت ویرایش یک سرویس پشتیبان. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BackupProviderCard(
    index: Int,
    backup: PumpAiBackup,
    onChange: (PumpAiBackup) -> Unit,
    onRemove: () -> Unit
) {
    val asConfig = PumpAiConfig(
        enabled = true,
        endpoint = backup.endpoint,
        model = backup.model,
        apiKey = backup.apiKey,
        providerSearch = backup.providerSearch
    )
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "پشتیبان ${index + 1}",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        when {
                            !backup.active -> "خاموش؛ در زنجیره استفاده نمی‌شود"
                            asConfig.isReady -> "آماده است"
                            backup.configured -> "ناقص؛ آدرس و نام مدل را کامل کن"
                            else -> "خالی"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = backup.active,
                    onCheckedChange = { onChange(backup.copy(active = it)) }
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "حذف پشتیبان ${index + 1}",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (preset in PumpAiConfig.PRESETS) {
                    FilterChip(
                        selected = backup.matches(preset),
                        onClick = {
                            onChange(
                                backup.copy(
                                    endpoint = preset.endpoint,
                                    model = preset.model,
                                    providerSearch = preset.providerSearch
                                )
                            )
                        },
                        label = { Text(preset.title, fontSize = 11.5.sp) }
                    )
                }
            }

            OutlinedTextField(
                value = backup.endpoint,
                onValueChange = { onChange(backup.copy(endpoint = it.take(500))) },
                label = { Text("آدرس API پشتیبان") },
                placeholder = { Text("https://example.com/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = backup.model,
                onValueChange = { onChange(backup.copy(model = it.take(150))) },
                label = { Text("نام مدل پشتیبان") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = backup.apiKey,
                onValueChange = { onChange(backup.copy(apiKey = it.take(1_000))) },
                label = { Text("API Key پشتیبان") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            PumpAiReviewer.keyWarning(backup.endpoint, backup.apiKey)?.let { problem ->
                if (backup.configured) Hint("⚠️ $problem")
            }
            if (asConfig.insecureKeyTransport) {
                Hint("⚠️ روی آدرس HTTP کلید ارسال نمی‌شود؛ آدرس HTTPS بگذار.")
            }
            if (backup.endpoint.isNotBlank() && !asConfig.endpointValid) {
                Hint("آدرس باید HTTP(S) معتبر، بدون نام کاربری، query یا fragment باشد.")
            }
        }
    }
}
