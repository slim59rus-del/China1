package com.leantech.finder.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val settings = ServiceLocator.settings
    val webViewHolder = remember { arrayOfNulls<WebView>(1) }

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
                    text = "Войдите через Alipay или по логину/паролю — сессия сохранится.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = {
                // Попытка нажать на кнопку входа через Alipay на странице площадки
                webViewHolder[0]?.evaluateJavascript(
                    """
                    (function() {
                        var alipayBtn = document.querySelector('a[href\*="alipay"], .alipay-login, [class\*="alipay"]');
                        if (alipayBtn) { alipayBtn.click(); }
                    })();
                    """.trimIndent(),
                    null
                )
            }) { Text("Alipay") }

            TextButton(onClick = {
                ServiceLocator.cookies.flush()
                onClosed()
            }) { Text("Готово") }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webViewHolder[0] = this
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
                            // Перехват перехода в приложение Alipay или Taobao
                            if (url.startsWith("alipays://") || url.startsWith("alipay://") || url.startsWith("intent://")) {
                                return try {
                                    val intent = if (url.startsWith("intent://")) {
                                        Intent.parseUri(url, Intent.URI\_INTENT\_SCHEME)
                                    } else {
                                        Intent(Intent.ACTION\_VIEW, Uri.parse(url))
                                    }
                                    intent.addFlags(Intent.FLAG\_ACTIVITY\_NEW\_TASK)
                                    context.startActivity(intent)
                                    true
                                } catch (\_: Exception) {
                                    // Если приложение Alipay не установлено, остаёмся в веб-версии
                                    false
                                }
                            }
                            return false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            ServiceLocator.cookies.flush()

                            val savedLogin = ServiceLocator.settings.savedLogin.replace("'", "\\'")
                            val savedPass = ServiceLocator.settings.savedPassword.replace("'", "\\'")

                            // Автоподстановка и отслеживание ввода логина и пароля
                            val script = """
                                (function() {
                                    var loginInput = document.querySelector('input[name="fm-login-id"], input[id\*="login-id"], input[type="text"], input[type="tel"]');
                                    var passInput = document.querySelector('input[name="fm-login-password"], input[id\*="login-password"], input[type="password"]');
                                    
                                    if (loginInput && '$savedLogin' !== '' && !loginInput.value) {
                                        loginInput.value = '$savedLogin';
                                        loginInput.dispatchEvent(new Event('input', { bubbles: true }));
                                    }
                                    if (passInput && '$savedPass' !== '' && !passInput.value) {
                                        passInput.value = '$savedPass';
                                        passInput.dispatchEvent(new Event('input', { bubbles: true }));
                                    }

                                    function saveValues() {
                                        var l = loginInput ? loginInput.value : '';
                                        var p = passInput ? passInput.value : '';
                                        if (l || p) {
                                            AndroidCredsBridge.onCredentialsEntered(l, p);
                                        }
                                    }

                                    if (loginInput) loginInput.addEventListener('change', saveValues);
                                    if (passInput) passInput.addEventListener('change', saveValues);
                                    document.addEventListener('submit', saveValues, true);
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(script, null)

                            if (ServiceLocator.login.isLoggedIn(marketplace)) {
                                ServiceLocator.cookies.backupKnownDomains()
                                onLoggedIn()
                            }
                        }
                    }
                    loadUrl(marketplace.loginUrl)
                }
            },
        )
    }
}
