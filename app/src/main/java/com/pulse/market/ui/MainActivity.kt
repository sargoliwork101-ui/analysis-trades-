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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.pulse.market.data.ConfigStore
import com.pulse.market.ui.settings.SettingsScreen
import com.pulse.market.widget.StockWidgetProvider
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** صفر = الگوی پیش‌فرض؛ هر شماره‌ی دیگر = تنظیمات همان ویجت */
    private val widgetIdState = mutableStateOf(0)
    private var closingConfigure = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readWidgetId(intent)

        // در جریان «افزودن ویجت»، حتی با دکمه‌ی برگشت هم ویجت اضافه شود
        // (اگر نتیجه cancel شود، لانچر پیام Couldn't add widget را نشان می‌دهد)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (widgetIdState.value != 0) {
                    if (closingConfigure) return
                    closingConfigure = true
                    val widgetId = widgetIdState.value
                    lifecycleScope.launch {
                        // برگشت هم طبق قرارداد، ویجت را اضافه می‌کند؛ اما باید اول
                        // ذخیره‌ی debounce شده تمام شود تا لانچر تنظیم قدیمی نبیند.
                        ConfigStore.awaitPending(widgetId)
                        StockWidgetProvider.syncLiveService(this@MainActivity)
                        StockWidgetProvider.requestUpdate(this@MainActivity)
                        closeAsConfigure()
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        setContent {
            PulseTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // با تغییر ویجت (افزودن دومی در حالت singleTop) کل صفحه از نو ساخته می‌شود
                    key(widgetIdState.value) {
                        SettingsScreen(
                            widgetId = widgetIdState.value,
                            isAddFlow = intent?.action == AppWidgetManager.ACTION_APPWIDGET_CONFIGURE,
                            onApply = { finishConfigure() }
                        )
                    }
                }
            }
        }
    }

    /** با singleTop ممکن است اکتیویتی زنده بماند و ویجت بعدی از onNewIntent بیاید */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readWidgetId(intent)
    }

    private fun readWidgetId(intent: Intent?) {
        widgetIdState.value = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0) ?: 0
    }

    /**
     * پایان جریان افزودن ویجت.
     * خودِ تنظیمات پیش از این در SettingsScreen ذخیره شده (save + NonCancellable)،
     * اینجا فقط به لانچر «موفق» اعلام می‌کنیم و ویجت‌ها را دوباره رندر می‌کنیم.
     */
    private fun finishConfigure() {
        StockWidgetProvider.requestUpdate(this)
        closeAsConfigure()
    }

    /** نتیجه‌ی موفق به لانچر — بدون این خط، ویجت اضافه نمی‌شود */
    private fun closeAsConfigure() {
        if (widgetIdState.value != 0) {
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetIdState.value)
            )
            finish()
        }
    }
}
