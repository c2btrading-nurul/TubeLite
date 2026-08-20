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
import com.tubelite.app.data.CloudSync
import com.tubelite.app.data.WatchHistoryStore

private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

@Composable
fun ProfileScreen(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    autoplayNext: Boolean,
    onAutoplayNextChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var account by remember { mutableStateOf(GoogleSignIn.getLastSignedInAccount(context)) }
    var syncing by remember { mutableStateOf(false) }

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
                if (found) "Drive থেকে ডেটা রিস্টোর হয়েছে" else "সাইন-ইন সফল, ডেটা Drive-এ সেভ শুরু হলো",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            account = task.getResult(ApiException::class.java)
            syncAfterSignIn()
        } catch (e: ApiException) {
            Toast.makeText(context, "সাইন-ইন ব্যর্থ (${e.statusCode})", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("প্রোফাইল", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        val acc = account
        if (acc != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (acc.photoUrl != null) {
                    AsyncImage(model = acc.photoUrl, contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(acc.displayName ?: "", fontWeight = FontWeight.Medium)
                    Text(acc.email ?: "", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (syncing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("সিঙ্ক হচ্ছে...", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(onClick = {
                client.signOut()
                account = null
            }) { Text("সাইন-আউট") }
        } else {
            Button(onClick = { launcher.launch(client.signInIntent) }) {
                Text("Google দিয়ে সাইন-ইন করুন")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "সাইন-ইন করলে প্লে-লিস্ট/হিস্ট্রি/সেটিংস আপনার Google Drive-এর লুকানো অ্যাপ ফোল্ডারে সেভ থাকবে।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Spacer(Modifier.height(24.dp))
        Divider()
        Spacer(Modifier.height(16.dp))

        Text("সেটিংস", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("ডার্ক মোড")
            Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("অটো-প্লে নেক্সট (ডিফল্ট)")
            Switch(checked = autoplayNext, onCheckedChange = onAutoplayNextChange)
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { WatchHistoryStore.clear(context) }) { Text("দেখার ইতিহাস মুছুন") }
    }
}
