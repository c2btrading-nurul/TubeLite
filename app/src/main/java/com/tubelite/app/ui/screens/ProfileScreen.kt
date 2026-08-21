package com.tubelite.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.tubelite.app.data.AppLanguageStore
import com.tubelite.app.data.CloudSync
import com.tubelite.app.data.LocalBackupStore
import com.tubelite.app.data.WatchHistoryStore

private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

@Composable
fun ProfileScreen(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    autoplayNext: Boolean,
    onAutoplayNextChange: (Boolean) -> Unit,
    language: String = AppLanguageStore.BANGLA,
    onLanguageChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var account by remember { mutableStateOf(GoogleSignIn.getLastSignedInAccount(context)) }
    var syncing by remember { mutableStateOf(false) }
    var languageMenuExpanded by remember { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val success = LocalBackupStore.export(context, uri)
            Toast.makeText(
                context,
                if (success) {
                    if (language == AppLanguageStore.ENGLISH) "Backup completed" else "ব্যাকআপ সম্পন্ন হয়েছে"
                } else {
                    if (language == AppLanguageStore.ENGLISH) "Backup failed" else "ব্যাকআপ ব্যর্থ হয়েছে"
                },
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val success = LocalBackupStore.import(context, uri)
            Toast.makeText(
                context,
                if (success) {
                    if (language == AppLanguageStore.ENGLISH) "Restore completed. Please restart the app." else "রিস্টোর সম্পন্ন হয়েছে। অনুগ্রহ করে অ্যাপটি পুনরায় চালু করুন।"
                } else {
                    if (language == AppLanguageStore.ENGLISH) "Restore failed" else "রিস্টোর ব্যর্থ হয়েছে"
                },
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_APPDATA_SCOPE))
            .build()
    }
    val client = remember { GoogleSignIn.getClient(context, gso) }

    fun syncAfterSignIn() {
        syncing = true
        CloudSync.pullAll(context) { found ->
            if (!found) CloudSync.pushAll(context)
            syncing = false
            Toast.makeText(
                context,
                if (language == AppLanguageStore.ENGLISH) {
                    if (found) "Data restored from Google Drive" else "Signed in successfully. Data sync started."
                } else {
                    if (found) "Drive থেকে ডেটা রিস্টোর হয়েছে" else "সাইন-ইন সফল, ডেটা Drive-এ সেভ শুরু হলো"
                },
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            account = task.getResult(ApiException::class.java)
            syncAfterSignIn()
        } catch (e: ApiException) {
            Toast.makeText(
                context,
                if (language == AppLanguageStore.ENGLISH) "Sign-in failed (${e.statusCode})" else "সাইন-ইন ব্যর্থ (${e.statusCode})",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            if (language == AppLanguageStore.ENGLISH) "Profile" else "প্রোফাইল",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                val acc = account
                if (acc != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (acc.photoUrl != null) {
                            AsyncImage(
                                model = acc.photoUrl,
                                contentDescription = null,
                                modifier = Modifier.size(54.dp).clip(CircleShape)
                            )
                        } else {
                            Box(
                                Modifier.size(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Cloud, contentDescription = null)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(acc.displayName.orEmpty(), fontWeight = FontWeight.SemiBold)
                            Text(acc.email.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (syncing) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text(if (language == AppLanguageStore.ENGLISH) "Syncing..." else "সিঙ্ক হচ্ছে...", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = {
                        client.signOut()
                        account = null
                    }) {
                        Text(if (language == AppLanguageStore.ENGLISH) "Sign out" else "সাইন-আউট")
                    }
                } else {
                    Text(
                        if (language == AppLanguageStore.ENGLISH) "Google Drive sync" else "Google Drive সিঙ্ক",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (language == AppLanguageStore.ENGLISH) "Optional: keep your app data synced across installs." else "ঐচ্ছিক: অ্যাপের ডেটা Google Drive-এ সিঙ্ক করে রাখতে পারবেন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { signInLauncher.launch(client.signInIntent) }) {
                        Text(if (language == AppLanguageStore.ENGLISH) "Sign in with Google" else "Google দিয়ে সাইন-ইন করুন")
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(if (language == AppLanguageStore.ENGLISH) "Settings" else "সেটিংস", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column {
                SettingsRow(
                    icon = { Icon(Icons.Default.Language, contentDescription = null) },
                    title = if (language == AppLanguageStore.ENGLISH) "App language" else "এপের ভাষা",
                    subtitle = if (language == AppLanguageStore.ENGLISH) "Choose বাংলা or English" else "বাংলা অথবা English নির্বাচন করুন",
                    trailing = {
                        Box {
                            OutlinedButton(onClick = { languageMenuExpanded = true }) {
                                Text(if (language == AppLanguageStore.ENGLISH) "English" else "বাংলা")
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = languageMenuExpanded,
                                onDismissRequest = { languageMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("বাংলা") },
                                    onClick = { languageMenuExpanded = false; onLanguageChange(AppLanguageStore.BANGLA) }
                                )
                                DropdownMenuItem(
                                    text = { Text("English") },
                                    onClick = { languageMenuExpanded = false; onLanguageChange(AppLanguageStore.ENGLISH) }
                                )
                            }
                        }
                    }
                )
                HorizontalDivider()
                SettingsRow(
                    icon = { Icon(Icons.Default.DarkMode, contentDescription = null) },
                    title = if (language == AppLanguageStore.ENGLISH) "Dark mode" else "ডার্ক মোড",
                    trailing = { Switch(checked = darkMode, onCheckedChange = onDarkModeChange) }
                )
                HorizontalDivider()
                SettingsRow(
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    title = if (language == AppLanguageStore.ENGLISH) "Auto-play next" else "অটো-প্লে নেক্সট",
                    trailing = { Switch(checked = autoplayNext, onCheckedChange = onAutoplayNextChange) }
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(if (language == AppLanguageStore.ENGLISH) "Data & Backup" else "ডেটা ও ব্যাকআপ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                SettingsRow(
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    title = if (language == AppLanguageStore.ENGLISH) "Backup" else "ব্যাকআপ",
                    subtitle = if (language == AppLanguageStore.ENGLISH) "Save playlist, history and settings to a file" else "প্লে-লিস্ট, হিস্ট্রি ও সেটিংস ফাইলে সংরক্ষণ করুন",
                    trailing = {
                        OutlinedButton(onClick = { backupLauncher.launch("TubeLite-backup.json") }) {
                            Text(if (language == AppLanguageStore.ENGLISH) "Backup" else "ব্যাকআপ")
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    icon = { Icon(Icons.Default.Restore, contentDescription = null) },
                    title = if (language == AppLanguageStore.ENGLISH) "Restore" else "রিস্টোর",
                    subtitle = if (language == AppLanguageStore.ENGLISH) "Restore from a previous TubeLite backup" else "আগের TubeLite ব্যাকআপ থেকে ডেটা ফিরিয়ে নিন",
                    trailing = {
                        OutlinedButton(onClick = {
                            restoreLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
                        }) {
                            Text(if (language == AppLanguageStore.ENGLISH) "Restore" else "রিস্টোর")
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                WatchHistoryStore.clear(context)
                Toast.makeText(
                    context,
                    if (language == AppLanguageStore.ENGLISH) "Watch history cleared" else "দেখার ইতিহাস মুছে ফেলা হয়েছে",
                    Toast.LENGTH_SHORT
                ).show()
            }
        ) {
            Text(if (language == AppLanguageStore.ENGLISH) "Clear watch history" else "দেখার ইতিহাস মুছুন")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}
