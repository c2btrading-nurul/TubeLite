package com.tubelite.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material3.Text as MaterialText
import com.tubelite.app.data.AppLanguageStore

val LocalAppLanguage = compositionLocalOf { AppLanguageStore.BANGLA }

private fun translateText(text: String, language: String): String {
    if (language != AppLanguageStore.ENGLISH) return text

    return when {
        text == "বাংলা" -> "Bangla"
        text == "এপের ভাষা" -> "App language"
        text == "ডার্ক মোড" -> "Dark mode"
        text == "অটো-প্লে নেক্সট (ডিফল্ট)" -> "Auto-play next (default)"
        text == "অটো-প্লে নেক্সট" -> "Auto-play next"
        text == "লোকাল ব্যাকআপ ও রিস্টোর" -> "Local Backup & Restore"
        text == "ব্যাকআপ" -> "Backup"
        text == "রিস্টোর" -> "Restore"
        text == "ব্যাকআপ সম্পন্ন হয়েছে" -> "Backup completed"
        text == "ব্যাকআপ ব্যর্থ হয়েছে" -> "Backup failed"
        text == "রিস্টোর ব্যর্থ হয়েছে" -> "Restore failed"
        text == "রিস্টোর সম্পন্ন হয়েছে। অনুগ্রহ করে অ্যাপটি পুনরায় চালু করুন।" -> "Restore completed. Please restart the app."
        text == "প্রোফাইল" -> "Profile"
        text == "সেটিংস" -> "Settings"
        text == "সাইন-আউট" -> "Sign out"
        text == "Google দিয়ে সাইন-ইন করুন" -> "Sign in with Google"
        text.startsWith("সাইন-ইন ব্যর্থ (") -> text.replace("সাইন-ইন ব্যর্থ", "Sign-in failed")
        text == "সিঙ্ক হচ্ছে..." -> "Syncing..."
        text == "সাইন-ইন সফল, ডেটা Drive-এ সেভ শুরু হলো" -> "Signed in successfully. Data sync started."
        text == "Drive থেকে ডেটা রিস্টোর হয়েছে" -> "Data restored from Google Drive"
        text.contains("Google অ্যাকাউন্টে সাইন-ইন না করেও") -> "Save your app data to a file and restore it later without signing in with Google."
        text == "আপনার সাবস্ক্রিপশন থেকে" -> "From your subscriptions"
        text == "আপনার আগ্রহ অনুযায়ী" -> "Recommended for you"
        text == "ট্রেন্ডিং" -> "Trending"
        text == "লোড করা যায়নি" -> "Could not load"
        text.startsWith("স্ট্রিম লোড করা যায়নি:") -> text.replace("স্ট্রিম লোড করা যায়নি:", "Could not load stream:")
        text == "ভিডিও সার্চ করুন..." -> "Search videos..."
        text == "সার্চ হিস্ট্রি" -> "Search history"
        text == "সব মুছুন" -> "Clear all"
        text == "এই সার্চটি মুছুন" -> "Delete this search"
        text == "আপনার সাম্প্রতিক সার্চ এখানে দেখা যাবে" -> "Your recent searches will appear here"
        text == "সার্চ করা যায়নি" -> "Search failed"
        text == "দেখার ইতিহাস" -> "Watch history"
        text == "দেখার ইতিহাস মুছুন" -> "Clear watch history"
        text == "দেখার ইতিহাস মুছে ফেলা হয়েছে" -> "Watch history cleared"
        text == "এখনো কিছু দেখা হয়নি" -> "Nothing watched yet"
        text == "সেভ করা প্লে-লিস্ট" -> "Saved playlists"
        text == "নতুন প্লে-লিস্ট" -> "New playlist"
        text == "নাম দিন" -> "Enter name"
        text == "নাম" -> "Name"
        text == "তৈরি করুন" -> "Create"
        text == "তৈরি ও যোগ করুন" -> "Create and add"
        text == "বাতিল" -> "Cancel"
        text == "সবগুলো চালান" -> "Play all"
        text == "এই প্লে-লিস্টে কোনো ভিডিও নেই" -> "This playlist is empty"
        text == "কোনো প্লে-লিস্ট নেই। + চেপে একটা তৈরি করুন।" -> "No playlists. Tap + to create one."
        text == "সম্পর্কিত ভিডিও" -> "Related videos"
        text == "প্লে-লিস্ট থেকে পরবর্তী" -> "Next from playlist"
        text == "সাবস্ক্রাইব" -> "Subscribe"
        text == "সাবস্ক্রাইব করা" -> "Subscribed"
        text == "আপনার সাবস্ক্রিপশন" -> "Your subscriptions"
        text == "সাম্প্রতিক ভিডিও" -> "Recent videos"
        text == "এখনও কোনো চ্যানেল সাবস্ক্রাইব করা হয়নি" -> "No channels subscribed yet"
        text.startsWith("চ্যানেল লোড করা যায়নি:") -> text.replace("চ্যানেল লোড করা যায়নি:", "Could not load channel:")
        text == "ডাউনলোড শুরু হয়েছে" -> "Download started"
        text == "ডাউনলোড লিংক পাওয়া যায়নি" -> "Download link not found"
        text == "ভিডিও শেয়ার করুন" -> "Share video"
        text == "নতুন প্লে-লিস্ট" -> "New playlist"
        text == "✓ " -> "✓ "
        text == "ডেটা" -> "Data"
        text == "প্লে-লিস্ট থেকে পরবর্তী" -> "Next from playlist"
        text == "-১০ সেকেন্ড" -> "-10 seconds"
        text == "+১০ সেকেন্ড" -> "+10 seconds"
        text == "নতুন প্লে-লিস্ট" -> "New playlist"
        text == "নাম" -> "Name"
        text == "তৈরি ও যোগ করুন" -> "Create and add"
        text == "বাতিল" -> "Cancel"
        text == "ডাউনলোড শুরু হয়েছে" -> "Download started"
        text == "ডাউনলোড লিংক পাওয়া যায়নি" -> "Download link not found"
        text == "ভিডিও শেয়ার করুন" -> "Share video"
        text == "+ নতুন প্লে-লিস্ট" -> "+ New playlist"
        text.endsWith("-এ যোগ হলো") -> "Added to " + text.removeSuffix("-এ যোগ হলো")

        text.contains(" ভিডিও") && text.substringBefore(" ভিডিও").all { it.isDigit() } ->
            "${text.substringBefore(" ভিডিও")} videos"
        else -> text
    }
}

/**
 * String overload used by the screens in this package. It keeps dynamic
 * video/channel titles untouched while translating the app's static UI text.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((androidx.compose.ui.text.TextLayoutResult) -> Unit)? = null,
    style: TextStyle = androidx.compose.ui.text.TextStyle.Default
) {
    MaterialText(
        text = translateText(text, LocalAppLanguage.current),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}
