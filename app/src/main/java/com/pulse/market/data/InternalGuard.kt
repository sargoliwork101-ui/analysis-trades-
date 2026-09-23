package com.pulse.market.data

import android.content.Context
import android.content.Intent
import java.util.UUID

/**
 * ─────────────────────────────────────────────────────────────────────────
 * نگهبان «درون‌برنامه‌ای بودن» پیام‌های ویجت.
 *
 * مسئله‌ی امنیتی: رسیورهای ویجت باید exported باشند (لانچر سیستمی باید بتواند
 * APPWIDGET_UPDATE بفرستد)، پس هر برنامه‌ی دیگری روی گوشی هم می‌تواند اکشن‌های
 * سفارشی ما (ACTION_REFRESH / ACTION_TOGGLE_LIVE) را بفرستد — یعنی با یک
 * broadcast ساده، رفرش اجباری (مصرف باتری/دیتا) یا روشن/خاموش کردن حالت زنده
 * از بیرون ممکن بود.
 *
 * راه‌حل: یک توکن تصادفی که فقط در حافظه‌ی خصوصی همین برنامه است. این توکن
 * داخل PendingIntent های روی ویجت گذاشته می‌شود (FLAG_IMMUTABLE، پس لانچر هم
 * نمی‌تواند تغییرش دهد). اکشن‌های سفارشی بدون توکن درست، نادیده گرفته می‌شوند.
 * پیام‌های سیستمی (APPWIDGET_UPDATE و…) چون اکشن سفارشی ما نیستند، دست‌نخورده
 * کار می‌کنند.
 * ─────────────────────────────────────────────────────────────────────────
 */
object InternalGuard {

    const val EXTRA_TOKEN = "com.pulse.market.EXTRA_INTERNAL_TOKEN"

    private const val PREF = "pulse_internal"
    private const val KEY_TOKEN = "token"

    /** توکن ثابتِ همین نصب — یک‌بار ساخته و در SharedPreferences خصوصی ذخیره می‌شود */
    fun token(context: Context): String {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.getString(KEY_TOKEN, null)?.let { if (it.length >= 16) return it }
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_TOKEN, fresh).apply()
        return fresh
    }

    /** گذاشتن توکن روی intent ای که خودمان می‌سازیم (PendingIntent های ویجت) */
    fun sign(context: Context, intent: Intent): Intent =
        intent.putExtra(EXTRA_TOKEN, token(context))

    /**
     * آیا این broadcast از خودِ برنامه‌ی ما آمده؟
     * false یعنی اکشن سفارشی باید نادیده گرفته شود.
     */
    fun isTrusted(context: Context, intent: Intent): Boolean =
        intent.getStringExtra(EXTRA_TOKEN) == token(context)
}
