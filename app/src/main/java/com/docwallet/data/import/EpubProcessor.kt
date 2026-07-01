package com.docwallet.data.import

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class EpubProcessor : DocumentProcessor {

    override suspend fun process(input: File, mimeType: String): ProcessorResult = withContext(Dispatchers.IO) {
        val zip = ZipFile(input)

        try {
            val containerEntry = zip.getEntry("META-INF/container.xml")
                ?: return@withContext ProcessorResult(input.nameWithoutExtension, "", 0, null, null)
            val containerXml = zip.getInputStream(containerEntry).readBytes()
            val opfPath = parseContainerXml(containerXml)
                ?: return@withContext ProcessorResult(input.nameWithoutExtension, "", 0, null, null)

            val opfEntry = zip.getEntry(opfPath)
                ?: return@withContext ProcessorResult(input.nameWithoutExtension, "", 0, null, null)
            val opfData = zip.getInputStream(opfEntry).readBytes()

            val opfDir = opfPath.substringBeforeLast('/', "").let { if (it.isNotEmpty()) "$it/" else "" }
            val (title, author, coverPath, spineItems) = parseOpf(opfData)

            val pageCount = spineItems.size

            val textContent = buildString {
                for (item in spineItems) {
                    val href = if (opfDir.isNotEmpty() && !item.startsWith("/")) "$opfDir$item" else item
                    val entry = zip.getEntry(href) ?: continue
                    val htmlData = zip.getInputStream(entry).readBytes()
                    val text = htmlData.decodeToString()
                    val stripped = text.replace(Regex("<[^>]*>"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    if (stripped.isNotEmpty()) {
                        appendLine(stripped)
                    }
                }
            }.takeIf { it.isNotBlank() }

            val thumbnailBitmap = coverPath?.let { cover ->
                val href = if (opfDir.isNotEmpty() && !cover.startsWith("/")) "$opfDir$cover" else cover
                val entry = zip.getEntry(href)
                if (entry != null) {
                    val data = zip.getInputStream(entry).readBytes()
                    BitmapFactory.decodeByteArray(data, 0, data.size)?.let { bmp ->
                        scaleToWidth(bmp, 200)
                    }
                } else null
            }

            ProcessorResult(
                title = title.takeIf { it.isNotBlank() } ?: input.nameWithoutExtension,
                author = author,
                pageCount = pageCount,
                textContent = textContent,
                thumbnailBitmap = thumbnailBitmap,
            )
        } finally {
            zip.close()
        }
    }

    private fun parseContainerXml(data: ByteArray): String? {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(data.inputStream())
        val root = doc.documentElement
        val rootfiles = root.getElementsByTagName("rootfile")
        if (rootfiles.length > 0) {
            return rootfiles.item(0).attributes.getNamedItem("full-path")?.textContent
        }
        return null
    }

    private data class OpfResult(
        val title: String,
        val author: String,
        val coverPath: String?,
        val spineItems: List<String>,
    )

    private fun parseOpf(data: ByteArray): OpfResult {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(data.inputStream())

        val title = doc.getElementsByTagName("dc:title")
            ?.item(0)
            ?.textContent
            ?: ""

        val author = doc.getElementsByTagName("dc:creator")
            ?.item(0)
            ?.textContent
            ?: ""

        val manifest = doc.getElementsByTagName("manifest")?.item(0)
        val idToHref = mutableMapOf<String, String>()
        val idToMediaType = mutableMapOf<String, String>()
        if (manifest != null) {
            val items = manifest.childNodes
            for (i in 0 until items.length) {
                val item = items.item(i)
                if (item.nodeName == "item") {
                    val attrs = item.attributes
                    val id = attrs.getNamedItem("id")?.textContent ?: continue
                    val href = attrs.getNamedItem("href")?.textContent ?: continue
                    val mediaType = attrs.getNamedItem("media-type")?.textContent ?: ""
                    idToHref[id] = href
                    idToMediaType[id] = mediaType
                }
            }
        }

        var coverPath: String? = null
        val metaNodes = doc.getElementsByTagName("meta")
        for (i in 0 until metaNodes.length) {
            val meta = metaNodes.item(i)
            val attrs = meta.attributes
            val name = attrs.getNamedItem("name")?.textContent
            val content = attrs.getNamedItem("content")?.textContent
            if (name == "cover" && content != null) {
                coverPath = idToHref[content]
            }
        }

        if (coverPath == null) {
            for ((id, mediaType) in idToMediaType) {
                if (mediaType.startsWith("image/")) {
                    coverPath = idToHref[id]
                    break
                }
            }
        }

        val spineItems = mutableListOf<String>()
        val spine = doc.getElementsByTagName("spine")?.item(0)
        if (spine != null) {
            val refs = spine.childNodes
            for (i in 0 until refs.length) {
                val ref = refs.item(i)
                if (ref.nodeName == "itemref") {
                    val idref = ref.attributes.getNamedItem("idref")?.textContent ?: continue
                    val href = idToHref[idref]
                    if (href != null) {
                        spineItems.add(href)
                    }
                }
            }
        }

        return OpfResult(title, author, coverPath, spineItems)
    }

    private fun scaleToWidth(source: Bitmap, targetWidth: Int): Bitmap {
        val targetHeight = (targetWidth * source.height) / source.width
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight.coerceAtLeast(1), true)
    }
}
