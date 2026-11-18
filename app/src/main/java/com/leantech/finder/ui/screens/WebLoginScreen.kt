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

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 22.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Вход: ${marketplace.title}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Нажмите Alipay для входа через кошелёк или введите номер/пароль.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = {
                val webView = webViewState.value ?: return@TextButton
                // Прямой переход на страницу входа с поддержкой Alipay для выбранной площадки
                val alipayEntryUrl = when (marketplace) {
                    Marketplace.TAOBAO -> "https://taobao.com"
                    Marketplace.XIANYU -> "https://taobao.com"
                    else -> "https://1688.com"
                }
                webView.loadUrl(alipayEntryUrl)
                Toast.makeText(context, "Открыта форма с входом через Alipay (支付宝)", Toast.LENGTH_SHORT).show()
            }) { Text("Alipay") }

            TextButton(onClick = {
                ServiceLocator.cookies.flush()
                onClosed()
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
                            if (url.startsWith("alipays://") || url.startsWith("alipay://") || url.startsWith("intent://")) {
                                return try {
                                    val intent = if (url.startsWith("intent://")) {
                                        Intent.parseUri(url, 1)
                                    } else {
                                        Intent("android.intent.action.VIEW", Uri.parse(url))
                                    }
                                    intent.addFlags(268435456)
                                    ctx.startActivity(intent)
                                    true
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "Приложение Alipay не найдено", Toast.LENGTH_SHORT).show()
                                    false
                                }
                            }
                            return false
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
