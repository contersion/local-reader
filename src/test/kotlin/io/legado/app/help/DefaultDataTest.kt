package io.legado.app.help

import com.htmake.reader.utils.storageFinalPath
import com.htmake.reader.utils.workDirInit
import com.htmake.reader.utils.workDirPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class DefaultDataTest {

    @Test
    fun fallsBackToBuiltinTxtTocRulesWhenExternalFileMissing() {
        withTempWorkDir {
            val rules = DefaultData.loadTxtTocRules()
            assertTrue(rules.isNotEmpty())
        }
    }

    @Test
    fun prefersExternalTxtTocRulesWhenFileExists() {
        withTempWorkDir { tempDir ->
            val externalFile = tempDir.resolve("storage").resolve("defaultData").resolve(DefaultData.txtTocRuleFileName)
            externalFile.parent.toFile().mkdirs()
            Files.write(
                externalFile,
                """
                [
                  {
                    "id": 9527,
                    "name": "外部规则",
                    "rule": "(?m)^外部章节.*$",
                    "serialNumber": 1,
                    "enable": true
                  }
                ]
                """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            )

            val rules = DefaultData.loadTxtTocRules()
            assertEquals(1, rules.size)
            assertEquals("外部规则", rules.first().name)
            assertEquals("(?m)^外部章节.*$", rules.first().rule)
        }
    }

    @Test
    fun fallsBackToBuiltinTxtTocRulesWhenExternalFileIsInvalid() {
        withTempWorkDir { tempDir ->
            val externalFile = tempDir.resolve("storage").resolve("defaultData").resolve(DefaultData.txtTocRuleFileName)
            externalFile.parent.toFile().mkdirs()
            Files.write(externalFile, "{invalid json}".toByteArray(StandardCharsets.UTF_8))

            val rules = DefaultData.loadTxtTocRules()
            assertTrue(rules.isNotEmpty())
        }
    }

    private fun withTempWorkDir(block: (java.nio.file.Path) -> Unit) {
        val previousWorkDirPath = workDirPath
        val previousWorkDirInit = workDirInit
        val previousStorageFinalPath = storageFinalPath
        val tempDir = Files.createTempDirectory("reader-default-data-test")
        try {
            workDirPath = tempDir.toString()
            workDirInit = true
            storageFinalPath = ""
            block(tempDir)
        } finally {
            storageFinalPath = previousStorageFinalPath
            workDirPath = previousWorkDirPath
            workDirInit = previousWorkDirInit
            tempDir.toFile().deleteRecursively()
        }
    }
}
