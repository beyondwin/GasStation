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
    arg(RoomSchemaArgProvider(layout.projectDirectory.dir("schemas")))
}

dependencies {
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
}
