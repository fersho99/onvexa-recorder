package com.onvexa.recorder.app.data

import com.onvexa.recorder.core.database.dao.OrganizationDao
import com.onvexa.recorder.core.database.entity.SyncState
import com.onvexa.recorder.core.database.entity.TemplateEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Plantillas predefinidas. Los ids coinciden con los de supabase/migrations/0003. */
object BuiltInTemplates {
    const val MEETING = "00000000-0000-4000-8000-000000000001"
    const val CLASS = "00000000-0000-4000-8000-000000000002"
    const val INTERVIEW = "00000000-0000-4000-8000-000000000003"
    const val SALES = "00000000-0000-4000-8000-000000000004"
    const val PERSONAL = "00000000-0000-4000-8000-000000000005"

    private fun template(id: String, slug: String, es: String, en: String, prompt: String) =
        TemplateEntity(
            id = id,
            slug = slug,
            nameEs = es,
            nameEn = en,
            systemPrompt = prompt,
            isBuiltIn = true,
            updatedAt = 0,
            syncState = SyncState.SYNCED,
        )

    val all = listOf(
        template(MEETING, "meeting", "Junta de trabajo", "Work meeting",
            "Resume una junta de trabajo. Prioriza decisiones, responsables, fechas y temas pendientes."),
        template(CLASS, "class", "Clase", "Class",
            "Resume una clase. Prioriza conceptos clave, definiciones, ejemplos y tareas asignadas."),
        template(INTERVIEW, "interview", "Entrevista", "Interview",
            "Resume una entrevista. Separa preguntas y respuestas, y destaca citas y conclusiones relevantes."),
        template(SALES, "sales", "Llamada de ventas", "Sales call",
            "Resume una llamada de ventas. Prioriza necesidades del cliente, objeciones, presupuesto, plazos y siguientes pasos."),
        template(PERSONAL, "personal", "Nota personal", "Personal note",
            "Resume una nota personal. Conserva ideas, recordatorios y pendientes sin formalidad innecesaria."),
    )
}

@Singleton
class TemplateBootstrap @Inject constructor(private val dao: OrganizationDao) {
    suspend fun ensure() = dao.upsertTemplates(BuiltInTemplates.all)
}
