package fr.maif.automate.dns.impl

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import fr.maif.automate.commons.Error
import fr.maif.automate.commons.HashUtils
import fr.maif.automate.commons.LetsAutomateConfig
import fr.maif.automate.dns.*
import fr.maif.automate.dns.Unit
import io.reactivex.Observable
import io.reactivex.Single
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.reactivex.core.buffer.Buffer
import io.vertx.reactivex.core.dns.DnsClient
import io.vertx.reactivex.ext.web.client.HttpResponse
import io.vertx.reactivex.ext.web.client.WebClient


class OvhDnsManager(private val client: WebClient, private val dnsClient: DnsClient, private val letsAutomateConfig: LetsAutomateConfig) : DnsManager {

    companion object {
        val LOGGER = LoggerFactory.getLogger(OvhDnsManager::class.java) as Logger
    }

    private fun signRequest(method: HttpMethod = HttpMethod.GET, query: String, body: String = "", ts: Long): String {
        val toSign = "${letsAutomateConfig.ovh.applicationSecret}+${letsAutomateConfig.ovh.consumerKey}+${method.name}+$query+$body+$ts"
        val signed = HashUtils.sha1(toSign)
        LOGGER.debug("Signing $toSign to $signed")
        return "$1$$signed"
    }

    private fun records(zone: String): Single<List<Record>> {
        val uri = "/1.0/domain/zone/$zone/record"
        return sendRequest(HttpMethod.GET, uri)
                .flatMap { response ->
                    when (response.statusCode()) {
                        200 -> {
                            LOGGER.info("GET $uri respond ${response.statusCode()} ${response.bodyAsString()}")
                            Observable.fromIterable(response.bodyAsJsonArray().list)
                                    .flatMap {r ->
                                        val recordId = r.toString().toLong()
                                        record(zone, recordId).toObservable()
                                                .map { obj ->
                                                    Record.fromJson(obj).copy(id = recordId)
                                                }
                                    }
                                    .toList()
                        }
                        else ->
                            Single.error(RuntimeException("Error fetching records ${response.bodyAsString()}"))
                    }
                }
    }

    private fun record(zone: String, record: Long): Single<JsonObject> {
        val uri = "/1.0/domain/zone/$zone/record/$record"
        return sendRequest(HttpMethod.GET, uri)
                .map { response ->
                    LOGGER.info("GET $uri respond ${response.statusCode()} ${response.bodyAsString()}")
                    response.bodyAsJsonObject()
                }
    }


    private fun getTimestamp(): Single<Long> {
        val uri = letsAutomateConfig.ovh.host + "/1.0/auth/time"
        return client.getAbs(uri).rxSend().flatMap { response ->
            LOGGER.info("GET $uri respond ${response.statusCode()} ${response.bodyAsString()}")
            if (response.statusCode() == 200) {
                Single.just(response.bodyAsString().toLong())
            } else{
                Single.error(RuntimeException("Error getting timestamp ${response.bodyAsString()}"))
            }
        }
    }


    private fun sendRequest(method: HttpMethod = HttpMethod.GET, query: String, body: String = ""): Single<HttpResponse<Buffer>> =
            getTimestamp().flatMap { timestamp ->
                val signRequest = signRequest(method, letsAutomateConfig.ovh.host + query, body, timestamp)
                val requestBuilder = client.requestAbs(method, letsAutomateConfig.ovh.host + query)
                        .timeout(5000)
                        .putHeader("X-Ovh-Timestamp", timestamp.toString())
                        .putHeader("X-Ovh-Signature", signRequest)
                        .putHeader("X-Ovh-Application", letsAutomateConfig.ovh.applicationKey)
                        .putHeader("X-Ovh-Consumer", letsAutomateConfig.ovh.consumerKey)


                LOGGER.debug("Sending $method to $query with body $body, signature $signRequest and timestamp $timestamp")
                if (body.isEmpty()) {
                    requestBuilder.rxSend()
                } else {
                    requestBuilder
                            .putHeader("Content-Type", "application/json")
                            .rxSendBuffer(Buffer.buffer(body))
                }
            }


    private fun refreshDomain(domain: String): Single<Either<Error, Unit>> {
        val uri = "/1.0/domain/zone/$domain/refresh"
        return sendRequest(HttpMethod.POST, uri)
                .map { resp ->
                    LOGGER.info("GET $uri respond ${resp.statusCode()} ${resp.bodyAsString()}")
                    when(resp.statusCode()) {
                        200 -> Unit.right()
                        else -> Error("Error while refreshing domain ${resp.bodyAsString()}").left()
                    }
                }

    }

    private fun refreshDomainAndGetDomain(domain: String): Single<Either<Error, Domain>> {
        return refreshDomain(domain).flatMap { r ->
            when(r) {
                is Either.Right ->
                    getDomain(domain)
                            .map { it.right() }
                is Either.Left ->
                    Single.just(r.a.left())
            }

        }
    }


    override fun checkRecord(domain: String, record: Record): Single<List<String>> =
            when(record.fieldType) {
                "TXT" -> dnsClient.rxResolveTXT("${record.subDomain}.$domain")
                else -> Single.just(emptyList())
            }


    override fun listDomains(): Observable<DomainResume> {
        val uri = "/1.0/domain/zone"
        return sendRequest(HttpMethod.GET, uri)
                .toObservable()
                .flatMap { response ->
                    LOGGER.info("GET $uri respond ${response.statusCode()} ${response.bodyAsString()}")
                    Observable.fromIterable(response.bodyAsJsonArray().list)
                            .map { z ->
                                DomainResume(z.toString())
                            }
                }
    }

    override fun getDomain(name: String): Single<Domain> {
        return records(name)
                .map { r -> Domain(name, r) }
    }


    override fun createRecord(domain: String, record: Record): Single<Either<Error, Record>> {

        val jsonString = json {
            obj(
                    "fieldType" to record.fieldType,
                    "subDomain" to record.subDomain,
                    "target" to record.target,
                    "ttl" to record.ttl
            )
        }.toBuffer().toString("utf-8")

        val uri = "/1.0/domain/zone/$domain/record/"
        return sendRequest(HttpMethod.POST, uri, jsonString)
                .flatMap { resp ->
                    LOGGER.info("POST $uri respond ${resp.statusCode()} ${resp.bodyAsString()}")
                    when(resp.statusCode()) {
                        200 ->
                            refreshDomain(domain)
                                    .map { _ -> record.right() }
                        else ->
                            Single.just(Error("Error while creating record ${resp.bodyAsString()}").left())
                    }
                }
    }




    override fun updateRecord(domain: String, recordId: Long, record: Record): Single<Either<Error, Record>> {

        val jsonString = json {
            obj(
                    "subDomain" to record.subDomain,
                    "target" to record.target,
                    "ttl" to record.ttl
            )
        }.toBuffer().toString("utf-8")

        val uri = "/1.0/domain/zone/$domain/record/$recordId"
        return sendRequest(HttpMethod.PUT, uri, jsonString)
                .flatMap { resp ->
                    LOGGER.info("PUT $uri respond ${resp.statusCode()} ${resp.bodyAsString()}")
                    when(resp.statusCode()) {
                        200 -> refreshDomain(domain)
                                .map { _ -> record.right() }
                        else ->
                            Single.just(Error("Error while updating record ${resp.bodyAsString()}").left())
                    }
                }

    }
    override fun deleteRecord(domain: String, recordId: Long): Single<Either<Error, Unit>> {
        val uri = "/1.0/domain/zone/$domain/record/$recordId"
        return sendRequest(HttpMethod.DELETE, uri)
                .flatMap { resp ->
                    LOGGER.info("DELETE $uri respond ${resp.statusCode()} ${resp.bodyAsString()}")
                    when(resp.statusCode()) {
                        200 ->
                            refreshDomain(domain)
                        else ->
                            Single.just(Error("Error while updating record ${resp.bodyAsString()}").left())
                    }
                }
    }
}