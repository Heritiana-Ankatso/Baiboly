import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        // A release keystore can be supplied through gradle properties; otherwise the
        // release build is signed with the debug key so it installs for testing.
        create("release") {
            val store = providers.gradleProperty("baiboly.storeFile").orNull
            if (store != null) {
                storeFile = file(store)
                storePassword = providers.gradleProperty("baiboly.storePassword").get()
                keyAlias = providers.gradleProperty("baiboly.keyAlias").get()
                keyPassword = providers.gradleProperty("baiboly.keyPassword").get()
            } else {
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
    inputs.file(rootProject.file("tools/abbreviations.tsv"))
    outputs.dir(assets)
    onlyIf { !file("src/main/assets/databases/manifest.json").exists() }
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
