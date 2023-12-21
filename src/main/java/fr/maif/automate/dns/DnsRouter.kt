package fr.maif.automate.dns

import io.vertx.core.Handler
import io.vertx.reactivex.ext.web.RoutingContext
import fr.maif.automate.commons.*
import io.vertx.kotlin.core.json.*
import arrow.core.*

class DnsRouter(dnsManager: DnsManager) {

    val listDomains = Handler<RoutingContext> { req ->
        dnsManager.listDomains()
                .toList()
                .subscribe ({ json ->
                    req.response().endWithJson(json)
                }, { err ->
                    req.response().setStatusCode(500).endWithJson(json {
                        obj("message" to err.message)
                    })
                })
    }

    val createRecord = Handler<RoutingContext> { req ->
        val domain = req.pathParam("domain")
        val record = Record.fromJson(req.body().asJsonObject())
        dnsManager.createRecord(domain, record)
                .subscribe ({ result ->
                    when(result) {
                        is Either.Right ->
                            req.response().endWithJson(json { obj ()})
                        is Either.Left ->
                            req.response().setStatusCode(400).endWithJson(result.a)
                    }
                }, { err ->
                    req.response().setStatusCode(500).endWithJson(json {
                        obj("message" to err.message)
                    })
                })
    }

    val updateRecord = Handler<RoutingContext> { req ->
        val domain = req.pathParam("domain")
        val recordId = req.pathParam("recordId")
        val record = Record.fromJson(req.body().asJsonObject())
        dnsManager.updateRecord(domain, recordId.toLong(), record)
                .subscribe ({ result ->
                    when(result) {
                        is Either.Right ->
                            req.response().endWithJson(json { obj ()})
                        is Either.Left ->
                            req.response().setStatusCode(400).endWithJson(result.a)
                    }
                }, { err ->
                    req.response().setStatusCode(500).endWithJson(json {
                        obj("message" to err.message)
                    })
                })
    }

    val deleteRecord = Handler<RoutingContext> { req ->
        val domain = req.pathParam("domain")
        val recordId = req.pathParam("recordId")
        dnsManager.deleteRecord(domain, recordId.toLong())
                .subscribe ({ result ->
                    when(result) {
                        is Either.Right ->
                            req.response().endWithJson(json { obj() })
                        is Either.Left ->
                            req.response().setStatusCode(400).endWithJson(result.a)
                    }
                }, { err ->
                    req.response().setStatusCode(500).endWithJson(json {
                        obj("message" to err.message)
                    })
                })
    }

}
