package com.nota.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.view.Menu
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Membuat tata letak utama dengan SwipeRefreshLayout
        swipeRefreshLayout = SwipeRefreshLayout(this)
        webView = WebView(this)

        swipeRefreshLayout.addView(webView)
        setContentView(swipeRefreshLayout)

        // Konfigurasi WebSettings
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.allowFileAccess = true
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true

        // Mengatur perilaku WebView
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Hentikan animasi putar refresh saat halaman selesai dimuat
                swipeRefreshLayout.isRefreshing = false
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null) {
                    view?.loadUrl(url)
                }
                return true
            }
        }

        webView.webChromeClient = WebChromeClient()

        // Fitur Swipe to Refresh
        swipeRefreshLayout.setOnRefreshListener {
            webView.reload()
        }

        // Menambahkan Interface Penanganan Unduhan PDF Blob
        webView.addJavascriptInterface(BlobDownloader(this), "AndroidBlobDownloader")

        // Penanganan Unduhan File PDF
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            if (url.startsWith("blob:")) {
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

        webView.loadUrl("https://nota.serviceacjakarta.my.id/")
    }

    // Menambahkan Tombol Refresh pada Menu Atas (Opsional)
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "Refresh")?.setIcon(android.R.drawable.ic_menu_rotate)?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            swipeRefreshLayout.isRefreshing = true
            webView.reload()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // Class Pengunduh Blob PDF
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
