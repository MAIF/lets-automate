package fr.maif.automate.certificate.views

import arrow.core.None
import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.toOption
import fr.maif.automate.certificate.write.*
import fr.maif.automate.commons.eventsourcing.EventReader
import fr.maif.automate.commons.eventsourcing.EventStore
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

data class CertificateError(val type: String, val cause: String) {
    fun json(): JsonObject = json {obj(
            "type" to type,
            "cause" to cause
    )}
}

data class CertificateDomainResume(
        val subdomain: Option<String>,
        val wildcard: Boolean = false,
        val certificate: Option<CertificateResume> = None,
        val publication: Option<PublicationResume> = None,
        val error: Option<CertificateError> = None
) {
    fun json(): JsonObject = json {obj(listOf(
            subdomain.map { "subdomain" to it },
            ("wildcard" to wildcard).toOption(),
            certificate.map { "certificate" to it.json() },
            publication.map { "publication" to it.json() },
            error.map { "error" to it.json() }
    ).flatMap { it.toList() })}

}

data class DomainResume(
        val domain: String,
        val certificates: ConcurrentHashMap<String, CertificateDomainResume> = ConcurrentHashMap()) {

    private fun buildKey(subdomain: Option<String>): String {
        return "$domain-${subdomain.getOrElse { "na" }}"
    }

    fun json(): JsonObject = json {obj(
            "domain" to domain,
            "certificates" to certificates.map { it.value.json() }
    )}

    fun updateCertificate(subdomain: Option<String>, f: (CertificateDomainResume) -> CertificateDomainResume) {
        val updated = f(certificates.getOrPut(buildKey(subdomain)){CertificateDomainResume(subdomain)})
        certificates.put(buildKey(subdomain), updated)
    }

    fun deleteCertificate(subdomain: Option<String>) {
        certificates.remove(buildKey(subdomain))
    }
}

data class CertificateResume(val expire: LocalDateTime) {
    fun json(): JsonObject = json {obj("expire" to DateTimeFormatter.ISO_DATE_TIME.format(expire))}
}
data class PublicationResume(val publishDate: LocalDateTime) {
    fun json(): JsonObject = json {obj("publishDate" to DateTimeFormatter.ISO_DATE_TIME.format(publishDate))}
}

class AllDomainView(
        private val eventStore: EventStore,
        private val eventReader: EventReader<CertificateEvent> = CertificateEventReader()
) {

    private val datas = ConcurrentHashMap<String, DomainResume>()

    private fun update(domain: String, mayBeSubdomain: Option<String>, f: (CertificateDomainResume) -> CertificateDomainResume) {
        val domainResume = datas.getOrPut(domain){ DomainResume(domain) }
        domainResume.updateCertificate(mayBeSubdomain, f)
    }

    init {
        reload()
    }

    private fun reload() {
        eventStore
                .loadEvents()
                .concatWith(eventStore.eventStream())
                .map { eventReader.read(it) }
                .subscribe { event ->
                    when (event) {
                        is CertificateCreated -> {
                            val (domain, subdomain, wildcard) = event
                            update(domain, subdomain) { it.copy(wildcard = wildcard) }
                        }
                        is CertificateOrdered -> {
                            val (domain, subdomain, _, _, _, certificate) = event
                            update(domain, subdomain) { it.copy(certificate = CertificateResume(expire = certificate.expire).toOption(), error = None) }
                        }
                        is CertificateOrderFailure -> {
                            val (domain, subdomain, cause) = event
                            update(domain, subdomain) { it.copy(error = CertificateError(CertificateOrderFailure::class.java.simpleName, cause).toOption()) }
                        }
                        is CertificateReOrdered -> {
                            val (domain, subdomain, _, _, _, certificate) = event
                            update(domain, subdomain) { it.copy(certificate = CertificateResume(expire = certificate.expire).toOption(), error = None) }
                        }
                        is CertificateReOrderFailure -> {
                            val (domain, subdomain, cause) = event
                            update(domain, subdomain) { it.copy(error = CertificateError(CertificateReOrderFailure::class.java.simpleName, cause).toOption()) }
                        }
                        is CertificatePublished -> {
                            val (domain, subdomain, published) = event
                            update(domain, subdomain) { it.copy(publication = PublicationResume(published).toOption(), error = None) }
                        }
                        is CertificatePublishFailure -> {
                            val (domain, subdomain, cause) = event
                            update(domain, subdomain) { it.copy(error = CertificateError(CertificatePublishFailure::class.java.simpleName, cause).toOption()) }
                        }
                        is CertificateDeleted -> {
                            val (domain, subdomain) = event
                            datas[domain].toOption().toList().forEach { it.deleteCertificate(subdomain) }
                        }
                    }
                }
    }


    fun listDomains(): Single<List<DomainResume>> {
        return Single.just(datas.values.toList())
    }

    fun getDomain(name: String): Single<Option<DomainResume>> {
        return Single.just(datas.get(name).toOption())
    }


}