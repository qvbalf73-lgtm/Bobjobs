package com.boxverse.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 图片存储：用户从相册或相机导入的图片会被压缩后复制到应用私有目录 files/images/，
 * 数据库中只保存文件名，备份时一起打包。
 */
object ImageStore {
    private const val MAX_SIZE = 1600

    fun dir(context: Context): File = File(context.filesDir, "images").apply { mkdirs() }

    fun file(context: Context, name: String): File =
        if (name.startsWith("/")) File(name) else File(dir(context), name)

    fun authority(context: Context): String = "${context.packageName}.fileprovider"

    /** 为相机拍照准备一个临时文件 Uri */
    fun newCameraUri(context: Context): Uri {
        val camDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val f = File(camDir, "cam_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, authority(context), f)
    }

    /** 导入图片，返回保存后的文件名；失败返回 null */
    suspend fun import(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_SIZE || bounds.outHeight / (sample * 2) >= MAX_SIZE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@runCatching null
            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees != 0f) {
                val m = Matrix().apply { postRotate(degrees) }
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            }
            val longest = maxOf(bitmap.width, bitmap.height)
            if (longest > MAX_SIZE) {
                val scale = MAX_SIZE.toFloat() / longest
                bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            }
            val name = "img_${UUID.randomUUID()}.jpg"
            FileOutputStream(File(dir(context), name)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            name
        }.getOrNull()
    }

    fun delete(context: Context, name: String?) {
        if (name.isNullOrBlank()) return
        runCatching { file(context, name).delete() }
    }
}

object ShareUtil {
    /** 把图片保存到缓存目录并调起系统分享 */
    suspend fun shareBitmap(context: Context, bitmap: Bitmap, title: String) {
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val f = File(dir, "boxverse_${System.currentTimeMillis()}.png")
            FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, ImageStore.authority(context), f)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** 新建款式时使用的占位插画配色 */
object ArtPalette {
    val pairs: List<Pair<Long, Long>> = listOf(
        0xFFFFB3C7 to 0xFFFFE3EC, 0xFFB9A8FF to 0xFFE6DEFF, 0xFFA8E6CF to 0xFFE0F7EC,
        0xFFFFD3A5 to 0xFFFFF0DC, 0xFF9AD0FF to 0xFFDDF0FF, 0xFFFFE27A to 0xFFFFF7D1,
        0xFFD7B5FF to 0xFFF1E5FF, 0xFFFFA8A8 to 0xFFFFE1E1, 0xFFB5EAD7 to 0xFFE9FBF3,
        0xFFF7A6C9 to 0xFFC9B8FF, 0xFF8EC5FF to 0xFFFFD1E8, 0xFFFFC48A to 0xFFFFE9A8,
    )

    val seriesThemes: List<Pair<Long, Long>> = listOf(
        0xFFC9B8FF to 0xFFFFB8D6, 0xFF8EA8FF to 0xFFB59CFF, 0xFFFFC9A3 to 0xFFFFE6B8,
        0xFFA8E0B8 to 0xFFD8F5A8, 0xFFFF9EC0 to 0xFFFFD6A8, 0xFF9AD8F0 to 0xFFC8B8FF,
    )

    fun pick(seed: Int): Pair<Long, Long> = pairs[Math.floorMod(seed, pairs.size)]
}
