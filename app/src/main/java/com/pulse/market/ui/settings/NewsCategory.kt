package com.pulse.market.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.market.data.MarketNewsItem
import com.pulse.market.data.NewsCategory
import com.pulse.market.data.NewsRegion
import com.pulse.market.data.NewsSortOrder
import com.pulse.market.data.PumpAiReviewer
import com.pulse.market.data.sortedFor
import com.pulse.market.ui.Format

/** تب اصلی خبرها: ایران/جهان، فیلتر بازار، خلاصه AI و لینک کاملِ منبع. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewsCategory(
    onConfigureAi: () -> Unit,
    vm: NewsViewModel = viewModel()
) {
    val context = LocalContext.current
    var categoryName by rememberSaveable { mutableStateOf("") }
    var regionName by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(NewsSortOrder.NEWEST.name) }
    var visibleCount by rememberSaveable { mutableIntStateOf(15) }

    LaunchedEffect(Unit) { vm.onVisible() }
    LaunchedEffect(categoryName, regionName, sortName) { visibleCount = 15 }

    val category = NewsCategory.entries.firstOrNull { it.name == categoryName }
    val region = NewsRegion.entries.firstOrNull { it.name == regionName }
    val sortOrder = NewsSortOrder.entries.firstOrNull { it.name == sortName } ?: NewsSortOrder.NEWEST
    val aiTarget = remember(vm.aiConfig) { PumpAiReviewer.connectionTarget(vm.aiConfig) }
    val analyzedItems = remember(vm.items) { vm.items.filter { it.hasCompleteAiAnalysis } }
    val filtered = remember(analyzedItems, category, region, sortOrder) {
        analyzedItems.filter { item ->
            (category == null || item.category == category) &&
                (region == null || item.region == region)
        }.sortedFor(sortOrder)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(
            "خبرهای مهم بازار",
            "ایران و جهان • کریپتو، طلا، ارز، بورس و اقتصاد"
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            vm.aiBusy -> vm.aiProgress.ifBlank {
                                "هوش مصنوعی در حال تحلیل است؛ هر دسته تا ۵ دقیقه فرصت پاسخ دارد…"
                            }
                            vm.aiConfig.enabled && vm.aiConfig.anyReady ->
                                "ترجمه و تحلیل کامل فارسی با ${vm.aiConfig.model} فعال است"
                            else -> "برای نمایش خبرهای فارسی و تحلیل‌شده، هوش مصنوعی را تنظیم کن"
                        },
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    "هر خبر با عنوان و چکیدهٔ فارسی، نظر AI، اثر احتمالی و الگوی تاریخی نمایش داده می‌شود. " +
                        "نظر و سابقه از دانش عمومی مدل است، جست‌وجوی زنده یا پیش‌بینی قطعی نیست؛ برای تصمیم مالی منبع را باز کن.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "تیترها مستقیماً از RSS گوگل‌نیوز، نبض بورس، اقتصاد۲۴، اقتصاد آنلاین، CoinDesk و CNBC خوانده می‌شوند. " +
                        if (aiTarget != null) {
                            "ترجمه/تحلیل با همان API تنظیم‌شده و مستقیم روی ${aiTarget.display} (${aiTarget.route}) انجام می‌شود."
                        } else {
                            "برای ترجمه/تحلیل هنوز مقصد AI معتبری تنظیم نشده است."
                        },
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (!vm.aiConfig.enabled || !vm.aiConfig.anyReady) {
                    OutlinedButton(onClick = onConfigureAi, modifier = Modifier.fillMaxWidth()) {
                        Text("تنظیم هوش مصنوعی", fontSize = 12.sp)
                    }
                }
            }
        }

        if (vm.loading || vm.aiBusy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (vm.fetchedAt > 0L) "آخرین بررسی: ${Format.dateTime(vm.fetchedAt)}" else "هنوز بررسی نشده",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (analyzedItems.isNotEmpty()) {
                    Text(
                        "${Format.toPersianDigits(analyzedItems.size.toString())} خبر فارسیِ تحلیل‌شده",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            OutlinedButton(onClick = vm::refresh, enabled = !vm.loading && !vm.aiBusy) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("تازه‌سازی", fontSize = 11.5.sp)
            }
        }

        if (vm.message.isNotBlank()) InfoCard(vm.message)
        vm.aiError?.let { InfoCard("ترجمه و تحلیل هوش مصنوعی: $it") }

        Text("محدوده", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            FilterChip(
                selected = region == null,
                onClick = { regionName = "" },
                label = { Text("همه", fontSize = 11.sp) }
            )
            for (option in NewsRegion.entries) {
                FilterChip(
                    selected = region == option,
                    onClick = { regionName = option.name },
                    label = { Text(option.label, fontSize = 11.sp) }
                )
            }
        }

        Text("بازار", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            FilterChip(
                selected = category == null,
                onClick = { categoryName = "" },
                label = { Text("همه", fontSize = 11.sp) }
            )
            for (option in NewsCategory.entries) {
                FilterChip(
                    selected = category == option,
                    onClick = { categoryName = option.name },
                    label = { Text(option.label, fontSize = 11.sp) }
                )
            }
        }

        Text("مرتب‌سازی", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            for (option in NewsSortOrder.entries) {
                FilterChip(
                    selected = sortOrder == option,
                    onClick = { sortName = option.name },
                    label = { Text(option.label, fontSize = 11.sp) }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        when {
            !vm.aiConfig.enabled || !vm.aiConfig.anyReady -> InfoCard(
                "برای اینکه همهٔ خبرها فارسی باشند و پیش از چکیده، نظر، اثر بازار و سابقهٔ تاریخی AI داشته باشند، ابتدا هوش مصنوعی را تنظیم کن."
            )
            (vm.loading || vm.aiBusy) && analyzedItems.isEmpty() -> InfoCard(
                "در حال جمع‌آوری، ترجمه و تحلیل فارسی خبرهای مهم…"
            )
            filtered.isEmpty() -> InfoCard(
                if (analyzedItems.isEmpty()) {
                    "هنوز تحلیل فارسیِ کاملی آماده نشده؛ تازه‌سازی کن یا وضعیت سرویس AI را بررسی کن."
                } else {
                    "در این فیلتر خبر فارسیِ تحلیل‌شده‌ای پیدا نشد؛ محدوده یا بازار دیگری را انتخاب کن."
                }
            )
            else -> {
                for (item in filtered.take(visibleCount)) {
                    NewsCard(
                        item = item,
                        onOpen = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                            }
                        }
                    )
                }
                if (visibleCount < filtered.size) {
                    OutlinedButton(
                        onClick = { visibleCount = (visibleCount + 15).coerceAtMost(filtered.size) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "نمایش خبرهای بیشتر (${Format.toPersianDigits((filtered.size - visibleCount).toString())})"
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewsCard(item: MarketNewsItem, onOpen: () -> Unit) {
    val accent = when (item.category) {
        NewsCategory.CRYPTO -> Color(0xFFF59E0B)
        NewsCategory.GOLD -> Color(0xFFD4A017)
        NewsCategory.CURRENCY -> Color(0xFF0EA5E9)
        NewsCategory.STOCKS -> Color(0xFF22C55E)
        NewsCategory.ECONOMY -> Color(0xFF8B5CF6)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                NewsBadge(item.category.label, accent)
                NewsBadge(item.region.label, MaterialTheme.colorScheme.secondary)
                if (item.isFresh()) NewsBadge("تازه • ۲۴ ساعت اخیر", MaterialTheme.colorScheme.primary)
                if (item.importance >= 78) NewsBadge("مهم", MaterialTheme.colorScheme.error)
                NewsBadge("تحلیل کامل AI", MaterialTheme.colorScheme.primary)
            }
            Text(
                if (item.publishedAt > 0L) {
                    "تاریخ انتشار: ${Format.dateTime(item.publishedAt)} • ${Format.relativeTime(item.publishedAt)}"
                } else {
                    "تاریخ انتشار: نامشخص"
                },
                fontSize = 10.8.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                item.displayTitle,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            NewsAnalysisSection(
                label = "نظر هوش مصنوعی و سناریوی محتمل",
                text = item.aiOutlook,
                color = MaterialTheme.colorScheme.primary
            )
            NewsAnalysisSection(
                label = "اثر احتمالی روی بازارها و دارایی‌ها",
                text = item.marketImpact,
                color = accent
            )
            NewsAnalysisSection(
                label = "سابقه یا الگوی تاریخی مشابه",
                text = item.historicalContext,
                color = MaterialTheme.colorScheme.tertiary
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            NewsAnalysisSection(
                label = "چکیدهٔ فارسی خبر",
                text = item.aiSummary,
                color = MaterialTheme.colorScheme.onSurface
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        listOf(item.source, item.host).filter { it.isNotBlank() }.distinct().joinToString(" • "),
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(onClick = onOpen) {
                    Text("مطالعهٔ کامل", fontSize = 11.5.sp)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun NewsAnalysisSection(label: String, text: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text,
            fontSize = 11.7.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NewsBadge(label: String, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
