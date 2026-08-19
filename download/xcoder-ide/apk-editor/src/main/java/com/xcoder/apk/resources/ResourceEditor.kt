package com.xcoder.apk.resources

import android.content.res.XmlResourceParser
import android.graphics.Color
import android.util.Log
import org.w3c.dom.*
import org.xml.sax.InputSource
import java.io.*
import java.util.zip.*
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

@Singleton
class ResourceEditor @Inject constructor() {

    data class ApkResource(
        val path: String,
        val type: ResourceType,
        val name: String,
        val value: String = "",
        val isModified: Boolean = false
    )

    enum class ResourceType(val folder: String, val displayName: String) {
        LAYOUT("layout", "Layouts"),
        DRAWABLE("drawable", "Drawables"),
        VALUES("values", "Values"),
        RAW("raw", "Raw"),
        ANIM("anim", "Animations"),
        COLOR("color", "Colors"),
        MENU("menu", "Menus"),
        MIPMAP("mipmap", "Mipmaps"),
        XML("xml", "XML"),
        FONT("font", "Fonts"),
        OTHER("other", "Other")
    }

    data class AndroidManifest(
        val packageName: String = "",
        val versionName: String = "",
        val versionCode: String = "",
        val minSdkVersion: String = "",
        val targetSdkVersion: String = "",
        val permissions: List<String> = emptyList(),
        val activities: List<String> = emptyList(),
        val services: List<String> = emptyList(),
        val receivers: List<String> = emptyList(),
        val providers: List<String> = emptyList(),
        val applicationLabel: String = ""
    )

    fun listResources(apkPath: String): List<ApkResource> {
        val resources = mutableListOf<ApkResource>()
        try {
            ZipFile(apkPath).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith("res/") }
                    .forEach { entry ->
                        val parts = entry.name.removePrefix("res/").split("/")
                        val folderName = parts.getOrNull(0) ?: "other"
                        val fileName = parts.drop(1).joinToString("/")
                        val type = ResourceType.values().find { folderName.startsWith(it.folder) } ?: ResourceType.OTHER
                        resources.add(ApkResource(
                            path = entry.name,
                            type = type,
                            name = fileName,
                            value = "${entry.size} bytes"
                        ))
                    }
            }
        } catch (e: Exception) { Log.e(TAG, "Error listing resources", e) }
        return resources.sortedWith(compareBy<ApkResource> { it.type.ordinal }.thenBy { it.name.lowercase() })
    }

    fun readResource(apkPath: String, resourcePath: String): String {
        val buf = StringBuilder()
        ZipFile(apkPath).use { zip ->
            zip.getEntry(resourcePath)?.let { entry ->
                zip.getInputStream(entry).use { inp -> BufferedReader(InputStreamReader(inp, "UTF-8")).use { it.forEachLine { buf.appendLine(it) } } }
            }
        }
        return buf.toString().trimEnd()
    }

    fun parseManifest(apkPath: String): AndroidManifest {
        var manifest = AndroidManifest()
        try {
            val xml = readResource(apkPath, "AndroidManifest.xml")
            val dbf = DocumentBuilderFactory.newInstance()
            val doc = dbf.newDocumentBuilder().parse(InputSource(StringReader(xml)))
            manifest = manifest.copy(
                packageName = doc.documentElement.getAttribute("package") ?: "",
                versionName = doc.documentElement.getAttribute("android:versionName") ?: "",
                versionCode = doc.documentElement.getAttribute("android:versionCode") ?: ""
            )
            val appNode = doc.getElementsByTagName("application").item(0) as? Element
            if (appNode != null) {
                val usesSdk = doc.getElementsByTagName("uses-sdk").item(0) as? Element
                if (usesSdk != null) {
                    manifest = manifest.copy(
                        minSdkVersion = usesSdk.getAttribute("android:minSdkVersion"),
                        targetSdkVersion = usesSdk.getAttribute("android:targetSdkVersion")
                    )
                }
                val permissions = mutableListOf<String>()
                val permNodes = doc.getElementsByTagName("uses-permission")
                for (i in 0 until permNodes.length) {
                    val perm = (permNodes.item(i) as Element).getAttribute("android:name")
                    if (perm.isNotBlank()) permissions.add(perm)
                }
                val activities = mutableListOf<String>()
                doc.getElementsByTagName("activity").let { nodes ->
                    for (i in 0 until nodes.length) {
                        val name = (nodes.item(i) as Element).getAttribute("android:name")
                        if (name.isNotBlank()) activities.add(name)
                    }
                }
                manifest = manifest.copy(permissions = permissions, activities = activities)
            }
        } catch (e: Exception) { Log.e(TAG, "Error parsing manifest", e) }
        return manifest
    }

    fun modifyManifestField(apkPath: String, outputApkPath: String, modifications: Map<String, String>): Boolean {
        return try {
            ZipFile(apkPath).use { zip ->
                ZipOutputStream(FileOutputStream(outputApkPath)).use { zos ->
                    zip.entries().asSequence().forEach { entry ->
                        if (entry.name == "AndroidManifest.xml" && modifications.isNotEmpty()) {
                            val content = readResource(apkPath, "AndroidManifest.xml")
                            var modified = content
                            modifications.forEach { (key, value) -> modified = modified.replace(key, value) }
                            val entryOut = ZipEntry(entry.name)
                            zos.putNextEntry(entryOut)
                            zos.write(modified.toByteArray(Charsets.UTF_8))
                            zos.closeEntry()
                        } else {
                            zos.putNextEntry(ZipEntry(entry.name))
                            if (!entry.isDirectory) zip.getInputStream(entry).copyTo(zos)
                            zos.closeEntry()
                        }
                    }
                }
            }
            true
        } catch (e: Exception) { Log.e(TAG, "Error modifying manifest", e); false }
    }

    fun extractResource(apkPath: String, resourcePath: String, outputPath: String): Boolean {
        return try {
            ZipFile(apkPath).use { zip ->
                zip.getEntry(resourcePath)?.let { entry ->
                    File(outputPath).parentFile?.mkdirs()
                    zip.getInputStream(entry).use { inp -> FileOutputStream(outputPath).use { out -> inp.copyTo(out) } }
                    true
                } ?: false
            }
        } catch (e: Exception) { Log.e(TAG, "Error extracting resource", e); false }
    }

    fun replaceResource(apkPath: String, resourcePath: String, newFilePath: String, outputApkPath: String): Boolean {
        return try {
            ZipFile(apkPath).use { zip ->
                ZipOutputStream(FileOutputStream(outputApkPath)).use { zos ->
                    zip.entries().asSequence().forEach { entry ->
                        if (entry.name == resourcePath) {
                            zos.putNextEntry(ZipEntry(entry.name))
                            FileInputStream(newFilePath).use { inp -> inp.copyTo(zos) }
                            zos.closeEntry()
                        } else {
                            zos.putNextEntry(ZipEntry(entry.name))
                            if (!entry.isDirectory) zip.getInputStream(entry).copyTo(zos)
                            zos.closeEntry()
                        }
                    }
                }
            }
            true
        } catch (e: Exception) { Log.e(TAG, "Error replacing resource", e); false }
    }

    fun listIconResources(apkPath: String): List<ApkResource> = listResources(apkPath).filter { it.type == ResourceType.MIPMAP || (it.type == ResourceType.DRAWABLE && it.name.contains("icon", ignoreCase = true)) }

    companion object { const val TAG = "ResourceEditor" }
}