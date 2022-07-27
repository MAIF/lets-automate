package fr.maif.automate.commons.eventsourcing

import io.kotlintest.Spec
import io.kotlintest.TestCase
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.jdbc.JDBCClient
import liquibase.Liquibase
import liquibase.database.Database
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.postgresql.ds.PGSimpleDataSource

class PostgresEventStoreTest : StringSpec() {
    val eventDb = "certificate_events"
    val offsetDb = "certificate_events_offsets"
    val host = "localhost"
    val database = "lets_automate"
    val user = "default_user"
    val password = "password"
    val port = 5455
    val vertx = Vertx.vertx()
    val url = "jdbc:postgresql://${host}:${port}/${database}?user=${user}&password=${password}"
    val pgClient: JDBCClient = JDBCClient.createShared(vertx, json {
        obj(
            "host" to host,
            "port" to port,
            "database" to database,
            "username" to user,
            "password" to password,
            "url" to url
        )
    })

    override fun beforeTest(testCase: TestCase) {
        // BeforeTest here
        pgClient.rxUpdate(
            """
      delete from $eventDb
    """
        ).flatMap {
            pgClient.rxUpdate(
                """
      delete from $offsetDb
    """
            )
        }.map { Unit }.onErrorReturn { Unit }.blockingGet()
    }

    override fun afterSpec(spec: Spec) {
        pgClient.close()
    }

    init {

        val datasource = PGSimpleDataSource()
        datasource.serverNames = arrayOf(host)
        datasource.databaseName = database
        datasource.user = user
        datasource.portNumbers = intArrayOf(port)
        datasource.password = password
        datasource.setURL(url)


        val connection = datasource.connection
        val db: Database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
        val liquidbase = Liquibase("db_changes.sql", ClassLoaderResourceAccessor(), db)
        liquidbase.update("")
        connection.close()


        val store = PostgresEventStore(eventDb, offsetDb, pgClient)

        "CRUD" {
            store.loadEvents().toList().blockingGet() shouldBe emptyList<EventEnvelope>()
            println("yeah")
            val event = EventEnvelope(
                "1", "1", 1L,
                "EventType", "1", json { obj("name" to "test") }
            )
            store.persist("1", event).blockingGet()
            store.loadEvents().toList().blockingGet() shouldBe listOf(event)
        }

        "Reload events" {
            val event1 = EventEnvelope(
                "1", "1", 1L,
                "EventType", "1", json { obj("name" to "test") }
            )
            val event2 = EventEnvelope(
                "2", "1", 2L,
                "EventType", "1", json { obj("name" to "test2") }
            )
            store.persist("1", event1).blockingGet()
            store.persist("2", event2).blockingGet()
            store.commit("test_group", 1L).blockingGet()

            val events = store.eventStreamByGroupId("test_group").take(1).toList().blockingGet()
            events shouldBe listOf(event2)
        }
    }


}