package com.resat.appjail

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Kullanıcıya gösterilecek uygulama satırının veri modeli. */
data class AppRow(val pkg: String, val label: String, val usedMinutes: Long, val limit: Int?)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Screen() } }
    }

    @Composable
    private fun Screen() {
        val ctx = LocalContext.current
        var rows by remember { mutableStateOf(emptyList<AppRow>()) }
        var refresh by remember { mutableStateOf(0) }

        // Ekran her tazelendiğinde uygulama listesi ve kullanım süreleri yeniden okunur
        LaunchedEffect(refresh) { rows = loadRows() }

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("AppJail", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))

            // İki izin de verilmeden sistem çalışmaz, bu yüzden kısayollar en üstte
            Row {
                Button(onClick = {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }) { Text("Kullanım izni") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("Erişilebilirlik") }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Süreyi kısaltmak anında, uzatmak yarın geçerli olur. Limit dolan uygulama gün sonuna kadar açılmaz.",
                style = MaterialTheme.typography.bodySmall
            )
            Divider(Modifier.padding(vertical = 12.dp))

            LazyColumn {
                items(rows, key = { it.pkg }) { row ->
                    AppRowItem(row) { refresh++ }
                    Divider()
                }
            }
        }
    }

    /** Tek bir uygulama satırı: isim, bugünkü kullanım ve dakika girişi. */
    @Composable
    private fun AppRowItem(row: AppRow, onChanged: () -> Unit) {
        val ctx = LocalContext.current
        var text by remember(row.pkg, row.limit) { mutableStateOf(row.limit?.toString() ?: "") }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.label, style = MaterialTheme.typography.bodyLarge)
                Text("Bugün ${row.usedMinutes} dk", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input.filter { it.isDigit() }.take(4)
                    val minutes = text.toIntOrNull()
                    // Boş bırakmak takibi kaldırır, sayı girmek limiti yazar
                    if (minutes == null) LimitStore.removeLimit(ctx, row.pkg)
                    else LimitStore.setLimit(ctx, row.pkg, minutes)
                    onChanged()
                },
                label = { Text("dk") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier.width(96.dp)
            )
        }
    }

    /** Çekmecede görünen uygulamaları toplar ve bugünkü süreleriyle eşleştirir. */
    private fun loadRows(): List<AppRow> {
        val pm = packageManager
        val usage = if (UsageTracker.hasUsagePermission(this)) UsageTracker.usageTodayMillis(this) else emptyMap()
        val limits = LimitStore.pendingLimits(this)

        return pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }  // sistem servislerini ele
            .filter { it.packageName != packageName }                          // kendimizi listeleme
            .map {
                AppRow(
                    pkg = it.packageName,
                    label = pm.getApplicationLabel(it).toString(),
                    usedMinutes = (usage[it.packageName] ?: 0L) / 60_000L,
                    limit = limits[it.packageName]
                )
            }
            .sortedWith(compareByDescending<AppRow> { it.usedMinutes }.thenBy { it.label })
    }
}
