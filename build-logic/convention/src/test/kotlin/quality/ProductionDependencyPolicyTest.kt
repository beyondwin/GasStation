package com.gasstation.buildlogic.quality

import java.nio.charset.StandardCharsets.UTF_8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionDependencyPolicyTest {
    @Test
    fun canonicalPolicyRequiresExactModulesScopesAndSortedRecords() {
        val text =
            """
            schema-version=1
            enforcement=blocking
            module|:core:model
            module|:domain:sample
            scope|:domain:sample|external|org.jetbrains.kotlin:kotlin-stdlib|implementation|compile=main|runtime=main
            scope|:domain:sample|project|:core:model|api|compile=main|runtime=main
            """.trimIndent() + "\n"

        val policy = ProductionDependencyPolicy.parse(text.toByteArray(UTF_8))

        assertEquals(ProductionDependencyEnforcement.BLOCKING, policy.enforcement)
        assertEquals(listOf(":core:model", ":domain:sample"), policy.modules)
        assertEquals(text, policy.canonicalText())
        assertEquals(64, policy.sha256.length)
    }

    @Test
    fun policyFailsClosedForCrLfWildcardsDuplicatesUnknownAndModuleDrift() {
        val base =
            """
            schema-version=1
            enforcement=blocking
            module|:core:model
            scope|:core:model|external|org.jetbrains.kotlin:kotlin-stdlib|implementation|compile=main|runtime=main
            """.trimIndent() + "\n"
        val mutations =
            listOf(
                base.replace("\n", "\r\n"),
                base.replace("kotlin-stdlib", "*"),
                base.replace("module|:core:model\n", "module|:core:model\nmodule|:core:model\n"),
                base.replace("schema-version=1", "schema-version=1\nunknown=value"),
                base.replace("compile=main", "compile=*")
            )

        mutations.forEach { mutation ->
            assertThrows(ProductionDependencyPolicyException::class.java) {
                ProductionDependencyPolicy.parse(mutation.toByteArray(UTF_8))
            }
        }

        val policy = ProductionDependencyPolicy.parse(base.toByteArray(UTF_8))
        val mismatch = assertThrows(ProductionDependencyPolicyException::class.java) {
            policy.requireExactActiveModules(listOf(":core:model", ":domain:station"))
        }
        assertTrue(mismatch.message.orEmpty().contains("active module"))
    }

    @Test
    fun directComparisonBindsDeclarationConfigurationAndComponentMembership() {
        val policy =
            ProductionDependencyPolicy.parse(
                (
                    """
                    schema-version=1
                    enforcement=blocking
                    module|:sample
                    scope|:sample|external|androidx.compose.ui:ui-tooling|debugImplementation|compile=debug|runtime=debug
                    """.trimIndent() + "\n"
                ).toByteArray(UTF_8),
            )
        val exact = policy.scopes.single()
        assertTrue(policy.compareDirectDeclarations(listOf(exact)).isEmpty())

        val wrongConfiguration = exact.copy(declarationConfiguration = "implementation")
        val wrongMembership = exact.copy(compileComponents = listOf("debug", "release"))
        assertTrue(policy.compareDirectDeclarations(listOf(wrongConfiguration)).isNotEmpty())
        assertTrue(policy.compareDirectDeclarations(listOf(wrongMembership)).isNotEmpty())
    }
}
