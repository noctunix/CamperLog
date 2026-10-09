package app.restvolt.camperlog.ui.guide

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Zuletzt gemeldete Bildschirmposition jedes über [Modifier.guideAnchor] registrierten Ankers. */
class GuideAnchorRegistry {
    private val bounds = MutableStateFlow<Map<String, Rect>>(emptyMap())

    val anchors: StateFlow<Map<String, Rect>> = bounds.asStateFlow()

    internal fun register(id: String, rect: Rect) {
        bounds.update { it + (id to rect) }
    }

    internal fun unregister(id: String) {
        bounds.update { it - id }
    }
}

/** Registry des aktuellen Compose-Baums; `GuideHost` liest hier die Position des aktiven Ankers aus. */
val LocalGuideAnchors = staticCompositionLocalOf { GuideAnchorRegistry() }

private class GuideAnchorNode(var id: String) : Modifier.Node(), GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    private var registry: GuideAnchorRegistry? = null

    override fun onAttach() {
        registry = currentValueOf(LocalGuideAnchors)
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        registry?.register(id, coordinates.boundsInWindow())
    }

    override fun onDetach() {
        registry?.unregister(id)
        registry = null
    }
}

private data class GuideAnchorElement(val id: String) : ModifierNodeElement<GuideAnchorNode>() {
    override fun create(): GuideAnchorNode = GuideAnchorNode(id)

    override fun update(node: GuideAnchorNode) {
        node.id = id
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "guideAnchor"
        properties["id"] = id
    }
}

/**
 * Registriert die Bildschirmposition dieses Composables unter [id] in [LocalGuideAnchors], solange
 * es im Baum ist. `GuideHost` liest die Position darüber, um Loch und Erklärkarte zu platzieren.
 */
fun Modifier.guideAnchor(id: String): Modifier = this then GuideAnchorElement(id)
