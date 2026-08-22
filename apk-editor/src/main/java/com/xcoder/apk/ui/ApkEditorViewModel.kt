package com.xcoder.apk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xcoder.apk.dex.DexEditor
import com.xcoder.apk.resources.*
import com.xcoder.apk.signing.ApkSigner
import com.xcoder.apk.smali.SmaliEditor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ApkEditorViewModel @Inject constructor(
    private val dexEditor: DexEditor,
    private val resourceEditor: ResourceEditor,
    private val apkSigner: ApkSigner,
    private val smaliEditor: SmaliEditor
) : ViewModel() {

    data class ApkEditorState(
        val apkPath: String = "",
        val isLoaded: Boolean = false,
        val isLoading: Boolean = false,
        val manifest: AndroidManifest? = null,
        val resources: List<ApkResource> = emptyList(),
        val filteredResources: List<ApkResource> = emptyList(),
        val dexClasses: List<DexEditor.DexClass> = emptyList(),
        val isSigned: Boolean = false,
        val signingInfo: Map<String, String> = emptyMap(),
        val selectedResourceType: ResourceEditor.ResourceType? = null,
        val searchQuery: String = "",
        val error: String? = null,
        val currentTab: ApkTab = ApkTab.MANIFEST
    )

    enum class ApkTab { MANIFEST, RESOURCES, DEX, SMALI, SIGNING }

    private val _state = MutableStateFlow(ApkEditorState())
    val state: StateFlow<ApkEditorState> = _state.asStateFlow()

    fun loadApk(apkPath: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, apkPath = apkPath, error = null)
            try {
                val manifest = resourceEditor.parseManifest(apkPath)
                val resources = resourceEditor.listResources(apkPath)
                val dexFiles = dexEditor.extractDexFromApk(apkPath, File(apkPath).parent + "/xcoder_dex_temp")
                val allClasses = dexFiles.flatMap { dexEditor.parseDexFile(it).classes }
                val isSigned = apkSigner.verifySignature(apkPath)
                val certs = apkSigner.extractSigningCertificates(apkPath)
                val signingInfo = if (certs.isNotEmpty()) certs.first() else emptyMap()
                File(apkPath).parent?.let { File(it, "xcoder_dex_temp").deleteRecursively() }
                _state.value = _state.value.copy(
                    isLoaded = true, isLoading = false,
                    manifest = manifest, resources = resources,
                    filteredResources = resources, dexClasses = allClasses,
                    isSigned = isSigned, signingInfo = signingInfo
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Failed to load APK: ${e.message}")
            }
        }
    }

    fun filterResources(type: ResourceEditor.ResourceType?) {
        val state = _state.value
        _state.value = state.copy(
            selectedResourceType = type,
            filteredResources = if (type == null) state.resources else state.resources.filter { it.type == type }
        )
    }

    fun searchResources(query: String) {
        _state.value = _state.value.copy(searchQuery = query, filteredResources = if (query.isBlank()) _state.value.resources else _state.value.resources.filter { it.name.contains(query, ignoreCase = true) })
    }

    fun setTab(tab: ApkTab) { _state.value = _state.value.copy(currentTab = tab) }
}
