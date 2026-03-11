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

    private sealed class TxtTocRulesCacheKey {
        object Builtin : TxtTocRulesCacheKey()

        data class External(
            val path: String,
            val lastModified: Long,
            val length: Long,
        ) : TxtTocRulesCacheKey()
    }

    @Volatile
    private var cachedTxtTocRules: List<TxtTocRule>? = null

    @Volatile
    private var cachedTxtTocRulesKey: TxtTocRulesCacheKey? = null

    private val builtinTxtTocRules: List<TxtTocRule> by lazy {
        loadBuiltinTxtTocRules()
    }

    val txtTocRules: List<TxtTocRule>
        get() = loadTxtTocRulesCached()

    internal fun isBuiltinTxtTocRule(rule: String): Boolean {
        return builtinTxtTocRules.any { it.rule == rule }
    }

    private fun loadTxtTocRulesCached(): List<TxtTocRule> {
        val externalFile = externalTxtTocRulesFile()
        val key = txtTocRulesCacheKey(externalFile)
        cachedTxtTocRules?.let { cachedRules ->
            if (cachedTxtTocRulesKey == key) {
                return cachedRules
            }
        }
        synchronized(this) {
            val externalFile2 = externalTxtTocRulesFile()
            val key2 = txtTocRulesCacheKey(externalFile2)
            cachedTxtTocRules?.let { cachedRules ->
                if (cachedTxtTocRulesKey == key2) {
                    return cachedRules
                }
            }

            val rules = when (key2) {
                is TxtTocRulesCacheKey.External -> loadExternalTxtTocRules(externalFile2) ?: builtinTxtTocRules
                TxtTocRulesCacheKey.Builtin -> builtinTxtTocRules
            }
            cachedTxtTocRules = rules
            cachedTxtTocRulesKey = key2
            return rules
        }
    }

    internal fun loadTxtTocRules(): List<TxtTocRule> {
        val externalFile = externalTxtTocRulesFile()
        loadExternalTxtTocRules(externalFile)?.let {
            return it
        }
        return builtinTxtTocRules
    }

    private fun externalTxtTocRulesFile(): File {
        return File(getStoragePath(), "defaultData${File.separator}${txtTocRuleFileName}")
    }

    private fun txtTocRulesCacheKey(externalFile: File): TxtTocRulesCacheKey {
        if (!externalFile.exists()) {
            return TxtTocRulesCacheKey.Builtin
        }
        return TxtTocRulesCacheKey.External(
            path = externalFile.absolutePath,
            lastModified = externalFile.lastModified(),
            length = externalFile.length(),
        )
    }

    private fun loadExternalTxtTocRules(externalFile: File): List<TxtTocRule>? {
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
