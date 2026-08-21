package com.tubelite.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

private const val DRIVE_APPDATA_SCOPE =
    "https://www.googleapis.com/auth/drive.appdata"

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

    var account by remember {
        mutableStateOf(
            GoogleSignIn.getLastSignedInAccount(context)
        )
    }

    var syncing by remember { mutableStateOf(false) }

    /*
     * ------------------------------------------------------------
     * Local Backup
     * ------------------------------------------------------------
     */
    val backupLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->

            if (uri != null) {
                val success = LocalBackupStore.export(
                    context = context,
                    uri = uri
                )

                val message =
                    if (language == AppLanguageStore.ENGLISH) {
                        if (success) {
                            "Backup completed"
                        } else {
                            "Backup failed"
                        }
                    } else {
                        if (success) {
                            "ব্যাকআপ সম্পন্ন হয়েছে"
                        } else {
                            "ব্যাকআপ ব্যর্থ হয়েছে"
                        }
                    }

                Toast.makeText(
                    context,
                    message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    /*
     * ------------------------------------------------------------
     * Local Restore
     * ------------------------------------------------------------
     */
    val restoreLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {
                val success = LocalBackupStore.import(
                    context = context,
                    uri = uri
                )

                val message =
                    if (language == AppLanguageStore.ENGLISH) {
                        if (success) {
                            "Restore completed. Please restart the app."
                        } else {
                            "Restore failed"
                        }
                    } else {
                        if (success) {
                            "রিস্টোর সম্পন্ন হয়েছে। অনুগ্রহ করে অ্যাপটি পুনরায় চালু করুন।"
                        } else {
                            "রিস্টোর ব্যর্থ হয়েছে"
                        }
                    }

                Toast.makeText(
                    context,
                    message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    /*
     * ------------------------------------------------------------
     * Google Sign-In / Drive Sync
     * ------------------------------------------------------------
     */
    val gso = remember {
        GoogleSignInOptions.Builder(
            GoogleSignInOptions.DEFAULT_SIGN_IN
        )
            .requestEmail()
            .requestScopes(
                Scope(DRIVE_APPDATA_SCOPE)
            )
            .build()
    }

    val client = remember {
        GoogleSignIn.getClient(
            context,
            gso
        )
    }

    fun syncAfterSignIn() {
        syncing = true

        CloudSync.pullAll(context) { found ->

            if (!found) {
                CloudSync.pushAll(context)
            }

            syncing = false

            val message =
                if (language == AppLanguageStore.ENGLISH) {
                    if (found) {
                        "Data restored from Google Drive"
                    } else {
                        "Signed in successfully. Data sync started."
                    }
                } else {
                    if (found) {
                        "Drive থেকে ডেটা রিস্টোর হয়েছে"
                    } else {
                        "সাইন-ইন সফল, ডেটা Drive-এ সেভ শুরু হলো"
                    }
                }

            Toast.makeText(
                context,
                message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            val task =
                GoogleSignIn.getSignedInAccountFromIntent(
                    result.data
                )

            try {
                account =
                    task.getResult(
                        ApiException::class.java
                    )

                syncAfterSignIn()

            } catch (e: ApiException) {

                val message =
                    if (language == AppLanguageStore.ENGLISH) {
                        "Sign-in failed (${e.statusCode})"
                    } else {
                        "সাইন-ইন ব্যর্থ (${e.statusCode})"
                    }

                Toast.makeText(
                    context,
                    message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    /*
     * ------------------------------------------------------------
     * UI
     * ------------------------------------------------------------
     */
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text =
                if (language == AppLanguageStore.ENGLISH)
                    "Profile"
                else
                    "প্রোফাইল",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * --------------------------------------------------------
         * Google Account
         * --------------------------------------------------------
         */
        val acc = account

        if (acc != null) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                if (acc.photoUrl != null) {

                    AsyncImage(
                        model = acc.photoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                    )
                }

                Spacer(
                    modifier = Modifier.width(12.dp)
                )

                Column {

                    Text(
                        text = acc.displayName ?: "",
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = acc.email ?: "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            if (syncing) {

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Text(
                        text =
                            if (language == AppLanguageStore.ENGLISH)
                                "Syncing..."
                            else
                                "সিঙ্ক হচ্ছে...",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(
                    modifier = Modifier.height(8.dp)
                )
            }

            OutlinedButton(
                onClick = {
                    client.signOut()
                    account = null
                }
            ) {
                Text(
                    text =
                        if (language == AppLanguageStore.ENGLISH)
                            "Sign out"
                        else
                            "সাইন-আউট"
                )
            }

        } else {

            Button(
                onClick = {
                    launcher.launch(
                        client.signInIntent
                    )
                }
            ) {
                Text(
                    text =
                        if (language == AppLanguageStore.ENGLISH)
                            "Sign in with Google"
                        else
                            "Google দিয়ে সাইন-ইন করুন"
                )
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text =
                    if (language == AppLanguageStore.ENGLISH) {
                        "When you sign in, your playlist, history and settings will be saved in the hidden TubeLite app folder in Google Drive."
                    } else {
                        "সাইন-ইন করলে প্লে-লিস্ট/হিস্ট্রি/সেটিংস আপনার Google Drive-এর লুকানো অ্যাপ ফোল্ডারে সেভ থাকবে।"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = 0.6f
                )
            )
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        HorizontalDivider()

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * --------------------------------------------------------
         * Settings
         * --------------------------------------------------------
         */
        Text(
            text =
                if (language == AppLanguageStore.ENGLISH)
                    "Settings"
                else
                    "সেটিংস",
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * --------------------------------------------------------
         * Local Backup & Restore
         * --------------------------------------------------------
         */
        Text(
            text =
                if (language == AppLanguageStore.ENGLISH)
                    "Local Backup & Restore"
                else
                    "লোকাল ব্যাকআপ ও রিস্টোর",
            fontWeight = FontWeight.Medium
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text =
                if (language == AppLanguageStore.ENGLISH)
                    "Save your app data to a file and restore it later without signing in with Google."
                else
                    "Google অ্যাকাউন্টে সাইন-ইন না করেও অ্যাপের ডেটা ফাইলে সংরক্ষণ করে পরে রিস্টোর করতে পারবেন।",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = 0.65f
            )
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    backupLauncher.launch(
                        "TubeLite-backup.json"
                    )
                }
            ) {
                Text(
                    text =
                        if (language == AppLanguageStore.ENGLISH)
                            "Backup"
                        else
                            "ব্যাকআপ"
                )
            }

            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    restoreLauncher.launch(
                        arrayOf(
                            "application/json",
                            "text/json",
                            "text/plain"
                        )
                    )
                }
            ) {
                Text(
                    text =
                        if (language == AppLanguageStore.ENGLISH)
                            "Restore"
                        else
                            "রিস্টোর"
                )
            }
        }

        Spacer(
            modifier = Modifier.height(18.dp)
        )

        /*
         * --------------------------------------------------------
         * Dark Mode
         * --------------------------------------------------------
         */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text =
                    if (language == AppLanguageStore.ENGLISH)
                        "Dark mode"
                    else
                        "ডার্ক মোড"
            )

            Switch(
                checked = darkMode,
                onCheckedChange = onDarkModeChange
            )
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        /*
         * --------------------------------------------------------
         * Auto Play
         * --------------------------------------------------------
         */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text =
                    if (language == AppLanguageStore.ENGLISH)
                        "Auto-play next (default)"
                    else
                        "অটো-প্লে নেক্সট (ডিফল্ট)"
            )

            Switch(
                checked = autoplayNext,
                onCheckedChange = onAutoplayNextChange
            )
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * --------------------------------------------------------
         * App Language
         * --------------------------------------------------------
         */
        Text(
            text =
                if (language == AppLanguageStore.ENGLISH)
                    "App language"
                else
                    "এপের ভাষা",
            fontWeight = FontWeight.Medium
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            FilterChip(
                selected = language == AppLanguageStore.BANGLA,
                onClick = {
                    onLanguageChange(
                        AppLanguageStore.BANGLA
                    )
                },
                label = {
                    Text("বাংলা")
                }
            )

            FilterChip(
                selected = language == AppLanguageStore.ENGLISH,
                onClick = {
                    onLanguageChange(
                        AppLanguageStore.ENGLISH
                    )
                },
                label = {
                    Text("English")
                }
            )
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * --------------------------------------------------------
         * Clear Watch History
         * --------------------------------------------------------
         */
        OutlinedButton(
            onClick = {
                WatchHistoryStore.clear(context)

                Toast.makeText(
                    context,
                    if (language == AppLanguageStore.ENGLISH)
                        "Watch history cleared"
                    else
                        "দেখার ইতিহাস মুছে ফেলা হয়েছে",
                    Toast.LENGTH_SHORT
                ).show()
            }
        ) {
            Text(
                text =
                    if (language == AppLanguageStore.ENGLISH)
                        "Clear watch history"
                    else
                        "দেখার ইতিহাস মুছুন"
            )
        }
    }
}
