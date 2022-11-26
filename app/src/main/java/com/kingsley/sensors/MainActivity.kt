package com.kingsley.sensors

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Nullable
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let {
            val startTimeMillis = System.currentTimeMillis()
            Log.d(TAG, "start: $startTimeMillis")
            parsePhoto(it){
                Log.d(TAG, ": $it")
                val endTimeMillis = System.currentTimeMillis()
                Log.d(TAG, "start: $startTimeMillis, end: $endTimeMillis, 用時： ${endTimeMillis - startTimeMillis}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.tv_main).setOnClickListener {
            takePictureLauncher.launch("image/*")
        }
    }

    private fun openImageUtils() {
        val intent: Intent
        if (Build.VERSION.SDK_INT < 19) {
            intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
        } else {
            intent = Intent(
                Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        }
        startActivityForResult(intent, 1000)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, @Nullable data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                1000 -> {
                    var uri = data?.data
                    if (data != null && uri != null) {

                    }
                }
                else -> {

                }
            }
        }
    }

    /**
     * 启动线程解析二维码图片
     *
     * @param path 要解析的二维码图片本地路径
     * @param callback 返回二维码图片里的内容 或 null
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun parsePhoto(path: String, callback: (String?) -> Unit) {
        //启动线程完成图片扫码
        GlobalScope.launch(Dispatchers.IO) {
            // 解析二维码/条码
            val qrCode = QRCodeParseUtils.syncDecodeQRCode(path)
            launch(Dispatchers.Main) {
                callback(qrCode)
            }
        }
    }

    /**
     * 启动线程解析二维码图片
     *
     * @param uri 要解析的二维码图片本地路径
     * @param callback 返回二维码图片里的内容 或 null
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun parsePhoto(uri: Uri, callback: (String?) -> Unit) {
        //启动线程完成图片扫码
        GlobalScope.launch(Dispatchers.IO) {
            // 解析二维码/条码
            val qrCode = QRCodeParseUtils.syncDecodeQRCode(QRCodeParseUtils.getBitmapFormUri(this@MainActivity, uri))
            launch(Dispatchers.Main) {
                callback(qrCode)
            }
        }
    }
}