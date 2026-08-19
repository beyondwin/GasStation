package com.gasstation.buildlogic.quality

import java.io.File
import javax.tools.ToolProvider
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

class VerifyPublicApiBoundariesTaskTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun compiledClassesBindTypedRootsResolveInheritedOwnerAndScanDefaultsAndSignatures() {
        val root = temporaryFolder.root
        val modules =
            listOf(
                Triple(":core:model", "fixture.model", "model.api"),
                Triple(":core:observability", "fixture.observability", "observability.api"),
                Triple(":domain:location", "fixture.location", "location.api"),
                Triple(":domain:settings", "fixture.settings", "settings.api"),
                Triple(":domain:station", "fixture.station", "station.api"),
            )
        val classRoots = linkedMapOf<String, File>()
        val dumpFiles = mutableListOf<File>()
        modules.forEach { (module, packageName, dumpName) ->
            val moduleDirectory = root.resolve(module.removePrefix(":").replace(':', '/'))
            val classRoot = moduleDirectory.resolve("build/classes/java/main")
            classRoot.mkdirs()
            classRoots[module] = classRoot
            val sources = moduleDirectory.resolve("src/main/java")
            if (module == ":core:model") {
                writeJava(sources, "android/fake/Forbidden.java", "package android.fake; public final class Forbidden {}")
                writeJava(
                    sources,
                    "fixture/model/Parent.java",
                    "package fixture.model; import java.util.List; " +
                        "class Parent<T extends android.fake.Forbidden> { public List<T> inherited() { return null; } }",
                )
                writeJava(
                    sources,
                    "fixture/model/Marker.java",
                    "package fixture.model; public final class Marker extends Parent<android.fake.Forbidden> {}",
                )
                writeJava(
                    sources,
                    "fixture/model/Other.java",
                    "package fixture.model; import java.util.List; " +
                        "interface Other<T> { List<T> inherited(); }",
                )
                writeJava(
                    sources,
                    "fixture/model/ApiDefault.java",
                    "package fixture.model; public @interface ApiDefault { " +
                        "Class<?> value() default android.fake.Forbidden.class; }",
                )
            } else if (module == ":domain:station") {
                writeJava(
                    sources,
                    "${packageName.replace('.', '/')}/Marker.java",
                    "package $packageName; import java.util.List; public final class Marker { " +
                        "public List<String> generic() { return null; } }",
                )
            } else {
                writeJava(
                    sources,
                    "${packageName.replace('.', '/')}/Marker.java",
                    "package $packageName; public final class Marker {}",
                )
            }
            compileJava(sources, classRoot)
            val dump = moduleDirectory.resolve("api/$dumpName")
            dump.parentFile.mkdirs()
            dump.writeText(
                if (module == ":core:model") {
                    "public final class fixture/model/Marker : fixture/model/Parent {\n" +
                        "\tpublic fun <init> ()V\n" +
                        "\tpublic fun inherited ()Ljava/util/List;\n" +
                        "}\n\n" +
                        "public abstract interface annotation class fixture/model/ApiDefault : " +
                        "java/lang/annotation/Annotation {\n" +
                        "\tpublic abstract fun value ()Ljava/lang/Class;\n" +
                        "}\n"
                } else if (module == ":domain:station") {
                    "public final class ${packageName.replace('.', '/')}/Marker {\n" +
                        "\tpublic fun <init> ()V\n" +
                        "\tpublic fun generic ()Ljava/util/List;\n}\n"
                } else {
                    "public final class ${packageName.replace('.', '/')}/Marker {\n\tpublic fun <init> ()V\n}\n"
                },
            )
            dumpFiles += dump
        }

        val project = ProjectBuilder.builder().withProjectDir(root).build()
        val task = project.tasks.create("verifyFixturePublicApi", VerifyPublicApiBoundariesTask::class.java)
        task.moduleMappings.set(
            modules.map { (module, packageName, dumpName) ->
                "$module|${module.removePrefix(":").replace(':', '/')}/api/$dumpName|$packageName"
            },
        )
        task.selectedActiveModules.set(modules.map { it.first })
        task.classRootMappings.set(
            classRoots.map { (module, directory) -> "$module|${directory.relativeTo(root).invariantSeparatorsPath}" },
        )
        task.repositoryRoot.set(project.layout.projectDirectory)
        task.dumpFiles.from(dumpFiles)
        task.classDirectories.from(classRoots.values)
        task.scannerSchema.set("test-asm-9.9.1")
        task.reportFile.set(project.layout.buildDirectory.file("reports/quality/public-api.json"))

        task.forbiddenFamilies.set(emptyList())
        task.verify()
        val successReport = task.reportFile.get().asFile.readText()
        assertTrue(successReport.contains("\"selectedMemberCount\":8"))
        assertTrue(successReport.contains("\"signatureLocationCount\":"))
        assertTrue(successReport.contains(":core:model|fixture/model/Marker.class|root=core/model/build/classes/java/main"))

        task.forbiddenFamilies.set(listOf("android."))
        val forbidden = assertThrows(GradleException::class.java, task::verify)
        assertTrue(forbidden.message.orEmpty().contains("annotation-default-value|android.fake.Forbidden"))
        assertTrue(forbidden.message.orEmpty().contains("class-signature|android.fake.Forbidden"))

        val stationClass = classRoots.getValue(":domain:station").resolve("fixture/station/Marker.class")
        val validStation = stationClass.readBytes()
        stationClass.writeBytes(withMalformedClassSignature(validStation, "generic"))
        assertEquals(
            "()Ljava/util/List<Landroid/location/Location;>",
            methodSignature(stationClass.readBytes(), "generic"),
        )
        task.forbiddenFamilies.set(emptyList())
        val malformed = assertThrows(GradleException::class.java, task::verify)
        assertTrue(malformed.message.orEmpty().contains("malformed method-signature"))

    }

    private fun writeJava(sourceRoot: File, relativePath: String, source: String) {
        sourceRoot.resolve(relativePath).apply {
            parentFile.mkdirs()
            writeText(source)
        }
    }

    private fun compileJava(sourceRoot: File, output: File) {
        val sources = sourceRoot.walkTopDown().filter { it.extension == "java" }.map(File::getPath).toList()
        val exit = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", output.path, *sources.toTypedArray())
        check(exit == 0) { "javac failed with exit $exit" }
    }

    private fun withMalformedClassSignature(bytes: ByteArray, selectedName: String): ByteArray {
        val writer = ClassWriter(0)
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM9, writer) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ) = super.visitMethod(
                    access,
                    name,
                    descriptor,
                    if (name == selectedName) "()Ljava/util/List<Landroid/location/Location;>" else signature,
                    exceptions,
                )
            },
            0,
        )
        return writer.toByteArray()
    }

    private fun methodSignature(bytes: ByteArray, selectedName: String): String? {
        var selected: String? = null
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): org.objectweb.asm.MethodVisitor? {
                    if (name == selectedName) selected = signature
                    return null
                }
            },
            ClassReader.SKIP_CODE,
        )
        return selected
    }

}
