package com.elo.trackerhoras

import android.content.Context
import android.graphics.Typeface

/** DM Sans (licencia OFL), incluida en la app. */
object Fonts {
    private var regular: Typeface? = null
    private var medium: Typeface? = null

    fun regular(c: Context): Typeface =
        regular ?: (try { c.resources.getFont(R.font.dm_sans_regular) } catch (_: Exception) { Typeface.DEFAULT })
            .also { regular = it }

    fun medium(c: Context): Typeface =
        medium ?: (try { c.resources.getFont(R.font.dm_sans_medium) } catch (_: Exception) { Typeface.DEFAULT_BOLD })
            .also { medium = it }
}
