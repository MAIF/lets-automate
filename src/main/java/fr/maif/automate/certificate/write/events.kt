package fr.maif.automate.certificate.write

import arrow.core.*
import fr.maif.automate.commons.eventsourcing.Event
import fr.maif.automate.commons.eventsourcing.EventEnvelope
import fr.maif.automate.commons.eventsourcing.EventReader
import fr.maif.automate.commons.readKeyPair
import fr.maif.automate.commons.stringify
import fr.maif.automate.letsencrypt.Certificate
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import java.security.KeyPair
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

sealed class CertificateEvent : Event {
    override fun toJson(): JsonObject = JsonObject.mapFrom(this)
    override fun exposedJson(): JsonObject = toJson()
}
data class CertificateCreated(val domain: String, val subdomain: Option<String>, val wildcard: Boolean): CertificateEvent() {
    companion object {
        fun fromJson(json: JsonObject): CertificateCreated = CertificateCreated(
                json.getString("domain"),
                json.getString("subdomain").toOption(),
                json.getBoolean("wildcard")
        )
    }
    override fun toJson(): JsonObject = json {
        obj(listOf(
                Some("domain" to domain),
                subdomain.map { d -> "subdomain" to d },
                Some("wildcard" to wildcard)
        ).flatMap { it.toList() })
    }
}
data class CertificateOrdered(val domain: String, val subdomain: Option<String>, val wildcard: Boolean, val privateKey: KeyPair, val csr: String, val certificate: Certificate): CertificateEvent() {
    companion object {
        fun fromJson(json: JsonObject): CertificateOrdered = CertificateOrdered(
                json.getString("domain"),
                json.getString("subdomain").toOption(),
                json.getBoolean("wildcard"),
                readKeyPair(json.getString("privateKey")),
                json.getString("csr"),
                Certificate.fromJson(json.getJsonObject("certificate"))
        )
    }

    override fun toJson(): JsonObject = json {
        obj(listOf(
                Some("domain" to domain),
                subdomain.map { d -> "subdomain" to d },
                Some("wildcard" to wildcard),
                Some("privateKey" to privateKey.stringify()),
                Some("csr" to csr),
                Some("certificate" to certificate.toJson())
        ).flatMap { it.toList() })
    }

    override fun exposedJson(): JsonObject {
        return json {
            obj(listOf(
                    ("domain" to domain).toOption(),
                    subdomain.map { "subdomain" to it },
                    ("wildcard" to wildcard).toOption(),
                    ("expire" to DateTimeFormatter.ISO_DATE_TIME.format(certificate.expire)).toOption()
            ).flatMap { it.toList() })
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other is CertificateOrdered) {
            return (
                        domain == other.domain &&
                        subdomain == other.subdomain &&
                        wildcard == other.wildcard &&
                        privateKey.stringify() == other.privateKey.stringify() &&
                        csr == other.csr &&
                        certificate == other.certificate
            )
        }
        return false
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }
}
data class CertificateOrderFailure(val domain: String, val subdomain: Option<String>, val cause: String): CertificateEvent() {
    companion object {
        fun fromJson(json: JsonObject): CertificateOrderFailure = CertificateOrderFailure(
                json.getString("domain"),
                json.getString("subdomain").toOption(),
                json.getString("cause")
        )
    }
    override fun toJson(): JsonObject = json {
        obj(listOf(
                Some("domain" to domain),
                subdomain.map { d -> "subdomain" to d },
                Some("cause" to cause)
        ).flatMap { it.toList() })
    }
}
data class CertificateReOrderedStarted(val domain: String, val subdomain: Option<String>, val wildcard: Boolean): CertificateEvent() {
    companion object {
        fun fromJson(json: JsonObject): CertificateReOrderedStarted = CertificateReOrderedStarted(
                json.getString("domain"),
                json.getString("subdomain").toOption(),
                json.getBoolean("wildcard")
        )
    }

    override fun toJson(): JsonObject = json {
        obj(listOf(
                Some("domain" to domain),
                subdomain.map { d -> "subdomain" to d },
                Some("wildcard" to wildcard)
        ).flatMap { it.toList() })
    }
}
data class CertificateReOrdered(val domain: String, val subdomain: Option<String>, val wildcard: Boolean, val privateKey: KeyPair, val csr: String, val certificate: Certificate): CertificateEvent() {
    companion object {
        fun fromJson(json: JsonObject): CertificateReOrdered = CertificateReOrdered(
                json.getString("domain"),
                json.getString("subdomain").toOption(),
                json.getBoolean("wildcard"),
                readKeyPair(json.getString("privateKey")),
                json.getString("csr"),
                Certificate.fromJson(json.getJsonObject("certificate"))
        )
    }

    override fun toJson(): JsonObject = json {
        obj(listOf(
                Some("domain" to domain),
                subdomain.map { d -> "subdomain" to d },
                Some("wildcard" to wildcard),
                Some("privateKey" to privateKey.stringify()),
                Some("csr" to csr),
                Some("certificate" to certificate.toJson())
        ).flatMap { it.toList() })
    }

    override fun exposedJson(): JsonObject {
        return json {
            obj(listOf(
                    ("domain" to domain).toOption(),
                    subdomain.map { "subdomain" to it },
                    ("wildcard" to wildcard).toOption(),
                    ("expire" to DateTimeFormatter.ISO_DATE_TIME.format(certificate.expire)).toOption()
            ).flatMap { it.toList() })
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other is CertificateReOrdered) {
            return (
                    domain == other.domain &&
                            subdomain == other.subdomain &&
                            wildcard == other.wildcard &&
                            privateKey.stringify() == other.privateKey.stringify() &&
                            csr == other.csr &&
                            certificate == other.certificate
                    )
        }
        return false
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }
}
data class CertificateReOrderFailure(val domain: String, val subdomain: Option<String>, val cause: String): CertificateEvent() {
    companion object {
        fun fromJson(json: JsonObject): CertificateReOrderFailure = CertificateReOrderFailure(
                json.getString("domain"),
                json.getString("subdomain").toOption(),
                json.getString("cause")
        )
    }
    override fun toJson(): JsonObject = json {
        obj(listOf(
                Some("domain" to domain),
                subdomain.map { d -> "subdomain" to d },
                Some("cause" to cause)
        ).flatMap { it.toList() })
    }
}
data class CertificatePublished(val domain: String, val subdomain: Option<String>, val dateTime: LocalDateTime): CertificateEvent() {
    companion object {
        fun fromJson(json: JsonObject): CertificatePublished = CertificatePublished(
                json.getString("domain"),
                json.getString("subdomain").toOption(),
                LocalDateTime.parse(json.getString("dateTime"), DateTimeFormatter.ISO_DATE_TIME)
        )
    }
    override fun toJson(): JsonObject = json {
        obj(listOf(
                Some("domain" to domain),
                subdomain.map { d -> "subdomain" to d },
                Some("dateTime" to DateTimeFormatter.ISO_DATE_TIME.format(dateTime))
        ).flatMap { it.toList() })
    }
}
data class CertificatePublishFailure(val domain: String, val subdomain: Option<String>, val cause: String): CertificateEvent() {
    companion object {
        fun fromJson(json: JsonObject): CertificatePublishFailure = CertificatePublishFailure(
                json.getString("domain"),
                json.getString("subdomain").toOption(),
                json.getString("cause")
        )
    }
    override fun toJson(): JsonObject = json {
        obj(listOf(
                Some("domain" to domain),
                subdomain.map { d -> "subdomain" to d },
                Some("cause" to cause)
        ).flatMap { it.toList() })
    }
}
data class CertificateDeleted(val domain: String, val subdomain: Option<String>): CertificateEvent() {
    companion object {
        fun fromJson(json: JsonObject): CertificateDeleted = CertificateDeleted(
                json.getString("domain"),
                json.getString("subdomain").toOption()
        )
    }
    override fun toJson(): JsonObject = json {
        obj(listOf(
                Some("domain" to domain),
                subdomain.map { d -> "subdomain" to d }
        ).flatMap { it.toList() })
    }
}

class CertificateEventReader: EventReader<CertificateEvent> {
    override fun read(envelope: EventEnvelope): CertificateEvent =
            when(envelope.eventType) {
                nameOf(CertificateCreated::class) -> CertificateCreated.fromJson(envelope.event)
                nameOf(CertificateOrdered::class) -> CertificateOrdered.fromJson(envelope.event)
                nameOf(CertificateOrderFailure::class) -> CertificateOrderFailure.fromJson(envelope.event)
                nameOf(CertificateReOrderedStarted::class) -> CertificateReOrderedStarted.fromJson(envelope.event)
                nameOf(CertificateReOrdered::class) -> CertificateReOrdered.fromJson(envelope.event)
                nameOf(CertificateReOrderFailure::class) -> CertificateReOrderFailure.fromJson(envelope.event)
                nameOf(CertificatePublished::class) -> CertificatePublished.fromJson(envelope.event)
                nameOf(CertificatePublishFailure::class) -> CertificatePublishFailure.fromJson(envelope.event)
                nameOf(CertificateDeleted::class) -> CertificateDeleted.fromJson(envelope.event)
                else -> throw IllegalArgumentException("Unknown type ${envelope.eventType} on event ${envelope}")
            }
}