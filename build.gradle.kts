plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// Brief task 1: any audio, vibration or sound-effect API, or an INTERNET /
// VIBRATE permission, fails the build until it is reviewed and allow-listed.
val silenceCheck by tasks.registering(Exec::class) {
    group = "verification"
    description = "Fails if audio/vibration/sound APIs or INTERNET/VIBRATE permissions appear."
    commandLine("bash", "${rootDir}/tools/check_silence.sh")
}
subprojects {
    tasks.matching { it.name == "preBuild" || it.name == "check" || it.name == "compileKotlin" }.configureEach {
        dependsOn(rootProject.tasks.named("silenceCheck"))
    }
}
