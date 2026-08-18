package com.nota.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.allowFileAccess = true
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null) {
                    view?.loadUrl(url)
                }
                return true
            }
        }

        webView.webChromeClient = WebChromeClient()

        // Menambahkan antarmuka JavaScript untuk menangani Blob PDF
        webView.addJavascriptInterface(BlobDownloader(this), "AndroidBlobDownloader")

        // Menangkap event unduhan dari WebView
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            if (url.startsWith("blob:")) {
                // Menangani Blob URL buatan jsPDF
                val js = """
                    (function() {
                        var xhr = new XMLHttpRequest();
                        xhr.open('GET', '$url', true);
                        xhr.responseType = 'blob';
                        xhr.onload = function(e) {
                            if (this.status == 200) {
                                var blob = this.response;
                                var reader = new FileReader();
                                reader.readAsDataURL(blob);
                                reader.onloadend = function() {
                                    var base64data = reader.result;
                                    AndroidBlobDownloader.getBase64FromBlobData(base64data, '$mimetype');
                                }
                            }
                        };
                        xhr.send();
                    })();
                """.trimIndent()
                webView.evaluateJavascript(js, null)
            } else {
                // Menangani unduhan URL HTTPS standar
                val request = DownloadManager.Request(Uri.parse(url))
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                request.setMimeType(mimetype)
                request.addRequestHeader("User-Agent", userAgent)
                request.setTitle(fileName)
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(applicationContext, "Mengunduh file: $fileName", Toast.LENGTH_LONG).show()
            }
        }

        webView.loadUrl("https://serviceacjakarta.my.id/irfannota/notairfani.html")
    }

    // Class pembantu untuk mengonversi Blob Base64 ke File PDF fisik di folder Download
    class BlobDownloader(private val context: Context) {
        @JavascriptInterface
        fun getBase64FromBlobData(base64Data: String, mimeType: String) {
            try {
                val cleanBase64 = base64Data.replaceFirst("data:$mimeType;base64,", "")
                val pdfBytes = Base64.decode(cleanBase64, Base64.DEFAULT)

                val fileName = "Nota-Servis-${System.currentTimeMillis()}.pdf"
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)

                val os: OutputStream = FileOutputStream(file)
                os.write(pdfBytes)
                os.flush()
                os.close()

                (context as AppCompatActivity).runOnUiThread {
                    Toast.makeText(context, "PDF Berhasil Disimpan ke Folder Download!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                (context as AppCompatActivity).runOnUiThread {
                    Toast.makeText(context, "Gagal menyimpan PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
