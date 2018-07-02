package fr.maif.automate.publisher

import arrow.core.*
import fr.maif.automate.commons.CleverConfig
import fr.maif.automate.commons.Error
import fr.maif.automate.commons.stringify
import fr.maif.automate.letsencrypt.Certificate
import io.reactivex.Single
import io.vertx.reactivex.core.MultiMap
import io.vertx.reactivex.ext.web.client.WebClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.KeyPair
import java.util.*

interface CertificatePublisher {

    fun publishCertificate(domain: String, privateKey: KeyPair, csr: String, certificate: Certificate): Single<Either<Error, Unit>>

}

class CleverCloudCertificateConsumer(private val cleverConfig: CleverConfig, private val client: WebClient): CertificatePublisher {

    companion object {
        val random = Random().nextInt(1000000000)
        val LOGGER = LoggerFactory.getLogger(CleverCloudCertificateConsumer::class.java) as Logger
    }

    override fun publishCertificate(domain: String, privateKey: KeyPair, csr: String, certificate: Certificate): Single<Either<Error, Unit>> {
        val uri = "${cleverConfig.host}/v2/certificates"
        LOGGER.info("POST $uri")
        return client
                .postAbs(uri)
                .putHeader("Authorization", authorizationHeader())
                .rxSendForm(MultiMap.caseInsensitiveMultiMap()
                        .set("pem", "${privateKey.stringify()}\n${ certificate.chain.joinToString("\n") { it.stringify()} }")
                )
                .map { resp ->
                    LOGGER.info("POST $uri respond ${resp.statusCode()} ${resp.bodyAsString()}")
                    when(resp.statusCode()) {
                        200 ->
                            Right(Unit)
                        201 ->
                            Right(Unit)
                        else ->
                            Left(Error("Error while sending certificate to clever cloud: ${resp.statusCode()} ${resp.bodyAsString()}"))
                    }
                }
    }

    private fun authorizationHeader(): String {
        val attr = listOf(
            Pair("OAuth realm", "${cleverConfig.host}/oauth"),
            Pair("oauth_consumer_key", cleverConfig.consumerKey),
            Pair("oauth_token", cleverConfig.clientToken),
            Pair("oauth_signature_method", "PLAINTEXT"),
            Pair("oauth_signature", "${cleverConfig.consumerSecret}&${cleverConfig.clientSecret}"),
            Pair("oauth_timestamp", "${Math.floor( (System.currentTimeMillis() / 1000).toDouble() )}"),
            Pair("oauth_nonce", "$random")
        )
        return attr.joinToString(",") { (k, v) -> """$k="$v"""" }
    }

}