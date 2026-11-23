package com.anatoliy.filemanager

import android.util.Base64
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

object Fb2 {
    private fun open(file: File): InputStream? {
        val input = file.inputStream().buffered()
        if (!file.name.endsWith(".zip", ignoreCase = true)) return input
        val zip = ZipInputStream(input)
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.name.endsWith(".fb2", ignoreCase = true)) return zip
        }
        zip.close()
        return null
    }

    fun coverBytes(file: File): ByteArray? = runCatching {
        open(file)?.use { input ->
            val p = Xml.newPullParser()
            runCatching { p.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
            p.setInput(input, null)
            var coverId: String? = null
            var inCoverPage = false
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (p.name) {
                        "coverpage" -> inCoverPage = true
                        "image" -> if (inCoverPage && coverId == null) {
                            for (i in 0 until p.attributeCount) {
                                if (p.getAttributeName(i).endsWith("href")) {
                                    coverId = p.getAttributeValue(i).removePrefix("#")
                                }
                            }
                        }
                        "binary" -> {
                            val id = p.getAttributeValue(null, "id")
                            if (coverId == null || id == coverId) {
                                return@use Base64.decode(p.nextText(), Base64.DEFAULT)
                            }
                        }
                    }
                } else if (event == XmlPullParser.END_TAG && p.name == "coverpage") {
                    inCoverPage = false
                }
                event = p.next()
            }
            null
        }
    }.getOrNull()
}
