plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.pulse.market"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pulse.market"
        minSdk = 26          // اندروید ۸ به بالا
        targetSdk = 34
        versionCode = 10
        versionName = "1.9"
    }

    /**
     * کلیدِ ثابتِ امضا (app/ci-debug.keystore).
     *
     * چرا مهم است: اگر کلید تعریف نشود، گریدل روی هر ماشین/هر اجرای CI یک
     * debug.keystore تصادفی می‌سازد؛ در نتیجه هر APK امضای متفاوتی می‌گیرد و
     * اندروید نصبِ نسخه‌ی جدید روی نسخه‌ی نصب‌شده را رد می‌کند
     * (INSTALL_FAILED_UPDATE_INCOMPATIBLE) — همان چیزی که آپدیت درون‌برنامه‌ای را
     * هم از کار می‌انداخت. این یک کلید «دیباگ/سایدلود» است، نه کلید انتشار در
     * گوگل‌پلی؛ پس مخفی نیست و در مخزن نگه داشته می‌شود تا همیشه یکی بماند.
     */
    val stableKeystore = file("ci-debug.keystore")

    signingConfigs {
        if (stableKeystore.exists()) {
            create("stable") {
                storeFile = stableKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
                storeType = "PKCS12"
                // امضای v1 هم روشن باشد: هم سازگاری با اندرویدهای قدیمی‌تر،
                // هم می‌شود امضای APK را بدون apksigner (با openssl) بررسی کرد.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        // کلید ثابت برای هر دو حالت؛ اگر فایل کلید نبود، به رفتار پیش‌فرض برمی‌گردیم
        val stable = signingConfigs.findByName("stable")

        debug {
            signingConfig = stable ?: signingConfigs.getByName("debug")
        }

        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = stable ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Compose (صفحه‌ی تنظیمات و انتخاب منبع)
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // شبکه و پارس کردن داده
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.1")

    // ذخیره‌ی تنظیمات و سریالایز
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // به‌روزرسانی پس‌زمینه
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
