package io.github.dshfolk.desktoppet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 最小 OpenAI 兼容客户端。
 *
 * ## 为什么手写而不是引 okhttp / openai-sdk
 *
 * 扩展的 .dshx 是 zip(manifest + classes.dex)，**不打依赖**。引 okhttp 会把
 * dex 撑大几百 KB，而这里只需要发一个 POST 收一个 JSON —— HttpURLConnection
 * 完全够用，还省掉依赖版本与宿主冲突的风险。
 *
 * ## 只支持非流式
 *
 * 桌宠的气泡最多几十个字，流式收益很小，而流式要处理 SSE 分块解析、中途断连、
 * 半句话丢弃，复杂度远高于收益。等真需要再补。
 */
class PetAiClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val systemPrompt: String,
) {

    /** 结果：成功给出文本，失败给出原因（UI 可显示，日志可查）。 */
    sealed interface Result {
        data class Ok(val text: String) : Result
        data class Fail(val reason: String) : Result
    }

    /**
     * 让 AI 说一句话。
     *
     * @param userHint 用户输入或场景描述（例如"用户刚摸了你一下"）
     * @param timeoutMs 超时。桌宠说话不该让用户等太久，默认 15s。
     */
    suspend fun say(userHint: String, timeoutMs: Int = 15_000): Result = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.Fail("没有配置 API Key")
        if (baseUrl.isBlank()) return@withContext Result.Fail("没有配置 Base URL")

        val endpoint = runCatching {
            // 容忍用户填的是 "https://api.deepseek.com" 或 ".../v1" 或完整 ".../chat/completions"
            val trimmed = baseUrl.trimEnd('/')
            when {
                trimmed.endsWith("/chat/completions") -> trimmed
                trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
                else -> "$trimmed/v1/chat/completions"
            }
        }.getOrNull() ?: return@withContext Result.Fail("Base URL 格式不对")

        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 120)
            put("temperature", 1.1)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
                put(JSONObject().put("role", "user").put("content", userHint))
            })
        }.toString()

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer " + apiKey)
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                // 错误体也读出来：401 与 429 的处理方式完全不同，只说"失败"没法排查。
                val err = runCatching {
                    conn.errorStream?.let { s ->
                        BufferedReader(InputStreamReader(s, Charsets.UTF_8)).use { it.readText() }
                    }.orEmpty()
                }.getOrDefault("")
                return@withContext Result.Fail("HTTP " + code + (if (err.isNotBlank()) "：" + err.take(200) else ""))
            }

            val text = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            val content = runCatching {
                JSONObject(text)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            }.getOrNull()

            if (content.isNullOrBlank()) Result.Fail("接口返回里没有 content")
            else Result.Ok(content.take(120))
        } catch (e: Exception) {
            Result.Fail(e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}
