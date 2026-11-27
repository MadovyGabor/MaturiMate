package com.madovygabor.maturimate.core.network

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

/**
 * The pages shown in the user's browser after a desktop Google sign-in. They look like the app's welcome screen: the
 * logo, the "MaturiMate" title in Literata, the status line in the deep gold, the text in Manrope. Colors, type sizes,
 * spacing and radii are the values of `docs/design-system/tokens.json` (light and dark), written as CSS here because the
 * page is outside Compose.
 *
 * Self-contained: the app's own fonts and logo are read from the app's resources and embedded as `data:` URIs, and a
 * Content-Security-Policy forbids every other load (no network, no script). Nothing from the request is ever shown.
 */
internal object DesktopAuthPageCopy {
    // Every Slovak line needs a check by a native speaker before the release.
    data class Copy(
        val lang: String,
        val title: String,
        val heading: String,
        val message: String,
        val hint: String,
    )

    val doneHu = Copy(
        lang = "hu",
        title = "MaturiMate – Sikeres belépés",
        heading = "Sikeresen bejelentkeztél",
        message = "Visszatérhetsz a MaturiMate alkalmazásba.",
        hint = "Ezt a böngészőlapot bezárhatod.",
    )

    val doneSk = Copy(
        lang = "sk",
        title = "MaturiMate – Prihlásenie úspešné",
        heading = "Prihlásenie prebehlo úspešne",
        message = "Môžeš sa vrátiť do aplikácie MaturiMate.",
        hint = "Toto okno prehliadača môžeš zavrieť.",
    )

    val failedHu = Copy(
        lang = "hu",
        title = "MaturiMate – Sikertelen belépés",
        heading = "A bejelentkezés nem sikerült",
        message = "Térj vissza a MaturiMate alkalmazásba, és próbáld újra.",
        hint = "Ezt a böngészőlapot bezárhatod.",
    )

    val failedSk = Copy(
        lang = "sk",
        title = "MaturiMate – Prihlásenie zlyhalo",
        heading = "Prihlásenie sa nepodarilo",
        message = "Vráť sa do aplikácie MaturiMate a skús to znova.",
        hint = "Toto okno prehliadača môžeš zavrieť.",
    )
}

/** The page after a successful desktop Google sign-in in the app's language ([language]). */
internal fun signInDonePage(language: String? = null): String {
    val copy = if (isHungarian(language)) DesktopAuthPageCopy.doneHu else DesktopAuthPageCopy.doneSk
    return renderCallbackPage(copy = copy, success = true)
}

/** The page after a refused or failed desktop Google sign-in in the app's language ([language]). */
internal fun signInFailedPage(language: String? = null): String {
    val copy = if (isHungarian(language)) DesktopAuthPageCopy.failedHu else DesktopAuthPageCopy.failedSk
    return renderCallbackPage(copy = copy, success = false)
}

/**
 * Reads and encodes the fonts and the logo once. Called before the sign-in starts, so the page is ready at once when
 * the browser comes back (the receiver stops shortly after the outcome is known).
 */
internal fun warmUpDesktopAuthPages() {
    AuthPageAssets.fontFaces
    AuthPageAssets.logoDataUri
}

/** Escapes HTML special characters so no untrusted text can ever be interpreted as markup. */
internal fun escapeHtml(text: String): String = buildString(text.length) {
    for (ch in text) {
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(ch)
        }
    }
}

private fun isHungarian(language: String?): Boolean {
    val primary = language
        ?.trim()
        ?.lowercase()
        ?.substringBefore('-')
        ?.substringBefore('_')
    return primary == "hu"
}

/** The app's fonts and logo from its compose resources, as `data:` URIs; empty or null when a file is missing. */
private object AuthPageAssets {
    private const val ROOT = "/composeResources/maturimate.shared.generated.resources/"

    /** Width the logo is scaled to: twice its shown width (`logo-large`), sharp on a high-density screen. */
    private const val LOGO_PIXELS = 240

    val fontFaces: String by lazy {
        listOf(
            Triple("Literata", 600, "literata_semibold.ttf"),
            Triple("Manrope", 400, "manrope_regular.ttf"),
            Triple("Manrope", 600, "manrope_semibold.ttf"),
        ).mapNotNull { (family, weight, file) ->
            read("font/$file")?.let {
                "@font-face { font-family: \"$family\"; font-weight: $weight; font-style: normal; font-display: block; " +
                    "src: url(data:font/ttf;base64,${Base64.getEncoder().encodeToString(it)}) format(\"truetype\"); }"
            }
        }.joinToString("\n")
    }

    val logoDataUri: String? by lazy {
        val bytes = read("drawable/logoTransparent.png") ?: return@lazy null
        val png = runCatching { scaledPng(bytes, LOGO_PIXELS) }.getOrNull() ?: bytes
        "data:image/png;base64," + Base64.getEncoder().encodeToString(png)
    }

    private fun read(path: String): ByteArray? =
        AuthPageAssets::class.java.getResourceAsStream(ROOT + path)?.use { it.readBytes() }

    /** The logo scaled down (never up) to [width] pixels, with its transparency; the file itself is not changed. */
    private fun scaledPng(bytes: ByteArray, width: Int): ByteArray? {
        val source = ImageIO.read(bytes.inputStream()) ?: return null
        if (source.width <= width) return bytes
        val height = (source.height.toDouble() * width / source.width).toInt()
        val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val smooth = source.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH)
        scaled.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            drawImage(smooth, 0, 0, null)
            dispose()
        }
        return ByteArrayOutputStream().also { ImageIO.write(scaled, "png", it) }.toByteArray()
    }
}

private fun renderCallbackPage(copy: DesktopAuthPageCopy.Copy, success: Boolean): String {
    val statusClass = if (success) "done" else "failed"
    // the logo is described by the title right below it, so it is decorative here (alt="")
    val logo = AuthPageAssets.logoDataUri?.let { """<div class="logo"><img src="$it" alt="" width="120"></div>""" }.orEmpty()

    return """
<!DOCTYPE html>
<html lang="${escapeHtml(copy.lang)}">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="color-scheme" content="light dark">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; font-src data:; img-src data:">
  <meta name="referrer" content="no-referrer">
  <title>${escapeHtml(copy.title)}</title>
  <style>
${AuthPageAssets.fontFaces}
    :root {
      color-scheme: light dark;
      --background: #fcf8f1;
      --on-background: #211b13;
      --on-surface-variant: #4f4537;
      --primary: #7b5800;
      --error: #b3261e;
      --serif: "Literata", Georgia, "Times New Roman", serif;
      --sans: "Manrope", system-ui, "Segoe UI", sans-serif;
    }
    @media (prefers-color-scheme: dark) {
      :root {
        --background: #17130e;
        --on-background: #ece1d4;
        --on-surface-variant: #d3c4ad;
        --primary: #f4c04e;
        --error: #f2b8b5;
      }
      /* the app's dark theme shows the logo on its own cream tile (AppLogo) */
      .logo { background: #fcf8f1; border-radius: 28px; padding: 24px; }
    }
    * { box-sizing: border-box; }
    html, body { height: 100%; }
    body {
      margin: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 32px 16px;
      background: var(--background);
      color: var(--on-background);
      font-family: var(--sans);
      -webkit-font-smoothing: antialiased;
      text-rendering: optimizeLegibility;
    }
    main {
      width: 100%;
      max-width: 560px;
      text-align: center;
    }
    .logo { display: inline-block; margin: 0 0 24px; line-height: 0; }
    .logo img { width: 120px; height: auto; }
    .wordmark {
      margin: 0 0 16px;
      font-family: var(--serif);
      font-size: 40px;
      line-height: 48px;
      font-weight: 600;
      color: var(--on-background);
    }
    h1 {
      margin: 0 0 16px;
      font-family: var(--serif);
      font-size: 28px;
      line-height: 36px;
      font-weight: 600;
    }
    .done h1 { color: var(--primary); }
    .failed h1 { color: var(--error); }
    .message {
      margin: 0 0 32px;
      font-size: 16px;
      line-height: 24px;
      color: var(--on-surface-variant);
    }
    .hint {
      margin: 0;
      font-size: 14px;
      line-height: 20px;
      color: var(--on-surface-variant);
    }
    @media (max-width: 420px) {
      .wordmark { font-size: 34px; line-height: 42px; }
      h1 { font-size: 24px; line-height: 32px; }
    }
  </style>
</head>
<body>
  <main class="$statusClass">
    $logo
    <p class="wordmark">MaturiMate</p>
    <h1>${escapeHtml(copy.heading)}.</h1>
    <p class="message">${escapeHtml(copy.message)}</p>
    <p class="hint">${escapeHtml(copy.hint)}</p>
  </main>
</body>
</html>
""".trimIndent()
}
