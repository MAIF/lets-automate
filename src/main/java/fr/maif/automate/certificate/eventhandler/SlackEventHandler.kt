package fr.maif.automate.certificate.eventhandler

import arrow.core.getOrElse
import fr.maif.automate.certificate.write.*
import fr.maif.automate.commons.Dev
import fr.maif.automate.commons.Env
import fr.maif.automate.commons.SlackConfig
import fr.maif.automate.commons.eventsourcing.EventReader
import fr.maif.automate.commons.eventsourcing.EventStore
import io.reactivex.Single
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.reactivex.ext.web.client.WebClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class SlackEventHandler(val env: Env, val config: SlackConfig, val client: WebClient, val eventStore: EventStore, private val eventReader: EventReader<CertificateEvent>) {

    companion object {
        val LOGGER = LoggerFactory.getLogger(SlackEventHandler::class.java) as Logger
        val GROUP_ID = SlackEventHandler::class.java.simpleName
    }

    val slackApi = "https://slack.com/api"

    fun startSlackHandler() {
        LOGGER.info("Starting slack event handler")
        eventStore
                .eventStreamByGroupId(GROUP_ID)
                .map { enveloppe -> enveloppe.sequence to eventReader.read(enveloppe) }
                .map { (sequence, event) ->
                    when(event) {
                        is CertificateCreated -> {
                            sequence to "A Certificate is asked ${event.subdomain.map { "$it." }.getOrElse { "" }}${event.domain} with wildcard=${event.wildcard}"
                        }
                        is CertificateOrdered -> {
                            sequence to "A Certificate order succeed for ${event.subdomain.map { "$it." }.getOrElse { "" }}${event.domain}"
                        }
                        is CertificateOrderFailure -> {
                            sequence to "A Certificate order failed for ${event.subdomain.map { "$it." }.getOrElse { "" }}${event.domain}"
                        }
                        is CertificateReOrderedStarted -> {
                            sequence to "A Certificate renew was asked for ${event.subdomain.map { "$it." }.getOrElse { "" }}${event.domain}"
                        }
                        is CertificateReOrdered -> {
                            sequence to "A Certificate renew succeed for ${event.subdomain.map { "$it." }.getOrElse { "" }}${event.domain}"
                        }
                        is CertificateReOrderFailure -> {
                            sequence to "A Certificate renew failed for ${event.subdomain.map { "$it." }.getOrElse { "" }}${event.domain}"
                        }
                        is CertificatePublished -> {
                            sequence to "The Certificate for ${event.subdomain.map { "$it." }.getOrElse { "" }}${event.domain} has been published"
                        }
                        is CertificatePublishFailure -> {
                            sequence to "The Certificate for ${event.subdomain.map { "$it." }.getOrElse { "" }}${event.domain} fail to publish"
                        }
                        is CertificateDeleted -> {
                            sequence to "The Certificate for ${event.subdomain.map { "$it." }.getOrElse { "" }}${event.domain} has been deleted"
                        }
                    }
                }
                .subscribe { (sequence, message) ->
                    sendMessage(sequence,message).subscribe()
                }
    }

    fun sendMessage(sequence: Long, message: String): Single<Unit> {
        return when(env) {
            is Dev -> Single.just(Unit)
            else -> {
                LOGGER.info("""Sending "$message" to slack channel ${config.channel} with token: ${config.token}""")

                client
                        .postAbs("$slackApi/chat.postMessage")
                        .putHeader("Authorization", "Bearer ${config.token}")
                        .putHeader("Content-Type", "application/json")
                        .rxSendJsonObject(json {
                            obj(
                                    "token" to config.token,
                                    "channel" to config.channel,
                                    "text" to message
                            )
                        })
                        .doFinally {
                            LOGGER.info("Commiting for $sequence for $GROUP_ID")
                            eventStore.commit(GROUP_ID, sequence).subscribe()
                        }
                        .doOnSuccess { res ->
                            LOGGER.info("Message has been sent to slack: ${res.statusCode()} with body ${res.bodyAsString()}")
                        }
                        .doOnError { e ->
                            LOGGER.error("Error sending to slack", e)
                        }
                        .map { _ -> Unit }
            }
        }
    }

}