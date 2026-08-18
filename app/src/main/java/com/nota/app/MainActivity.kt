package com.nota.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
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

        // 1. Set Layout SwipeRefreshLayout agar mengisi seluruh layar (MATCH_PARENT)
        swipeRefreshLayout = SwipeRefreshLayout(this)
        swipeRefreshLayout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // 2. Set Layout WebView agar mengisi 100% kontainer
        webView = WebView(this)
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        swipeRefreshLayout.addView(webView)
        setContentView(swipeRefreshLayout)

        // 3. Konfigurasi WebSettings agar Mobile-Friendly (Tidak Terpotong)
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.allowFileAccess = true

        // Matikan mode Wide ViewPort desktop agar halaman merender murni ukuran HP
        webSettings.useWideViewPort = false
        webSettings.loadWithOverviewMode = false

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
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

        // Interface untuk Blob PDF
        webView.addJavascriptInterface(BlobDownloader(this), "AndroidBlobDownloader")

        // Listener Download File PDF
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

    // Tombol Refresh di Menu Atas
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

    // Class Download & Buka PDF Otomatis
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
                    Toast.makeText(context, "PDF Berhasil Disimpan & Membuka PDF...", Toast.LENGTH_SHORT).show()

                    try {
                        val contentUri: Uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            file
                        )

                        val openIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(contentUri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        context.startActivity(openIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "File tersimpan di Download, namun gagal membuka viewer: ${e.message}", Toast.LENGTH_LONG).show()
                    }
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
