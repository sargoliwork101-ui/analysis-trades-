package com.pulse.market.data

import kotlinx.serialization.Serializable

/**
 * فهرست نام‌دار و سراسری نمادها. اعمال فهرست روی یک ویجت، یک کپی مستقل از نمادها
 * می‌سازد؛ بنابراین ویرایش بعدی ویجت، فهرست ذخیره‌شده را ناخواسته تغییر نمی‌دهد.
 */
@Serializable
data class Watchlist(
    val id: String,
    val name: String,
    val sourceIds: List<String>,
    val symbols: List<SymbolDef>,
    val updatedAt: Long = System.currentTimeMillis()
)
