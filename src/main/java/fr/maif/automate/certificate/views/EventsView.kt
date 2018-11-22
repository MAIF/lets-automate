package fr.maif.automate.certificate.views

import arrow.core.None
import arrow.core.Option
import arrow.core.getOrElse
import fr.maif.automate.certificate.write.CertificateEvent
import fr.maif.automate.commons.eventsourcing.EventReader
import fr.maif.automate.commons.eventsourcing.EventStore
import io.reactivex.Observable
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj

class EventsView(private val eventStore: EventStore, private val eventReader: EventReader<CertificateEvent>) {

    fun events(domain: String, id: Option<Long> = None): Single<List<JsonObject>> {
        return eventStore.loadEventsById(domain, id.getOrElse { 0 })
                .map { Triple(eventReader.read(it), it.eventType, it.sequence) }
                .map { p ->
                    eventToJson(p)
                }
                .toList()

    }

    fun eventsStream(id: Option<Long> = None): Observable<Pair<Long, JsonObject>> {
        return eventStore
                .eventStream()
                .map { Triple(eventReader.read(it), it.eventType, it.sequence) }
                .map { p ->
                    p.third to eventToJson(p)
                }
    }

    private fun eventToJson(p: Triple<CertificateEvent, String, Long>): JsonObject {
        val (event, t, seq) = p
        return json {
            obj(
                    "sequence" to seq,
                    "type" to t,
                    "event" to event.exposedJson()
            )
        }
    }

}