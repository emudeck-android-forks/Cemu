package info.cemu.cemu.common.android.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import java.io.File

object StoragePathResolver {
    fun resolveTreeUriToPath(context: Context, treeUri: Uri): String? {
        val documentFile = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        if (!documentFile.canWrite()) return null

        val docId = getTreeDocumentId(treeUri) ?: return null
        val split = docId.split(":", limit = 2)
        if (split.isEmpty()) return null

        val storageId = split[0]
        val subPath = if (split.size > 1) split[1] else ""

        val basePath = if (storageId == "primary") {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$storageId"
        }

        val fullPath = if (subPath.isNotEmpty()) "$basePath/$subPath" else basePath
        val file = File(fullPath)

        if (!file.exists() || !file.canWrite()) return null

        return fullPath
    }

    fun validatePath(path: String): Boolean {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return false

        val testFile = File(dir, ".cemu_write_test_${System.currentTimeMillis()}")
        return try {
            testFile.createNewFile() && testFile.delete()
        } catch (_: Exception) {
            false
        }
    }

    private fun getTreeDocumentId(treeUri: Uri): String? {
        val paths = treeUri.pathSegments
        if (paths.size >= 2 && paths[0] == "tree") {
            return paths[1]
        }
        return null
    }
}
