package fr.maif.automate.certificate.write

import arrow.core.None
import arrow.core.Option
import arrow.core.toOption
import arrow.syntax.collections.flatten
import fr.maif.automate.letsencrypt.Certificate
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import java.security.KeyPair
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap


object State {

    data class Key(val domain: String, val subdomain: Option<String>)

    data class AllCertificates(val data: ConcurrentHashMap<Key, CertificateState> = ConcurrentHashMap()) {
        fun update(id: Key, cb: (CertificateState) -> CertificateState): AllCertificates {
            val current = data.getOrDefault(id, CertificateState())
            data.put(id, cb(current))
            return this.copy(data = data)
        }

        fun get(id: Key): Option<CertificateState> = data[id].toOption()

        fun list(): List<CertificateState> = data.toList().map { it.second }

        fun delete(id: Key): AllCertificates {
            data.remove(id)
            return this.copy(data = data)
        }
    }

    data class CertificateState(val domain: String? = null, val subdomain: Option<String> = None, val wildcard: Boolean? = null, val reordered: Boolean = false, val privateKey: KeyPair? = null, val csr: String? = null, val certificate: Certificate? = null, val publishedDate: LocalDateTime? = null) {
        fun exposedJson(): JsonObject = json {
            obj(listOf(
                    domain.toOption().map { "domain" to it },
                    subdomain.map { "subdomain" to it },
                    wildcard.toOption().map { "wildcard" to it },
                    certificate.toOption().flatMap { it.expire.toOption() }.map { "expire" to DateTimeFormatter.ISO_DATE_TIME.format(it) },
                    publishedDate.toOption().map { "publishedDate" to DateTimeFormatter.ISO_DATE_TIME.format(it) }
            ).flatten())
        }
    }

}
