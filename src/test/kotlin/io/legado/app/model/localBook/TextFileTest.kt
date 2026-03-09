package io.legado.app.model.localBook

import io.legado.app.data.entities.Book
import io.legado.app.exception.TocEmptyException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class TextFileTest {

    @Test
    fun builtinRuleKeepsBlankChapterAsVolume() {
        assertTrue(
            TextFile.shouldTreatAsVolume(
                "第一卷 山雨欲来",
                "第一卷 山雨欲来\n\n",
                false
            )
        )
    }

    @Test
    fun customRuleDoesNotTreatColonSubtitleAsVolume() {
        assertFalse(
            TextFile.shouldTreatAsVolume(
                "番外②：新年快乐",
                "番外②：新年快乐\n\n",
                true
            )
        )
    }

    @Test
    fun customRuleStillTreatsPureVolumeTitleAsVolume() {
        assertTrue(
            TextFile.shouldTreatAsVolume(
                "第一卷",
                "第一卷\n\n",
                true
            )
        )
    }

    @Test
    fun chapterWithBodyIsNotVolume() {
        assertFalse(
            TextFile.shouldTreatAsVolume(
                "第一章 开始",
                "第一章 开始\n正文第一段",
                true
            )
        )
    }

    @Test
    fun customRuleParsingDoesNotMarkColonSubtitleChaptersAsVolume() {
        val chapters = parseSample("(?m)^.*: .*$")

        assertEquals(listOf("前言", "角色A: 欢迎来到会场", "角色B: 大家新年快乐", "角色C: 烟花升空", "角色D: 新年的钟声已经响了"), chapters.map { it.title })
        assertEquals(listOf(false, false, false, false, false), chapters.map { it.isVolume })
    }

    @Test
    fun customRuleParsingStillMarksPureVolumeTitleAsVolume() {
        val chapters = parseSample("(?m)^(?:第一卷|角色[A-D]: .*)$")

        assertEquals(listOf("第一卷", "角色A: 欢迎来到会场", "角色B: 大家新年快乐", "角色C: 烟花升空", "角色D: 新年的钟声已经响了"), chapters.map { it.title })
        assertEquals(listOf(true, false, false, false, false), chapters.map { it.isVolume })
    }

    @Test(expected = TocEmptyException::class)
    fun unmatchedRuleThrowsFriendlyTocEmptyException() {
        try {
            parseSample("(?m)^这条规则匹配不到任何章节$")
        } catch (e: TocEmptyException) {
            assertEquals("目录规则未匹配到任何章节", e.localizedMessage)
            throw e
        }
    }

    private fun parseSample(tocRule: String) =
        TextFile.getChapterList(createLocalTxtBook(loadSampleText(), tocRule))

    private fun createLocalTxtBook(content: String, tocRule: String): Book {
        val dir = Files.createTempDirectory("reader-textfile-test")
        val file = dir.resolve("issue-659-minimal.txt")
        Files.write(file, content.toByteArray(StandardCharsets.UTF_8))
        file.toFile().deleteOnExit()
        dir.toFile().deleteOnExit()
        return Book.initLocalBook(file.toString(), file.toString()).apply {
            charset = "UTF-8"
            this.tocUrl = tocRule
        }
    }

    private fun loadSampleText(): String {
        val stream = javaClass.getResourceAsStream("/samples/issue-659-minimal.txt")
            ?: error("missing issue-659 sample resource")
        return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }
}
