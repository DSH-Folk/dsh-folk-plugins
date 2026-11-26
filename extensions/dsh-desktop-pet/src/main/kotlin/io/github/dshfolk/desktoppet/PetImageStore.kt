package io.github.dshfolk.desktoppet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * 用户换图的落盘逻辑。
 *
 * ## 为什么要缩放
 *
 * 手机相册里的图动辄 4000×3000。桌宠最大显示 140dp（约 400px），原图存下来
 * 只有两个后果：占存储、每次解码都慢（一张 12MP 图解码要几百毫秒，重组时明显卡）。
 * 缩到长边 512 已经超过最大显示尺寸，肉眼无差别。
 *
 * ## 为什么统一存 PNG
 *
 * 用户可能选 JPG/WebP/GIF。存 PNG 的理由是**透明通道**：桌宠要叠在界面上，
 * JPG 没有 alpha，存成 JPG 会出现一个白底方块。代价是体积大一些，但 512px 的
 * PNG 只有几十 KB。
 *
 * GIF 会被压成静态首帧 —— 这个扩展目前不支持动图（要动图得换 Coil/AnimatedImageDrawable，
 * 而扩展不能带依赖）。这一点在配置面板里有提示。
 */
object PetImageStore {

    /** 缩放后的长边上限。 */
    private const val MAX_EDGE_PX = 512

    /**
     * 把用户选的图处理并保存。返回是否成功。
     *
     * 全流程 runCatching：用户可能选一个损坏文件、或选择器返回的 uri 已经失效，
     * 那些都不该让 App 崩。
     */
    fun importFrom(context: Context, uri: Uri, dest: File): Boolean = runCatching {
        // 第一次只读边界拿尺寸，不分配像素内存（inJustDecodeBounds）。
        // 直接 decodeStream 一张 12MP 图要 48MB，低端机会直接 OOM。
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_EDGE_PX)
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return false

        val scaled = scaleToMaxEdge(decoded, MAX_EDGE_PX)
        if (scaled !== decoded) decoded.recycle()

        dest.parentFile?.mkdirs()
        val ok = runCatching {
            dest.outputStream().use { out ->
                // PNG 保留透明通道；质量参数对 PNG 无效（无损）。
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }.getOrDefault(false)
        scaled.recycle()
        ok
    }.getOrDefault(false)

    /** 删掉自定义图（恢复默认）。返回是否真的删掉了文件。 */
    fun restoreDefault(dest: File): Boolean = runCatching {
        dest.isFile && dest.delete()
    }.getOrDefault(false)

    /** 计算 2 的幂采样率，先粗降一轮再精确缩放（省内存）。 */
    private fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxEdge && h / 2 >= maxEdge) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    /** 精确缩放到长边 = maxEdge；已经够小就原样返回。 */
    private fun scaleToMaxEdge(src: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxEdge) return src
        val ratio = maxEdge.toFloat() / longest
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}
