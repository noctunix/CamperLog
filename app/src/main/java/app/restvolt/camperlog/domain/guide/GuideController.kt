package app.restvolt.camperlog.domain.guide

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Aktueller Stand einer geführten Tour; `tour == null` bedeutet, dass keine Tour läuft. */
data class GuideState(
    val tour: TourDefinition? = null,
    val stepIndex: Int = 0,
    val phase: GuidePhase = GuidePhase.IDLE,
) {
    val currentStep: TourStep? get() = tour?.steps?.getOrNull(stepIndex)
    val stepCount: Int get() = tour?.steps?.size ?: 0
}

/**
 * Steuert den Ablauf einer geführten Tour als Zustandsautomat. Befehle laufen serialisiert über
 * [scope] und eine [Mutex], damit sich [next]/[completeAction]/[end] nicht überholen können. Jede
 * Aktivierung eines Schritts erhöht eine interne Generation; eine [completeAction]- oder
 * [setCondition]-Meldung, die für einen davor schon verlassenen Schritt unterwegs war, wird beim
 * Verarbeiten verworfen statt den inzwischen aktuellen Schritt fälschlich zu erfüllen.
 */
class GuideController(private val scope: CoroutineScope) {

    private val mutex = Mutex()
    private val _state = MutableStateFlow(GuideState())
    val state: StateFlow<GuideState> = _state.asStateFlow()

    @Volatile
    private var generation = 0
    private var phaseBeforePause: GuidePhase? = null

    /** Startet [tour] am ersten Schritt; überschreibt eine eventuell schon laufende Tour. */
    fun start(tour: TourDefinition) = enqueue {
        generation++
        phaseBeforePause = null
        _state.value = GuideState(tour = tour, stepIndex = 0, phase = phaseFor(tour.steps.first()))
    }

    /** Schaltet zum nächsten Schritt; nur erlaubt, wenn der aktuelle Schritt [GuidePhase.ACTIVE] ist. Beendet die Tour nach dem letzten Schritt. */
    fun next() = enqueue {
        val current = _state.value
        val tour = current.tour ?: return@enqueue
        if (current.phase != GuidePhase.ACTIVE) return@enqueue
        generation++
        phaseBeforePause = null
        val nextIndex = current.stepIndex + 1
        _state.value = if (nextIndex >= tour.steps.size) {
            current.copy(phase = GuidePhase.ENDED)
        } else {
            current.copy(stepIndex = nextIndex, phase = phaseFor(tour.steps[nextIndex]))
        }
    }

    /** Schaltet zum vorherigen Schritt zurück; kein Effekt am ersten Schritt oder außerhalb einer laufenden Tour. */
    fun back() = enqueue {
        val current = _state.value
        val tour = current.tour ?: return@enqueue
        if (current.phase != GuidePhase.ACTIVE && current.phase != GuidePhase.BLOCKED) return@enqueue
        if (current.stepIndex == 0) return@enqueue
        generation++
        phaseBeforePause = null
        val previousIndex = current.stepIndex - 1
        _state.value = current.copy(stepIndex = previousIndex, phase = phaseFor(tour.steps[previousIndex]))
    }

    /** Unterbricht die laufende Tour; die Oberfläche blendet die Barriere aus, bis [resume] aufgerufen wird. */
    fun pause() = enqueue {
        val current = _state.value
        if (current.phase != GuidePhase.ACTIVE && current.phase != GuidePhase.BLOCKED) return@enqueue
        phaseBeforePause = current.phase
        _state.value = current.copy(phase = GuidePhase.PAUSED)
    }

    /** Setzt eine mit [pause] unterbrochene Tour im selben Schritt und derselben Phase fort. */
    fun resume() = enqueue {
        val current = _state.value
        if (current.phase != GuidePhase.PAUSED) return@enqueue
        _state.value = current.copy(phase = phaseBeforePause ?: GuidePhase.ACTIVE)
        phaseBeforePause = null
    }

    /** Beendet die Tour sofort, unabhängig vom aktuellen Schritt. */
    fun end() = enqueue {
        val current = _state.value
        if (current.phase == GuidePhase.IDLE || current.phase == GuidePhase.ENDED) return@enqueue
        generation++
        phaseBeforePause = null
        _state.value = current.copy(phase = GuidePhase.ENDED)
    }

    /**
     * Meldet den erfolgreichen Abschluss der Aktion [actionId]. Schaltet den aktuellen Schritt nur
     * frei, wenn er genau darauf wartet; eine verspätete Meldung für einen inzwischen verlassenen
     * Schritt bleibt wirkungslos.
     */
    fun completeAction(actionId: String) {
        val expectedGeneration = generation
        enqueue {
            if (expectedGeneration != generation) return@enqueue
            val current = _state.value
            val completion = current.currentStep?.completion
            if (effectivePhase(current) == GuidePhase.BLOCKED && completion is StepCompletion.Action && completion.actionId == actionId) {
                applyPhase(current, GuidePhase.ACTIVE)
            }
        }
    }

    /**
     * Setzt den Wahrheitswert der Bedingung [conditionId]. Wirkt nur, solange der aktuelle Schritt
     * genau darauf wartet; eine verspätete Meldung für einen inzwischen verlassenen Schritt bleibt
     * wirkungslos.
     */
    fun setCondition(conditionId: String, satisfied: Boolean) {
        val expectedGeneration = generation
        enqueue {
            if (expectedGeneration != generation) return@enqueue
            val current = _state.value
            val completion = current.currentStep?.completion
            val phase = effectivePhase(current)
            if ((phase == GuidePhase.BLOCKED || phase == GuidePhase.ACTIVE) &&
                completion is StepCompletion.Condition && completion.conditionId == conditionId
            ) {
                applyPhase(current, if (satisfied) GuidePhase.ACTIVE else GuidePhase.BLOCKED)
            }
        }
    }

    private fun phaseFor(step: TourStep): GuidePhase = when (step.completion) {
        is StepCompletion.Information -> GuidePhase.ACTIVE
        is StepCompletion.Action, is StepCompletion.Condition -> GuidePhase.BLOCKED
    }

    /** Die Phase, gegen die [completeAction]/[setCondition] prüfen: bei [GuidePhase.PAUSED] die vor der Pause gültige. */
    private fun effectivePhase(current: GuideState): GuidePhase =
        if (current.phase == GuidePhase.PAUSED) phaseBeforePause ?: GuidePhase.PAUSED else current.phase

    /** Setzt [newPhase], ohne eine laufende Pause optisch zu beenden: während [GuidePhase.PAUSED] landet sie in [phaseBeforePause]. */
    private fun applyPhase(current: GuideState, newPhase: GuidePhase) {
        if (current.phase == GuidePhase.PAUSED) {
            phaseBeforePause = newPhase
        } else {
            _state.value = current.copy(phase = newPhase)
        }
    }

    private fun enqueue(block: suspend () -> Unit) {
        scope.launch { mutex.withLock { block() } }
    }
}
