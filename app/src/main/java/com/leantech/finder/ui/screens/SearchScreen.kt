package com.leantech.finder.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.leantech.finder.domain.model.Marketplace
import com.leantech.finder.domain.model.SortOption
import com.leantech.finder.ui.AppViewModel
import com.leantech.finder.ui.UiState
import com.leantech.finder.ui.components.InfoChip
import com.leantech.finder.ui.components.ProductCard
import com.leantech.finder.ui.components.SectionTitle
import com.leantech.finder.ui.components.openLink
import com.leantech.finder.ui.theme.Navy
import com.leantech.finder.ui.theme.NavyDeep

@Composable
fun SearchScreen(state: UiState, viewModel: AppViewModel) {
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Header(
            state = state,
            onQueryChange = viewModel::onQueryChange,
            onSearch = viewModel::search,
            onSettings = viewModel::openSettings,
            onHistoryClick = viewModel::selectHistoryQuery,
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Filters(state, viewModel)

            if (state.loading) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(state.stage.ifBlank { "Ищу..." }, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            state.translatedQuery?.takeIf { state.hasSearched }?.let { chinese ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InfoChip(
                        text = "запрос на китайском: $chinese",
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "за ${state.tookMs} мс",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.problems.forEach { problem ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${problem.marketplace.title}: нужен вход или другая формулировка",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = problem.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(onClick = { viewModel.openLogin(problem.marketplace) }) {
                                Icon(Icons.Filled.AccountCircle, contentDescription = null, modifier = Modifier.height(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Войти / Зайти заново")
                            }
                            TextButton(onClick = { openLink(context, problem.webFallbackUrl) }) {
                                Text("Открыть на сайте", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }

            if (state.products.isNotEmpty()) {
                SectionTitle("Найдено товаров: ${state.products.size}")
            }
            state.products.forEachIndexed { index, product ->
                ProductCard(product = product, index = index, cnyRate = state.cnyRate)
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
@Composable
private fun Header(
    state: UiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onHistoryClick: (String) -> Unit,
) {
    val gradient = Brush.verticalGradient(listOf(NavyDeep, Navy))
    Box(
        Modifier
            .fillMaxWidth()
            .background(gradient)
            .padding(start = 16.dp, end = 8.dp, top = 28.dp, bottom = 16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Поиск по Китаю",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "Taobao · 1688 · 闲鱼 — прямой перевод и цены в ₽",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = Color.White)
                }
            }

            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Например: датчики холла для электровелосипеда", color = Color.White.copy(alpha = 0.7f)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                )
            }

            if (state.queryHistory.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.queryHistory.forEach { past ->
                        AssistChip(
                            onClick = { onHistoryClick(past) },
                            label = { Text(past, color = Color.White, maxLines = 1) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.History,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.height(16.dp),
                                )
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onSearch,
                    enabled = !state.loading,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp).width(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.height(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.loading) "Ищу..." else "Искать на ${state.selectedSources.size} площадках")
                }
            }
        }
    }
}

@Composable
private fun Filters(state: UiState, viewModel: AppViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            SectionTitle("Где искать")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Marketplace.entries.forEach { marketplace ->
                    FilterChip(
                        selected = marketplace in state.selectedSources,
                        onClick = { viewModel.toggleSource(marketplace) },
                        label = { Text(marketplace.title) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Сортировка")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.sort == SortOption.SALES_DESC,
                    onClick = { viewModel.setSort(SortOption.SALES_DESC) },
                    label = { Text("По продажам") },
                )
                FilterChip(
                    selected = state.sort == SortOption.PRICE_ASC,
                    onClick = { viewModel.setSort(SortOption.PRICE_ASC) },
                    label = { Text("Дешевле") },
                )
                FilterChip(
                    selected = state.sort == SortOption.PRICE_DESC,
                    onClick = { viewModel.setSort(SortOption.PRICE_DESC) },
                    label = { Text("Дороже") },
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Только фабрики и производители", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Скрыть розничных перекупщиков и оставить прямых поставщиков",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.factoryOnly, onCheckedChange = viewModel::setFactoryOnly)
            }
        }
    }
}
