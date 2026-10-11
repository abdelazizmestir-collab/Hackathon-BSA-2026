package com.example.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Stroke icons — exactly the same 24x24 SVG paths used in the Figma file.
 * They are tinted by `Icon(tint = …)`, so the stroke colour here doesn't matter.
 */
object SwissIcons {

    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    private fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float = 0f): String =
        if (rx <= 0f) "M$x ${y}h${w}v${h}h${-w}z"
        else "M${x + rx} ${y}h${w - 2 * rx}a$rx $rx 0 0 1 $rx ${rx}v${h - 2 * rx}" +
            "a$rx $rx 0 0 1 ${-rx} ${rx}h${-(w - 2 * rx)}a$rx $rx 0 0 1 ${-rx} ${-rx}" +
            "v${-(h - 2 * rx)}a$rx $rx 0 0 1 $rx ${-rx}z"

    private fun icon(name: String, vararg paths: String, stroke: Float = 2f): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    // Navigation
    val Home by lazy { icon("home", "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z") }
    val Trade by lazy { icon("trade", "M5 20V11M10 20V5M15 20v-6M20 20V8") }
    val Banking by lazy { icon("banking", rect(3f, 5f, 18f, 14f, 2f), "M3 10h18M7 15h3") }
    val Plan by lazy { icon("plan", "M12 2 3 7v10l9 5 9-5V7z", "m3 7 9 5 9-5M12 12v10") }
    val Wallet by lazy {
        icon(
            "wallet",
            "M17 7V5a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4",
            "M3 6v12a2 2 0 0 0 2 2h14a1 1 0 0 0 1-1V9a1 1 0 0 0-1-1H5",
            "M16 14h.01",
        )
    }
    val History by lazy { icon("history", "M3 12a9 9 0 1 0 3-6.7L3 8", "M3 3v5h5M12 7v5l3 2") }
    val Profile by lazy { icon("profile", circle(12f, 8f, 4f), "M4 21a8 8 0 0 1 16 0") }

    // Actions
    val Search by lazy { icon("search", circle(11f, 11f, 7f), "m20 20-3.5-3.5") }
    val UserCircle by lazy {
        icon("user_circle", circle(12f, 12f, 10f), circle(12f, 10f, 3f), "M6.2 18.4a7 7 0 0 1 11.6 0", stroke = 1.6f)
    }
    val ChevronRight by lazy { icon("chevron_right", "m9 6 6 6-6 6") }
    val ChevronDown by lazy { icon("chevron_down", "m6 9 6 6 6-6") }
    val Back by lazy { icon("back", "M19 12H5M11 18l-6-6 6-6") }
    val Close by lazy { icon("close", "M6 6l12 12M18 6 6 18") }
    val Sort by lazy { icon("sort", "M12 4v16M6 14l6 6 6-6") }
    val Plus by lazy { icon("plus", "M12 5v14M5 12h14") }
    val Check by lazy { icon("check", "m5 12 5 5L20 7") }
    val Bell by lazy { icon("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.9 1.9 0 0 0 3.4 0") }
    val Settings by lazy {
        icon(
            "settings",
            circle(12f, 12f, 3f),
            "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
        )
    }
    val ArrowDown by lazy { icon("arrow_down", "M12 5v14M6 13l6 6 6-6") }
    val ArrowUp by lazy { icon("arrow_up", "M12 19V5M6 11l6-6 6 6") }
    val ArrowDownLeft by lazy { icon("arrow_down_left", "M17 7 7 17M17 17H7V7") }
    val ArrowUpRight by lazy { icon("arrow_up_right", "M7 17 17 7M7 7h10v10") }
    val Swap by lazy { icon("swap", "M7 4 3 8l4 4M3 8h14M17 20l4-4-4-4M21 16H7") }
    val Eye by lazy { icon("eye", "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z", circle(12f, 12f, 3f)) }
    val EyeOff by lazy {
        icon("eye_off", "M2 12s3.5-7 10-7c2 0 3.7.6 5.1 1.5M22 12s-3.5 7-10 7c-2 0-3.7-.6-5.1-1.5", "M3 3l18 18")
    }
    val Copy by lazy {
        icon("copy", rect(9f, 9f, 12f, 12f, 2f), "M5 15H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1")
    }
    val Qr by lazy {
        icon(
            "qr",
            rect(3f, 3f, 7f, 7f, 1f), rect(14f, 3f, 7f, 7f, 1f), rect(3f, 14f, 7f, 7f, 1f),
            "M14 14h3v3h-3zM20 14v.01M14 20h.01M17 20h4v-3",
        )
    }
    val Share by lazy { icon("share", "M12 3v12M7 8l5-5 5 5", "M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6") }
    val Shield by lazy { icon("shield", "M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6z", "m9 12 2 2 4-4") }
    val Lock by lazy { icon("lock", rect(4f, 11f, 16f, 10f, 2f), "M8 11V7a4 4 0 0 1 8 0v4") }
    val Bank by lazy { icon("bank", "M3 10h18L12 4zM5 10v8M9 10v8M15 10v8M19 10v8M3 21h18") }
    val Edit by lazy { icon("edit", "M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z") }
    val Fingerprint by lazy {
        icon("fingerprint", "M12 11v4M8 9a4 4 0 0 1 8 0v5a8 8 0 0 1-1 4M8 12v2a8 8 0 0 0 1.5 5M5 7.5A8 8 0 0 1 19 8v4M4.5 12v1")
    }
    val Key by lazy { icon("key", circle(8f, 15f, 4f), "m11 12 9-9M17 6l3 3M15 8l2 2") }
    val Coins by lazy { icon("coins", circle(9f, 9f, 6f), "M15.7 9.4A6 6 0 1 1 9.4 15.7") }
    val Globe by lazy {
        icon("globe", circle(12f, 12f, 9f), "M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18")
    }
    val Logout by lazy {
        icon("logout", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9")
    }
    val Link by lazy {
        icon(
            "link",
            "M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7",
            "M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7",
        )
    }
    val External by lazy {
        icon("external", "M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5")
    }
    val Alert by lazy { icon("alert", circle(12f, 12f, 9f), "M12 8v5M12 16.5v.01") }
    val Zap by lazy { icon("zap", "M13 2 4 14h7l-1 8 9-12h-7z") }
    val Image by lazy { icon("image", rect(3f, 4f, 18f, 16f, 2f), "m3 16 5-5 4 4 3-3 6 6", circle(15.5f, 8.5f, 1.5f)) }
    val Layers by lazy { icon("layers", "M12 3 2 8l10 5 10-5z", "m2 13 10 5 10-5") }
}
