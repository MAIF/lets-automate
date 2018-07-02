package fr.maif.automate.commons.eventsourcing

import arrow.core.Option
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.ConcurrentHashMap


class InMemoryEventStore(init: Map<String, List<EventEnvelope>> = emptyMap()): EventStore {

    private val data: ConcurrentHashMap<String, List<EventEnvelope>> = ConcurrentHashMap(init)

    private val events = PublishSubject.create<EventEnvelope>()

    private fun values(): List<EventEnvelope> = data.values.flatMap { it }

    override fun loadEvents(): Observable<EventEnvelope> =
            Observable.fromIterable(values())

    override fun loadEvents(sequenceNum: Long): Observable<EventEnvelope> =
            loadEvents().filter { it.sequence >= sequenceNum }

    override fun loadEventsById(id: String, sequenceNum: Long): Observable<EventEnvelope> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun eventStream(): Observable<EventEnvelope> = events

    override fun eventStream(id: String): Observable<EventEnvelope> = eventStream().filter { it.entityId == id }

    override fun persist(id: String, event: EventEnvelope): Single<EventEnvelope> {
        val l: List<EventEnvelope> = Option(data[id]).toList().flatMap { l -> l.orEmpty() }
        data.put(id, l.plus(event))
        events.onNext(event)
        return Single.just(event)
    }

    override fun eventStreamByGroupId(groupId: String): Observable<EventEnvelope> {
        return eventStream()
    }

    override fun commit(groupId: String, sequenceNum: Long): Single<Unit> {
        return Single.just(Unit)
    }
}
