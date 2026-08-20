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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.tubelite.app.R
import com.tubelite.app.data.CloudSync
import com.tubelite.app.data.WatchHistoryStore

@Composable
fun ProfileScreen(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    autoplayNext: Boolean,
    onAutoplayNextChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var firebaseUser by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
    var syncing by remember { mutableStateOf(false) }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    }
    val client = remember { GoogleSignIn.getClient(context, gso) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            syncing = true
            FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnSuccessListener {
                    firebaseUser = FirebaseAuth.getInstance().currentUser
                    CloudSync.pullAll(context) { foundCloudData ->
                        if (!foundCloudData) CloudSync.pushAll(context)
                        syncing = false
                        Toast.makeText(
                            context,
                            if (foundCloudData) "ক্লাউড থেকে ডেটা রিস্টোর হয়েছে" else "সাইন-ইন সফল, ডেটা ক্লাউডে সেভ শুরু হলো",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .addOnFailureListener {
                    syncing = false
                    Toast.makeText(context, "Firebase সাইন-ইন ব্যর্থ: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: ApiException) {
            Toast.makeText(context, "সাইন-ইন ব্যর্থ (${e.statusCode})", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("প্রোফাইল", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        val user = firebaseUser
        if (user != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (user.photoUrl != null) {
                    AsyncImage(
                        model = user.photoUrl,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(CircleShape)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(user.displayName ?: "", fontWeight = FontWeight.Medium)
                    Text(user.email ?: "", style = MaterialTheme.typography.bodySmall)
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
                FirebaseAuth.getInstance().signOut()
                client.signOut()
                firebaseUser = null
            }) { Text("সাইন-আউট") }
        } else {
            Button(onClick = { launcher.launch(client.signInIntent) }) {
                Text("Google দিয়ে সাইন-ইন করুন")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "সাইন-ইন করলে আপনার প্লে-লিস্ট, হিস্ট্রি ও সেটিংস ক্লাউডে সেভ থাকবে — অ্যাপ পুনরায় ইনস্টল করে সাইন-ইন করলেও ফিরে পাবেন।",
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
