plugins {
    id("com.android.application") version "9.0.0" apply false
    id("com.android.test") version "9.0.0" apply false
    id("com.google.dagger.hilt.android") version "2.59.2" apply false
    id("androidx.baselineprofile") version "1.5.0-alpha03" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
    id("com.google.devtools.ksp") version "2.3.6" apply false
}

subprojects {
    val buildRoot = System.getenv("TRAE_BUILD_ROOT")?.takeIf { it.isNotBlank() }
        ?: "${rootProject.projectDir}/.build_asmr_player_android"
    layout.buildDirectory.set(file("$buildRoot/${project.name}"))
}
