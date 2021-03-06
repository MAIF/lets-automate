package fr.maif.automate.certificate.scheduler

import arrow.core.Either
import fr.maif.automate.certificate.Certificates
import fr.maif.automate.certificate.write.CertificateEvent
import fr.maif.automate.certificate.write.RenewCertificate
import fr.maif.automate.certificate.write.State
import fr.maif.automate.commons.Error
import fr.maif.automate.commons.Interval
import io.reactivex.Observable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime


class CertificateRenewer(
        private val pollingInterval: Interval,
        private val certificates: Certificates
) {


    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(CertificateRenewer::class.java)

        fun isCertificateExpired(c: State.CertificateState, d: LocalDateTime = LocalDateTime.now()): Boolean =
          c.certificate?.expire?.isBefore(d.plusDays(30)) ?: false
    }

    fun startScheduler() {
        Observable.interval(pollingInterval.period, pollingInterval.unit)
                .flatMap {
                    LOGGER.info("Looking for certificate to renew")
                    findDomainToRenew()
                            .onErrorReturn { emptyList() }
                            .doOnNext {domains ->
                                if (domains.isNotEmpty()) {
                                    LOGGER.info("Found ${domains.map { it.domain }} to renew")
                                }
                            }
                            .concatMapIterable { it }
                }
                .flatMap { domain ->
                    if (domain.wildcard != null && domain.domain != null) {
                        certificates.onCommand(RenewCertificate(domain.domain, domain.subdomain, domain.wildcard))
                                .toObservable()
                                .onErrorReturn { e ->
                                    LOGGER.error("Error while renewing certificate for ${domain.domain}", e)
                                    Either.Left(Error(e.message)) as Either<Error, CertificateEvent>
                                }
                    } else{
                        Observable.just(Either.Left(Error("Domain $domain invalid")) as Either<Error, CertificateEvent>)
                    }

                }
                .subscribe({ res ->
                    when(res) {
                        is Either.Right ->
                            LOGGER.debug("Renew ok for ${res.b}")
                        is Either.Left ->
                            LOGGER.error("Error while renewing certificate: ${res.a}")
                    }
                }, { e ->
                    LOGGER.error("Error while renewing certificate", e)
                })
    }

    private fun findDomainToRenew() =
            certificates
                    .state()
                    .toObservable()
                    .map { all ->
                        val toRenew = all.list().filter { isCertificateExpired(it) }
                        LOGGER.info("""Finding certificates to renew in ${all.list().map { c -> (c.domain to c.subdomain) to c.certificate?.expire }}.
                          | -Now is ${LocalDateTime.now()}\n
                          | -expire: ${LocalDateTime.now().plusDays(30)}
                          | Found $toRenew
                          | """.trimMargin())
                        toRenew
                    }


}