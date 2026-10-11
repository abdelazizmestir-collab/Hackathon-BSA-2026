import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Secrets live in local.properties (never committed):
//   PIMLICO_API_KEY=pim_xxx
//   SWISSWALLET_CHAIN=base-sepolia        (optional: base-sepolia | sepolia, default base-sepolia)
//   RPC_URL=https://sepolia.base.org       (optional override of the node URL)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(name: String, default: String = ""): String =
    (localProps.getProperty(name) ?: System.getenv(name) ?: default).trim()

android {
    namespace = "com.example.swisswallet"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.swisswallet"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "PIMLICO_API_KEY", "\"${secret("PIMLICO_API_KEY")}\"")
        buildConfigField("String", "SWISSWALLET_CHAIN", "\"${secret("SWISSWALLET_CHAIN", "base-sepolia")}\"")
        buildConfigField("String", "RPC_URL", "\"${secret("RPC_URL", secret("SEPOLIA_RPC_URL"))}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            // web3j / bouncycastle ship duplicate META-INF entries
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/FastDoubleParser-*",
                "META-INF/DISCLAIMER",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.web3j.crypto)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
