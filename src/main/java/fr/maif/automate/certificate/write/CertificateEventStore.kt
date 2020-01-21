package fr.maif.automate.certificate.write

import arrow.core.*
import arrow.core.Either.Left
import arrow.core.Either.Right
import fr.maif.automate.commons.Error
import fr.maif.automate.commons.eventsourcing.EventEnvelope
import fr.maif.automate.commons.eventsourcing.EventReader
import fr.maif.automate.commons.eventsourcing.EventStore
import fr.maif.automate.commons.eventsourcing.Store
import fr.maif.automate.letsencrypt.LetSEncryptManager
import fr.maif.automate.publisher.CertificatePublisher
import io.reactivex.Single
import java.time.LocalDateTime
import kotlin.reflect.KClass


fun <T : Any> nameOf(c: KClass<T>): String = c.java.simpleName


class CertificateEventStore (
        id: String,
        private val letSEncryptManager: LetSEncryptManager,
        private val certificatePublisher: CertificatePublisher,
        eventStore: EventStore,
        private val eventReader: EventReader<CertificateEvent> = CertificateEventReader()
): Store<State.AllCertificates, CertificateCommand, CertificateEvent>(
        id,
        { State.AllCertificates() },
        eventStore
    ) {

    override fun applyEventToState(current: State.AllCertificates, event: EventEnvelope): State.AllCertificates {
        val evt = eventReader.read(event)
        return updateState(current, evt)
    }

    private fun updateState(current: State.AllCertificates, event: CertificateEvent): State.AllCertificates =
        when(event) {
            is CertificateCreated -> {
                val (domain, subdomain, wildcard) = event
                current.update(State.Key(domain, subdomain)){ it.copy(domain = domain, subdomain = subdomain, wildcard = wildcard) }
            }
            is CertificateOrdered -> {
                val (domain, subdomain, wildcard, privateKey, csr, certificate) = event
                current.update(State.Key(domain, subdomain)){ it.copy(domain = domain, subdomain = subdomain, wildcard = wildcard, privateKey = privateKey, csr = csr, certificate = certificate) }
            }
            is CertificateOrderFailure -> {
                current
            }
            is CertificateReOrderedStarted -> {
                val (domain, subdomain, wildcard) = event
                current.update(State.Key(domain, subdomain)){ it.copy(domain = domain, subdomain = subdomain, wildcard = wildcard, reordered = true) }
            }
            is CertificateReOrdered -> {
                val (domain, subdomain, wildcard, privateKey, csr, certificate) = event
                current.update(State.Key(domain, subdomain)){ it.copy(domain = domain, subdomain = subdomain, wildcard = wildcard, reordered = false, privateKey = privateKey, csr = csr, certificate = certificate) }
            }
            is CertificateReOrderFailure -> {
                current
            }
            is CertificatePublished -> {
                val (domain, subdomain) = event
                current.update(State.Key(domain, subdomain)){ it.copy(subdomain = subdomain, publishedDate = LocalDateTime.now()) }
            }
            is CertificatePublishFailure -> {
                current
            }
            is CertificateDeleted -> {
                val (domain, subdomain) = event
                current.delete(State.Key(domain, subdomain))
            }
        }

    override fun applyCommand(state: State.AllCertificates, command: CertificateCommand): Single<Either<Error, CertificateEvent>> {
        LOGGER.info("Handling command $command ")
        return when(command) {
            is CreateCertificate -> {
                val (domain, subdomain, wildcard) = command
                val validation = CreateCertificate.validate(command, state)
                when (validation) {
                    is Left -> Single.just(validation.a.left() as Either<Error, CertificateEvent>)
                    is Right -> {
                        val event: CertificateEvent = CertificateCreated(domain, subdomain, wildcard)
                        persist(domain, event).map { it.map { _ ->  event } }
                    }

                }
            }
            is OrderCertificate -> {
                val (domain, subdomain, wildcard) = command
                val validation = OrderCertificate.validate(command, state)
                when (validation) {
                    is Left -> Single.just(validation.a.left() as Either<Error, CertificateEvent>)
                    is Right -> {
                        letSEncryptManager
                                .orderCertificate(domain, subdomain, wildcard)
                                .flatMap { r ->
                                    when(r) {
                                        is Right -> {
                                            val (_, privateKey, csr, certificate) = r.b
                                            val event: CertificateEvent = CertificateOrdered(domain, subdomain, wildcard, privateKey, csr, certificate)
                                            persist(domain, event).map { it.map { _ ->  event } }
                                        }
                                        is Left -> {
                                            val event = CertificateOrderFailure(domain, subdomain, r.a.message)
                                            persist(domain, event).map { _ -> Left(r.a) }
                                        }
                                    }
                                }.onErrorResumeNext { e ->
                                    LOGGER.error("Error while ordering certificate", e)
                                    val cause = e.message ?: ""
                                    val event = CertificateOrderFailure(domain, subdomain, cause)
                                    persist(domain, event)
                                            .map { e ->
                                                LOGGER.error("Error persisting event $event ", e)
                                                Left(Error(cause))
                                            }
                                }
                    }
                }
            }
            is StartRenewCertificate -> {
                val (domain, subdomain, wildcard) = command
                val validation = StartRenewCertificate.validate(command, state)
                when (validation) {
                    is Left -> Single.just(validation.a.left() as Either<Error, CertificateEvent>)
                    is Right -> {
                        val event: CertificateEvent = CertificateReOrderedStarted(domain, subdomain, wildcard)
                        persist(domain, event).map { it.map { _ ->  event } }
                    }
                }
            }
            is RenewCertificate -> {
                val (domain, subdomain, wildcard) = command
                val validation = RenewCertificate.validate(command, state)
                when (validation) {
                    is Left -> Single.just(validation.a.left() as Either<Error, CertificateEvent>)
                    is Right -> {
                        letSEncryptManager
                                .orderCertificate(domain , subdomain, wildcard)
                                .flatMap { r ->
                                    when (r) {
                                        is Right -> {
                                            val (_, privateKey, csr, certificate) = r.b
                                            val event: CertificateEvent = CertificateReOrdered(domain, subdomain, wildcard, privateKey, csr, certificate)
                                            persist(domain, event).map { it.map { _ -> event } }
                                        }
                                        is Left -> {
                                            LOGGER.error("Error while re-ordering certificate $r")
                                            val event = CertificateReOrderFailure(domain, subdomain, r.a.message)
                                            persist(domain, event)
                                                    .map { _ -> Left(r.a) }
                                        }
                                    }
                                }.onErrorResumeNext { e ->
                                    LOGGER.error("Error while re-ordering certificate", e)
                                    val cause = e.message ?: ""
                                    val event = CertificateReOrderFailure(domain, subdomain, cause)
                                    persist(domain, event)
                                            .map { e ->
                                                LOGGER.error("Error persisting event $event ", e)
                                                Left(Error(cause))
                                            }
                                }
                    }
                }
            }
            is DeleteCertificate -> {
                val (domain, subdomain) = command
                val validation = DeleteCertificate.validate(command, state)
                when (validation) {
                    is Left -> Single.just(validation.a.left() as Either<Error, CertificateEvent>)
                    is Right -> {
                        val event: CertificateEvent = CertificateDeleted(domain, subdomain)
                        persist(domain, event).map { it.map { _ -> event } }
                    }
                }
            }
            is PublishCertificate -> {
                val (domain, subdomain) = command
                val validation = PublishCertificate.validate(command, state)
                when (validation) {
                    is Left -> Single.just(validation.a.left() as Either<Error, CertificateEvent>)
                    is Right -> {
                        val mayBeState: Option<State.CertificateState> = state.get(State.Key(domain, subdomain))
                        when (mayBeState) {
                            is Some ->
                                certificatePublisher.publishCertificate(domain, mayBeState.t.privateKey!!, mayBeState.t.csr!!, mayBeState.t.certificate!!)
                                    .flatMap {r ->
                                        when (r) {
                                            is Right -> {
                                                val event: CertificateEvent = CertificatePublished(domain, subdomain, LocalDateTime.now())
                                                persist(domain, event).map { it.map { _ -> event } }
                                            }
                                            is Left -> {
                                                val event = CertificatePublishFailure(domain, subdomain, r.a.message)
                                                persist(domain, event)
                                                    .map { _ -> Left(r.a) }
                                            }
                                        }
                                    }.onErrorResumeNext { e ->
                                        LOGGER.error("Error while publishing certificate", e)
                                        val cause = e.message ?: ""
                                        val event = CertificatePublishFailure(domain, subdomain, cause)
                                        persist(domain, event)
                                            .retry(3)
                                            .map { _ -> Left(Error(cause)) }
                                    }
                            is None ->
                                Single.just(
                                    CertificatePublishFailure(domain, subdomain, "Certificate state not found for $domain $subdomain").left() as Either<Error, CertificateEvent>
                                )
                        }

                    }
                }
            }
        }
    }

}