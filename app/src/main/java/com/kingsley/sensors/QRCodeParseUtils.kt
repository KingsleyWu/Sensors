package com.kingsley.sensors

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import com.google.zxing.*
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.*
import java.io.*
import java.util.*

class QRCodeParseUtils {
    /**
     * 启动线程解析二维码图片
     *
     * @param path 要解析的二维码图片本地路径
     * @param callback 返回二维码图片里的内容 或 null
     */
    fun CoroutineScope.parsePhoto(path: String, callback: (String?) -> Unit) {
        //启动线程完成图片扫码
        launch(Dispatchers.IO) {
            // 解析二维码/条码
            val qrCode = syncDecodeQRCode(path)
            launch(Dispatchers.Main) {
                callback(qrCode)
            }
        }
    }

    companion object {
        private val HINTS = EnumMap<DecodeHintType, Any?>(DecodeHintType::class.java).apply {
            val allFormats = mutableListOf<BarcodeFormat>().apply {
                add(BarcodeFormat.AZTEC)
                add(BarcodeFormat.CODABAR)
                add(BarcodeFormat.CODE_39)
                add(BarcodeFormat.CODE_93)
                add(BarcodeFormat.CODE_128)
                add(BarcodeFormat.DATA_MATRIX)
                add(BarcodeFormat.EAN_8)
                add(BarcodeFormat.EAN_13)
                add(BarcodeFormat.ITF)
                add(BarcodeFormat.MAXICODE)
                add(BarcodeFormat.PDF_417)
                add(BarcodeFormat.QR_CODE)
                add(BarcodeFormat.RSS_14)
                add(BarcodeFormat.RSS_EXPANDED)
                add(BarcodeFormat.UPC_A)
                add(BarcodeFormat.UPC_E)
                add(BarcodeFormat.UPC_EAN_EXTENSION)
            }
            put(DecodeHintType.TRY_HARDER, BarcodeFormat.QR_CODE)
            put(DecodeHintType.POSSIBLE_FORMATS, allFormats)
            put(DecodeHintType.CHARACTER_SET, "utf-8")
        }

        /**
         * 同步解析本地图片二维码。该方法是耗时操作，请在子线程中调用。
         *
         * @param picturePath 要解析的二维码图片本地路径
         * @return 返回二维码图片里的内容 或 null
         */
        @JvmStatic
        fun syncDecodeQRCode(picturePath: String): String? {
            return syncDecodeQRCode(getDecodeAbleBitmap(picturePath))
        }

        /**
         * 同步解析bitmap二维码。该方法是耗时操作，请在子线程中调用。
         *
         * @param bitmap 要解析的二维码图片
         * @return 返回二维码图片里的内容 或 null
         */
        @JvmStatic
        fun syncDecodeQRCode(bitmap: Bitmap?): String? {
            var result: Result?
            var source: RGBLuminanceSource? = null
            try {
                if (bitmap != null) {
                    val width = bitmap.width
                    val height = bitmap.height
                    val pixels = IntArray(width * height)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                    source = RGBLuminanceSource(width, height, pixels)
                    result = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), HINTS)
                    return result?.text
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (source != null) {
                    try {
                        result = MultiFormatReader().decode(BinaryBitmap(GlobalHistogramBinarizer(source)), HINTS)
                        return result?.text
                    } catch (e2: Throwable) {
                        e2.printStackTrace()
                    }
                }
            }
            return null
        }

        /**
         * 将本地图片文件转换成可解码二维码的 Bitmap。为了避免图片太大，这里对图片进行了压缩。感谢 https://github.com/devilsen 提的 PR
         *
         * @param picturePath 本地图片文件路径
         * @return
         */
        @Suppress("LiftReturnOrAssignment")
        private fun getDecodeAbleBitmap(picturePath: String): Bitmap? {
            try {
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(picturePath, options)
                var sampleSize = options.outHeight / 400
                if (sampleSize <= 0) {
                    sampleSize = 1
                }
                options.inSampleSize = sampleSize
                options.inJustDecodeBounds = false
                return BitmapFactory.decodeFile(picturePath, options)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        /**
         * 通过uri获取图片并进行压缩
         *
         * @param uri
         */
        @JvmStatic
        @Throws(FileNotFoundException::class, IOException::class)
        fun getBitmapFormUri(ctx: Context, uri: Uri): Bitmap? {
            var input: InputStream? = ctx.contentResolver.openInputStream(uri)
            val onlyBoundsOptions = BitmapFactory.Options()
            onlyBoundsOptions.inJustDecodeBounds = true
            if (Build.VERSION_CODES.N < Build.VERSION.SDK_INT) {
                @Suppress("DEPRECATION")
                onlyBoundsOptions.inDither = true //optional
            }
            onlyBoundsOptions.inPreferredConfig = Bitmap.Config.ARGB_8888 //optional
            BitmapFactory.decodeStream(input, null, onlyBoundsOptions)
            input?.close()
            val originalWidth = onlyBoundsOptions.outWidth
            val originalHeight = onlyBoundsOptions.outHeight
            if (originalWidth == -1 || originalHeight == -1) return null
            //图片分辨率以480x800为标准
            val hh = 800f //这里设置高度为800f
            val ww = 480f //这里设置宽度为480f
            //缩放比。由于是固定比例缩放，只用高或者宽其中一个数据进行计算即可
            var be = 1 //be=1表示不缩放
            if (originalWidth > originalHeight && originalWidth > ww) { //如果宽度大的话根据宽度固定大小缩放
                be = (originalWidth / ww).toInt()
            } else if (originalWidth < originalHeight && originalHeight > hh) { //如果高度高的话根据宽度固定大小缩放
                be = (originalHeight / hh).toInt()
            }
            if (be <= 0) be = 1
            //比例压缩
            val bitmapOptions = BitmapFactory.Options()
            bitmapOptions.inSampleSize = be //设置缩放比例
            if (Build.VERSION_CODES.N < Build.VERSION.SDK_INT) {
                @Suppress("DEPRECATION")
                bitmapOptions.inDither = true //optional
            }
            bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888 //optional
            input = ctx.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(input, null, bitmapOptions)
            input?.close()
            return bitmap?.compressImage() //再进行质量压缩
        }

        /**
         * 质量压缩方法
         *
         * @return
         */
        private fun Bitmap.compressImage(): Bitmap? {
            val baos = ByteArrayOutputStream()
            compress(Bitmap.CompressFormat.JPEG, 100, baos) //质量压缩方法，这里100表示不压缩，把压缩后的数据存放到baos中
            var options = 100
            while (baos.toByteArray().size / 1024 > 100) { //循环判断如果压缩后图片是否大于100kb,大于继续压缩
                baos.reset() //重置baos即清空baos
                //第一个参数 ：图片格式 ，第二个参数： 图片质量，100为最高，0为最差 ，第三个参数：保存压缩后的数据的流
                compress(Bitmap.CompressFormat.JPEG, options, baos) //这里压缩options%，把压缩后的数据存放到baos中
                options -= 10 //每次都减少10
            }
            val isBm = ByteArrayInputStream(baos.toByteArray()) //把压缩后的数据baos存放到ByteArrayInputStream中
            return BitmapFactory.decodeStream(isBm, null, null)
        }
    }
}