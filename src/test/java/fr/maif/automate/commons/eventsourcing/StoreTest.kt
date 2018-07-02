package fr.maif.automate.commons.eventsourcing

import arrow.core.*
import com.fasterxml.jackson.module.kotlin.KotlinModule
import fr.maif.automate.commons.Error
import io.kotlintest.matchers.*
import io.kotlintest.specs.*
import io.reactivex.Single
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject

sealed class VikingCommand: Command
data class CreateViking(val id: String): VikingCommand()
data class UpdateName(val name: String): VikingCommand()
data class UpdateCity(val city: String): VikingCommand()

sealed class VikingEvent: Event {
    override fun toJson(): JsonObject = JsonObject.mapFrom(this)
}
data class VikingCreated(val id: String): VikingEvent()
data class NameUpdated(val name: String): VikingEvent()
data class CityUpdated(val city: String): VikingEvent()


data class Viking(val id: String? = null, val name: String? = null, val city: String? = null)

class VikingReader: EventReader<VikingEvent> {
    override fun read(envelope: EventEnvelope): VikingEvent {
        return when(envelope.eventType) {
            VikingCreated::class.java.simpleName -> envelope.event.mapTo(VikingCreated::class.java)
            NameUpdated::class.java.simpleName -> envelope.event.mapTo(NameUpdated::class.java)
            CityUpdated::class.java.simpleName -> envelope.event.mapTo(CityUpdated::class.java)
            else -> throw IllegalArgumentException("Unknown type ${envelope.eventType}")
        }
    }
}

class VikingStore(id: String, eventStore: EventStore, val reader: VikingReader) : Store<Viking, VikingCommand, VikingEvent>(id, {Viking()}, eventStore) {

    override fun applyEventToState(current: Viking, event: EventEnvelope): Viking {
        val evt = reader.read(event)
        return when(evt) {
            is VikingCreated -> current.copy(id = evt.id)
            is NameUpdated -> current.copy(name = evt.name)
            is CityUpdated -> current.copy(city = evt.city)
        }
    }

    override fun applyCommand(state: Viking, command: VikingCommand): Single<Either<Error, VikingEvent>> {
        return when(command) {
            is CreateViking -> {
                if (command.id == "2") {
                    Single.just(Error("Bad id").left() as Either<Error, VikingEvent>)
                } else {
                    val event = VikingCreated(command.id)
                    persist("1", event).map {
                        it.map { event as VikingEvent }
                    }
                }
            }
            is UpdateName -> {
                val event = NameUpdated(command.name)
                persist("2", event).map {
                    it.map { event }
                }
            }
            is UpdateCity -> {
                val event = CityUpdated(command.city)
                persist("3", event).map {
                    it.map { event }
                }
            }
        }
    }
}


class StoreTest: FunSpec() {

    init {
        Json.mapper.registerModule(KotlinModule())
        applyCommands()
        applyCommandAfterRecover()
        applyCommandOnErrorAfterRecover()
    }

    private fun applyCommands() = test("A command must store event on an empty journal") {
        val store = VikingStore("1", InMemoryEventStore(), VikingReader())

        val id = "entityId"
        val name = "Ragnard Lodbrok"
        val city = "Kattegat"

        val vikingCreated: Either<Error, VikingEvent> = store.onCommand(CreateViking(id)).blockingGet()
        vikingCreated should beInstanceOf(Either.Right::class)
        vikingCreated.get() shouldBe VikingCreated(id)

        val nameUpdated: Either<Error, VikingEvent> = store.onCommand(UpdateName(name)).blockingGet()
        nameUpdated should beInstanceOf(Either.Right::class)
        nameUpdated.get() shouldBe NameUpdated(name)

        val cityUpdated: Either<Error, VikingEvent> = store.onCommand(UpdateCity(city)).blockingGet()
        cityUpdated should beInstanceOf(Either.Right::class)
        cityUpdated.get() shouldBe CityUpdated(city)

        store.state().blockingGet() shouldBe Viking(id, name, city)
    }

    private fun applyCommandAfterRecover() = test("A store must recover and handle command") {

        val id = "1"
        val name = "Ragnard Lodbrok"
        val city = "Kattegat"

        val initialData: Map<String, List<EventEnvelope>> = mapOf(
                Pair(id, listOf(
                    EventEnvelope("$id-0", id, 0, VikingCreated::class.java.simpleName, "1.0", VikingCreated(id).toJson()),
                    EventEnvelope("$id-1", id, 1, NameUpdated::class.java.simpleName, "1.0", NameUpdated(name).toJson())
                ))
        )
        val eventStore = InMemoryEventStore(initialData)
        val store = VikingStore(id, eventStore, VikingReader())

        val cityUpdated: Either<Error, VikingEvent> = store.onCommand(UpdateCity("Kattegat")).blockingGet()
        cityUpdated should beInstanceOf(Either.Right::class)
        cityUpdated.get() shouldBe CityUpdated(city)

        store.state().blockingGet() shouldBe Viking(id, name, city)
    }

    private fun applyCommandOnErrorAfterRecover() = test("A store must recover and reject command") {

        val id = "1"
        val name = "Ragnard Lodbrok"

        val initialData: Map<String, List<EventEnvelope>> = mapOf(
                Pair(id, listOf(
                    EventEnvelope("$id-0", id, 0, VikingCreated::class.java.simpleName, "1.0", VikingCreated(id).toJson()),
                    EventEnvelope("$id-1", id, 1, NameUpdated::class.java.simpleName, "1.0", NameUpdated(name).toJson())
                ))
        )
        val eventStore = InMemoryEventStore(initialData)
        val store = VikingStore(id, eventStore, VikingReader())

        val cityUpdated: Either<Error, VikingEvent> = store.onCommand(CreateViking("2")).blockingGet()
        cityUpdated should beInstanceOf(Either.Left::class)

        store.state().blockingGet() shouldBe Viking(id, name)
    }

}


