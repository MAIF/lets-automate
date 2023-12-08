package fr.maif.automate.commons.eventsourcing

import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.json
import io.vertx.reactivex.ext.sql.SQLClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PostgresEventStore(private val table: String, private val offetsTable: String, private val pgClient: SQLClient) :
        EventStore {

    private val events = PublishSubject.create<EventEnvelope>()

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(PostgresEventStore::class.java)
    }

    override fun loadEvents(): Observable<EventEnvelope> {
        return pgClient
                .rxQuery("SELECT * FROM $table ORDER BY sequence_num")
                .toObservable()
                .flatMap {
                    Observable.fromIterable(it.rows.map { rowToEvent(it) })
                }
    }

    override fun loadEvents(sequenceNum: Long): Observable<EventEnvelope> {
        return pgClient
                .rxQueryWithParams(
                        "SELECT * FROM $table WHERE sequence_num > ? ORDER BY sequence_num",
                        json { array(sequenceNum) }
                )
                .toObservable()
                .flatMap {
                    Observable.fromIterable(it.rows.map { rowToEvent(it) })
                }
    }

    override fun loadEventsById(id: String, sequenceNum: Long): Observable<EventEnvelope> {
        return pgClient
                .rxQueryWithParams(
                        "SELECT * FROM $table WHERE sequence_num > ? and entity_id = ? ORDER BY sequence_num",
                        json { array(sequenceNum, id) }
                )
                .toObservable()
                .flatMap {
                    Observable.fromIterable(it.rows.map { rowToEvent(it) })
                }
    }

    private fun rowToEvent(event: JsonObject): EventEnvelope =
            EventEnvelope(
                    event.getString("unique_id"),
                    event.getString("entity_id"),
                    event.getLong("sequence_num"),
                    event.getString("event_type"),
                    event.getString("version"),
                    JsonObject(event.getString("event")),
                    LocalDateTime.parse(event.getString("created_at"), DateTimeFormatter.ISO_DATE_TIME),
                    JsonObject(event.getString("metadata"))
            )

    override fun eventStream(): Observable<EventEnvelope> = events

    override fun eventStreamByGroupId(groupId: String): Observable<EventEnvelope> {
        return pgClient.rxQueryWithParams(
                "SELECT sequence_num FROM $offetsTable WHERE group_id = ? ",
                json { array(groupId) })
                .toObservable()
                .flatMap { r ->
                    if (r.rows.isEmpty()) {
                        eventStream()
                    } else {
                        val first: JsonObject = r.rows.first()
                        val sequenceNum = first.getLong("sequence_num")
                        loadEvents(sequenceNum).concatWith(eventStream())
                    }
                }
    }

    override fun commit(groupId: String, sequenceNum: Long): Single<Unit> {
        return pgClient.rxGetConnection().flatMap { connection ->
            connection.rxSetAutoCommit(false)
                    .toSingleDefault(Unit).flatMap {
                        LOGGER.debug("Upsert last commit for $groupId to $sequenceNum")
                        val query = """INSERT INTO $offetsTable (group_id, sequence_num) VALUES(?, ?) ON CONFLICT (group_id) DO UPDATE SET sequence_num = ?"""
                        connection.rxUpdateWithParams(query, json { array(groupId, sequenceNum, sequenceNum) })
                    }.flatMap { _ ->
                        connection.rxCommit().toSingle { Unit }
                    }.doOnError { e ->
                        LOGGER.error("Error during commit -> rollback", e)
                        connection.rxRollback().subscribe()
                    }
                    .flatMap { _ -> connection.rxSetAutoCommit(true).andThen(Single.just(Unit)) }
                    .doFinally { connection.rxClose().subscribe() }
        }
    }

    override fun eventStream(id: String): Observable<EventEnvelope> = eventStream().filter { it.entityId == id }

    override fun persist(id: String, event: EventEnvelope): Single<EventEnvelope> {
        return pgClient
                .rxUpdateWithParams(
                        """
                        INSERT INTO $table (
                            unique_id,
                            entity_id,
                            sequence_num,
                            event_type,
                            version,
                            event,
                            created_at,
                            metadata
                        ) VALUES (?, ?, ?, ?, ?, ?::JSON, ?, ?::JSON)
                        """,
                        json {
                            array(
                                    event.uniqueId,
                                    event.entityId,
                                    event.sequence,
                                    event.eventType,
                                    event.version,
                                    event.event.encode(),
                                    DateTimeFormatter.ISO_DATE_TIME.format(event.date),
                                    event.metadata.encode()
                            )
                        }
                )
                .map { _ ->
                    events.onNext(event)
                    event
                }
    }
}