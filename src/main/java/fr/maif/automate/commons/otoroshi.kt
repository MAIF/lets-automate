package fr.maif.automate.commons

import arrow.core.*
import arrow.instances.option.applicative.*
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import fr.maif.automate.administrator.Administrator
import io.vertx.core.Handler
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.reactivex.ext.web.RoutingContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class OtoroshiHandler(private val config: OtoroshiConfig, private val env: Env): Handler<RoutingContext> {

    companion object {
        val LOGGER = LoggerFactory.getLogger(OtoroshiHandler::class.java) as Logger
    }

    private val algorithm: Algorithm = Algorithm.HMAC512(config.sharedKey)

    private val verifier: JWTVerifier = JWT
            .require(algorithm)
            .withIssuer(config.issuer)
            .acceptLeeway(5000)
            .build()

    override fun handle(routingContext: RoutingContext) =
        when(env) {
            is Dev -> {
                routingContext.put("user", Administrator("ragnar", "ragnar.lothbrok@gmail.com", true))
                routingContext.next()
            }
            is Prod -> {
                val req = routingContext.request()
                val maybeReqId = req.getHeader(config.headerRequestId).toOption()
                val maybeState = req.getHeader(config.headerGatewayState).toOption()
                val maybeClaim = req.getHeader(config.headerClaim).toOption()
                LOGGER.info("New request ${req.method()} ${req.absoluteURI()} id = $maybeReqId, state = $maybeState, claim = $maybeClaim")
                maybeReqId.forall { id ->

                    val method = routingContext.request().method()
                    val uri = routingContext.request().uri()
                    val headers = routingContext.request().headers()
                    val strHeaders = headers.names().map { n ->
                        "$n: [${headers.getAll(n).joinToString("," )}]"
                    }
                    LOGGER.debug(
                            "Request from Gateway with id : $id => $method $uri with request headers $strHeaders"
                    )
                    true
                }

                Option.applicative().tupled(maybeState, maybeClaim).fix().map { (state, claim) ->

                    Try {
                        val decoded: DecodedJWT = verifier.verify(claim)
                        val otoAdmin: Option<Administrator> = Administrator.fromOtoroshiJwtToken(decoded)
                        when(otoAdmin) {
                            is Some -> {
                                routingContext.response().headers().add(config.headerGatewayStateResp, state)
                                routingContext.put("user", otoAdmin.t)
                                routingContext.next()
                            }
                            is None -> {
                                LOGGER.error("Error no session found ")
                                routingContext.response().headers().add(config.headerGatewayStateResp, state)
                                routingContext.response()
                                    .setStatusCode(401)
                                    .endWithJson(json { obj("message" to "Unauthorized" ) })
                            }
                        }


                    }.recover { e ->
                        LOGGER.error("Error decoding token ", e)
                        routingContext.response().headers().add(config.headerGatewayStateResp, maybeState.getOrElse { "--" })
                        routingContext.response()
                            .setStatusCode(401)
                            .endWithJson(json { obj("message" to "Unauthorized" ) })
                    }

                    Unit

                }.fix().getOrElse {
                    LOGGER.error("Error during otoroshi filter")
                    routingContext.response().headers().add(config.headerGatewayStateResp, maybeState.getOrElse { "--" })
                    routingContext.response()
                            .setStatusCode(401)
                            .endWithJson(json { obj("message" to "Unauthorized" ) })
                    Unit
                }
            }
        }
}