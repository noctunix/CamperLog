package app.restvolt.camperlog.ui.edit

import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import app.restvolt.camperlog.data.AttachmentImportError
import app.restvolt.camperlog.data.AttachmentImportResult
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.ui.FakeAttachmentFileStore
import app.restvolt.camperlog.ui.FakeAttachmentRepository
import app.restvolt.camperlog.ui.FakeStationRepository
import app.restvolt.camperlog.ui.FakeTourRepository
import app.restvolt.camperlog.ui.FakeVehicleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Fotos, die vor dem ersten Speichern einer neuen Station aufgenommen werden: Zwischenspeicher im
 * Formular, Übernahme in die Datenbank nach dem ersten Speichern, Aufräumen ohne Speichern.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditStationViewModelPendingPhotosTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val fakeUri = "content://fake/photo.jpg".toUri()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        attachments: FakeAttachmentRepository = FakeAttachmentRepository(),
        fileStore: FakeAttachmentFileStore = FakeAttachmentFileStore(),
        stations: FakeStationRepository = FakeStationRepository(),
    ) = EditStationViewModel(
        repository = stations,
        tours = FakeTourRepository(),
        vehicles = FakeVehicleRepository(),
        attachments = attachments,
        fileStore = fileStore,
        stationId = 0,
        savedStateHandle = SavedStateHandle(),
    )

    @Test
    fun onAddPendingPhoto_success_addsItToPendingPhotos() {
        val viewModel = viewModel()

        viewModel.onAddPendingPhoto(fakeUri)

        assertEquals(1, viewModel.uiState.value.pendingPhotos.size)
        assertEquals(false, viewModel.uiState.value.pendingPhotoImporting)
        assertEquals(null, viewModel.uiState.value.pendingPhotoImportError)
    }

    @Test
    fun onAddPendingPhoto_failure_setsTypedErrorWithoutAddingAPhoto() {
        val fileStore = FakeAttachmentFileStore()
        fileStore.nextPhotoResult = { AttachmentImportResult.Failure(AttachmentImportError.TOO_LARGE) }
        val viewModel = viewModel(fileStore = fileStore)

        viewModel.onAddPendingPhoto(fakeUri)

        assertTrue(viewModel.uiState.value.pendingPhotos.isEmpty())
        assertEquals(AttachmentImportError.TOO_LARGE, viewModel.uiState.value.pendingPhotoImportError)

        viewModel.onDismissPendingPhotoImportError()
        assertEquals(null, viewModel.uiState.value.pendingPhotoImportError)
    }

    @Test
    fun onRemovePendingPhoto_removesItAndDeletesItsFile() {
        val fileStore = FakeAttachmentFileStore()
        val viewModel = viewModel(fileStore = fileStore)
        viewModel.onAddPendingPhoto(fakeUri)
        val photo = viewModel.uiState.value.pendingPhotos.single()

        viewModel.onRemovePendingPhoto(photo)

        assertTrue(viewModel.uiState.value.pendingPhotos.isEmpty())
        assertEquals(listOf(photo.fileName), fileStore.deleted)
    }

    @Test
    fun onPendingPhotoCaptionChange_changesOnlyThatPhotosCaption() {
        val viewModel = viewModel()
        viewModel.onAddPendingPhoto(fakeUri)
        val photo = viewModel.uiState.value.pendingPhotos.single()

        viewModel.onPendingPhotoCaptionChange(photo, "Sonnenuntergang")

        assertEquals("Sonnenuntergang", viewModel.uiState.value.pendingPhotos.single().caption)
    }

    @Test
    fun onPendingPhotoUseLocation_setsCoordinatesAndWritesExif() {
        val fileStore = FakeAttachmentFileStore()
        val viewModel = viewModel(fileStore = fileStore)
        viewModel.onAddPendingPhoto(fakeUri)
        val photo = viewModel.uiState.value.pendingPhotos.single()

        viewModel.onPendingPhotoUseLocation(photo, 48.0, 11.0)

        val updated = viewModel.uiState.value.pendingPhotos.single()
        assertEquals(48.0, updated.latitude)
        assertEquals(11.0, updated.longitude)
        assertEquals(listOf(Triple(photo.fileName, 48.0, 11.0)), fileStore.locationsWritten)
    }

    @Test
    fun save_persistsPendingPhotosWithTheNewStationIdAndClearsThem() {
        val attachments = FakeAttachmentRepository()
        val stations = FakeStationRepository()
        val viewModel = viewModel(attachments = attachments, stations = stations)
        viewModel.onInputChange { it.copy(date = LocalDate.of(2026, 7, 4)) }
        viewModel.onAddPendingPhoto(fakeUri)
        val pendingFileName = viewModel.uiState.value.pendingPhotos.single().fileName

        viewModel.save()

        assertTrue(viewModel.uiState.value.isSaved)
        assertTrue(viewModel.uiState.value.pendingPhotos.isEmpty())
        val saved = attachments.attachments.single()
        assertEquals(AttachmentOwnerType.STATION, saved.ownerType)
        assertEquals(stations.stations.single().id, saved.ownerId)
        assertEquals(pendingFileName, saved.fileName)
    }
}
