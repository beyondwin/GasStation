package com.gasstation.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

internal data class ContractApiModule(
    val projectPath: String,
    val dumpPath: String,
    val packageRoot: String,
)

internal val contractApiModules: List<ContractApiModule> =
    listOf(
        ContractApiModule(":core:model", "core/model/api/model.api", "com.gasstation.core.model"),
        ContractApiModule(
            ":core:observability",
            "core/observability/api/observability.api",
            "com.gasstation.core.observability",
        ),
        ContractApiModule(
            ":domain:location",
            "domain/location/api/location.api",
            "com.gasstation.domain.location",
        ),
        ContractApiModule(
            ":domain:settings",
            "domain/settings/api/settings.api",
            "com.gasstation.domain.settings",
        ),
        ContractApiModule(
            ":domain:station",
            "domain/station/api/station.api",
            "com.gasstation.domain.station",
        ),
    )

internal fun requireContractApiModules(activeModulePaths: List<String>): List<ContractApiModule> {
    require(activeModulePaths.size == activeModulePaths.toSet().size) {
        "gasstation.activeModulePaths contains duplicate paths"
    }
    val selectedByPredicate =
        activeModulePaths.filter { path ->
            path.startsWith(":domain:") || path == ":core:model" || path == ":core:observability"
        }.sorted()
    val expected = contractApiModules.map(ContractApiModule::projectPath)
    require(selectedByPredicate == expected) {
        "strict contract API topology mismatch: predicate=$selectedByPredicate mapping=$expected"
    }
    return contractApiModules
}

@OptIn(ExperimentalAbiValidation::class)
internal fun Project.configureGasStationContractApiConvention() {
    if (!(path.startsWith(":domain:") || path == ":core:model" || path == ":core:observability")) return
    extensions.configure<KotlinJvmProjectExtension> {
        explicitApiWarning()
        abiValidation()
    }
}
