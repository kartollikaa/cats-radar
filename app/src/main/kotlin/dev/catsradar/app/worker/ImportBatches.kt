package dev.catsradar.app.worker

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.util.UUID

/** The photos each import run was handed, kept on disk under the run's work id. */
internal class ImportBatches(context: Context) {

    private val directory = File(context.noBackupFilesDir, "import-batches")

    /** Stores [uris] as run [runId]'s batch; an earlier run's batch goes, as that run is being replaced. */
    fun replaceWith(runId: UUID, uris: List<String>) {
        directory.listFiles()?.forEach(File::delete)
        directory.mkdirs()
        fileOf(runId).writeText(JSONArray(uris).toString())
    }

    /** Empty when run [runId] has no batch stored. */
    fun read(runId: UUID): List<String> {
        val file = fileOf(runId)
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        return List(array.length(), array::getString)
    }

    fun delete(runId: UUID) {
        fileOf(runId).delete()
    }

    private fun fileOf(runId: UUID) = File(directory, runId.toString())
}
