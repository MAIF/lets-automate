package fr.maif.automate.certificate

import arrow.core.Either
import fr.maif.automate.certificate.eventhandler.EventToCommandAdapter
import fr.maif.automate.certificate.eventhandler.TeamsEventHandler
import fr.maif.automate.commons.LetsAutomateConfig
import fr.maif.automate.commons.eventsourcing.PostgresEventStore
import fr.maif.automate.letsencrypt.LetSEncryptManager
import fr.maif.automate.publisher.CertificatePublisher
import fr.maif.automate.certificate.scheduler.CertificateRenewer
import fr.maif.automate.certificate.views.AllDomainView
import fr.maif.automate.certificate.views.EventsView
import fr.maif.automate.certificate.write.*
import fr.maif.automate.commons.Error
import io.reactivex.Single
import io.vertx.reactivex.ext.asyncsql.AsyncSQLClient
import io.vertx.reactivex.ext.web.client.WebClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Certificates(
        letsAutomateConfig: LetsAutomateConfig,
        letSEncryptManager: LetSEncryptManager,
        client: WebClient,
        certificatePublisher: CertificatePublisher,
        postgresClient: AsyncSQLClient
) {
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(Certificates::class.java)
    }
    
    private val eventStore = PostgresEventStore("certificate_events", "certificate_events_offsets", postgresClient)
    private val eventReader = CertificateEventReader()
    private val certificateEventStore = CertificateEventStore("certificate", letSEncryptManager, certificatePublisher, eventStore, eventReader)
    private val certificateRenewer = CertificateRenewer(letsAutomateConfig.certificates.pollingInterval, this)
    private val eventToCommandAdapter = EventToCommandAdapter(eventStore, this,  eventReader)
    private val teamsEventHandler = TeamsEventHandler(letsAutomateConfig.env, letsAutomateConfig.teams, client, eventStore, eventReader)
    val allDomainsView = AllDomainView(eventStore, eventReader)
    val eventsView = EventsView(eventStore, eventReader)

    init {
        certificateRenewer.startScheduler()
        eventToCommandAdapter.startAdapter()
        teamsEventHandler.startTeamsHandler()
    }

    fun state(): Single<State.AllCertificates> = certificateEventStore.state()

    fun onCommand(command: CertificateCommand): Single<Either<Error, CertificateEvent>> = certificateEventStore.onCommand(command)

}
