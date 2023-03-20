package fr.maif.automate.commons.eventsourcing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import fr.maif.automate.commons.Error
import io.reactivex.Observable
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong


interface Command
interface Event {
    fun type(): String = this::class.java.simpleName
    fun toJson(): JsonObject
    fun exposedJson(): JsonObject = toJson()
    fun version(): String = "1.0.0"
}

data class EventEnvelope(
    val uniqueId: String,
    val entityId: String,
    val sequence: Long,
    val eventType: String,
    val version: String,
    val event: JsonObject,
    val date: LocalDateTime = LocalDateTime.now(),
    val metadata: JsonObject = JsonObject()
)

interface EventStore {

    fun loadEvents(): Observable<EventEnvelope>

    fun loadEvents(sequenceNum: Long): Observable<EventEnvelope>
    fun loadEventsById(id: String, sequenceNum: Long = 0): Observable<EventEnvelope>

    fun eventStream(): Observable<EventEnvelope>

    fun eventStreamByGroupId(groupId: String): Observable<EventEnvelope>

    fun commit(groupId: String, sequenceNum: Long): Single<Unit>

    fun eventStream(id: String): Observable<EventEnvelope>

    fun persist(id: String, event: EventEnvelope): Single<EventEnvelope>
}

interface EventReader<E : Event> {
    fun read(envelope: EventEnvelope): E
}

abstract class Store<S, C : Command, E : Event>(
    private val name: String,
    private val initialState: () -> S & Any,
    private val eventStore: EventStore
) {

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(Store::class.java)
    }

    private val lastSequence = AtomicLong(0)

    fun state(): Single<S> {
        LOGGER.debug("Loading state for $name")
        val loadEvents = eventStore.loadEvents()
        return loadEvents
            .reduce(initialState()) { acc, e -> nextState(acc, e) }
    }

    private fun nextState(acc: S, e: EventEnvelope): S & Any {
        val nextState = applyEventToState(acc, e)
        lastSequence.set(e.sequence)
        return nextState
    }

    protected abstract fun applyEventToState(current: S, event: EventEnvelope): S & Any

    protected abstract fun applyCommand(state: S, command: C): Single<Either<Error, E>>

    fun onCommand(command: C): Single<Either<Error, E>> = state().flatMap { s -> applyCommand(s, command) }

    protected fun persist(id: String, event: E): Single<Either<Error, EventEnvelope>> {
        val nextSequence = nextSequence()
        val evt = EventEnvelope("$id-$nextSequence", id, nextSequence, event.type(), event.version(), event.toJson())
        return eventStore
            .persist(id, evt).map { it.right() as Either<Error, EventEnvelope> }
            .onErrorReturn { e ->
                LOGGER.error("Error storing event", e)
                Error(" Error storing event ").left()
            }
    }

    protected fun nextSequence() = lastSequence.incrementAndGet()
}