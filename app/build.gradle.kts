import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

fun gitCommitCount(): Int = try {
    val p = ProcessBuilder("git", "rev-list", "--count", "HEAD").directory(rootDir).redirectErrorStream(true).start()
    p.inputStream.bufferedReader().readText().trim().toInt().also { p.waitFor() }
} catch (e: Exception) {
    1
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "mg.baiboly.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "mg.baiboly.app"
        minSdk = 24
        targetSdk = 35
        // versionCode rises with every commit, so each build installs over the previous one;
        // versionName is the human version, set in gradle.properties for each release.
        versionCode = gitCommitCount()
        versionName = providers.gradleProperty("baiboly.versionName").getOrElse("1.0.0")
    }

    signingConfigs {
        // The permanent release key is never in the repository. It comes from
        // environment variables (GitHub Actions secrets) or from a local, ignored
        // keystore.properties file. Without it, release builds fall back to the
        // debug key and say so: such an APK cannot update an installed release.
        create("release") {
            val props = Properties().apply {
                rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
            }
            fun secret(name: String): String? = System.getenv(name) ?: props.getProperty(name)
            val store = secret("BAIBOLY_KEYSTORE")
            if (store != null && file(store).exists()) {
                storeFile = file(store)
                storeType = "pkcs12"
                storePassword = secret("BAIBOLY_KEYSTORE_PASSWORD")
                keyAlias = secret("BAIBOLY_KEY_ALIAS") ?: "baiboly"
                keyPassword = secret("BAIBOLY_KEY_PASSWORD")
            } else {
                logger.warn("Baiboly: no release key (BAIBOLY_KEYSTORE); release APK is signed with the DEBUG key and cannot update an installed release.")
                val debug = getByName("debug")
                storeFile = debug.storeFile
                storePassword = debug.storePassword
                keyAlias = debug.keyAlias
                keyPassword = debug.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "3g"
            it.forkEvery = 1 // Robolectric resets preferences but not SQLite files between tests
            // a failing assertion's message (not only its file and line) in the CI log
            it.testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL; events("failed") }
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

// Brief task 2: the Bible databases are produced by tools/build_data.py.
// Built once (sources are cached in tools/.cache), then reused.
val bibleData by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the bundled Bible and study databases (tools/build_data.py)."
    val assets = file("src/main/assets/databases")
    inputs.file(rootProject.file("tools/build_data.py"))
    inputs.file(rootProject.file("tools/packs.py"))
    inputs.file(rootProject.file("tools/abbreviations.tsv"))
    outputs.dir(assets)
    // The optional downloads (other texts, Hebrew and Greek, commentaries) are
    // written to src/debug/assets: debug builds and the tests keep them inside
    // the APK, release builds leave them out (tools/packs.py).
    outputs.dir(file("src/debug/assets/databases"))
    onlyIf { !file("src/main/assets/databases/manifest.json").exists() || !file("src/main/assets/databases/packs.json").exists() }
    commandLine("python3", rootProject.file("tools/build_data.py").path)
}
tasks.named("preBuild") { dependsOn(bibleData) }

dependencies {
    implementation(project(":data"))
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended") // standard icons (R8 keeps only the ones used)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // JVM tests of the real databases and UI (Robolectric): the acceptance scenarios.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit-ktx:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation(kotlin("test"))
}
