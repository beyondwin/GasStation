import com.android.build.api.dsl.LibraryExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.file.Directory
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

plugins {
    id("gasstation.android.library")
    id("gasstation.android.hilt")
    id("gasstation.android.room")
}

extensions.configure<LibraryExtension> {
    namespace = "com.gasstation.core.database"
    defaultConfig {
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
    }
    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
    sourceSets.named("androidTest") {
        assets.directories.add("$projectDir/schemas")
    }
}

class RoomSchemaArgProvider(
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val schemaDir: Directory,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> = listOf("room.schemaLocation=${schemaDir.asFile.path}")
}

extensions.configure<KspExtension> {
    val schemaOutput = providers.gradleProperty("gasstation.roomSchemaOutput")
        .map { layout.dir(providers.provider { file(it).canonicalFile }).get() }
        .orElse(layout.projectDirectory.dir("schemas"))
    arg(RoomSchemaArgProvider(schemaOutput.get()))
}

dependencies {
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestUtil(libs.androidx.test.services)
}
