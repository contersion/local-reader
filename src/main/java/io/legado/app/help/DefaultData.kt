package io.legado.app.help

import com.htmake.reader.utils.getStoragePath
// import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import java.io.File
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

object DefaultData {
    const val txtTocRuleFileName = "txtTocRule.json"

    val txtTocRules: List<TxtTocRule> by lazy {
        loadTxtTocRules()
    }

    internal fun loadTxtTocRules(): List<TxtTocRule> {
        loadExternalTxtTocRules()?.let {
            return it
        }
        return loadBuiltinTxtTocRules()
    }

    private fun loadExternalTxtTocRules(): List<TxtTocRule>? {
        val externalFile = File(getStoragePath(), "defaultData${File.separator}${txtTocRuleFileName}")
        if (!externalFile.exists()) {
            return null
        }
        return kotlin.runCatching {
            parseTxtTocRules(externalFile.readText())
        }.onFailure {
            logger.warn(it) { "load external txt toc rules failed: ${externalFile.absolutePath}" }
        }.getOrNull() ?: kotlin.run {
            logger.warn { "parse external txt toc rules failed: ${externalFile.absolutePath}" }
            null
        }
    }

    private fun loadBuiltinTxtTocRules(): List<TxtTocRule> {
        val json = String(DefaultData::class.java.getResource("/defaultData/${txtTocRuleFileName}").readBytes())
        return parseTxtTocRules(json) ?: emptyList()
    }

    private fun parseTxtTocRules(json: String): List<TxtTocRule>? {
        return GSON.fromJsonArray<TxtTocRule>(json).getOrNull()
    }

    // val rssSources by lazy {
    //     val json = String(
    //         File("defaultData${File.separator}rssSources.json")
    //             .readBytes()
    //     )
    //     GSON.fromJsonArray<RssSource>(json)!!
    // }
}
