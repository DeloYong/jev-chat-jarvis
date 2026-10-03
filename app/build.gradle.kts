import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing: reads a properties file kept OUTSIDE the repo
// (storeFile / storePassword / keyAlias / keyPassword). Override the path with
// the JEV_KEYSTORE_PROPS env var. Without it, release builds are unsigned.
val releaseProps = Properties().apply {
    val f = file(System.getenv("JEV_KEYSTORE_PROPS") ?: "H:/android/keys/jev-release.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

// 仓库根 .env 只放非敏感的 JEV_CLOUD_BASE。优先级: -PjevCloudBase > .env > 空。
val dotEnv = Properties().apply {
    val f = rootProject.file(".env")
    if (f.exists()) f.reader(Charsets.UTF_8).use { load(it) }
}

android {
    namespace = "com.jev.probe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jev.probe"
        minSdk = 30
        targetSdk = 35
        versionCode = 5
        versionName = "1.4"

        // Hosted-mode gateway root, e.g. -PjevCloudBase=https://gw.example.com (or in
        // ~/.gradle/gradle.properties). Blank keeps every hosted/paywall surface hidden,
        // so the plain open-source build behaves exactly as before.
        val cloudBase = ((project.findProperty("jevCloudBase") as String?)
            ?: dotEnv.getProperty("JEV_CLOUD_BASE") ?: "").trim().replace("\"", "")
        buildConfigField("String", "CLOUD_BASE_URL", "\"$cloudBase\"")
        // 订阅版: 带 https 网关地址即关闭自带密钥入口; 非 https 视为未配置, 防止明文传令牌。
        buildConfigField("boolean", "HOSTED_ONLY", cloudBase.startsWith("https://").toString())

        // ML Kit's bundled Chinese recognizer ships native libs for every ABI.
        // The target phone (and every phone this can run on: minSdk 30) is
        // arm64, so keep only that one — the other three are dead weight.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (releaseProps.isNotEmpty()) {
            create("release") {
                storeFile = file(releaseProps.getProperty("storeFile"))
                storePassword = releaseProps.getProperty("storePassword")
                keyAlias = releaseProps.getProperty("keyAlias")
                keyPassword = releaseProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // Uncompressed, page-aligned .so files: required for the 16 KB page-size
    // devices Android 15+ ships, and it lets the loader mmap the ML Kit natives
    // instead of unpacking them at install time.
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // Real org.json for unit tests: the mockable android.jar only has stubs that
    // throw, so anything parsing a response body could not be tested at all.
    testImplementation("org.json:json:20240303")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // On-device OCR. The *bundled* Chinese model (not the play-services variant):
    // it works on phones with no Google Play services and needs no model download.
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
}
