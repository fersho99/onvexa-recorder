package com.onvexa.recorder.wear.demo

import android.content.Context
import com.onvexa.recorder.core.database.dao.RecordingDao
import com.onvexa.recorder.core.database.dao.SummaryDao
import com.onvexa.recorder.core.database.entity.ActionItemEntity
import com.onvexa.recorder.core.database.entity.RecordingEntity
import com.onvexa.recorder.core.database.entity.SummaryEntity
import com.onvexa.recorder.core.database.entity.SyncState
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ejemplos para recorrer el reloj. Todos los ids empiezan con "demo-". */
class WatchDemoSeederImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingDao: RecordingDao,
    private val summaryDao: SummaryDao,
) : WatchDemoSeeder {

    private val prefs get() = context.getSharedPreferences("watch_demo", Context.MODE_PRIVATE)

    override suspend fun seedIfFirstRun() {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()

            val meeting = "demo-rec-meeting"
            recordingDao.upsert(rec(meeting, "Junta semanal", 18 * MIN + 42_000, now - 2 * HOUR, "ready", SyncState.SYNCED))
            summaryDao.upsertSummary(
                summary(
                    meeting,
                    "Se revisó el avance de Tecnología: la sincronización va al 70% y el diseño está listo para probarse. " +
                        "Se decidió priorizar las pruebas en dispositivo la próxima semana.",
                    now - 2 * HOUR,
                ),
            )
            summaryDao.upsertActionItems(
                listOf(
                    action(meeting, 1, "Compilar la siguiente etapa", "open", days(2), now),
                    action(meeting, 2, "Medir la batería en 20 minutos de grabación", "open", days(5), now),
                    action(meeting, 3, "Ajustar el contraste del texto", "done", days(1), now),
                ),
            )

            val call = "demo-rec-call"
            recordingDao.upsert(rec(call, "Llamada con cliente", 7 * MIN + 15_000, now - DAY - 3 * HOUR, "ready", SyncState.SYNCED))
            summaryDao.upsertSummary(
                summary(
                    call,
                    "El cliente pidió una cotización de dos retroexcavadoras con entrega en 15 días. " +
                        "Quedó pendiente confirmar el precio de flete.",
                    now - DAY - 3 * HOUR,
                ),
            )
            summaryDao.upsertActionItems(
                listOf(
                    action(call, 1, "Enviar cotización con flete", "open", days(1), now),
                    action(call, 2, "Confirmar disponibilidad de las unidades", "open", null, now),
                ),
            )

            recordingDao.upsert(rec("demo-rec-idea", "Idea para la app", 2 * MIN + 5_000, now - 25 * MIN, "recorded", SyncState.PENDING_UPSERT))
            recordingDao.upsert(rec("demo-rec-supplier", "Reunión con proveedor", 31 * MIN + 8_000, now - 4 * HOUR, "transcribing", SyncState.SYNCED))

            val klass = "demo-rec-class"
            recordingDao.upsert(rec(klass, "Clase de bases de datos", 25 * MIN + 10_000, now - 2 * DAY, "ready", SyncState.SYNCED))
            summaryDao.upsertSummary(
                summary(klass, "Se explicó la normalización hasta tercera forma normal con ejemplos de ventas e inventario.", now - 2 * DAY),
            )

            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
        }
    }

    private fun rec(id: String, title: String, durationMs: Long, startedAt: Long, status: String, sync: Int) = RecordingEntity(
        id = id, deviceId = null, folderId = null, templateId = null, title = title, source = "watch",
        status = status, language = "es", durationMs = durationMs, startedAt = startedAt,
        localAudioPath = null, remoteAudioPath = null, audioBytes = null, audioSha256 = null,
        createdAt = startedAt, updatedAt = startedAt, syncState = sync,
    )

    private fun summary(recordingId: String, executive: String, at: Long) = SummaryEntity(
        id = "$recordingId-sum", recordingId = recordingId, templateId = null, executive = executive,
        detailed = "", keyTopicsJson = "[]", decisionsJson = "[]", openQuestionsJson = "[]",
        model = "demo", promptVersion = "demo", createdAt = at, updatedAt = at, deletedAt = null, syncState = SyncState.SYNCED,
    )

    private fun action(recordingId: String, n: Int, text: String, status: String, due: String?, now: Long) = ActionItemEntity(
        id = "$recordingId-a$n", recordingId = recordingId, summaryId = "$recordingId-sum", description = text,
        assignee = null, dueDate = due, status = status, sourceMs = null,
        createdAt = now, updatedAt = now, deletedAt = null, syncState = SyncState.SYNCED,
    )

    private fun days(n: Long): String = LocalDate.now().plusDays(n).toString()

    private companion object {
        const val KEY_SEEDED = "seeded"
        const val MIN = 60_000L
        const val HOUR = 60 * MIN
        const val DAY = 24 * HOUR
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DemoModule {
    @Binds
    abstract fun bindSeeder(impl: WatchDemoSeederImpl): WatchDemoSeeder
}
