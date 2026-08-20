import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class GasStationAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("gasstation.spotless")
        pluginManager.apply("com.android.library")
        configureGasStationKotlinAndTestConventions()

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

        extensions.configure<LibraryExtension> {
            compileSdk = libs.findVersion("compileSdk").get().requiredVersion.toInt()
            buildToolsVersion = libs.findVersion("buildTools").get().requiredVersion

            defaultConfig {
                minSdk = libs.findVersion("minSdk").get().requiredVersion.toInt()
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            sourceSets.getByName("test").resources.directories.add(
                rootProject.file("config/robolectric").absolutePath,
            )

            compileOptions {
                isCoreLibraryDesugaringEnabled = true
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            testOptions {
                unitTests.isIncludeAndroidResources = true
                unitTests.all {
                    it.jvmArgs("--enable-native-access=ALL-UNNAMED")
                }
            }
            configureGasStationManagedDevices()

            packaging {
                resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }

            lint {
                configureGasStationAndroidLint(this, checkDependencies = false)
            }
        }

        dependencies {
            add("coreLibraryDesugaring", libs.findLibrary("android-desugarJdkLibs").get())
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
            add("testImplementation", libs.findLibrary("androidx-test-core").get())
            add("testImplementation", libs.findLibrary("robolectric").get())
        }
    }
}
