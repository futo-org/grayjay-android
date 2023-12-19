package com.futo.platformplayer

import android.text.Html
import android.text.Spanned
import androidx.core.text.HtmlCompat
import org.jsoup.Jsoup
import org.jsoup.nodes.Attributes
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Tag
import java.text.DecimalFormat
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs


//Long
val countInKilo = 1000;
val countInMillion = countInKilo * 1000;
val countInBillion = countInMillion * 1000;

fun Long.toHumanNumber(): String {
    val v = Math.abs(this);
    if(v >= countInBillion)
        return "${Math.floor((this / countInBillion).toDouble()).toLong()}B"
    if(v >= countInMillion)
        return "${"%.2f".format((this.toDouble() / countInMillion)).trim('0').trim('.')}M"
    if(v >= countInKilo)
        return "${"%.2f".format((this.toDouble() / countInKilo)).trim('0').trim('.')}K"

    return "${this}";
}

val decimalDigits2 = DecimalFormat("#.##");

val countInKbit = 1000;
val countInMbit = countInKbit * 1000;
val countInGbit = countInMbit * 1000;

fun Int.toHumanBitrate() = this.toLong().toHumanBitrate();
fun Long.toHumanBitrate(): String{
    val v = Math.abs(this);
    if(v >= countInGbit)
        return "${this / countInGbit}gbps";
    else if(v >= countInMbit)
        return "${this / countInMbit}mbps";
    else if(v >= countInKbit)
        return "${this / countInKbit}kbps";

    return "${this}bps";
}
fun Int.toHumanBytesSpeed() = this.toLong().toHumanBytesSpeed();
fun Long.toHumanBytesSpeed(): String{
    val v = Math.abs(this);
    if(v >= countInGbit)
        return "${decimalDigits2.format(this / countInGbit.toDouble())}GB/s";
    else if(v >= countInMbit)
        return "${decimalDigits2.format(this / countInMbit.toDouble())}MB/s";
    else if(v >= countInKbit)
        return "${decimalDigits2.format(this / countInKbit.toDouble())}KB/s";

    return "${this}B/s";
}

fun Int.toHumanBytesSize() = this.toLong().toHumanBytesSize();
fun Long.toHumanBytesSize(withDecimal: Boolean = true): String{
    val v = Math.abs(this);
    if(withDecimal) {
        if(v >= countInGbit)
            return "${decimalDigits2.format(this / countInGbit.toDouble())}GB";
        else if(v >= countInMbit)
            return "${decimalDigits2.format(this / countInMbit.toDouble())}MB";
        else if(v >= countInKbit)
            return "${decimalDigits2.format(this / countInKbit.toDouble())}KB";

        return "${this}B";
    }
    else {
        if(v >= countInGbit)
            return "${(this / countInGbit.toDouble()).toInt()}GB";
        else if(v >= countInMbit)
            return "${(this / countInMbit.toDouble()).toInt()}MB";
        else if(v >= countInKbit)
            return "${(this / countInKbit.toDouble()).toInt()}KB";

        return "${this}B";
    }
}


//OffestDateTime
val secondsInMinute = 60;
val secondsInHour = secondsInMinute * 60;
val secondsInDay = secondsInHour * 24;
val secondsInWeek = secondsInDay * 7;
val secondsInMonth = secondsInDay * 30; //Roughly
val secondsInYear = secondsInDay * 365;

fun OffsetDateTime.getNowDiffMiliseconds(): Long {
    return ChronoUnit.MILLIS.between(this, OffsetDateTime.now());
}
fun OffsetDateTime.getNowDiffSeconds(): Long {
    return ChronoUnit.SECONDS.between(this, OffsetDateTime.now());
}
fun OffsetDateTime.getNowDiffMinutes(): Long {
    return ChronoUnit.MINUTES.between(this, OffsetDateTime.now());
}
fun OffsetDateTime.getNowDiffHours(): Long {
    return ChronoUnit.HOURS.between(this, OffsetDateTime.now());
}
fun OffsetDateTime.getNowDiffDays(): Long {
    return ChronoUnit.DAYS.between(this, OffsetDateTime.now());
}
fun OffsetDateTime.getNowDiffWeeks(): Long {
    return ChronoUnit.WEEKS.between(this, OffsetDateTime.now());
}
fun OffsetDateTime.getNowDiffMonths(): Long {
    return ChronoUnit.MONTHS.between(this, OffsetDateTime.now());
}
fun OffsetDateTime.getNowDiffYears(): Long {
    return ChronoUnit.YEARS.between(this, OffsetDateTime.now());
}

fun OffsetDateTime.getDiffDays(otherDate: OffsetDateTime): Long {
    return ChronoUnit.WEEKS.between(this, otherDate);
}

fun OffsetDateTime.toHumanNowDiffStringMinDay(abs: Boolean = false) : String {
    var value = getNowDiffSeconds();

    if(abs) value = abs(value);
    if (value >= 2 * secondsInDay) {
        return "${toHumanNowDiffString(abs)} ago";
    }

    if (value >= 1 * secondsInDay) {
        return "Yesterday";
    }

    return "Today";
};

fun OffsetDateTime.toHumanNowDiffString(abs: Boolean = false) : String {
    var value = getNowDiffSeconds();

    var unit = "second";

    if(abs) value = abs(value);
    if(value >= secondsInYear) {
        value = getNowDiffYears();
        if(abs) value = abs(value);
        unit = "year";
    }
    else if(value >= secondsInMonth) {
        value = getNowDiffMonths();
        if(abs) value = abs(value);
        value = Math.max(1, value);
        unit = "month";
    }
    else if(value >= secondsInWeek) {
        value = getNowDiffWeeks();
        if(abs) value = abs(value);
        unit = "week";
    }
    else if(value >= secondsInDay) {
        value = getNowDiffDays();
        if(abs) value = abs(value);
        unit = "day";
    }
    else if(value >= secondsInHour) {
        value = getNowDiffHours();
        if(abs) value = abs(value);
        unit = "hour";
    }
    else if(value >= secondsInMinute) {
        value = getNowDiffMinutes();
        if(abs) value = abs(value);
        unit = "minute";
    }

    if(value != 1L)
        unit += "s";

    return "${value} ${unit}";
};
fun Int.toHumanTimeIndicator(abs: Boolean = false) : String {
    var value = this;

    var unit = "s";

    if(abs) value = abs(value);
    if(value >= secondsInHour) {
        value = (this / secondsInHour).toInt();
        if(abs) value = abs(value);
        unit = "hr" + (if(value > 1) "s" else "");
    }
    else if(value >= secondsInMinute) {
        value = (this / secondsInMinute).toInt();
        if(abs) value = abs(value);
        unit = "min";
    }

    return "${value}${unit}";
}

fun Long.toHumanTime(isMs: Boolean): String {
    var scaler = 1;
    if(isMs)
        scaler = 1000;
    val v = Math.abs(this);
    val hours = Math.max(v/(secondsInHour*scaler), 0);
    val mins = Math.max((v % (secondsInHour*scaler)) / (secondsInMinute * scaler), 0);
    val minsStr = mins.toString();
    val seconds = Math.max(((v % (secondsInHour*scaler)) % (secondsInMinute * scaler))/scaler, 0);
    val secsStr = seconds.toString().padStart(2, '0');
    val prefix = if (this < 0) { "-" } else { "" };

    if(hours > 0)
        return "${prefix}${hours}:${minsStr.padStart(2, '0')}:${secsStr}"
    else
        return  "${prefix}${minsStr}:${secsStr}"
}

//TODO: Determine if below stuff should have its own proper class, seems a bit too complex for a utility method
fun String.fixHtmlWhitespace(): Spanned {
    return Html.fromHtml(replace("\n", "<br />"), HtmlCompat.FROM_HTML_MODE_LEGACY);
}

fun Long.formatDuration(): String {
    val hours = this / 3600000
    val minutes = (this % 3600000) / 60000
    val seconds = (this % 60000) / 1000

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

fun String.fixHtmlLinks(): Spanned {
    //TODO: Properly fix whitespace handling.
    val doc = Jsoup.parse(replace("\n", "<br />"));
    for (n in doc.body().childNodes()) {
        replaceLinks(n);
    }
    for (n in doc.body().childNodes()) {
        replaceTimestamps(n);
    }

    val modifiedDoc = doc.body().toString();
    return HtmlCompat.fromHtml(modifiedDoc, HtmlCompat.FROM_HTML_MODE_LEGACY);
}

val timestampRegex = Regex("\\d+:\\d+(?::\\d+)?");
private val urlRegex = Regex("https?://\\S+");
private val linkTag = Tag.valueOf("a");
private fun replaceTimestamps(node: Node) {
    for (n in node.childNodes()) {
        replaceTimestamps(n);
    }

    if (node is TextNode) {
        val text = node.text();
        var lastOffset = 0;
        var lastNode = node;

        val matches = timestampRegex.findAll(text).toList();
        for (i in matches.indices) {
            val match = matches[i];

            val textBeforeNode = TextNode(text.substring(lastOffset, match.range.first));
            lastNode.after(textBeforeNode);
            lastNode = textBeforeNode;

            val attributes = Attributes();
            attributes.add("href", match.value);
            val linkNode = Element(linkTag, null, attributes);
            linkNode.text(match.value);
            lastNode.after(linkNode);
            lastNode = linkNode;

            lastOffset = match.range.last + 1;
        }

        if (lastOffset > 0) {
            if (lastOffset < text.length) {
                lastNode.after(TextNode(text.substring(lastOffset)));
            }

            node.remove();
        }
    }
}
private fun replaceLinks(node: Node) {
    for (n in node.childNodes()) {
        replaceLinks(n);
    }

    if (node is Element && node.tag() == linkTag) {
        node.text(node.text().trim());
    }

    if (node is TextNode) {
        val text = node.text();
        var lastOffset = 0;
        var lastNode = node;

        val matches = urlRegex.findAll(text).toList();
        for (i in matches.indices) {
            val match = matches[i];

            val textBeforeNode = TextNode(text.substring(lastOffset, match.range.first));
            lastNode.after(textBeforeNode);
            lastNode = textBeforeNode;

            val attributes = Attributes();
            attributes.add("href", match.value);
            val linkNode = Element(linkTag, null, attributes);
            linkNode.text(match.value);
            lastNode.after(linkNode);
            lastNode = linkNode;

            lastOffset = match.range.last + 1;
        }

        if (lastOffset > 0) {
            if (lastOffset < text.length) {
                lastNode.after(TextNode(text.substring(lastOffset)));
            }

            node.remove();
        }
    }
}

fun ByteArray.toHexString(): String {
    return this.joinToString("") { "%02x".format(it) }
}

fun ByteArray.toHexString(size: Int): String {
    return this.sliceArray(IntRange(0, size)).toHexString();
}

private val safeCharacters = HashSet(('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('-', '_'));
fun String.toSafeFileName(): String {
    return this.map { if (it in safeCharacters) it else '_' }.joinToString(separator = "")
}

fun String.matchesDomain(queryDomain: String): Boolean {
    if(queryDomain.startsWith("."))
    //TODO: Should be safe, but double verify if can't be exploited
        return this.endsWith(queryDomain) || this == queryDomain.trimStart('.')
    else
        return this == queryDomain;
}