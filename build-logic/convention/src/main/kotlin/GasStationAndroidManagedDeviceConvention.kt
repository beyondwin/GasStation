import com.android.build.api.dsl.CommonExtension

internal fun CommonExtension.configureGasStationManagedDevices() {
    testOptions.managedDevices.localDevices.apply {
        create("gasstationPixel2Api28") {
            device = "Pixel 2"
            apiLevel = 28
            systemImageSource = "aosp"
        }
        create("gasstationPixel2Api36") {
            device = "Pixel 2"
            apiLevel = 36
            systemImageSource = "google"
        }
    }
}
