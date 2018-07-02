package fr.maif.automate.dns

import fr.maif.automate.commons.Error
import fr.maif.automate.commons.LetSEncryptConfig
import fr.maif.automate.commons.LetsAutomateConfig
import fr.maif.automate.commons.Ovh
import io.kotlintest.specs.FunSpec
import io.reactivex.Observable
import io.vertx.kotlin.core.json.Json
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.client.WebClient
import java.util.concurrent.TimeUnit

class DnsManagerTest: FunSpec() {

    init {
        testCreateRecord()
    }

    private fun testCreateRecord() = test("Create record to ovh") {

//        val letsAutomateConfig = LetsAutomateConfig(
//                ovh = Ovh(
//                    applicationKey = "oNWDI156akva3YdN",
//                    applicationSecret = "sUWJj7N8e0CdyeE0WH06xcyfiCl7Proj",
//                    consumerKey = "rmrfup2lKqjF3ZyzZJ5YIaOsOD9CD4aa",
//                    redirectHost = "NA",
//                    host = "https://api.ovh.com"
//                ),
//                letSEncrypt = LetSEncryptConfig("", "maif"))
//        val vertx = Vertx.vertx()
//        val client = WebClient.create(vertx)
//        val dnsManager = OvhDnsManager(client, vertx.createDnsClient(), letsAutomateConfig)


//        val message: Domain = dnsManager.listDomains().toList().blockingGet().get(0)
//        println(message)

//
//        try {
//
//            val recordCreated: Either<Error, Domain> = dnsManager.createRecord("adelegue.com", Record(
//                    name = null,
//                    target = "test",
//                    subDomain = "tutu",
//                    fieldType = "TXT",
//                    ttl = 0
//            )).blockingGet()
//            if (recordCreated.isRight) {
//                println(recordCreated.right().get())
//            } else {
//                println(recordCreated.left().get())
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }


//        try {
//
//            val recordCreated: Either<Error, Domain> = dnsManager.updateRecord("adelegue.com", 1546392325, Record(
//                    name = 1546392325,
//                    target = "test",
//                    subDomain = "_acme-challenge",
//                    fieldType = "TXT",
//                    ttl = 0
//            )).blockingGet()
//
//            if (recordCreated.isRight) {
//                println(recordCreated.right().get())
//            } else {
//                println(recordCreated.left().get())
//            }
//
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }


//        val check = Observable
//                .interval(2, TimeUnit.SECONDS)
//                .take(6, TimeUnit.HOURS)
//                .flatMap { _ ->
//                    println("New DNS check")
//                    dnsManager
//                                .checkRecord("adelegue.com", Record(null, "", 0, "TXT", "tutu"))
//                                .onErrorReturnItem(emptyList())
//                                .toObservable()
//                }
//                .filter { l -> l.isNotEmpty() }
//                .take(1)
//                .singleOrError()
//                .blockingGet()
//        println(check)

    }


}