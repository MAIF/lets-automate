package fr.maif.automate.certificate.eventhandler

import arrow.core.None
import arrow.core.Some
import arrow.core.toOption
import fr.maif.automate.certificate.Certificates
import fr.maif.automate.certificate.write.*
import fr.maif.automate.commons.eventsourcing.EventReader
import fr.maif.automate.commons.eventsourcing.EventStore
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

class EventToCommandAdapter(private val eventStore: EventStore, private val certificates: Certificates, private val eventReader: EventReader<CertificateEvent>) {
    companion object {
        val LOGGER = LoggerFactory.getLogger(EventToCommandAdapter::class.java) as Logger
        const val GROUP_ID = "EventToCommandAdapter"
        val ref = AtomicReference<Disposable>(null)
    }

    fun startAdapter() {
        LOGGER.info("Starting event to command adapter")
        val disposable = adaptaterStream()
                .retry(3)
                .subscribe({}, { e ->
                    LOGGER.error("Error consuming command stream, going to restart", e)
                })
        ref.set(disposable)
    }

    private fun adaptaterStream(): Observable<Unit> {
        return eventStore
                .eventStreamByGroupId(GROUP_ID)
                .map { enveloppe ->
                    val event = eventReader.read(enveloppe)

                    val commands = when (event) {
                        is CertificateCreated -> {
                            val (domain, subdomain, wildcard) = event
                            enveloppe.sequence to OrderCertificate(domain, subdomain, wildcard).toOption()
                        }
                        is CertificateOrdered -> {
                            val (domain, subdomain) = event
                            enveloppe.sequence to PublishCertificate(domain, subdomain).toOption()
                        }
                        is CertificateReOrderedStarted -> {
                            val (domain, subdomain, wildcard) = event
                            enveloppe.sequence to RenewCertificate(domain, subdomain, wildcard).toOption()
                        }
                        is CertificateReOrdered -> {
                            val (domain, subdomain) = event
                            enveloppe.sequence to PublishCertificate(domain, subdomain).toOption()
                        }
                        else -> enveloppe.sequence to None
                    }
                    LOGGER.info("Event adapt $event to $commands")
                    commands
                }
                .flatMap { (sequence, mayBeCommand) ->
                    when (mayBeCommand) {
                        is Some -> {
                            certificates.onCommand(mayBeCommand.t)
                                    .flatMap {
                                        LOGGER.info("Command success, committing from group id $GROUP_ID and sequence_num $sequence")
                                        eventStore.commit(GROUP_ID, sequence)
                                    }.toObservable()
                        }

                        is None -> {
                            LOGGER.info("Empty Command, committing from group id $GROUP_ID and sequence_num $sequence")
                            eventStore.commit(GROUP_ID, sequence).toObservable()
                        }
                    }
                }
    }

}