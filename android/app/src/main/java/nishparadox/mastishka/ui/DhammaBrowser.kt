package nishparadox.mastishka.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import nishparadox.mastishka.MeditationViewModel
import nishparadox.mastishka.MeditationViewModel.WebSave
import nishparadox.mastishka.data.DHAMMA_RECORDINGS_URL
import nishparadox.mastishka.data.DHAMMA_SESSION_COOKIE
import nishparadox.mastishka.data.DHAMMA_SITTINGS_URL
import nishparadox.mastishka.data.PageRecording
import nishparadox.mastishka.data.isAudioUrl
import nishparadox.mastishka.data.isDhammaHost
import nishparadox.mastishka.data.tidyRecordingLabel
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Collects the audio download links on the current page, each with a readable label taken
 * from the page around it: the site's recording card or table row (kind · place · year), or
 * the recording page's own heading. `gs` marks a group sitting.
 */
private const val FIND_AUDIO_JS = """
(function(){
  var out=[], seen={};
  function txt(e){ return e ? (e.textContent||'').replace(/\s+/g,' ').trim() : '' }
  function label(a){
    var dl=a.getAttribute('download')||'';
    var year=(dl.match(/_(\d{4})\.\w+$/)||[])[1]||'';
    var c=a.closest('.card');
    // Some card titles repeat the whole description after the place name; keep the place.
    if(c) return [txt(c.querySelector('.card-subtitle')), txt(c.querySelector('.card-title')).split('•')[0].trim(), year].filter(Boolean).join(' · ');
    var tr=a.closest('tr');
    if(tr){ var t=[].map.call(tr.querySelectorAll('td'),txt); return [t[3],t[1],t[2]].filter(Boolean).join(' · ') }
    var h=document.querySelector('#main h5');
    if(h) return txt(h);
    return dl.replace(/\.\w+$/,'').replace(/_/g,' ');
  }
  document.querySelectorAll('a[href]').forEach(function(a){
    var u; try{ u=new URL(a.getAttribute('href'), location.href).href }catch(e){ return }
    if(seen[u]) return; seen[u]=1;
    out.push({url:u, title:label(a).slice(0,200), gs:a.getAttribute('data-dimension2')==='GS'});
  });
  return JSON.stringify({signedIn: !!document.querySelector('a[href="/logout"]'), items:out});
})()
"""

/**
 * dhamma.org inside Mastishka, for recordings the site serves only to logged-in old students.
 *
 * The person signs in themselves, once: the sign-in is kept between visits, and the next visit
 * opens straight on the group sittings. Tapping the site's own Download button saves that
 * recording, with their session, into the Guided list. A page that shows a single recording
 * also gets a "Save this recording" button; list pages do not. Nothing is fetched without the
 * person asking for it, and the browser stays on dhamma.org.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DhammaBrowserScreen(vm: MeditationViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("dhamma_browser", Context.MODE_PRIVATE) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    // The one recording this page is about, if it is a single-recording page.
    var single by remember { mutableStateOf<PageRecording?>(null) }

    /** What the page being shown offers: sign-in state and its audio download links. */
    fun inspect(view: WebView, then: (PageInfo) -> Unit) {
        view.evaluateJavascript(FIND_AUDIO_JS) { raw ->
            val info = parsePageInfo(raw)
            then(info.copy(recordings = info.recordings.filter { isAudioUrl(it.url) && isDhammaHost(Uri.parse(it.url).host) }))
        }
    }

    /** Save the audio at [url], named after its label on the page (or the page title). */
    fun save(url: String) {
        val view = webView ?: return
        inspect(view) { info ->
            vm.saveWebRecording(
                url = url,
                title = info.recordings.firstOrNull { it.url == url }?.title?.ifBlank { null } ?: view.title.orEmpty(),
                cookie = CookieManager.getInstance().getCookie(url),
                userAgent = view.settings.userAgentString,
            )
        }
    }

    fun leave() {
        vm.clearWebSave()
        onBack()
    }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else leave()
    }
    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
            webView?.destroy()
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { leave() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back to Mastishka") }
            Text("Explore more sittings", style = MaterialTheme.typography.titleMedium)
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url
                            // The site's Download button: save it instead of navigating to the file.
                            if (isDhammaHost(url.host) && isAudioUrl(url.toString())) {
                                view.post { save(url.toString()) }
                                return true
                            }
                            if (isDhammaHost(url.host)) return false
                            // Anything off the publisher's sites opens in the real browser.
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                            return true
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                            loading = true
                            single = null
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            loading = false
                            keepSession(url)
                            inspect(view) { info ->
                                // Remember whether we are signed in, to open on the right page next time.
                                prefs.edit().putBoolean(KEY_SIGNED_IN, info.signedIn).apply()
                                single = info.recordings.singleOrNull()
                            }
                        }
                    }
                    // A link the site marks as a download.
                    setDownloadListener { url, _, _, _, _ ->
                        if (isDhammaHost(Uri.parse(url).host)) save(url)
                    }
                    // Signed in last time: straight to the sittings. Otherwise the sign-in form.
                    loadUrl(if (prefs.getBoolean(KEY_SIGNED_IN, false)) DHAMMA_SITTINGS_URL else DHAMMA_RECORDINGS_URL)
                    webView = this
                }
            },
        )

        // Bottom bar, only when there is something to say: a save in progress or finished, or
        // a page about one recording that can be saved.
        val state = vm.webSave
        val onePage = single
        if (state != null || onePage != null) {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (state) {
                        is WebSave.Saving -> {
                            CircularProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 3.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Saving ${(state.progress * 100).toInt()}% · ${state.title}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        is WebSave.Saved -> {
                            Text("Saved to Guided · ${state.title}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { leave() }) { Text("Done") }
                        }
                        else -> {
                            if (state is WebSave.Failed) {
                                Text("Couldn't save that one", Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            if (onePage != null) {
                                Button(onClick = { save(onePage.url) }) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Save this recording")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val KEY_SIGNED_IN = "signed_in"
private const val SESSION_KEEP_SECONDS = 180L * 24 * 3600

/**
 * The site's sign-in cookie has no expiry, so the browser engine forgets it when the app
 * closes. Re-store it with a long life after every page, so signing in is a one-time step.
 */
private fun keepSession(pageUrl: String?) {
    val uri = pageUrl?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return
    if (!isDhammaHost(uri.host)) return
    val origin = "https://${uri.host}"
    val cookies = CookieManager.getInstance()
    cookies.getCookie(origin)?.split(';')?.map { it.trim() }
        ?.filter { it.startsWith("$DHAMMA_SESSION_COOKIE=") }
        ?.forEach { cookies.setCookie(origin, "$it; Max-Age=$SESSION_KEEP_SECONDS; Path=/; Secure; HttpOnly; SameSite=Lax") }
    cookies.flush()
}

/** What a page offers: whether the visitor is signed in, and its audio download links. */
private data class PageInfo(val signedIn: Boolean, val recordings: List<PageRecording>)

/** Parse what [FIND_AUDIO_JS] returned (a JSON string, itself JSON-encoded by the WebView). */
private fun parsePageInfo(raw: String?): PageInfo = runCatching {
    val json = JSONTokener(raw ?: return PageInfo(false, emptyList())).nextValue() as? String
        ?: return PageInfo(false, emptyList())
    val root = JSONObject(json)
    val items = root.optJSONArray("items")
    val recordings = (0 until (items?.length() ?: 0)).mapNotNull { i ->
        val o = items?.optJSONObject(i) ?: return@mapNotNull null
        PageRecording(o.optString("url"), tidyRecordingLabel(o.optString("title"), groupSitting = o.optBoolean("gs")))
    }
    PageInfo(root.optBoolean("signedIn"), recordings)
}.getOrDefault(PageInfo(false, emptyList()))
