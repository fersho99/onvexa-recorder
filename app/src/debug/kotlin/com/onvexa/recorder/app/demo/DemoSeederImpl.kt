package com.onvexa.recorder.app.demo

import android.content.Context
import com.onvexa.recorder.app.data.BuiltInTemplates
import com.onvexa.recorder.app.data.TemplateBootstrap
import com.onvexa.recorder.core.database.dao.AttachmentDao
import com.onvexa.recorder.core.database.dao.BookmarkDao
import com.onvexa.recorder.core.database.dao.ChatDao
import com.onvexa.recorder.core.database.dao.NoteDao
import com.onvexa.recorder.core.database.dao.OrganizationDao
import com.onvexa.recorder.core.database.dao.RecordingDao
import com.onvexa.recorder.core.database.dao.SummaryDao
import com.onvexa.recorder.core.database.dao.TranscriptDao
import com.onvexa.recorder.core.database.entity.ActionItemEntity
import com.onvexa.recorder.core.database.entity.AttachmentEntity
import com.onvexa.recorder.core.database.entity.BookmarkEntity
import com.onvexa.recorder.core.database.entity.ChatMessageEntity
import com.onvexa.recorder.core.database.entity.FolderEntity
import com.onvexa.recorder.core.database.entity.MindMapEntity
import com.onvexa.recorder.core.database.entity.NoteEntity
import com.onvexa.recorder.core.database.entity.RecordingEntity
import com.onvexa.recorder.core.database.entity.RecordingTagCrossRef
import com.onvexa.recorder.core.database.entity.SpeakerEntity
import com.onvexa.recorder.core.database.entity.SummaryEntity
import com.onvexa.recorder.core.database.entity.SyncState
import com.onvexa.recorder.core.database.entity.TagEntity
import com.onvexa.recorder.core.database.entity.TranscriptSegmentEntity
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Datos de ejemplo. Todos los ids empiezan con "demo-" para poder quitarlos sin tocar nada real. */
class DemoSeederImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingDao: RecordingDao,
    private val transcriptDao: TranscriptDao,
    private val summaryDao: SummaryDao,
    private val bookmarkDao: BookmarkDao,
    private val attachmentDao: AttachmentDao,
    private val organizationDao: OrganizationDao,
    private val noteDao: NoteDao,
    private val chatDao: ChatDao,
    private val templates: TemplateBootstrap,
) : DemoSeeder {

    override val available: Boolean = true

    private val prefs get() = context.getSharedPreferences("demo", Context.MODE_PRIVATE)

    override suspend fun seedIfFirstRun() {
        if (!prefs.getBoolean(KEY_SEEDED, false)) seed()
    }

    override suspend fun seed() = withContext(Dispatchers.IO) {
        templates.ensure()
        val now = System.currentTimeMillis()

        organizationDao.upsertFolder(folder("work", "Trabajo", now))
        organizationDao.upsertFolder(folder("school", "Escuela", now))
        organizationDao.upsertFolder(folder("personal", "Personal", now))
        listOf("trabajo", "juntas", "ventas", "escuela", "ideas").forEach {
            organizationDao.upsertTag(TagEntity("$P-tag-$it", it, null, now, now, null, SyncState.SYNCED))
        }

        seedWeeklyMeeting(now)
        seedClientCall(now)
        seedClass(now)
        seedInterview(now)
        seedProcessing(now)
        seedFailed(now)
        seedGlobalChat(now)

        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        recordingDao.deleteByIdPrefix("$P-")
        organizationDao.deleteFoldersByIdPrefix("$P-")
        organizationDao.deleteTagsByIdPrefix("$P-")
    }

    // ----------------------------------------------------------------------------------------
    // 1. Junta semanal (reloj): la grabación completa, con todas las secciones
    // ----------------------------------------------------------------------------------------
    private suspend fun seedWeeklyMeeting(now: Long) {
        val id = "$P-rec-meeting"
        recordingDao.upsert(
            recording(id, "Junta semanal de Tecnología", "watch", "ready", 18 * MIN + 42_000, now - 2 * HOUR,
                "$P-folder-work", BuiltInTemplates.MEETING),
        )
        tag(id, "trabajo"); tag(id, "juntas")

        val ana = "$id-s1"; val luis = "$id-s2"; val third = "$id-s3"
        transcriptDao.upsertSpeakers(
            listOf(speaker(ana, id, "Hablante 1", "Ana", now), speaker(luis, id, "Hablante 2", null, now),
                speaker(third, id, "Hablante 3", null, now)),
        )
        transcriptDao.upsertSegments(
            listOf(
                segment(id, 0, 0, 14_000, ana, "Buenos días a todos. Empecemos revisando el avance de los proyectos de esta semana."),
                segment(id, 1, 14_000, 41_000, luis, "La sincronización entre el reloj y el celular va en un setenta por ciento. Falta la parte de reintentos cuando se corta la conexión."),
                segment(id, 2, 41_000, 95_000, ana, "Perfecto. ¿Y el diseño de las pantallas? Necesito saber si ya podemos probarlo en un dispositivo real."),
                segment(id, 3, 95_000, 160_000, third, "El diseño está listo. Solo falta ajustar el contraste del texto secundario, pero ya se puede instalar y recorrer."),
                segment(id, 4, 160_000, 330_000, luis, "Mi preocupación es la batería del reloj. Si grabamos veinte minutos seguidos hay que medir cuánto consume el micrófono."),
                segment(id, 5, 330_000, 610_000, ana, "Entonces la decisión es priorizar las pruebas en dispositivo la próxima semana, antes de agregar funciones nuevas."),
                segment(id, 6, 610_000, 890_000, third, "De acuerdo. Yo me encargo de compilar la siguiente etapa y mandar las capturas del reloj."),
                segment(id, 7, 890_000, 1_122_000, ana, "Quedamos así. Quedan pendientes definir las plantillas finales y revisar el aviso legal de grabación."),
            ),
        )
        summaryDao.upsertSummary(
            summary(
                "$id-sum1", id, BuiltInTemplates.MEETING,
                executive = "Se revisó el avance de los proyectos de Tecnología. La sincronización reloj-celular lleva 70% y el diseño está listo para probarse. Se decidió priorizar pruebas en dispositivo la próxima semana, con atención al consumo de batería del reloj.",
                detailed = "Avance\n- Sincronización reloj-celular al 70%; falta la lógica de reintentos.\n- Diseño de pantallas terminado; solo falta ajustar el contraste del texto secundario.\n\nRiesgos\n- El consumo de batería del micrófono en grabaciones de 20 minutos aún no está medido.\n\nAcuerdos\n- Dar prioridad a las pruebas en dispositivo antes de agregar funciones nuevas.\n- Compilar la siguiente etapa y compartir capturas del reloj.",
                topics = listOf("Sincronización reloj-celular", "Diseño de pantallas", "Batería del reloj", "Pruebas en dispositivo"),
                decisions = listOf("Priorizar pruebas en dispositivo la próxima semana", "No agregar funciones nuevas hasta validar la batería"),
                questions = listOf("¿Cuánto consume el micrófono en 20 minutos de grabación?", "¿Cuáles son las plantillas finales?"),
                now = now - 2 * HOUR,
            ),
        )
        summaryDao.upsertActionItems(
            listOf(
                action("$id-a1", id, "$id-sum1", "Compilar la siguiente etapa y mandar capturas del reloj", "Hablante 3", days(2), "open", 610_000, now),
                action("$id-a2", id, "$id-sum1", "Medir el consumo de batería en una grabación de 20 minutos", "Luis", days(5), "open", 160_000, now),
                action("$id-a3", id, "$id-sum1", "Ajustar el contraste del texto secundario", "Hablante 3", days(1), "done", 95_000, now),
                action("$id-a4", id, "$id-sum1", "Definir las plantillas finales y revisar el aviso legal", null, null, "open", 890_000, now),
            ),
        )
        summaryDao.upsertMindMap(
            MindMapEntity(
                "$id-map", id,
                treeJson = """{"title":"Junta de Tecnología","children":[
                    {"title":"Avance","children":[{"title":"Sincronización 70%"},{"title":"Diseño listo"}]},
                    {"title":"Decisiones","children":[{"title":"Pruebas en dispositivo"},{"title":"Sin funciones nuevas"}]},
                    {"title":"Pendientes","children":[{"title":"Compilar etapa"},{"title":"Medir batería"},{"title":"Definir plantillas"}]},
                    {"title":"Riesgos","children":[{"title":"Batería del reloj"}]}]}""",
                markdown = null, createdAt = now, updatedAt = now, syncState = SyncState.SYNCED,
            ),
        )
        bookmarkDao.upsertAll(
            listOf(
                BookmarkEntity("$id-b1", id, 95_000, "Diseño listo", now, now, null, SyncState.SYNCED),
                BookmarkEntity("$id-b2", id, 610_000, "Decisión: pruebas primero", now, now, null, SyncState.SYNCED),
            ),
        )
        noteDao.upsertAll(
            listOf(NoteEntity("$id-n1", id, 300_000, "Preguntar a Luis por las mediciones de batería del reloj anterior.", now, now, null, SyncState.SYNCED)),
        )
        attachmentDao.upsert(
            AttachmentEntity(
                "$id-img1", id, localImagePath = null, remoteImagePath = null, mimeType = "image/jpeg", positionMs = 420_000,
                description = "Pizarrón con el cronograma de entregas: etapa 1 pantallas, etapa 2 grabación en el celular, etapa 3 reloj completo. La etapa 3 aparece subrayada.",
                createdAt = now, updatedAt = now, deletedAt = null, syncState = SyncState.SYNCED,
            ),
        )
        chatDao.upsertAll(
            listOf(
                chat("$id-c1", id, "user", "¿Cuál era la preocupación principal con el reloj?", "[]", now - 1000),
                chat("$id-c2", id, "assistant",
                    "Luis planteó que lo más delicado es el consumo de batería del micrófono: si se graban veinte minutos seguidos hay que medir cuánto gasta antes de seguir con funciones nuevas.",
                    """[{"ms":160000,"text":"La batería del reloj"}]""", now),
            ),
        )
    }

    // ----------------------------------------------------------------------------------------
    // 2. Llamada con cliente (celular): dos resúmenes con distinta plantilla
    // ----------------------------------------------------------------------------------------
    private suspend fun seedClientCall(now: Long) {
        val id = "$P-rec-call"
        recordingDao.upsert(
            recording(id, "Llamada con cliente del hospital", "phone", "ready", 24 * MIN + 10_000, now - 26 * HOUR,
                "$P-folder-work", BuiltInTemplates.SALES),
        )
        tag(id, "ventas"); tag(id, "trabajo")
        val client = "$id-s1"; val me = "$id-s2"
        transcriptDao.upsertSpeakers(listOf(speaker(client, id, "Hablante 1", "Cliente", now), speaker(me, id, "Hablante 2", "Yo", now)))
        transcriptDao.upsertSegments(
            listOf(
                segment(id, 0, 0, 30_000, client, "Necesitamos un tablero que muestre las citas del día y los tiempos de espera por área."),
                segment(id, 1, 30_000, 120_000, me, "Podemos empezar con un panel de lectura sobre su base de datos actual, sin tocar el sistema que ya usan."),
                segment(id, 2, 120_000, 400_000, client, "El presupuesto lo aprueba la dirección el viernes. Nos interesaría ver una demostración antes."),
            ),
        )
        summaryDao.upsertSummary(
            summary("$id-sum1", id, BuiltInTemplates.SALES,
                executive = "El cliente necesita un tablero de citas y tiempos de espera. Se propuso un panel de solo lectura sobre su base actual y quedó pendiente agendar una demostración antes de que la dirección apruebe el presupuesto el viernes.",
                detailed = "Necesidades\n- Ver las citas del día y los tiempos de espera por área.\n\nObjeciones\n- Quieren una demostración antes de aprobar.\n\nSiguientes pasos\n- Agendar una demostración.\n- Enviar una propuesta con alcance y costo.",
                topics = listOf("Tablero de citas", "Tiempos de espera", "Presupuesto"),
                decisions = listOf("Empezar con un panel de solo lectura"),
                questions = listOf("¿Qué fecha tiene la demostración?"),
                now = now - 26 * HOUR),
        )
        summaryDao.upsertSummary(
            summary("$id-sum2", id, BuiltInTemplates.MEETING,
                executive = "Reunión de descubrimiento: se acordó un panel de solo lectura y una demostración previa a la aprobación de la dirección.",
                detailed = "- Alcance inicial: panel de lectura.\n- Decide la dirección el viernes.\n- Pendiente: demostración.",
                topics = listOf("Alcance", "Aprobación"), decisions = listOf("Panel de solo lectura"), questions = emptyList(),
                now = now - 26 * HOUR + 1000),
        )
        summaryDao.upsertActionItems(
            listOf(
                action("$id-a1", id, "$id-sum1", "Agendar la demostración con el cliente", "Yo", days(3), "open", 400_000, now),
                action("$id-a2", id, "$id-sum1", "Enviar propuesta con alcance y costo", "Yo", days(4), "open", 120_000, now),
            ),
        )
        summaryDao.upsertMindMap(
            MindMapEntity("$id-map", id,
                treeJson = """{"title":"Cliente del hospital","children":[{"title":"Necesidades","children":[{"title":"Citas del día"},{"title":"Tiempos de espera"}]},{"title":"Siguientes pasos","children":[{"title":"Demostración"},{"title":"Propuesta"}]}]}""",
                markdown = null, createdAt = now, updatedAt = now, syncState = SyncState.SYNCED),
        )
    }

    // ----------------------------------------------------------------------------------------
    // 3. Clase (reloj)
    // ----------------------------------------------------------------------------------------
    private suspend fun seedClass(now: Long) {
        val id = "$P-rec-class"
        recordingDao.upsert(
            recording(id, "Clase: Administración de proyectos TI", "watch", "ready", 52 * MIN + 8_000, now - 3 * DAY,
                "$P-folder-school", BuiltInTemplates.CLASS),
        )
        tag(id, "escuela")
        val teacher = "$id-s1"
        transcriptDao.upsertSpeakers(listOf(speaker(teacher, id, "Hablante 1", "Profesor", now)))
        transcriptDao.upsertSegments(
            listOf(
                segment(id, 0, 0, 60_000, teacher, "Hoy vamos a ver el triángulo de hierro: alcance, tiempo y costo, y cómo un cambio en uno afecta a los otros dos."),
                segment(id, 1, 60_000, 600_000, teacher, "Para la próxima clase entreguen el acta de constitución del proyecto integrador con objetivos y riesgos."),
            ),
        )
        summaryDao.upsertSummary(
            summary("$id-sum1", id, BuiltInTemplates.CLASS,
                executive = "Se explicó el triángulo de hierro (alcance, tiempo y costo) y cómo se afectan entre sí. Tarea: entregar el acta de constitución del proyecto integrador.",
                detailed = "Conceptos\n- Triángulo de hierro: alcance, tiempo, costo.\n- Un cambio en uno repercute en los otros dos.\n\nTarea\n- Acta de constitución con objetivos y riesgos.",
                topics = listOf("Triángulo de hierro", "Acta de constitución"), decisions = emptyList(),
                questions = listOf("¿Cómo se prioriza cuando hay conflicto entre tiempo y costo?"), now = now - 3 * DAY),
        )
        summaryDao.upsertActionItems(
            listOf(action("$id-a1", id, "$id-sum1", "Entregar el acta de constitución del proyecto integrador", "Yo", days(4), "open", 60_000, now)),
        )
        summaryDao.upsertMindMap(
            MindMapEntity("$id-map", id,
                treeJson = """{"title":"Administración de proyectos","children":[{"title":"Triángulo de hierro","children":[{"title":"Alcance"},{"title":"Tiempo"},{"title":"Costo"}]},{"title":"Tarea","children":[{"title":"Acta de constitución"}]}]}""",
                markdown = null, createdAt = now, updatedAt = now, syncState = SyncState.SYNCED),
        )
    }

    // ----------------------------------------------------------------------------------------
    // 4. Entrevista (celular): resumen sin tareas ni mapa
    // ----------------------------------------------------------------------------------------
    private suspend fun seedInterview(now: Long) {
        val id = "$P-rec-interview"
        recordingDao.upsert(
            recording(id, "Entrevista a candidato de desarrollo", "phone", "ready", 31 * MIN, now - 5 * DAY,
                null, BuiltInTemplates.INTERVIEW),
        )
        tag(id, "trabajo")
        val a = "$id-s1"
        transcriptDao.upsertSpeakers(listOf(speaker(a, id, "Hablante 1", null, now)))
        transcriptDao.upsertSegments(listOf(segment(id, 0, 0, 90_000, a, "Cuéntame sobre el último proyecto en el que trabajaste con bases de datos.")))
        summaryDao.upsertSummary(
            summary("$id-sum1", id, BuiltInTemplates.INTERVIEW,
                executive = "El candidato describió su experiencia con bases de datos relacionales y trabajo en equipo. Destacó su manejo de consultas y optimización.",
                detailed = "- Experiencia con bases de datos relacionales.\n- Buena comunicación al explicar decisiones técnicas.",
                topics = listOf("Bases de datos", "Trabajo en equipo"), decisions = emptyList(), questions = emptyList(), now = now - 5 * DAY),
        )
    }

    // ----------------------------------------------------------------------------------------
    // 5. En proceso (celular): se está transcribiendo
    // ----------------------------------------------------------------------------------------
    private suspend fun seedProcessing(now: Long) {
        val id = "$P-rec-processing"
        recordingDao.upsert(recording(id, "Idea para la app de subastas", "phone", "transcribing", 4 * MIN + 20_000, now - 20 * MIN, "$P-folder-personal", null))
        tag(id, "ideas")
    }

    // ----------------------------------------------------------------------------------------
    // 6. Fallida (reloj)
    // ----------------------------------------------------------------------------------------
    private suspend fun seedFailed(now: Long) {
        val id = "$P-rec-failed"
        recordingDao.upsert(recording(id, "", "watch", "failed", 38_000, now - 5 * HOUR, null, null))
    }

    private suspend fun seedGlobalChat(now: Long) {
        chatDao.upsertAll(
            listOf(
                chat("$P-chat-g1", null, "user", "¿Qué tareas tengo pendientes esta semana?", "[]", now - 2000),
                chat("$P-chat-g2", null, "assistant",
                    "Tienes cuatro pendientes abiertos: compilar la siguiente etapa, medir la batería del reloj, agendar la demostración con el cliente y entregar el acta de constitución del proyecto integrador.",
                    "[]", now - 1000),
            ),
        )
    }

    // ----------------------------------------------------------------------------------------
    // Constructores de filas
    // ----------------------------------------------------------------------------------------
    private suspend fun tag(recordingId: String, name: String) =
        organizationDao.addTag(RecordingTagCrossRef(recordingId, "$P-tag-$name", SyncState.SYNCED))

    private fun folder(key: String, name: String, now: Long) =
        FolderEntity("$P-folder-$key", name, null, now, now, null, SyncState.SYNCED)

    private fun recording(
        id: String, title: String, source: String, status: String, durationMs: Long, startedAt: Long,
        folderId: String?, templateId: String?,
    ) = RecordingEntity(
        id = id, deviceId = null, folderId = folderId, templateId = templateId, title = title, source = source,
        status = status, language = "es", durationMs = durationMs, startedAt = startedAt,
        localAudioPath = null, remoteAudioPath = null, audioBytes = null, audioSha256 = null,
        createdAt = startedAt, updatedAt = startedAt, syncState = SyncState.SYNCED,
    )

    private fun speaker(id: String, recordingId: String, label: String, name: String?, now: Long) =
        SpeakerEntity(id, recordingId, label, name, now, now, null, SyncState.SYNCED)

    private fun segment(recordingId: String, seq: Int, start: Long, end: Long, speakerId: String, text: String) =
        TranscriptSegmentEntity("$recordingId-seg$seq", recordingId, speakerId, seq, start, end, text, "es", 0, 0, null, SyncState.SYNCED)

    private fun summary(
        id: String, recordingId: String, templateId: String, executive: String, detailed: String,
        topics: List<String>, decisions: List<String>, questions: List<String>, now: Long,
    ) = SummaryEntity(
        id, recordingId, templateId, executive, detailed, json(topics), json(decisions), json(questions),
        model = "demo", promptVersion = "demo", createdAt = now, updatedAt = now, deletedAt = null, syncState = SyncState.SYNCED,
    )

    private fun action(
        id: String, recordingId: String, summaryId: String, text: String, assignee: String?, due: String?,
        status: String, sourceMs: Long, now: Long,
    ) = ActionItemEntity(id, recordingId, summaryId, text, assignee, due, status, sourceMs, now, now, null, SyncState.SYNCED)

    private fun chat(id: String, recordingId: String?, role: String, content: String, citations: String, at: Long) =
        ChatMessageEntity(id, recordingId, role, content, citations, at, at, null, SyncState.SYNCED)

    private fun json(items: List<String>) =
        items.joinToString(prefix = "[", postfix = "]") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }

    private fun days(n: Long): String = LocalDate.now().plusDays(n).toString()

    private companion object {
        const val P = "demo"
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
    abstract fun bindSeeder(impl: DemoSeederImpl): DemoSeeder
}
