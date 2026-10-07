package app.restvolt.camperlog.data

import app.restvolt.camperlog.domain.TrackPoint
import app.restvolt.camperlog.domain.TrackRepository
import app.restvolt.camperlog.domain.TrackSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant

/** [TrackRepository] auf Basis von Room. */
class RoomTrackRepository(private val database: CamperLogDatabase) : TrackRepository {

    private val dao get() = database.trackPointDao()

    override fun observeForTour(tourId: Long): Flow<List<TrackPoint>> =
        dao.observeForTour(tourId).map { rows -> rows.map(TrackPointEntity::toDomain) }

    override fun observeSummary(tourId: Long): Flow<TrackSummary> =
        dao.observeSummary(tourId).map { TrackSummary(it.points, it.segments) }.distinctUntilChanged()

    override suspend fun allPoints(): List<TrackPoint> = dao.getAll().map(TrackPointEntity::toDomain)

    override suspend fun nextSegment(tourId: Long): Int = dao.maxSegment(tourId) + 1

    override suspend fun addAll(points: List<TrackPoint>) {
        if (points.isNotEmpty()) dao.insertAll(points.map(TrackPoint::toEntity))
    }

    override suspend fun deleteForTour(tourId: Long) = dao.deleteForTour(tourId)
}

internal fun TrackPointEntity.toDomain() = TrackPoint(
    id = id,
    tourId = tourId,
    segment = segment,
    recordedAt = Instant.ofEpochMilli(recordedAt),
    latitude = latitude,
    longitude = longitude,
    accuracyM = accuracyM,
    altitudeM = altitudeM,
)

internal fun TrackPoint.toEntity() = TrackPointEntity(
    id = id,
    tourId = tourId,
    segment = segment,
    recordedAt = recordedAt.toEpochMilli(),
    latitude = latitude,
    longitude = longitude,
    accuracyM = accuracyM,
    altitudeM = altitudeM,
)
