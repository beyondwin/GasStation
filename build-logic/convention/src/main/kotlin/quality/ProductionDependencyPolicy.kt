package com.gasstation.buildlogic.quality

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

internal enum class ProductionDependencyEnforcement(val value: String) {
    REPORT_ONLY("report-only"),
    BLOCKING("blocking"),
}

internal enum class ProductionDependencyKind(val value: String) {
    PROJECT("project"),
    EXTERNAL("external"),
}

internal data class ProductionDependencyScope(
    val consumer: String,
    val kind: ProductionDependencyKind,
    val target: String,
    val declarationConfiguration: String,
    val compileComponents: List<String>,
    val runtimeComponents: List<String>,
) : Comparable<ProductionDependencyScope> {
    val encoded: String
        get() =
            "scope|$consumer|${kind.value}|$target|$declarationConfiguration|" +
                "compile=${compileComponents.encodeComponents()}|runtime=${runtimeComponents.encodeComponents()}"

    override fun compareTo(other: ProductionDependencyScope): Int = encoded.compareTo(other.encoded)
}

internal data class TestedTargetRelation(
    val consumer: String,
    val components: List<String>,
    val target: String,
    val selfInstrumenting: Boolean,
    val compileOnlyMembership: String,
    val compileOnlyIdentities: List<String> = emptyList(),
) {
    val encoded: String
        get() =
            "tested-target|$consumer|${components.joinToString(",")}|$target|" +
                "self-instrumenting=$selfInstrumenting|compile-only-membership=$compileOnlyMembership|" +
                "compile-only-identities=${compileOnlyIdentities.ifEmpty { listOf("-") }.joinToString(",")}"
}

internal data class ProductionDependencyPolicy(
    val enforcement: ProductionDependencyEnforcement,
    val modules: List<String>,
    val scopes: List<ProductionDependencyScope>,
    val testedTarget: TestedTargetRelation?,
) {
    fun canonicalText(): String =
        buildString {
            appendLine("schema-version=1")
            appendLine("enforcement=${enforcement.value}")
            modules.forEach { appendLine("module|$it") }
            scopes.forEach { appendLine(it.encoded) }
            testedTarget?.let { appendLine(it.encoded) }
        }

    val sha256: String
        get() = sha256(canonicalText().toByteArray(UTF_8))

    fun requireExactActiveModules(activeModules: List<String>) {
        val canonical = activeModules.sorted()
        if (activeModules.size != activeModules.toSet().size || canonical != modules) {
            throw ProductionDependencyPolicyException(
                "production dependency policy active module mismatch: policy=$modules active=$canonical",
            )
        }
        val active = canonical.toSet()
        val inactiveEndpoints =
            buildList {
                scopes.forEach { scope ->
                    if (scope.consumer !in active) add("scope consumer ${scope.consumer}")
                    if (scope.kind == ProductionDependencyKind.PROJECT && scope.target !in active) {
                        add("scope target ${scope.target}")
                    }
                }
                testedTarget?.let { relation ->
                    if (relation.consumer !in active) add("tested-target consumer ${relation.consumer}")
                    if (relation.target !in active) add("tested-target target ${relation.target}")
                }
            }.sorted()
        if (inactiveEndpoints.isNotEmpty()) {
            throw ProductionDependencyPolicyException(
                "production dependency policy contains inactive topology endpoints: $inactiveEndpoints",
            )
        }
    }

    fun compareDirectDeclarations(actual: List<ProductionDependencyScope>): List<String> {
        val expected = scopes.map(ProductionDependencyScope::encoded).toSortedSet()
        val observed = actual.map(ProductionDependencyScope::encoded).toSortedSet()
        return buildList {
            (expected - observed).forEach { add("missing direct production declaration: $it") }
            (observed - expected).forEach { add("unallowlisted direct production declaration: $it") }
        }
    }

    companion object {
        fun parse(bytes: ByteArray): ProductionDependencyPolicy {
            if (bytes.isEmpty() || bytes.last() != '\n'.code.toByte() || bytes.any { it == '\r'.code.toByte() }) {
                throw ProductionDependencyPolicyException("policy must be UTF-8 with LF endings and one trailing LF")
            }
            val decoder = UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val text = try {
                decoder.decode(ByteBuffer.wrap(bytes)).toString()
            } catch (failure: Exception) {
                throw ProductionDependencyPolicyException("policy must be valid UTF-8", failure)
            }
            val lines = text.dropLast(1).split('\n')
            if (lines.any(String::isBlank)) {
                throw ProductionDependencyPolicyException("policy must not contain blank lines")
            }
            if (lines.firstOrNull() != "schema-version=1") {
                throw ProductionDependencyPolicyException("policy must begin with schema-version=1")
            }
            val enforcementLine = lines.getOrNull(1)
                ?: throw ProductionDependencyPolicyException("policy enforcement is missing")
            val enforcement = ProductionDependencyEnforcement.entries.singleOrNull {
                enforcementLine == "enforcement=${it.value}"
            } ?: throw ProductionDependencyPolicyException("policy enforcement must be report-only or blocking")

            val modules = mutableListOf<String>()
            val scopes = mutableListOf<ProductionDependencyScope>()
            var testedTarget: TestedTargetRelation? = null
            lines.drop(2).forEach { line ->
                when {
                    line.startsWith("module|") -> modules += parseModule(line)
                    line.startsWith("scope|") -> scopes += parseScope(line)
                    line.startsWith("tested-target|") -> {
                        if (testedTarget != null) throw ProductionDependencyPolicyException("duplicate tested-target record")
                        testedTarget = parseTestedTarget(line)
                    }
                    else -> throw ProductionDependencyPolicyException("unknown policy record: $line")
                }
            }
            if (modules != modules.sorted() || modules.size != modules.toSet().size) {
                throw ProductionDependencyPolicyException("module records must be unique and sorted")
            }
            if (scopes != scopes.sorted() || scopes.size != scopes.toSet().size) {
                throw ProductionDependencyPolicyException("scope records must be unique and sorted")
            }
            val policy = ProductionDependencyPolicy(enforcement, modules, scopes, testedTarget)
            if (policy.canonicalText() != text) {
                throw ProductionDependencyPolicyException("policy does not round-trip canonically")
            }
            return policy
        }

        private fun parseModule(line: String): String {
            val fields = line.split('|')
            if (fields.size != 2) throw ProductionDependencyPolicyException("malformed module record: $line")
            return requireProjectPath(fields[1], "module")
        }

        private fun parseScope(line: String): ProductionDependencyScope {
            val fields = line.split('|')
            if (fields.size != 7) throw ProductionDependencyPolicyException("malformed scope record: $line")
            val consumer = requireProjectPath(fields[1], "scope consumer")
            val kind = ProductionDependencyKind.entries.singleOrNull { it.value == fields[2] }
                ?: throw ProductionDependencyPolicyException("unsupported dependency kind: ${fields[2]}")
            val target = when (kind) {
                ProductionDependencyKind.PROJECT -> requireProjectPath(fields[3], "scope project target")
                ProductionDependencyKind.EXTERNAL -> requireExternalCoordinate(fields[3])
            }
            val configuration = requireIdentifier(fields[4], "declaration configuration")
            return ProductionDependencyScope(
                consumer = consumer,
                kind = kind,
                target = target,
                declarationConfiguration = configuration,
                compileComponents = parseComponents(fields[5], "compile"),
                runtimeComponents = parseComponents(fields[6], "runtime"),
            )
        }

        private fun parseTestedTarget(line: String): TestedTargetRelation {
            val fields = line.split('|')
            if (fields.size != 7) throw ProductionDependencyPolicyException("malformed tested-target record: $line")
            val components = fields[2].split(',').map { requireIdentifier(it, "tested-target component") }
            if (components != components.sorted() || components.size != components.toSet().size) {
                throw ProductionDependencyPolicyException("tested-target components must be unique and sorted")
            }
            val self = when (fields[4]) {
                "self-instrumenting=true" -> true
                "self-instrumenting=false" -> false
                else -> throw ProductionDependencyPolicyException("invalid tested-target self-instrumenting value")
            }
            val membership = fields[5].removePrefix("compile-only-membership=")
            if (membership !in setOf("absent", "present")) {
                throw ProductionDependencyPolicyException("invalid tested-target compile-only membership")
            }
            if (!fields[6].startsWith("compile-only-identities=")) {
                throw ProductionDependencyPolicyException("missing tested-target compile-only identities")
            }
            val rawIdentities = fields[6].removePrefix("compile-only-identities=")
            val identities = if (rawIdentities == "-") emptyList() else rawIdentities.split(',')
            if (
                identities != identities.sorted() ||
                identities.any { identity ->
                    !identity.matches(
                        Regex("[A-Za-z][A-Za-z0-9_-]*:[A-Za-z][A-Za-z0-9_-]*->:[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*)*@targetConfiguration=[A-Za-z][A-Za-z0-9_-]*"),
                    )
                }
            ) {
                throw ProductionDependencyPolicyException("invalid tested-target compile-only identities")
            }
            if ((membership == "absent") != identities.isEmpty()) {
                throw ProductionDependencyPolicyException("tested-target compile-only membership and identities disagree")
            }
            return TestedTargetRelation(
                consumer = requireProjectPath(fields[1], "tested-target consumer"),
                components = components,
                target = requireProjectPath(fields[3], "tested-target project"),
                selfInstrumenting = self,
                compileOnlyMembership = membership,
                compileOnlyIdentities = identities,
            )
        }

        private fun parseComponents(field: String, name: String): List<String> {
            if (!field.startsWith("$name=")) throw ProductionDependencyPolicyException("missing $name scope")
            val value = field.removePrefix("$name=")
            if (value == "-") return emptyList()
            val components = value.split(',').map { requireIdentifier(it, "$name component") }
            if (components != components.sorted() || components.size != components.toSet().size) {
                throw ProductionDependencyPolicyException("$name components must be unique and sorted")
            }
            return components
        }
    }
}

internal class ProductionDependencyPolicyException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

private fun List<String>.encodeComponents(): String = if (isEmpty()) "-" else joinToString(",")

private fun requireProjectPath(value: String, label: String): String {
    if (!value.matches(Regex(":[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*)*")) || '*' in value) {
        throw ProductionDependencyPolicyException("invalid $label project path: $value")
    }
    return value
}

private fun requireExternalCoordinate(value: String): String {
    if (!value.matches(Regex("[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+")) || '*' in value) {
        throw ProductionDependencyPolicyException("invalid external coordinate: $value")
    }
    return value
}

private fun requireIdentifier(value: String, label: String): String {
    if (!value.matches(Regex("[A-Za-z][A-Za-z0-9_-]*")) || '*' in value) {
        throw ProductionDependencyPolicyException("invalid $label: $value")
    }
    return value
}

internal fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
