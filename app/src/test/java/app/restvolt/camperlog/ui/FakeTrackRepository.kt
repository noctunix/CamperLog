package app.restvolt.camperlog.ui

import app.restvolt.camperlog.domain.TrackPoint
import app.restvolt.camperlog.domain.TrackRepository
import app.restvolt.camperlog.domain.TrackSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Synchrones In-Memory-Repository für UI-Tests; die Room-Anbindung testet RoomTrackRepositoryTest. */
class FakeTrackRepository(initial: List<TrackPoint> = emptyList()) : TrackRepository {
    private val state = MutableStateFlow(initial)

    val points: List<TrackPoint> get() = state.value

    override fun observeForTour(tourId: Long): Flow<List<TrackPoint>> =
        state.map { list -> list.filter { it.tourId == tourId }.sortedWith(compareBy({ it.segment }, { it.recordedAt })) }

    override fun observeSummary(tourId: Long): Flow<TrackSummary> = state.map { list ->
        val own = list.filter { it.tourId == tourId }
        TrackSummary(own.size, own.map { it.segment }.distinct().size)
    }

    override suspend fun allPoints(): List<TrackPoint> = state.value

    override suspend fun nextSegment(tourId: Long): Int =
        (state.value.filter { it.tourId == tourId }.maxOfOrNull { it.segment } ?: 0) + 1

    override suspend fun addAll(points: List<TrackPoint>) {
        val existing = state.value.map { it.tourId to it.recordedAt }.toSet()
        state.value += points.filter { (it.tourId to it.recordedAt) !in existing }
    }

    override suspend fun deleteForTour(tourId: Long) {
        state.value = state.value.filter { it.tourId != tourId }
    }
}
