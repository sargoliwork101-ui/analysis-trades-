package com.pulse.market.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0) ?: 0

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

    /** اگر از دکمه‌ی «افزودن ویجت» باز شده باشیم، باید نتیجه را به لانچر برگردانیم */
    private fun finishConfigure(cfg: WidgetConfig) {
        StockWidgetProvider.requestUpdate(this)
        if (widgetId != 0) {
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            )
            finish()
        }
    }
}
