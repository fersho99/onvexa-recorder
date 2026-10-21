# Diagrama ER

```mermaid
erDiagram
    profiles ||--o{ devices : tiene
    profiles ||--o{ folders : tiene
    profiles ||--o{ tags : tiene
    profiles ||--o{ recordings : posee
    profiles |o--o{ templates : "propias"

    folders |o--o{ folders : "subcarpeta"
    folders |o--o{ recordings : contiene
    devices |o--o{ recordings : origen
    templates |o--o{ recordings : usa
    templates |o--o{ summaries : usa

    recordings ||--o{ recording_tags : etiquetada
    tags ||--o{ recording_tags : aplica
    recordings ||--o{ bookmarks : marca
    recordings ||--o{ speakers : participan
    recordings ||--o{ transcript_segments : transcribe
    speakers |o--o{ transcript_segments : habla
    recordings ||--o{ summaries : resume
    summaries |o--o{ action_items : origina
    recordings ||--o{ action_items : pendientes
    recordings ||--o{ mind_maps : mapa
    recordings ||--o{ attachments : imagenes
    recordings ||--o{ processing_jobs : cola

    recordings {
        uuid id PK
        enum source "watch | phone"
        enum status
        text language
        bigint duration_ms
        timestamptz started_at
        text audio_path
        tsvector search_vector
        timestamptz deleted_at
    }
    transcript_segments {
        uuid id PK
        int seq
        bigint start_ms
        bigint end_ms
        text text
    }
    summaries {
        uuid id PK
        text executive
        text detailed
        jsonb key_topics
        jsonb decisions
        jsonb open_questions
    }
    action_items {
        uuid id PK
        text description
        text assignee
        date due_date
        enum status
    }
    mind_maps {
        uuid id PK
        jsonb tree
        text markdown
    }
    attachments {
        uuid id PK
        text image_path
        bigint position_ms
        text description
    }
    processing_jobs {
        uuid id PK
        enum stage
        enum status
        int attempts
        text last_error
    }
```

## Notas

- Todas las tablas (salvo `templates` predefinidas) llevan `user_id` y RLS por `auth.uid()`.
- `recording_tags` y las tablas hijas validan además que la grabación padre sea del mismo usuario
  (política restrictiva `*_insert_parent_owned`).
- Audio e imágenes viven en Storage (`audio`, `attachments`), bajo la carpeta `{user_id}/`.
- La búsqueda local usa FTS4 de Room sobre `transcript_segments`; la remota, `recordings.search_vector`.
