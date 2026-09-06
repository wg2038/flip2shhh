package shhh.cicada.flip

import android.app.ActivityManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// ════════════════════════════════════════════════════════════════════════
// Easter Egg Terminal Screen (Authentic Linux / Ubuntu 26.04 Terminal)
// Sole difference between the easter-egg edition and the official line:
// everything the Coral Sea terminal needs lives in this file.
// ════════════════════════════════════════════════════════════════════════

enum class LyricSinger {
    MALE,    // 周杰伦男声 -> ANSI Light Blue (#729FCF)
    FEMALE,  // 梁心颐女声 -> ANSI Light Yellow (#FCE94F)
    DUET     // 合唱       -> ANSI Light Green (#8AE234)
}

data class CleanLyric(
    val timestampMs: Long,
    val text: String,
    val singer: LyricSinger
)

// Per-line typing schedule, all relative to the line's LRC vocal timestamp:
//  - the line activates LYRIC_LEAD_MS early: blank line, cursor and scroll pre-position,
//    but no text is exposed yet;
//  - typing begins AT the onset and the first character renders immediately there;
//  - every later character is pulled forward by LYRIC_BODY_LEAD_MS on top of the
//    end-anchored pacing, so the line completes LEAD + BODY_LEAD before the next vocal.
//    Body characters never render before the onset — on fast lines they bunch at it.
// Safety: the typing window is lineDuration - LEAD; the closest cleaned lines are ~0.66s
// apart, so keep LEAD below ~600ms or fast lines lose the typing effect and snap to full.
private const val LYRIC_LEAD_MS = 300L
private const val LYRIC_TYPE_DELAY_MS = 0L
private const val LYRIC_BODY_LEAD_MS = 300L

// Optional global shift for every lyric timestamp (0 = trust the embedded LRC as-is).
// The per-line timing knobs below stay within the safe envelope regardless.
private const val LYRICS_GLOBAL_OFFSET_MS = 0L

private fun parseAndCleanLrc(content: String): List<CleanLyric> {
    val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")
    val rawList = mutableListOf<CleanLyric>()
    var currentSinger = LyricSinger.MALE

    content.lineSequence().forEach { rawLine ->
        val trimmed = rawLine.trim()
        val match = regex.find(trimmed)
        if (match != null) {
            val minStr = match.groupValues[1]
            val secStr = match.groupValues[2]
            val millisStr = match.groupValues[3]
            val min = minStr.toLongOrNull() ?: 0L
            val sec = secStr.toLongOrNull() ?: 0L
            val millis = if (millisStr.length == 2) millisStr.toLong() * 10 else millisStr.toLong()
            var text = match.groupValues[4].trim()

            // State machine singer identification
            if (text.startsWith("男：") || text.startsWith("男:") || text.startsWith("男；") || text.startsWith("男;")) {
                currentSinger = LyricSinger.MALE
                text = text.substring(2).trim()
            } else if (text.startsWith("女：") || text.startsWith("女:") || text.startsWith("女；") || text.startsWith("女;")) {
                currentSinger = LyricSinger.FEMALE
                text = text.substring(2).trim()
            } else if (text.startsWith("合：") || text.startsWith("合:") || text.startsWith("合；") || text.startsWith("合;")) {
                currentSinger = LyricSinger.DUET
                text = text.substring(2).trim()
            } else if (text == "男" || text == "(男)" || text == "（男）") {
                currentSinger = LyricSinger.MALE
                text = ""
            } else if (text == "女" || text == "(女)" || text == "（女）") {
                currentSinger = LyricSinger.FEMALE
                text = ""
            } else if (text == "合" || text == "(合)" || text == "（合）") {
                currentSinger = LyricSinger.DUET
                text = ""
            }

            // Filter out metadata lines
            val isMeta = text.startsWith("词") ||
                    text.startsWith("曲") ||
                    text.startsWith("编曲") ||
                    text.startsWith("制作人") ||
                    text.startsWith("作词") ||
                    text.startsWith("作曲") ||
                    text.startsWith("演唱") ||
                    text == "珊瑚海"

            if (text.isNotEmpty() && !isMeta) {
                rawList.add(CleanLyric(min * 60000 + sec * 1000 + millis, text, currentSinger))
            }
        }
    }

    return rawList.sortedBy { it.timestampMs }.map {
        it.copy(timestampMs = it.timestampMs + LYRICS_GLOBAL_OFFSET_MS)
    }
}

private fun extractLyricsFromOgg(context: Context): String {
    return try {
        context.resources.openRawResource(R.raw.coralsea).use { inputStream ->
            // Read the whole file: a single read() call may underfill, and the LYRICS
            // comment is not guaranteed to land in the first 64KB of the stream.
            val buffer = inputStream.readBytes()
            val target = "LYRICS=".toByteArray(Charsets.UTF_8)
            var targetIndex = -1
            for (i in 0 until (buffer.size - target.size)) {
                var match = true
                for (j in target.indices) {
                    if (buffer[i + j] != target[j]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    targetIndex = i
                    break
                }
            }
            if (targetIndex != -1) {
                val lenOffset = targetIndex - 4
                val len = if (lenOffset >= 0) {
                    (buffer[lenOffset].toInt() and 0xFF) or
                    ((buffer[lenOffset + 1].toInt() and 0xFF) shl 8) or
                    ((buffer[lenOffset + 2].toInt() and 0xFF) shl 16) or
                    ((buffer[lenOffset + 3].toInt() and 0xFF) shl 24)
                } else 4096
                // A length outside [7, 1 MiB] means a misaligned match; bail out instead of
                // swallowing OGG audio bytes as lyric text.
                val lyricsLen = if (len >= target.size && len <= (1 shl 20)) len - target.size else -1
                if (lyricsLen < 0 || targetIndex + target.size + lyricsLen > buffer.size) "" else
                    String(buffer, targetIndex + target.size, lyricsLen, Charsets.UTF_8)
            } else ""
        }
    } catch (_: Exception) {
        ""
    }
}

// Ubuntu logo (neofetch "Ubuntu" block), right edge mirror-normalized so the ring
// renders symmetric. Pure ASCII: every glyph lives in the monospace font on every
// OEM - no fallback glyphs can deform the shape.
private val UBUNTU_LOGO = """
            .-/+oosssssoo+\-.
        ´:+sssssssssssssssssss+:`
      -+sssssssssssssssssssyyssss+-
    .osssssssssssssssssssdMMMNysssso.
   /sssssssssssshdmmNNmmyNMMMMhssssss\
  +sssssssssshmydMMMMMMMNddddyssssssss+
 /ssssssssshNMMMyhhyyyyhmNMMMNhssssssss\
.ssssssssdMMMNhssssssssssshNMMMdssssssss.
+sssshhhhyNMMNyssssssssssssyNMMMysssssss+
ossyNMMMNyMMhssssssssssssssshmmmhssssssso
ossyNMMMNyMMhssssssssssssssshmmmhssssssso
+sssshhhhyNMMNyssssssssssssyNMMMysssssss+
.ssssssssdMMMNhssssssssssshNMMMdssssssss.
 \ssssssssshNMMMyhhyyyyhdNMMMNhssssssss/
  +ssssssssssdmydMMMMMMMMddddyssssssss+
   \sssssssssssshdmNNNNmyNMMMMhssssss/
    .osssssssssssssssssssdMMMNysssso.
      -+sssssssssssssssssyyyyssss+-
        `:+sssssssssssssssssss+:`
            .-\+oosssssoo+/-.
""".trimIndent().trimEnd()

// One prompt line of the session scrollback: user@host, path, typed text and the
// always-present (metric-stable) block cursor.
@Composable
private fun TerminalPromptLine(command: String, cursorVisible: Boolean) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = Color(0xFF8AE234), fontWeight = FontWeight.Bold)) {
                append("cicada@ubuntu")
            }
            withStyle(SpanStyle(color = Color.White)) {
                append(":")
            }
            withStyle(SpanStyle(color = Color(0xFF729FCF), fontWeight = FontWeight.Bold)) {
                append("~")
            }
            withStyle(SpanStyle(color = Color.White)) {
                append("$ $command")
            }
            withStyle(SpanStyle(color = if (cursorVisible) Color.White else Color.Transparent)) {
                append("█")
            }
        },
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

// neofetch snapshot: Host/CPU/RAM/Disk/Uptime come from the real device (no
// permissions needed); OS/Kernel/DE stay in the Ubuntu fiction on purpose.
// Collected off the main thread — /proc & /sys file reads and StatFs are disk I/O.
private fun collectNeofetchInfo(context: Context): List<Pair<String, String>> {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val memTotal = mi.totalMem / (1024L * 1024L)
        val memUsed = (mi.totalMem - mi.availMem) / (1024L * 1024L)
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val diskTotalG = stat.totalBytes shr 30
        val diskUsedG = (stat.totalBytes - stat.availableBytes) shr 30
        val diskPct = (((stat.totalBytes - stat.availableBytes) * 100) / stat.totalBytes).toInt()
        val upS = SystemClock.elapsedRealtime() / 1000L
        val upD = upS / 86400
        val upH = (upS % 86400) / 3600
        val upM = (upS % 3600) / 60
        val uptime = when {
            upD > 0 -> "$upD days, $upH hours, $upM mins"
            upH > 0 -> "$upH hours, $upM mins"
            else -> "$upM mins"
        }
        val abi = when (Build.SUPPORTED_ABIS.firstOrNull()) {
            "arm64-v8a" -> "aarch64"
            "armeabi-v7a" -> "armv7l"
            "x86_64" -> "x86_64"
            else -> "aarch64"
        }
        val hw = try {
            File("/proc/cpuinfo").bufferedReader().useLines { lines ->
                var found: String? = null
                for (l in lines) {
                    if (l.startsWith("Hardware")) {
                        found = l.substringAfter(':').trim()
                        break
                    }
                }
                found
            }
        } catch (_: Exception) {
            null
        }
        val cpuName = hw?.replace("Qualcomm Technologies, Inc", "Qualcomm")?.takeIf { it.isNotBlank() }
            ?: Build.HARDWARE.replaceFirstChar { it.uppercase(Locale.US) }
        val ghz = try {
            String.format(
                Locale.US, "%.2f",
                File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").readText().trim().toLong() / 1_000_000.0
            )
        } catch (_: Exception) {
            null
        }
        val cpuLine = buildString {
            append(cpuName)
            append(" (")
            append(Runtime.getRuntime().availableProcessors())
            append(")")
            if (ghz != null) {
                append(" @ ")
                append(ghz)
                append("GHz")
            }
        }
        val wm = context.resources.displayMetrics
        return listOf(
            "OS" to "Ubuntu 26.04.1 LTS $abi",
            "Host" to Build.MODEL,
            "Kernel" to "7.2.2-070202-generic",
            "Uptime" to uptime,
            "Packages" to "2184 (dpkg)",
            "Shell" to "bash 5.2.32",
            "Resolution" to "${wm.widthPixels}x${wm.heightPixels}",
            "DE" to "GNOME 50",
            "Terminal" to "gnome-terminal",
            "CPU" to cpuLine,
            "Memory" to "${memUsed}MiB / ${memTotal}MiB",
            "Disk (/)" to "${diskUsedG}G / ${diskTotalG}G ($diskPct%)",
            "Installed" to "2016-02-15"
        )
}

@Composable
private fun NeofetchBlock() {
    val context = LocalContext.current
    var info by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(Unit) {
        info = withContext(Dispatchers.IO) { collectNeofetchInfo(context) }
    }

    Column {
        // The block wraps its widest line so the Box can center the ring as a whole;
        // per-line centering (textAlign) would realign each row and scramble the art.
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = UBUNTU_LOGO,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp, // the art is 41 columns: sized to fit even 320dp-wide screens
                lineHeight = 13.sp,
                color = Color(0xFFE95420), // Ubuntu orange
                modifier = Modifier
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "cicada@ubuntu",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE95420)
        )
        Text(
            text = "-------------",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Color(0xFFE95420)
        )
        Spacer(modifier = Modifier.height(4.dp))
        info.orEmpty().forEach { (label, value) ->
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color(0xFFE95420), fontWeight = FontWeight.Bold)) {
                        append("$label: ")
                    }
                    withStyle(SpanStyle(color = Color(0xFFD3D7CF))) {
                        append(value)
                    }
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = buildAnnotatedString {
                listOf(
                    Color(0xFFCC0000), Color(0xFF4E9A06), Color(0xFFC4A000), Color(0xFF3465A4),
                    Color(0xFF75507B), Color(0xFF06989A), Color(0xFFD3D7CF)
                ).forEach { c ->
                    withStyle(SpanStyle(color = c)) { append("███") }
                }
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
        Text(
            text = buildAnnotatedString {
                listOf(
                    Color(0xFFEF2929), Color(0xFF8AE234), Color(0xFFFCE94F), Color(0xFF729FCF),
                    Color(0xFFAD7FA8), Color(0xFF34E2E2), Color(0xFFFFFFFF)
                ).forEach { c ->
                    withStyle(SpanStyle(color = c)) { append("███") }
                }
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

@Composable
fun EasterEggTerminalScreen(
    onExit: () -> Unit,
    onBarsDarkChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Parsing scans the 2.8 MB OGG for the LYRICS comment — keep it off the main thread.
    var lyrics by remember { mutableStateOf(emptyList<CleanLyric>()) }
    var lyricsLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        lyrics = withContext(Dispatchers.IO) {
            try {
                parseAndCleanLrc(extractLyricsFromOgg(context))
            } catch (_: Exception) {
                emptyList()
            }
        }
        lyricsLoaded = true
    }

    var currentPosMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var mediaPlayerInstance by remember { mutableStateOf<MediaPlayer?>(null) }
    var playerFailed by remember { mutableStateOf(false) }
    var showPlayerError by remember { mutableStateOf(false) }
    var isSongFinished by remember { mutableStateOf(false) }
    var rmCommandTyped by remember { mutableStateOf("") }
    var showRmOutput by remember { mutableStateOf(false) }
    var showSudoPassword by remember { mutableStateOf(false) }

    // Exit stops the audio immediately; the player itself is only released on dispose,
    // which runs after the AnimatedContent fade-out and would otherwise let the song
    // ring on for the whole transition.
    val exitSession = {
        try {
            mediaPlayerInstance?.let { player -> if (player.isPlaying) player.pause() }
        } catch (_: Exception) {}
        onExit()
    }
    BackHandler { exitSession() }

    // Command typing animation on entry (Authentic Linux CLI invocation)
    val fullCommand = "./coralsea-cli -f coralsea.ogg --lyrics"
    val neofetchCommand = "neofetch"
    var neofetchTyped by remember { mutableStateOf("") }
    var showNeofetch by remember { mutableStateOf(false) }
    var coralseaTyped by remember { mutableStateOf("") }
    var isCommandEntered by remember { mutableStateOf(false) }
    var showFinalPrompt by remember { mutableStateOf(false) }

    // Hard terminal-style cursor blink: a boolean toggle recomposes only the cursor
    // readers twice per blink, instead of an alpha animation running at display rate.
    // Every cursor site keeps the █ glyph in the string permanently and toggles only its
    // color: on devices whose monospace font lacks U+2588 (One UI) the fallback glyph has
    // taller metrics, so adding/removing it re-measured the line and made the column
    // visibly jump on every blink.
    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(530)
            cursorVisible = !cursorVisible
        }
    }

    // Boot sequence: type neofetch, print its snapshot in one shot, then type the
    // player command. The seek shortcut may complete the session instantly; every
    // step bails out as soon as that happens.
    LaunchedEffect(Unit) {
        delay(300)
        for (i in 1..neofetchCommand.length) {
            if (isCommandEntered) return@LaunchedEffect
            neofetchTyped = neofetchCommand.substring(0, i)
            delay(45)
        }
        delay(250)
        if (isCommandEntered) return@LaunchedEffect
        showNeofetch = true
        delay(1400)
        if (isCommandEntered) return@LaunchedEffect
        for (i in 1..fullCommand.length) {
            if (isCommandEntered) return@LaunchedEffect
            coralseaTyped = fullCommand.substring(0, i)
            delay(55)
        }
        delay(200)
        isCommandEntered = true
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(context, lifecycleOwner) {
        val player = try {
            MediaPlayer.create(context, R.raw.coralsea)?.apply {
                isLooping = false
                setOnCompletionListener {
                    isSongFinished = true
                }
            }
        } catch (_: Exception) {
            null
        }
        mediaPlayerInstance = player
        playerFailed = player == null
        if (player != null) {
            totalDurationMs = player.duration.toLong()
        }

        // Take media audio focus so background players pause instead of mixing
        // with the easter egg song.
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
            ) {
                try {
                    if (player?.isPlaying == true) {
                        player?.pause()
                    }
                } catch (_: Exception) {}
            }
        }
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        audioManager?.requestAudioFocus(focusRequest)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_PAUSE -> {
                    try {
                        if (player != null && player.isPlaying) {
                            player.pause()
                        }
                    } catch (_: Exception) {}
                }
                Lifecycle.Event.ON_RESUME -> {
                    try {
                        if (player != null && isCommandEntered && !player.isPlaying && !isSongFinished) {
                            // Re-acquire focus before resuming: after another app took it
                            // (e.g. a music player), silently resuming would fight it.
                            val granted = audioManager?.requestAudioFocus(focusRequest)
                            if (granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                                player.start()
                            }
                        }
                    } catch (_: Exception) {}
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                if (player != null) {
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.release()
                }
            } catch (_: Exception) {}
            audioManager?.abandonAudioFocusRequest(focusRequest)
            mediaPlayerInstance = null
        }
    }

    // Start audio ONLY after command is fully typed and entered
    LaunchedEffect(isCommandEntered, mediaPlayerInstance) {
        if (isCommandEntered) {
            val player = mediaPlayerInstance
            if (player != null && !player.isPlaying && !isSongFinished) {
                player.start()
            }
        }
    }

    // Global lead: a line becomes current LYRIC_LEAD_MS before its vocal timestamp.
    val effectivePos = currentPosMs + LYRIC_LEAD_MS
    val activeIndex = remember(effectivePos, lyrics) {
        lyrics.indexOfLast { it.timestampMs <= effectivePos }
    }

    LaunchedEffect(mediaPlayerInstance) {
        val player = mediaPlayerInstance ?: return@LaunchedEffect
        while (true) {
            try {
                if (player.isPlaying) {
                    currentPosMs = player.currentPosition.toLong()
                }
            } catch (_: Exception) {}
            if (isSongFinished) break
            delay(80)
        }
    }

    // End-of-song sequence: sudo asks for the password (typed invisibly — sudo never
    // echoes it), then the rm -v flood compressed to the glob-order highlights. rm
    // itself cannot segfault, so after the flood bash simply returns a fresh prompt:
    // a working shell on a system that no longer exists.
    LaunchedEffect(isSongFinished) {
        if (!isSongFinished) return@LaunchedEffect
        delay(600)
        val fullCmd = "sudo rm -rfv /*"
        for (i in 1..fullCmd.length) {
            rmCommandTyped = fullCmd.substring(0, i)
            delay(55)
        }
        delay(350)
        showSudoPassword = true
        delay(1600)
        showRmOutput = true
        delay(1200)
        showFinalPrompt = true
        delay(3000)
        exitSession()
    }

    // Player creation failed: print the failure output once the command prompt has
    // finished typing, then leave the session so the screen never hangs.
    LaunchedEffect(playerFailed, isCommandEntered) {
        if (!playerFailed || !isCommandEntered) return@LaunchedEffect
        delay(500)
        showPlayerError = true
        delay(3200)
        exitSession()
    }

    val displayedCount = if (isCommandEntered && !playerFailed) (activeIndex + 1).coerceAtLeast(0) else 0
    // Stream head items that precede the intro logs; scroll targets account for them.
    val neofetchItemCount = if (showNeofetch) 3 else 1

    // 4 Authentic Linux kernel & audio pipeline logs during the 10-second piano instrumental gap (1:1 realtime aligned)
    val introLogs = remember {
        listOf(
            Triple(50L, "[0.042] ", "ALSA: opened pcmC0D0p, 44.1kHz stereo"),
            Triple(2150L, "[2.150] ", "PipeWire: bound stream 'coralsea.ogg'"),
            Triple(5820L, "[5.820] ", "Vorbis: comments: 珊瑚海 / Jay Chou & Lara"),
            Triple(8940L, "[8.940] ", "TTY1: streaming embedded lyrics (ANSI)")
        )
    }

    val displayedIntroCount = if (!isCommandEntered) 0 else {
        introLogs.count { it.first <= currentPosMs }
    }

    // Lyric items read playback position through this State so that only the active
    // line subscribes to position updates; past lines never recompose on a tick.
    val effectivePosState = rememberUpdatedState(effectivePos)

    // Pause auto-scroll while the user drags the list; resume after a grace period.
    var userDragging by remember { mutableStateOf(false) }
    var lastDragEndTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listState.interactionSource) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> userDragging = true
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    userDragging = false
                    lastDragEndTime = System.currentTimeMillis()
                }
            }
        }
    }

    // Auto-scroll seamlessly with visual offset
    LaunchedEffect(
        neofetchTyped, showNeofetch, coralseaTyped, isCommandEntered,
        activeIndex, isSongFinished, showSudoPassword, showRmOutput, displayedIntroCount, showPlayerError, userDragging
    ) {
        if (userDragging) return@LaunchedEffect
        val sinceDragEnd = System.currentTimeMillis() - lastDragEndTime
        if (sinceDragEnd < 4000L) delay(4000L - sinceDragEnd)
        if (isSongFinished || showPlayerError) {
            if (listState.layoutInfo.totalItemsCount > 0) {
                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
            }
        } else if (!isCommandEntered) {
            // While commands type, pin the stream to its bottom like a real terminal.
            listState.dispatchRawDelta(1_000_000f)
        } else if (activeIndex >= 0) {
            val targetScroll = (displayedIntroCount + neofetchItemCount + activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        } else if (displayedIntroCount > 0) {
            listState.animateScrollToItem(displayedIntroCount - 1 + neofetchItemCount)
        }
    }

    // The terminal is dark regardless of the app theme; route the bar override through the
    // theme (see barsDarkOverride) so the correct appearance is restored on exit.
    DisposableEffect(Unit) {
        onBarsDarkChanged(true)
        onDispose { onBarsDarkChanged(false) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF300A24)) // Ubuntu Aubergine
            .statusBarsPadding()
            .navigationBarsPadding()
            // Long-press anywhere for 3s to jump to the last 10 seconds of the song.
            // Implemented with a raw gesture loop so a slow DRAG of the lyrics list
            // (finger down and moving) disarms the timer instead of firing the seek
            // mid-scroll, which the tap-detector version did after any 3s touch.
            .pointerInput(mediaPlayerInstance, totalDurationMs) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    var seekJob: Job? = null
                    try {
                        seekJob = coroutineScope.launch {
                            delay(3000)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val player = mediaPlayerInstance
                            val duration = if (totalDurationMs > 0) totalDurationMs else (player?.duration?.toLong() ?: 0L)
                            if (player != null && duration > 10000L) {
                                neofetchTyped = neofetchCommand
                                showNeofetch = true
                                coralseaTyped = fullCommand
                                isCommandEntered = true
                                val targetMs = (duration - 10000L).coerceAtLeast(0L).toInt()
                                player.seekTo(targetMs)
                                currentPosMs = targetMs.toLong()
                            }
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            if ((change.position - downPos).getDistance() > viewConfiguration.touchSlop) break
                        }
                    } finally {
                        seekJob?.cancel()
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // Session scrollback stream (ANSI colors: Male -> Blue, Female -> Yellow, Duet -> Green)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 0f,
                                endY = 48f
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    },
                contentPadding = PaddingValues(top = 10.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // neofetch: typed on the first prompt, its snapshot prints in one shot.
                item(key = "cmd_neofetch") {
                    TerminalPromptLine(
                        command = neofetchTyped,
                        cursorVisible = !showNeofetch && cursorVisible
                    )
                }
                if (showNeofetch) {
                    item(key = "neofetch_out") {
                        NeofetchBlock()
                    }
                    item(key = "cmd_coralsea") {
                        TerminalPromptLine(
                            command = coralseaTyped,
                            cursorVisible = !isCommandEntered && cursorVisible
                        )
                    }
                }

                // Player failure output (fallback when MediaPlayer.create fails)
                if (showPlayerError) {
                    item(key = "player_error") {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "alsa: open /dev/snd/pcmC0D0p failed: No such device or address",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.5.sp,
                                color = Color(0xFFD3D7CF)
                            )
                            Text(
                                text = "coralsea-cli: audio pipeline aborted — nothing to stream",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.5.sp,
                                color = Color(0xFFD3D7CF)
                            )
                            Text(
                                text = "Segmentation fault (core dumped)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF2929)
                            )
                        }
                    }
                }

                // Intro authentic Linux system logs during the instrumental piano gap
                items(displayedIntroCount) { logIdx ->
                    val log = introLogs[logIdx]
                    val isLatestLog = logIdx == displayedIntroCount - 1 && displayedCount == 0

                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color(0xFF8AE234))) {
                                append(log.second)
                            }
                            when (logIdx) {
                                0 -> {
                                    withStyle(SpanStyle(color = Color(0xFF729FCF), fontWeight = FontWeight.Bold)) { append("ALSA: ") }
                                    withStyle(SpanStyle(color = Color(0xFFD3D7CF))) { append("opened pcmC0D0p, 44.1kHz stereo") }
                                }
                                1 -> {
                                    withStyle(SpanStyle(color = Color(0xFF729FCF), fontWeight = FontWeight.Bold)) { append("PipeWire: ") }
                                    withStyle(SpanStyle(color = Color(0xFFD3D7CF))) { append("bound stream ") }
                                    withStyle(SpanStyle(color = Color(0xFFFCE94F), fontWeight = FontWeight.Bold)) { append("'coralsea.ogg'") }
                                }
                                2 -> {
                                    withStyle(SpanStyle(color = Color(0xFF729FCF), fontWeight = FontWeight.Bold)) { append("Vorbis: ") }
                                    withStyle(SpanStyle(color = Color(0xFFD3D7CF))) { append("comments: 珊瑚海 / ") }
                                    withStyle(SpanStyle(color = Color(0xFF729FCF), fontWeight = FontWeight.Bold)) { append("Jay Chou ") }
                                    withStyle(SpanStyle(color = Color(0xFFD3D7CF))) { append("& ") }
                                    withStyle(SpanStyle(color = Color(0xFFFCE94F), fontWeight = FontWeight.Bold)) { append("Lara") }
                                }
                                3 -> {
                                    withStyle(SpanStyle(color = Color(0xFF729FCF), fontWeight = FontWeight.Bold)) { append("TTY1: ") }
                                    withStyle(SpanStyle(color = Color(0xFF8AE234), fontWeight = FontWeight.SemiBold)) { append("streaming embedded lyrics (ANSI)") }
                                }
                            }
                            withStyle(
                                SpanStyle(color = if (isLatestLog && cursorVisible) Color.White else Color.Transparent)
                            ) {
                                append("█")
                            }
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (displayedIntroCount == introLogs.size && displayedCount == 0) {
                    item(key = "intro_divider_spacer") {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // Fallback notice when the OGG container carries no LYRICS= comment
                if (displayedIntroCount == introLogs.size && lyricsLoaded && lyrics.isEmpty()) {
                    item(key = "no_lyrics_notice") {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = Color(0xFF729FCF), fontWeight = FontWeight.Bold)) {
                                    append("Vorbis: ")
                                }
                                withStyle(SpanStyle(color = Color(0xFFD3D7CF))) {
                                    append("no embedded lyrics in container — streaming audio only")
                                }
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                items(displayedCount, key = { it }) { index ->
                    val line = lyrics[index]
                    val isCurrent = index == activeIndex && !isSongFinished

                    // ANSI color selection according to singer
                    val (textColor, activeColor) = when (line.singer) {
                        LyricSinger.MALE -> Pair(Color(0xFF6B92BC), Color(0xFF729FCF))     // ANSI Blue
                        LyricSinger.FEMALE -> Pair(Color(0xFFC7B935), Color(0xFFFCE94F))   // ANSI Yellow
                        LyricSinger.DUET -> Pair(Color(0xFF76A83C), Color(0xFF8AE234))     // ANSI Green
                    }

                    if (isCurrent) {
                        val lineDuration = if (index < lyrics.size - 1) {
                            // Floor stays above LEAD so a line always has a real typing
                            // window; gaps below it (fastest chorus lines ~0.66s) type at
                            // their true pace instead of an inflated one.
                            (lyrics[index + 1].timestampMs - line.timestampMs).coerceIn(700L, 5000L)
                        } else {
                            4000L
                        }
                        // Typing is keyed to real playback position. Keep the RAW elapsed
                        // value: it is negative during the activation lead-in, and only
                        // that negative state (not a coerced 0) may gate the first char.
                        val rawElapsedInLine = effectivePosState.value - line.timestampMs - LYRIC_LEAD_MS - LYRIC_TYPE_DELAY_MS
                        val typingWindowMs = (lineDuration - LYRIC_LEAD_MS - LYRIC_TYPE_DELAY_MS).coerceAtLeast(300L)
                        // The body track (every char after the first) runs BODY_LEAD ahead:
                        // folding it into the elapsed measure shifts each later char earlier
                        // by exactly that much, while the rawElapsed<0 gate above keeps the
                        // first char anchored to the vocal onset.
                        val progress = ((rawElapsedInLine + LYRIC_BODY_LEAD_MS).toFloat() / typingWindowMs.toFloat()).coerceIn(0f, 1f)
                        // First character renders on the vocal onset; the rest pace across
                        // the window so the line completes LYRIC_LEAD_MS before the next
                        // vocal. Truncating the full length here would hide the first char
                        // for window/(len+1) ms — clearly after the first sung syllable.
                        val typedCharsCount = if (rawElapsedInLine < 0L) 0 else
                            (progress * (line.text.length - 1)).toInt().coerceIn(0, line.text.length - 1) + 1

                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(color = activeColor, fontWeight = FontWeight.SemiBold)
                                ) {
                                    append(line.text.substring(0, typedCharsCount))
                                }
                                withStyle(
                                    SpanStyle(color = if (cursorVisible) activeColor else Color.Transparent)
                                ) {
                                    append("█")
                                }
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = buildAnnotatedString {
                                append(line.text)
                                withStyle(SpanStyle(color = Color.Transparent)) {
                                    append("█")
                                }
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            color = textColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // End-of-song sequence. '/*' expands to the children of /, so rm's
                // root-directory guard never triggers and --no-preserve-root would be
                // dead weight; the flood keeps glob order as highlight entries.
                if (isSongFinished) {
                    item(key = "rm_rf_sequence") {
                        Spacer(modifier = Modifier.height(14.dp))
                        TerminalPromptLine(
                            command = rmCommandTyped,
                            cursorVisible = !showSudoPassword && cursorVisible
                        )
                        if (showSudoPassword) {
                            Text(
                                text = buildAnnotatedString {
                                    append("[sudo] password for cicada:")
                                    withStyle(
                                        SpanStyle(color = if (!showRmOutput && cursorVisible) Color.White else Color.Transparent)
                                    ) {
                                        append("█")
                                    }
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = Color(0xFFD3D7CF),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (showRmOutput) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf(
                                    "removed '/bin'",
                                    "removed '/boot/vmlinuz-7.2.2-070202-generic'",
                                    "removed '/dev/soul'",
                                    "rm: cannot remove '/dev': Device or resource busy",
                                    "removed directory '/etc'",
                                    "removed directory '/home'",
                                    "removed directory '/opt'",
                                    "rm: cannot remove '/proc': Device or resource busy",
                                    "rm: cannot remove '/sys': Device or resource busy",
                                    "removed directory '/usr'",
                                    "removed directory '/var'"
                                ).forEach { line ->
                                    Text(
                                        text = line,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.5.sp,
                                        color = Color(0xFFD3D7CF)
                                    )
                                }
                            }
                            if (showFinalPrompt) {
                                Spacer(modifier = Modifier.height(10.dp))
                                TerminalPromptLine(command = "", cursorVisible = cursorVisible)
                            }
                        }
                    }
                }
            }
        }
    }
}
