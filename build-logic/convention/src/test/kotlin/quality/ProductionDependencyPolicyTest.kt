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
    fun activeTopologyBindsEveryScopeAndTestedTargetEndpoint() {
        val active = listOf(":app", ":benchmark", ":core:model")
        val validScope = projectScope(":app", ":core:model")
        val validTarget =
            TestedTargetRelation(
                consumer = ":benchmark",
                components = listOf("benchmark", "debug"),
                target = ":app",
                selfInstrumenting = true,
                compileOnlyMembership = "absent",
            )
        val mutations =
            listOf(
                validScope.copy(consumer = ":inactive"),
                validScope.copy(target = ":grouping"),
            )

        mutations.forEach { invalidScope ->
            val policy =
                ProductionDependencyPolicy(
                    enforcement = ProductionDependencyEnforcement.BLOCKING,
                    modules = active,
                    scopes = listOf(invalidScope),
                    testedTarget = validTarget,
                )
            assertThrows(ProductionDependencyPolicyException::class.java) {
                policy.requireExactActiveModules(active)
            }
        }

        listOf(
            validTarget.copy(consumer = ":inactive"),
            validTarget.copy(target = ":grouping"),
        ).forEach { invalidTarget ->
            val policy =
                ProductionDependencyPolicy(
                    enforcement = ProductionDependencyEnforcement.BLOCKING,
                    modules = active,
                    scopes = listOf(validScope),
                    testedTarget = invalidTarget,
                )
            assertThrows(ProductionDependencyPolicyException::class.java) {
                policy.requireExactActiveModules(active)
            }
        }
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

    @Test
    fun evidenceAggregationKeepsExactDeclarationBucketAndCompileRuntimeComponents() {
        val actual =
            aggregateProductionScopes(
                listOf(
                    ":sample|demo|compile|external|example.group:artifact|api",
                    ":sample|demo|runtime|external|example.group:artifact|api",
                    ":sample|release|compile|external|example.group:artifact|api",
                    ":sample|release|runtime|external|example.group:artifact|api",
                    ":sample|release|compile|external|example.group:runtime|implementation",
                ),
            )

        assertEquals(
            listOf(
                ProductionDependencyScope(
                    consumer = ":sample",
                    kind = ProductionDependencyKind.EXTERNAL,
                    target = "example.group:artifact",
                    declarationConfiguration = "api",
                    compileComponents = listOf("demo", "release"),
                    runtimeComponents = listOf("demo", "release"),
                ),
                ProductionDependencyScope(
                    consumer = ":sample",
                    kind = ProductionDependencyKind.EXTERNAL,
                    target = "example.group:runtime",
                    declarationConfiguration = "implementation",
                    compileComponents = listOf("release"),
                    runtimeComponents = emptyList(),
                ),
            ),
            actual,
        )
    }

    @Test
    fun externalCoordinatesAreExactAndSameGroupSiblingIsUnallowlisted() {
        val allowed =
            ProductionDependencyScope(
                consumer = ":sample",
                kind = ProductionDependencyKind.EXTERNAL,
                target = "androidx.compose.ui:ui-tooling",
                declarationConfiguration = "debugImplementation",
                compileComponents = listOf("debug"),
                runtimeComponents = listOf("debug"),
            )
        val sibling = allowed.copy(target = "androidx.compose.ui:ui-tooling-preview")
        val policy =
            ProductionDependencyPolicy(
                enforcement = ProductionDependencyEnforcement.BLOCKING,
                modules = listOf(":sample"),
                scopes = listOf(allowed),
                testedTarget = null,
            )

        assertTrue(policy.compareDirectDeclarations(listOf(allowed)).isEmpty())
        val diagnostics = policy.compareDirectDeclarations(listOf(sibling))
        assertEquals(2, diagnostics.size)
        assertTrue(diagnostics.any { it.startsWith("missing direct production declaration") })
        assertTrue(diagnostics.any { it.startsWith("unallowlisted direct production declaration") })
    }

    @Test
    fun testedTargetRelationRejectsTargetAndSelfInstrumentingMembershipMutations() {
        val expected =
            TestedTargetRelation(
                consumer = ":benchmark",
                components = listOf("benchmark", "debug"),
                target = ":app",
                selfInstrumenting = true,
                compileOnlyMembership = "absent",
            )

        assertTrue(compareTestedTarget(expected, listOf(expected.encoded)).isEmpty())
        val wrongTarget = expected.copy(target = ":feature:station-list")
        val wrongSelfInstrumenting = expected.copy(selfInstrumenting = false, compileOnlyMembership = "present")
        assertTrue(compareTestedTarget(expected, listOf(wrongTarget.encoded)).single().contains("mismatch"))
        assertTrue(compareTestedTarget(expected, listOf(wrongSelfInstrumenting.encoded)).single().contains("mismatch"))
    }

    @Test
    fun exactProjectAllowlistKillsEveryRetiredRuleAndKeepsTheOneIntentionalException() {
        val mutations =
            listOf(
                ":feature:probe" to ":core:location",
                ":feature:probe" to ":core:network",
                ":feature:probe" to ":core:database",
                ":feature:probe" to ":core:datastore",
                ":feature:probe" to ":data:station",
                ":data:probe" to ":core:location",
                ":data:probe" to ":feature:settings",
                ":domain:probe" to ":data:station",
                ":domain:probe" to ":feature:settings",
                ":domain:probe" to ":core:location",
                ":domain:probe" to ":core:network",
                ":domain:probe" to ":core:database",
                ":domain:probe" to ":core:datastore",
                ":domain:probe" to ":core:designsystem",
                ":core:model" to ":domain:station",
                ":core:model" to ":data:station",
                ":core:network" to ":domain:station",
                ":core:observability" to ":domain:station",
            )
        val allowedException = projectScope(":core:location", ":domain:location")
        val policy =
            ProductionDependencyPolicy(
                enforcement = ProductionDependencyEnforcement.BLOCKING,
                modules = mutations.flatMap { listOf(it.first, it.second) }.plus(
                    listOf(":core:location", ":domain:location"),
                ).distinct().sorted(),
                scopes = listOf(allowedException),
                testedTarget = null,
            )

        assertTrue(policy.compareDirectDeclarations(listOf(allowedException)).isEmpty())
        mutations.forEach { (consumer, target) ->
            val diagnostics =
                policy.compareDirectDeclarations(
                    listOf(allowedException, projectScope(consumer, target)),
                )
            assertEquals("$consumer -> $target", 1, diagnostics.size)
            assertTrue(diagnostics.single().contains("unallowlisted direct production declaration"))
        }
    }

    private fun projectScope(consumer: String, target: String): ProductionDependencyScope =
        ProductionDependencyScope(
            consumer = consumer,
            kind = ProductionDependencyKind.PROJECT,
            target = target,
            declarationConfiguration = "implementation",
            compileComponents = listOf("main"),
            runtimeComponents = listOf("main"),
        )
}
