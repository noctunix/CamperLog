package app.restvolt.camperlog.ui

import app.restvolt.camperlog.domain.FormDraftStore

class FakeFormDraftStore : FormDraftStore {
    private val drafts = mutableMapOf<Pair<String, Long>, String>()

    override suspend fun get(kind: String, recordId: Long): String? = drafts[kind to recordId]

    override suspend fun put(kind: String, recordId: Long, payload: String) {
        drafts[kind to recordId] = payload
    }

    override suspend fun delete(kind: String, recordId: Long) {
        drafts.remove(kind to recordId)
    }
}
