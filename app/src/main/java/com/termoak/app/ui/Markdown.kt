package com.termoak.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Blocks of a markdown text (just enough for the AI's answers). */
private sealed class MdBlock {
    data class Paragraph(val text: String) : MdBlock()
    data class Heading(val text: String, val level: Int) : MdBlock()
    data class Bullet(val text: String, val marker: String, val indent: Int) : MdBlock()
    data class Code(val text: String) : MdBlock()
}

private fun blocks(md: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val para = StringBuilder()
    fun flush() {
        if (para.isNotBlank()) out += MdBlock.Paragraph(para.toString().trim())
        para.clear()
    }
    val lines = md.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val t = line.trim()
        when {
            t.startsWith("```") -> {
                flush()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.appendLine(lines[i])
                    i++
                }
                out += MdBlock.Code(code.toString().trimEnd())
            }
            t.isEmpty() -> flush()
            t.startsWith("#") -> {
                flush()
                val level = t.takeWhile { it == '#' }.length
                out += MdBlock.Heading(t.drop(level).trim(), level)
            }
            Regex("^([-*+]|\\d+[.)])\\s+.*").matches(t) -> {
                flush()
                val marker = t.substringBefore(' ')
                out += MdBlock.Bullet(t.substringAfter(' ').trim(), if (marker[0].isDigit()) marker else "•",
                    (line.length - line.trimStart().length) / 2)
            }
            else -> {
                if (para.isNotEmpty()) para.append('\n')
                para.append(t)
            }
        }
        i++
    }
    flush()
    return out
}

/** `**bold**`, `*italic*`, `` `code` `` and `[link](url)` (the text is kept). */
private fun inline(text: String, code: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '`' && text.indexOf('`', i + 1) > i -> {
                val end = text.indexOf('`', i + 1)
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = code, fontSize = 13.sp)) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
            }
            text.startsWith("**", i) && text.indexOf("**", i + 2) > i + 2 -> {
                val end = text.indexOf("**", i + 2)
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(inline(text.substring(i + 2, end), code)) }
                i = end + 2
            }
            c == '*' && i + 1 < text.length && !text[i + 1].isWhitespace() &&
                text.indexOf(c, i + 1).let { it > i + 1 && !text[it - 1].isWhitespace() } -> {
                val end = text.indexOf(c, i + 1)
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                i = end + 1
            }
            c == '[' -> {
                val close = text.indexOf("](", i)
                val end = if (close > 0) text.indexOf(')', close) else -1
                if (close > 0 && end > 0 && '\n' !in text.substring(i, end)) {
                    withStyle(SpanStyle(color = Brand.Blue)) { append(text.substring(i + 1, close)) }
                    i = end + 1
                } else {
                    append(c); i++
                }
            }
            else -> { append(c); i++ }
        }
    }
}

/** Simple markdown text: paragraphs, headings, lists, code. */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val parsed = remember(text) { blocks(text) }
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            parsed.forEach { b ->
                when (b) {
                    is MdBlock.Paragraph -> Text(inline(b.text, codeBg), style = MaterialTheme.typography.bodyMedium)
                    is MdBlock.Heading -> Text(
                        inline(b.text, codeBg),
                        style = if (b.level <= 2) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
                    )
                    is MdBlock.Bullet -> Row(Modifier.padding(start = (b.indent * 12).dp)) {
                        Text(b.marker, Modifier.padding(end = 6.dp), style = MaterialTheme.typography.bodyMedium)
                        Text(inline(b.text, codeBg), style = MaterialTheme.typography.bodyMedium)
                    }
                    is MdBlock.Code -> Surface(Modifier.fillMaxWidth(), color = codeBg, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            b.text, Modifier.horizontalScroll(rememberScrollState()).padding(10.dp),
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}
