package com.gasstation.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ContractApiConventionTest {
    @Test
    fun exactFiveActiveJvmContractsOwnImmutableDumpMappings() {
        val active =
            listOf(
                ":app",
                ":core:model",
                ":core:network",
                ":core:observability",
                ":domain:location",
                ":domain:settings",
                ":domain:station",
            )

        assertEquals(
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
            ),
            requireContractApiModules(active),
        )
    }

    @Test
    fun topologyCrossCheckRejectsNewDomainAndMissingApprovedModule() {
        assertThrows(IllegalArgumentException::class.java) {
            requireContractApiModules(
                listOf(
                    ":core:model",
                    ":core:observability",
                    ":domain:location",
                    ":domain:settings",
                    ":domain:station",
                    ":domain:new-contract",
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireContractApiModules(
                listOf(
                    ":core:model",
                    ":core:observability",
                    ":domain:location",
                    ":domain:settings",
                ),
            )
        }
    }
}
