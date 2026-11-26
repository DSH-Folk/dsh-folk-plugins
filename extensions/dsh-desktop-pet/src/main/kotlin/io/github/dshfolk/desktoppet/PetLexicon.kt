package io.github.dshfolk.desktoppet

import org.json.JSONObject
import kotlin.random.Random

/**
 * 台词场景。
 *
 * 这七个分类沿用 dsh-pet-luotianyi 的划分：它的词库文件就是按这七个键组织的，
 * 保持一致可以让用户直接把自己攒的台词 JSON 导进来，不用改写。
 */
enum class PetScene(val key: String, val label: String) {
    GREET("greet", "打招呼"),
    PAT("pat", "抚摸"),
    FEED("feed", "喂食"),
    SLEEP("sleep", "睡觉"),
    WAKE("wake", "唤醒"),
    IDLE("idle", "闲置"),
    PHOTO("photo", "换图");

    companion object {
        fun ofKey(key: String): PetScene? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 词库：每个场景一组台词。
 *
 * ## 为什么默认词库要写死在代码里
 *
 * 第一次装上就说话，用户才有"它活着"的感觉。若默认是空的、要用户自己填，
 * 大多数人永远不会填 —— 桌宠就变成一个不能交互的贴图。
 *
 * 默认词库同时也是**格式示例**：用户想自定义时照着它的结构写 JSON 即可。
 */
data class PetLexicon(val lines: Map<PetScene, List<String>>) {

    /** 随机取一句；该场景没有台词时返回 null（调用方决定要不要兜底）。 */
    fun pick(scene: PetScene): String? {
        val pool = lines[scene]?.filter { it.isNotBlank() } ?: return null
        if (pool.isEmpty()) return null
        return pool[Random.nextInt(pool.size)]
    }

    /** 序列化成 JSON（导入/导出用）。 */
    fun toJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        val scenes = JSONObject()
        for ((scene, list) in lines) {
            val arr = org.json.JSONArray()
            list.forEach { arr.put(it) }
            scenes.put(scene.key, arr)
        }
        root.put("scenes", scenes)
        return root.toString(2)
    }

    companion object {

        /** 内置默认词库。 */
        val DEFAULT: PetLexicon = PetLexicon(
            mapOf(
                PetScene.GREET to listOf(
                    "在的。",
                    "来了？",
                    "今天想聊点什么。",
                    "我在这儿。",
                ),
                PetScene.PAT to listOf(
                    "嗯…",
                    "再摸一下也可以。",
                    "别揉乱我的头发。",
                    "……谢谢。",
                ),
                PetScene.FEED to listOf(
                    "谢谢，我收下了。",
                    "这个我喜欢。",
                    "还有吗？",
                    "味道不错。",
                ),
                PetScene.SLEEP to listOf(
                    "那我先睡一会儿。",
                    "有事再叫我。",
                    "Zzz…",
                ),
                PetScene.WAKE to listOf(
                    "醒了。",
                    "唔…多久了？",
                    "我梦到你在敲代码。",
                ),
                PetScene.IDLE to listOf(
                    "容器还在跑。",
                    "需要我帮什么吗？",
                    "要不要看看日志？",
                    "我一直在这儿。",
                ),
                PetScene.PHOTO to listOf(
                    "换好了，看看合不合适。",
                    "这张我也喜欢。",
                ),
            ),
        )

        /**
         * 解析用户导入的 JSON。
         *
         * 容错优先：单个场景格式不对只跳过那一个，不让整份词库导入失败 ——
         * 用户手写的 JSON 很难一次全对，全有或全无的校验只会让人放弃。
         *
         * @return 解析结果；完全无法解析时返回 null（调用方保留原词库）
         */
        fun fromJson(text: String): PetLexicon? {
            val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
            val scenes = root.optJSONObject("scenes") ?: root   // 也接受直接给 scenes 层
            val out = LinkedHashMap<PetScene, List<String>>()
            for (scene in PetScene.entries) {
                val arr = scenes.optJSONArray(scene.key) ?: continue
                val list = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i, "")
                    if (v.isNotBlank()) list.add(v)
                }
                if (list.isNotEmpty()) out[scene] = list
            }
            return if (out.isEmpty()) null else PetLexicon(out)
        }

        /**
         * 合并：用户词库覆盖默认词库中**有对应场景**的部分，缺失场景仍用默认。
         *
         * 为什么合并而不是替换：用户往往只改其中一两个场景（比如只加自己的抚摸台词），
         * 纯替换会让其它场景变成"不说话"，看起来像坏了。
         */
        fun merge(user: PetLexicon?, fallback: PetLexicon = DEFAULT): PetLexicon {
            if (user == null) return fallback
            val merged = LinkedHashMap<PetScene, List<String>>()
            for (scene in PetScene.entries) {
                merged[scene] = user.lines[scene] ?: fallback.lines[scene] ?: emptyList()
            }
            return PetLexicon(merged)
        }
    }
}
