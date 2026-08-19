package com.gasstation.buildlogic.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinAbiDumpParserTest {
    @Test
    fun realWriterGrammarSelectsClassesFieldsAndFunctions() {
        val dump =
            """
            public final class com/gasstation/core/model/Sample : java/lang/Object {
            ${'\t'}public final field names Ljava/util/List;
            ${'\t'}public final fun value ()I
            }

            """.trimIndent() + "\n"

        val entries = KotlinAbiDumpParser.parse(dump, "com.gasstation.core.model")

        assertEquals(1, entries.classes.size)
        assertEquals("com/gasstation/core/model/Sample", entries.classes.single().internalName)
        assertEquals(2, entries.classes.single().members.size)
    }

    @Test
    fun parserFailsClosedOnUnknownRecordsMalformedDescriptorsAndDuplicates() {
        val validHeader = "public final class com/gasstation/core/model/Sample {\n"
        val mutations =
            listOf(
                validHeader + "\tpublic property value I\n}\n\n",
                validHeader + "\tpublic field value Ljava.lang.String;\n}\n\n",
                validHeader + "\tpublic fun value (I\n}\n\n",
                validHeader + "\tpublic field value I\n\tpublic field value I\n}\n\n",
                "public final class wrong/package/Sample {\n}\n\n",
            )
        mutations.forEachIndexed { index, mutation ->
            assertThrows("mutation $index must fail closed", KotlinAbiFormatException::class.java) {
                KotlinAbiDumpParser.parse(mutation, "com.gasstation.core.model")
            }
        }
    }

    @Test
    fun forbiddenFamiliesMatchCanonicalTypesNotSubstrings() {
        val tokens =
            JvmAbiTypeScanner.typesFromDescriptor(
                "(Landroid/location/Location;[Lcom/google/gson/Gson;)Lretrofit2/Response;",
            )
        assertEquals(
            setOf("android.location.Location", "com.google.gson.Gson", "retrofit2.Response"),
            tokens,
        )
        assertEquals(
            tokens,
            tokens.filterTo(sortedSetOf(), JvmAbiTypeScanner::isForbiddenType),
        )
        assertTrue(!JvmAbiTypeScanner.isForbiddenType("com.example.Myandroid.Widget"))
        assertTrue(!JvmAbiTypeScanner.isForbiddenType("java.time.Instant"))
    }
}
