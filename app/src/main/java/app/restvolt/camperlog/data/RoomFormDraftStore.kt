package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.FormDraftStore

class RoomFormDraftStore(
    private val dao: FormDraftDao,
    private val now: () -> Long = System::currentTimeMillis,
) : FormDraftStore {
    override suspend fun get(kind: String, recordId: Long): String? = dao.get(kind, recordId)

    override suspend fun put(kind: String, recordId: Long, payload: String) {
        dao.put(FormDraftEntity(kind, recordId, payload, now()))
    }

    override suspend fun delete(kind: String, recordId: Long) = dao.delete(kind, recordId)
}
