package app.fors.camera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.result.ActivityResult
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import app.tauri.annotation.ActivityCallback
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.Permission
import app.tauri.annotation.PermissionCallback
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import java.io.ByteArrayOutputStream
import java.io.File

@InvokeArg
class TakePhotoArgs {
    var gallery: Boolean = false
    var quality: Int = 90
    var targetWidth: Int = 1440
    var targetHeight: Int = 1440
}

@TauriPlugin(
    permissions = [
        Permission(
            strings = [Manifest.permission.CAMERA],
            alias = "camera"
        )
    ]
)
class ForsCameraPlugin(private val activity: Activity) : Plugin(activity) {
    private var pendingArgs: TakePhotoArgs? = null
    private var photoUri: Uri? = null

    private fun resolveBitmap(invoke: Invoke, args: TakePhotoArgs, original: Bitmap) {
        try {
            val scaled = scaleBitmap(original, args.targetWidth, args.targetHeight)
            if (scaled !== original) original.recycle()
            val jpeg = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, args.quality.coerceIn(1, 100), jpeg)
            scaled.recycle()
            val base64 = Base64.encodeToString(jpeg.toByteArray(), Base64.NO_WRAP)
            val ret = JSObject()
            ret.put("data", base64)
            ret.put("name", System.currentTimeMillis().toString() + ".jpg")
            ret.put("mimeType", "image/jpeg")
            invoke.resolve(ret)
        } catch (ex: Exception) {
            invoke.reject(ex.message ?: "Photo error")
        }
    }

    private fun resolveUri(invoke: Invoke, args: TakePhotoArgs, uri: Uri) {
        try {
            var original = decodeSampledBitmap(uri, args.targetWidth, args.targetHeight)
            original = applyExifOrientation(original, uri)
            resolveBitmap(invoke, args, original)
        } catch (ex: Exception) {
            invoke.reject(ex.message ?: "Photo error")
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, uri: Uri): Bitmap {
        val degrees = readExifRotationDegrees(uri)
        if (degrees == 0) return bitmap
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun readExifRotationDegrees(uri: Uri): Int {
        val input = activity.contentResolver.openInputStream(uri) ?: return 0
        return input.use {
            when (ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }
    }

    private fun decodeSampledBitmap(uri: Uri, maxWidth: Int, maxHeight: Int): Bitmap {
        val resolver = activity.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        bounds.inSampleSize = calculateInSampleSize(bounds, maxWidth, maxHeight)
        bounds.inJustDecodeBounds = false
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        return decoded ?: throw IllegalStateException("Cannot decode image")
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, maxWidth: Int, maxHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > maxHeight || width > maxWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= maxHeight && halfWidth / inSampleSize >= maxWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun scaleBitmap(source: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxWidth && height <= maxHeight) return source
        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val matrix = Matrix()
        matrix.postScale(ratio, ratio)
        return Bitmap.createBitmap(source, 0, 0, width, height, matrix, true)
    }

    private fun launchCapture(invoke: Invoke, args: TakePhotoArgs) {
        pendingArgs = args
        if (args.gallery) {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(invoke, intent, "onGalleryResult")
        } else {
            val file = File(activity.cacheDir, "fors_capture_${System.currentTimeMillis()}.jpg")
            val authority = "${activity.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(activity, authority, file)
            photoUri = uri
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            startActivityForResult(invoke, intent, "onCameraResult")
        }
    }

    @ActivityCallback
    private fun onCameraResult(invoke: Invoke, result: ActivityResult) {
        val args = pendingArgs
        val uri = photoUri
        pendingArgs = null
        photoUri = null
        if (args == null) {
            invoke.reject("takePhoto args missing")
            return
        }
        if (result.resultCode != Activity.RESULT_OK || uri == null) {
            invoke.reject("No Image Selected")
            return
        }
        resolveUri(invoke, args, uri)
    }

    @ActivityCallback
    private fun onGalleryResult(invoke: Invoke, result: ActivityResult) {
        val args = pendingArgs
        pendingArgs = null
        photoUri = null
        if (args == null) {
            invoke.reject("takePhoto args missing")
            return
        }
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) {
            invoke.reject("No Image Selected")
            return
        }
        resolveUri(invoke, args, uri)
    }

    @PermissionCallback
    private fun cameraPermissionCallback(invoke: Invoke) {
        val args = pendingArgs
        if (args == null) {
            invoke.reject("takePhoto args missing")
            return
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingArgs = null
            invoke.reject("CAMERA permission denied")
            return
        }
        launchCapture(invoke, args)
    }

    @Command
    fun takePhoto(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(TakePhotoArgs::class.java)
            if (!args.gallery &&
                ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                pendingArgs = args
                requestPermissionForAlias("camera", invoke, "cameraPermissionCallback")
                return
            }
            launchCapture(invoke, args)
        } catch (ex: Exception) {
            pendingArgs = null
            photoUri = null
            invoke.reject(ex.message ?: "takePhoto error")
        }
    }
}