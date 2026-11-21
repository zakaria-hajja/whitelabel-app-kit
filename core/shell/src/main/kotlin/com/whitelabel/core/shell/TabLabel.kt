package com.whitelabel.core.shell

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * A tab's label at the user's full font size whenever it fits. It may wrap between words as
 * Material's label does, but it never breaks inside a word ("Calenda/r", bug 8zZ4HDYM): when a
 * word doesn't fit its tab, the label shrinks only as far as it needs to, and never below its
 * size at 100 % font scale. Its line height shrinks with it. The text, and so what a screen
 * reader reads, is always the whole label.
 */
@Composable
internal fun TabLabel(text: String) {
    val style = LocalTextStyle.current
    if (!style.fontSize.isSp) {
        Text(text)
        return
    }
    val lineHeight = if (style.lineHeight.isSp) (style.lineHeight.value / style.fontSize.value).em else style.lineHeight
    Text(text = text, style = style.copy(lineHeight = lineHeight), autoSize = TabLabelAutoSize(style.fontSize))
}

/**
 * Picks the biggest size, from [maxFontSize] (the user's full size) down in 0.25 sp steps, at which
 * no word breaks across lines. The floor is [maxFontSize] at 100 % font scale; if even that breaks
 * a word, the label stays at the floor and wraps as Material's would.
 */
private data class TabLabelAutoSize(val maxFontSize: TextUnit) : TextAutoSize {

    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        val max = maxFontSize.value
        val floor = minOf(max, maxFontSize.value.dp.toSp().value)
        fun fits(size: Float): Boolean {
            val layout = performLayout(constraints, text, size.sp)
            val ends = (0 until layout.lineCount - 1).map { layout.getLineEnd(it) }
            return !layout.didOverflowWidth && !breaksMidWord(text.text, ends)
        }
        if (fits(max)) return maxFontSize
        val steps = ((max - floor) / STEP).toInt()
        // Binary search for the fewest steps down that fit; step `steps` is clamped to the floor.
        var low = 1
        var high = steps
        var best = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (fits(maxOf(floor, max - mid * STEP))) { best = mid; high = mid - 1 } else low = mid + 1
        }
        return if (best == -1) floor.sp else maxOf(floor, max - best * STEP).sp
    }
}

private const val STEP = 0.25f

/**
 * Whether a line ending at one of [lineEnds] (each line but the last) splits a word: the
 * characters on both sides of the break are letters or digits.
 */
internal fun breaksMidWord(text: String, lineEnds: List<Int>): Boolean = lineEnds.any { end ->
    end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()
}
