package fr.maif.automate.certificate

import io.vertx.core.Handler
import io.vertx.reactivex.ext.web.RoutingContext
import fr.maif.automate.commons.*
import io.vertx.kotlin.core.json.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import arrow.core.*
import fr.maif.automate.certificate.write.*

class CertificateRouter(certificates: Certificates) {

    companion object {
        val LOGGER = LoggerFactory.getLogger(CertificateRouter::class.java) as Logger
    }

    val getDomain  = Handler<RoutingContext> { req ->
        val domain = req.pathParam("domain")
        certificates.allDomainsView.getDomain(domain)
                .subscribe ({ mayBeDomain ->
                    when(mayBeDomain) {
                        is Some -> req.response().endWithJson(mayBeDomain.t.json())
                        is None -> req.response().setStatusCode(404) .endWithJson(json { obj() })
                    }
                }, { err ->
                    LOGGER.error("Error during process", err)
                    req.response().end(err.message)
                })
    }

    val applyCommand = Handler<RoutingContext> { req ->
        val bodyAsJson = req.bodyAsJson
        LOGGER.info("Certificate command {}", bodyAsJson)

        val command = CertificateCommand.fromJson(bodyAsJson)
        certificates.onCommand(command)
                .subscribe ({ either ->
                    either.fold({ err ->
                        req.response()
                                .setStatusCode(400)
                                .endWithJson(err)
                    }, { _ ->
                        req.response()
                                .endWithJson( json{ obj("message" to "doing")})
                    })
                }, { err ->
                    LOGGER.error("Error during process", err)
                    req.response().end(err.message)
                })
    }


    val listCertificates = Handler<RoutingContext> { req ->
        certificates.allDomainsView.listDomains()
                .subscribe ({ json ->
                    req.response().endWithJson(json.map { it.json() })
                }, { err ->
                    LOGGER.error("Error during process", err)
                    req.response().end(err.message)
                })
    }


    val certificatesHistory = Handler<RoutingContext> { req ->
        val domain = req.pathParam("domain")
        certificates.eventsView.events(domain)
                .subscribe ({ json ->
                    req.response().endWithJson(Json.array(json))
                }, { err ->
                    LOGGER.error("Error during process", err)
                    req.response().end(err.message)
                })
    }



    val streamEvents = Handler<RoutingContext> { context ->

        val response = context.response()

        val lastId = context.request().getHeader("Last-Event-Id").toOption().map { it.toLong() }

        response.isChunked = true
        response.headers().add("Content-Type", "text/event-stream")
        response.headers().add("Cache-Control", "no-cache")
        response.headers().add("Connection", "keep-alive")
        response.statusCode = 200
        response.write("")
        certificates.eventsView.eventsStream(lastId).subscribe({ (id, evt) ->
            context.response().write("id: $id\ndata: ${evt.encode()}\n\n")
        }, { e ->
            //LOGGER.error("Error during sse", e)
            //context.response().end()
        })
    }

}
