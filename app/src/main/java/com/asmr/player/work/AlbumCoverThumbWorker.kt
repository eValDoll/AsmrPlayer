package com.asmr.player.work

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.drawable.toBitmap
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.remote.NetworkHeaders
import java.io.File
import java.io.FileOutputStream

class AlbumCoverThumbWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val albumId = inputData.getLong(KEY_ALBUM_ID, 0L)
        if (albumId <= 0L) return Result.failure()

        val database = AppDatabaseProvider.get(applicationContext)
        val albumDao = database.albumDao()
        val entity = albumDao.getAlbumById(albumId) ?: return Result.success()

        val source = entity.coverPath.takeIf { it.isNotBlank() && it != "null" } ?: entity.coverUrl
        if (source.isBlank()) return Result.success()

        val sourceHash = source.trim().hashCode().toString()
        val dir = File(applicationContext.filesDir, "album_thumbs")
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, "a_${albumId}_$sourceHash.jpg")

        if (entity.coverThumbPath == target.absolutePath && target.exists() && target.length() > 0L) {
            return Result.success()
        }

        val request = ImageRequest.Builder(applicationContext)
            .data(source)
            .size(THUMB_SIZE_PX)
            .allowHardware(false)
            .apply {
                if (source.startsWith("http", ignoreCase = true) && source.contains("dlsite", ignoreCase = true)) {
                    addHeader("Referer", NetworkHeaders.REFERER_DLSITE)
                    addHeader("User-Agent", NetworkHeaders.USER_AGENT)
                    addHeader("Accept-Language", NetworkHeaders.ACCEPT_LANGUAGE)
                }
            }
            .build()

        val result = applicationContext.imageLoader.execute(request)
        val drawable = (result as? SuccessResult)?.drawable ?: return Result.success()

        val bitmap = drawable.toBitmap()
        val thumb = centerCropSquare(bitmap, THUMB_SIZE_PX)

        FileOutputStream(target).use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }

        val updated = entity.copy(coverThumbPath = target.absolutePath)
        albumDao.updateAlbum(updated)
        return Result.success()
    }

    private fun centerCropSquare(src: Bitmap, size: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val side = minOf(w, h)
        val left = (w - side) / 2
        val top = (h - side) / 2
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            src,
            Rect(left, top, left + side, top + side),
            Rect(0, 0, size, size),
            paint
        )
        return out
    }

    companion object {
        const val KEY_ALBUM_ID = "albumId"
        private const val THUMB_SIZE_PX = 320
    }
}
