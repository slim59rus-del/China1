package com.leantech.finder.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.leantech.finder.di.ServiceLocator
import com.leantech.finder.domain.model.Marketplace

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLoginScreen(
    marketplace: Marketplace,
    onClosed: () -> Unit,
    onLoggedIn: () -> Unit = {},
) {
    val context = LocalContext.current
    val webViewState = remember { mutableStateOf<WebView?>(null) }

    fun saveAndClose() {
        ServiceLocator.cookies.flush()
        ServiceLocator.cookies.backupKnownDomains()
        if (ServiceLocator.login.isLoggedIn(marketplace)) {
            onLoggedIn()
        }
        onClosed()
    }

    BackHandler(enabled = true) {
        val wv = webViewState.value
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            saveAndClose()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 6.dp, top = 22.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                val wv = webViewState.value
                if (wv != null && wv.canGoBack()) {
                    wv.goBack()
                } else {
                    saveAndClose()
                }
            }) { Text("← Назад") }

            Column(Modifier.weight(1f)) {
                Text(
                    text = marketplace.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            TextButton(onClick = {
                val webView = webViewState.value ?: return@TextButton
                webView.loadUrl(marketplace.loginUrl)
                Toast.makeText(context, "Форма входа по номеру телефона", Toast.LENGTH_SHORT).show()
            }) { Text("Телефон") }

            TextButton(onClick = {
                val webView = webViewState.value ?: return@TextButton
                val alipayEntryUrl = when (marketplace) {
                    Marketplace.TAOBAO -> "https://taobao.com"
                    Marketplace.XIANYU -> "https://taobao.com"
                    else -> "https://m.1688.com"
                }
                webView.loadUrl(alipayEntryUrl)
                Toast.makeText(context, "Открыта форма с иконкой Alipay (支付宝)", Toast.LENGTH_SHORT).show()
            }) { Text("Alipay") }

            TextButton(onClick = {
                saveAndClose()
            }) { Text("Готово") }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewState.value = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onCredentialsEntered(login: String, password: String) {
                                if (login.isNotBlank()) ServiceLocator.settings.savedLogin = login
                                if (password.isNotBlank()) ServiceLocator.settings.savedPassword = password
                            }
                        },
                        "AndroidCredsBridge"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            val scheme = request.url?.scheme?.lowercase() ?: ""

                            if (scheme == "http" || scheme == "https") {
                                return false
                            }

                            if (url.startsWith("alipays://") || url.startsWith("alipay://") ||
                                (url.startsWith("intent://") && url.contains("alipay", ignoreCase = true))
                            ) {
                                return try {
                                    val intent = if (url.startsWith("intent://")) {
                                        Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                    } else {
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    }
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ctx.startActivity(intent)
                                    true
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "Приложение Alipay не найдено", Toast.LENGTH_SHORT).show()
                                    true
                                }
                            }

                            // Блокируем wireless1688://, tbopen:// и прочие диплинки в родные приложения
                            return true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            ServiceLocator.cookies.flush()

                            val savedLogin = ServiceLocator.settings.savedLogin.replace("'", "\\'")
                            val savedPass = ServiceLocator.settings.savedPassword.replace("'", "\\'")
                            val jsAutoFill = "(function(){var l=document.querySelector('input[name=\"fm-login-id\"],input[type=\"text\"],input[type=\"tel\"]');var p=document.querySelector('input[name=\"fm-login-password\"],input[type=\"password\"]');if(l&&'$savedLogin'!==''&&!l.value){l.value='$savedLogin';l.dispatchEvent(new Event('input',{bubbles:true}));}if(p&&'$savedPass'!==''&&!p.value){p.value='$savedPass';p.dispatchEvent(new Event('input',{bubbles:true}));}function sv(){var lv=l?l.value:'';var pv=p?p.value:'';if(lv||pv)AndroidCredsBridge.onCredentialsEntered(lv,pv);}if(l)l.addEventListener('change',sv);if(p)p.addEventListener('change',sv);document.addEventListener('submit',sv,true);})();"
                            view?.evaluateJavascript(jsAutoFill, null)

                            if (ServiceLocator.login.isLoggedIn(marketplace)) {
                                ServiceLocator.cookies.backupKnownDomains()
                                onLoggedIn()
                            }
                        }
                    }
                    loadUrl(marketplace.loginUrl)
                }
            }
        )
    }
}
