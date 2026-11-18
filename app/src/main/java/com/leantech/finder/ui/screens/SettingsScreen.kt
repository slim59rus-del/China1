package com.leantech.finder.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.leantech.finder.domain.model.Marketplace
import com.leantech.finder.ui.AppViewModel
import com.leantech.finder.ui.UiState
import com.leantech.finder.ui.components.InfoChip
import com.leantech.finder.ui.components.SectionTitle

@Composable
fun SettingsScreen(state: UiState, viewModel: AppViewModel) {
    BackHandler(enabled = true) {
        viewModel.openSearch()
    }

    var rateText by remember(state.cnyRate) { mutableStateOf(state.cnyRate.toString()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 14.dp, top = 22.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::openSearch) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text("Настройки и аккаунты", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier.padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    SectionTitle("Аккаунты площадок (Alipay / Телефон)")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Управляйте входом для каждой площадки отдельно. Сессия сохраняется после выхода из приложения.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))

                    Marketplace.entries.forEachIndexed { idx, marketplace ->
                        if (idx > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            )
                        }
                        val loggedIn = state.loginStatus[marketplace] == true
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = marketplace.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                if (loggedIn) {
                                    InfoChip(text = "Активен")
                                } else {
                                    InfoChip(
                                        text = "Не выполнен вход",
                                        container = MaterialTheme.colorScheme.surfaceVariant,
                                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (loggedIn) {
                                    OutlinedButton(onClick = { viewModel.reloginMarketplace(marketplace) }) {
                                        Text("Зайти заново")
                                    }
                                    TextButton(onClick = { viewModel.logoutMarketplace(marketplace) }) {
                                        Text("Выйти", color = MaterialTheme.colorScheme.error)
                                    }
                                } else {
                                    Button(onClick = { viewModel.openLogin(marketplace) }) {
                                        Text("Войти (Alipay / Тел.)")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::refreshLoginStatus) { Text("Обновить статусы") }
                        OutlinedButton(onClick = viewModel::logout) { Text("Выйти везде") }
                    }
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    SectionTitle("Валюта и расчёт цены")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { input ->
                            rateText = input
                            input.replace(',', '.').toFloatOrNull()?.let { viewModel.setCnyRate(it) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Курс 1 юаня (¥) в рублях (₽)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = {
                            Text("Используется для расчёта ориентировочной стоимости в рублях на карточках товаров.")
                        },
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    SectionTitle("Перевод и поиск")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Переводить названия товаров", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Прямой перевод с китайского на русский (китайский оригинал остаётся ниже).",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.translateTitles, onCheckedChange = viewModel::setTranslateTitles)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Товаров с каждой площадки: ${state.limitPerMarketplace}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = state.limitPerMarketplace.toFloat(),
                        onValueChange = { viewModel.setLimit(it.toInt()) },
                        valueRange = 10f..100f,
                        steps = 8,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.translatorEndpoint,
                        onValueChange = viewModel::setTranslatorEndpoint,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Адрес сервиса перевода (необязательно)") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.proxyBaseUrl,
                        onValueChange = viewModel::setProxyBaseUrl,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Адрес сервера подписи (необязательно)") },
                        singleLine = true,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
