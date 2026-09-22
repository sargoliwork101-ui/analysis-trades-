package com.pulse.market.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pulse.market.data.WidgetConfig
import com.pulse.market.ui.settings.SettingsScreen
import com.pulse.market.widget.StockWidgetProvider

class MainActivity : ComponentActivity() {

    private var widgetId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readWidgetId(intent)

        // در جریان «افزودن ویجت»، حتی با دکمه‌ی برگشت هم ویجت اضافه شود
        // (اگر نتیجه cancel شود، لانچر پیام Couldn't add widget را نشان می‌دهد)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (widgetId != 0) {
                    closeAsConfigure()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        setContent {
            PulseTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(
                        fromWidget = widgetId != 0,
                        onApply = { cfg -> finishConfigure(cfg) }
                    )
                }
            }
        }
    }

    /** با singleTop ممکن است اکتیویتی زنده بماند و ویجت بعدی از onNewIntent بیاید */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readWidgetId(intent)
    }

    private fun readWidgetId(intent: Intent?) {
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0) ?: 0
    }

    /** ذخیره‌ی تنظیمات + پایان جریان افزودن ویجت */
    private fun finishConfigure(cfg: WidgetConfig) {
        StockWidgetProvider.requestUpdate(this)
        closeAsConfigure()
    }

    /** نتیجه‌ی موفق به لانچر — بدون این خط، ویجت اضافه نمی‌شود */
    private fun closeAsConfigure() {
        if (widgetId != 0) {
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            )
            finish()
        }
    }
}
