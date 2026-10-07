package app.restvolt.camperlog.ui.attachments

import android.net.Uri
import androidx.core.net.toUri
import app.restvolt.camperlog.data.AttachmentImportError
import app.restvolt.camperlog.data.AttachmentImportResult
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.ui.FakeAttachmentFileStore
import app.restvolt.camperlog.ui.FakeAttachmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AttachmentsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val fakeUri: Uri = "content://fake/photo.jpg".toUri()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.collect(flow: StateFlow<*>) {
        backgroundScope.launch { flow.collect {} }
    }

    @Test
    fun importPhoto_success_addsItToPhotos() = runTest(dispatcher) {
        val viewModel = AttachmentsViewModel(FakeAttachmentRepository(), FakeAttachmentFileStore(), AttachmentOwnerType.STATION, 1)
        collect(viewModel.uiState)

        viewModel.importPhoto(fakeUri)

        assertEquals(1, viewModel.uiState.value.photos.size)
        assertEquals(false, viewModel.uiState.value.importing)
        assertNull(viewModel.uiState.value.importError)
    }

    @Test
    fun importPhoto_failure_setsTypedError() = runTest(dispatcher) {
        val fileStore = FakeAttachmentFileStore()
        fileStore.nextPhotoResult = { AttachmentImportResult.Failure(AttachmentImportError.TOO_LARGE) }
        val viewModel = AttachmentsViewModel(FakeAttachmentRepository(), fileStore, AttachmentOwnerType.STATION, 1)
        collect(viewModel.uiState)

        viewModel.importPhoto(fakeUri)

        assertTrue(viewModel.uiState.value.photos.isEmpty())
        assertEquals(AttachmentImportError.TOO_LARGE, viewModel.uiState.value.importError)

        viewModel.dismissImportError()
        assertNull(viewModel.uiState.value.importError)
    }

    @Test
    fun delete_removesPhotoAndUndoRestoresIt() = runTest(dispatcher) {
        val viewModel = AttachmentsViewModel(FakeAttachmentRepository(), FakeAttachmentFileStore(), AttachmentOwnerType.STATION, 1)
        collect(viewModel.uiState)
        viewModel.importPhoto(fakeUri)
        val attachment = viewModel.uiState.value.photos.single()

        viewModel.delete(attachment)

        assertTrue(viewModel.uiState.value.photos.isEmpty())
        assertEquals(attachment, viewModel.uiState.value.lastDeleted)

        viewModel.undoDelete()

        assertEquals(listOf(attachment), viewModel.uiState.value.photos)
        assertNull(viewModel.uiState.value.lastDeleted)
    }

    @Test
    fun useLocation_writesCoordinatesOnTheAttachment() = runTest(dispatcher) {
        val repository = FakeAttachmentRepository()
        val viewModel = AttachmentsViewModel(repository, FakeAttachmentFileStore(), AttachmentOwnerType.STATION, 1)
        collect(viewModel.uiState)
        viewModel.importPhoto(fakeUri)
        val attachment = viewModel.uiState.value.photos.single()

        viewModel.useLocation(attachment, 48.0, 11.0)

        assertEquals(48.0, viewModel.uiState.value.photos.single().latitude)
        assertEquals(11.0, viewModel.uiState.value.photos.single().longitude)
    }

    @Test
    fun updateCaption_changesTheStoredCaption() = runTest(dispatcher) {
        val viewModel = AttachmentsViewModel(FakeAttachmentRepository(), FakeAttachmentFileStore(), AttachmentOwnerType.STATION, 1)
        collect(viewModel.uiState)
        viewModel.importPhoto(fakeUri)
        val attachment = viewModel.uiState.value.photos.single()

        viewModel.updateCaption(attachment, "Sunset")

        assertEquals("Sunset", viewModel.uiState.value.photos.single().caption)
    }

    @Test
    fun uiState_onlyShowsAttachmentsOfTheSameOwner() = runTest(dispatcher) {
        val repository = FakeAttachmentRepository()
        val viewModel = AttachmentsViewModel(repository, FakeAttachmentFileStore(), AttachmentOwnerType.STATION, 1)
        val otherOwner = AttachmentsViewModel(repository, FakeAttachmentFileStore(), AttachmentOwnerType.STATION, 2)
        collect(viewModel.uiState)
        collect(otherOwner.uiState)

        viewModel.importPhoto(fakeUri)

        assertEquals(1, viewModel.uiState.value.photos.size)
        assertTrue(otherOwner.uiState.value.photos.isEmpty())
    }
}
