plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.dagger.hilt.android") version "2.49" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}

subprojects {
    val buildRoot = System.getenv("TRAE_BUILD_ROOT")?.takeIf { it.isNotBlank() }
        ?: "${rootProject.projectDir}/.build_asmr_player_android"
    layout.buildDirectory.set(file("$buildRoot/${project.name}"))
}
