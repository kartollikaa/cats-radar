package dev.catsradar.app.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import dev.catsradar.app.notification.ImportNotifier
import dev.catsradar.app.reporting.NonFatalReporter
import dev.catsradar.app.reporting.RecordingNonFatalReporter
import dev.catsradar.domain.usecase.AttachLocation
import dev.catsradar.domain.usecase.ExportBackup
import dev.catsradar.domain.usecase.ImportBackup
import dev.catsradar.domain.usecase.PurgeDeleted
import dev.catsradar.domain.usecase.ResolvePendingPlaces
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock

private inline fun <reified T : Any> failing(error: Throwable): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, _, _ -> throw error } as T

@RunWith(AndroidJUnit4::class)
class WorkerFailureReportingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val reporter = RecordingNonFatalReporter()
    private val broken = IllegalStateException("database is gone")
    private val withUri = Data.Builder().putString(BackupWork.KEY_URI, "content://backups/cats.zip").build()
    private val withEncounter = Data.Builder().putString(AttachLocationWorker.KEY_ENCOUNTER_ID, "cat-1").build()
    private val withPhoto = Data.Builder().putStringArray(ImportPhotosWorker.KEY_URIS, arrayOf("content://1")).build()

    private fun useCasesFailingWith(error: Throwable) = module {
        single { AttachLocation(failing(error), failing(error), failing(error), Clock.System) }
        single { ExportBackup(failing(error), failing(error), failing(error), failing(error)) }
        single { ImportBackup(failing(error), failing(error), failing(error), failing(error), failing(error)) }
        single { ResolvePendingPlaces(failing(error), failing(error), Clock.System) }
        single { PurgeDeleted(failing(error), failing(error), Clock.System) }
        single<NonFatalReporter> { reporter }
    }

    private inline fun <reified W : CoroutineWorker> worker(
        error: Throwable,
        input: Data = Data.EMPTY,
        attempt: Int = 0,
    ): CoroutineWorker = TestListenableWorkerBuilder<W>(context)
        .setInputData(input)
        .setRunAttemptCount(attempt)
        .setWorkerFactory(KoinWorkerFactory(koinApplication { modules(useCasesFailingWith(error)) }.koin))
        .build()

    private fun importWorker(error: Throwable): CoroutineWorker =
        TestListenableWorkerBuilder<ImportPhotosWorker>(context)
            .setInputData(withPhoto)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = ImportPhotosWorker(
                        appContext,
                        workerParameters,
                        { _, _ -> throw error },
                        ImportNotifier(appContext),
                        reporter,
                    )
                },
            )
            .build()

    @Test
    fun `an unexpected failure attaching a location is recorded and fails the work`() = runTest {
        val result = worker<AttachLocationWorker>(broken, withEncounter).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(listOf<Throwable>(broken), reporter.recorded)
    }

    @Test
    fun `an unexpected failure importing photos is recorded and fails the work`() = runTest {
        val result = importWorker(broken).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(listOf<Throwable>(broken), reporter.recorded)
    }

    @Test
    fun `an unexpected failure exporting a backup is recorded and fails the work`() = runTest {
        val result = worker<ExportBackupWorker>(broken, withUri).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(listOf<Throwable>(broken), reporter.recorded)
    }

    @Test
    fun `an unexpected failure importing a backup is recorded and fails the work`() = runTest {
        val result = worker<ImportBackupWorker>(broken, withUri).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(listOf<Throwable>(broken), reporter.recorded)
    }

    @Test
    fun `an unexpected failure naming places is recorded on the first attempt and retried`() = runTest {
        val result = worker<GeocodePendingCellsWorker>(broken).doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(listOf<Throwable>(broken), reporter.recorded)
    }

    @Test
    fun `a retried failure naming places is not recorded again`() = runTest {
        val result = worker<GeocodePendingCellsWorker>(broken, attempt = 1).doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(emptyList(), reporter.recorded)
    }

    @Test
    fun `an unexpected failure purging deleted cats is recorded on the first attempt and retried`() = runTest {
        val result = worker<PurgeDeletedWorker>(broken).doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(listOf<Throwable>(broken), reporter.recorded)
    }

    @Test
    fun `a retried failure purging deleted cats is not recorded again`() = runTest {
        val result = worker<PurgeDeletedWorker>(broken, attempt = 1).doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(emptyList(), reporter.recorded)
    }

    @Test
    fun `a cancelled worker records nothing`() = runTest {
        val cancelled = CancellationException("stopped by WorkManager")
        val workers = listOf(
            worker<AttachLocationWorker>(cancelled, withEncounter),
            importWorker(cancelled),
            worker<ExportBackupWorker>(cancelled, withUri),
            worker<ImportBackupWorker>(cancelled, withUri),
            worker<GeocodePendingCellsWorker>(cancelled),
            worker<PurgeDeletedWorker>(cancelled),
        )

        workers.forEach { worker ->
            assertFailsWith<CancellationException> { worker.doWork() }
        }
        assertEquals(emptyList(), reporter.recorded)
    }
}
