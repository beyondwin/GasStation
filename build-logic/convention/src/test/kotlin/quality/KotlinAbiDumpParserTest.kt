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
                validHeader + "\tpublic field value V\n}\n\n",
                validHeader + "\tpublic field value [V\n}\n\n",
                validHeader + "\tpublic field value Lfoo\n}\n\n",
                validHeader + "\tpublic field value Lfoo/Bar;;\n}\n\n",
                validHeader + "\tpublic fun value (V)V\n}\n\n",
                validHeader + "\tpublic fun value ([V)V\n}\n\n",
                validHeader + "\tpublic fun value ()Vx\n}\n\n",
                validHeader + "\tpublic fun value (I)Vjunk\n}\n\n",
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

    @Test
    fun signatureScannerFindsDirectArrayNestedGenericBoundSuspendAndFunctionPositions() {
        val signatures =
            listOf(
                "Landroid/location/Location;" to true,
                "[Landroid/location/Location;" to true,
                "Ljava/util/List<Landroid/location/Location;>;" to true,
                "<T:Landroid/location/Location;>Ljava/lang/Object;" to false,
                "(Landroid/location/Location;)Lretrofit2/Response;" to false,
                "(Lkotlin/coroutines/Continuation<-Lokhttp3/ResponseBody;>;)Ljava/lang/Object;" to false,
            )

        val scanned = signatures.flatMap { (signature, typeSignature) ->
            JvmAbiTypeScanner.typesFromSignature(signature, typeSignature)
        }.toSet()

        assertTrue("android.location.Location" in scanned)
        assertTrue("retrofit2.Response" in scanned)
        assertTrue("okhttp3.ResponseBody" in scanned)
        assertThrows(KotlinAbiFormatException::class.java) {
            JvmAbiTypeScanner.typesFromSignature("Ljava/util/List<Landroid/location/Location;>", true)
        }
    }
}
