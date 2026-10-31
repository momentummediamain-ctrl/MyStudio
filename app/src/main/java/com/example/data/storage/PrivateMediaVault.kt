package com.example.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.data.model.SecurityHashUtil
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Private Media Storage Vault.
 *
 * Ensures full binary media (images/videos) is NEVER stored inside Firestore documents.
 * Stores files inside app-private tenant-isolated directories (`filesDir/private_vault/users/{uid}/...`)
 * and returns lightweight `vault://users/{uid}/...` URIs along with SHA-256 content hashes.
 * Also supports immediate file purge when a persona or asset is deleted per retention policy.
 */
class PrivateMediaVault(context: Context) {
    private val appContext = context.applicationContext
    private val rootDir: File
        get() = File(appContext.filesDir, "private_vault").apply { if (!exists()) mkdirs() }

    data class StoredMediaRef(
        val storageUri: String,
        val sha256Hash: String,
        val byteSize: Long
    )

    fun saveBitmapToVault(
        userId: String,
        category: String,
        fileId: String,
        bitmap: Bitmap
    ): StoredMediaRef {
        require(userId.isNotBlank()) { "userId required for tenant-isolated vault storage" }
        val userDir = File(rootDir, "users/$userId/$category").apply { if (!exists()) mkdirs() }
        val targetFile = File(userDir, "$fileId.jpg")

        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bos)
        val bytes = bos.toByteArray()
        targetFile.writeBytes(bytes)

        val sha256 = SecurityHashUtil.sha256Hex(bytes)
        val uri = "vault://users/$userId/$category/$fileId.jpg"
        return StoredMediaRef(
            storageUri = uri,
            sha256Hash = sha256,
            byteSize = bytes.size.toLong()
        )
    }

    fun loadBitmapFromVault(userId: String, storageUri: String): Bitmap? {
        if (!storageUri.startsWith("vault://users/$userId/")) {
            return null // Enforce tenant isolation on file reads
        }
        val relativePath = storageUri.removePrefix("vault://")
        val targetFile = File(rootDir, relativePath)
        if (!targetFile.exists()) return null
        return BitmapFactory.decodeFile(targetFile.absolutePath)
    }

    fun purgeVaultUri(userId: String, storageUri: String): Boolean {
        if (!storageUri.startsWith("vault://users/$userId/")) {
            return false
        }
        val relativePath = storageUri.removePrefix("vault://")
        val targetFile = File(rootDir, relativePath)
        return if (targetFile.exists()) targetFile.delete() else false
    }
}
