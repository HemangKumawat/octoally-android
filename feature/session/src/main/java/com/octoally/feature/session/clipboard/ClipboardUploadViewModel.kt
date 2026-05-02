package com.octoally.feature.session.clipboard

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.octoally.core.network.api.UploadApi
import com.octoally.core.network.model.UploadRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Uploads images (from PickVisualMedia or a share-intent Uri) to the clipboard-
 * bridge sidecar on port 7799 and surfaces the returned server path.
 *
 * Protocol (confirmed from dashboard/public/assets/clipboard-bridge.js):
 *   POST /upload  { name, data }       — data is a full `data:<mime>;base64,<b64>` URL
 *   ← 200         { path: String }
 *
 * This module never touches the system clipboard or paste events — Android
 * TextField paste doesn't expose raw image bytes reliably across API levels.
 * Entry points are (1) the CommandBar attach button and (2) ACTION_SEND.
 */
@HiltViewModel
class ClipboardUploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uploadApi: UploadApi,
) : ViewModel() {

    private val _events = MutableSharedFlow<UploadEvent>(
        replay = 0,
        extraBufferCapacity = 8
    )
    val events: SharedFlow<UploadEvent> = _events.asSharedFlow()

    /**
     * Upload the image referenced by [uri]. Emits a sequence of
     * [UploadEvent.Uploading] → [UploadEvent.Success] or [UploadEvent.Failure].
     *
     * Non-image content types are rejected before any network call.
     */
    fun upload(uri: Uri) {
        viewModelScope.launch {
            _events.emit(UploadEvent.Uploading)
            val result = runCatching { performUpload(uri) }
            result
                .onSuccess { path -> _events.emit(UploadEvent.Success(path)) }
                .onFailure { err ->
                    _events.emit(UploadEvent.Failure(err.message ?: "Upload failed"))
                }
        }
    }

    private suspend fun performUpload(uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val detected = resolver.getType(uri)
        val mime = detected?.takeIf { it.startsWith("image/") }
            ?: if (detected == null) DEFAULT_MIME
            else throw IllegalArgumentException(NOT_AN_IMAGE_MSG)

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read image bytes")

        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val dataUrl = "data:$mime;base64,$b64"
        val name = "clipboard-${System.currentTimeMillis()}.${mimeToExt(mime)}"

        val response = uploadApi.upload(UploadRequest(name = name, data = dataUrl))
        response.path
    }

    private fun mimeToExt(mime: String): String = when (mime.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp"              -> "webp"
        "image/gif"               -> "gif"
        "image/heic"              -> "heic"
        "image/heif"              -> "heif"
        else                      -> "png"
    }

    companion object {
        private const val DEFAULT_MIME = "image/png"
        const val NOT_AN_IMAGE_MSG = "Only images can be attached"
    }
}

/** One-shot events emitted during an upload. */
sealed interface UploadEvent {
    data object Uploading : UploadEvent
    data class Success(val path: String) : UploadEvent
    data class Failure(val message: String) : UploadEvent
}
