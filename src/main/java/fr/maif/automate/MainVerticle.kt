package fr.maif.automate

import arrow.core.Either
import arrow.core.Some
import arrow.core.identity
import arrow.core.right
import arrow.mtl.instances.list.monadFilter.mapFilter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import fr.maif.automate.administrator.Administrator
import fr.maif.automate.certificate.CertificateRouter
import fr.maif.automate.certificate.Certificates
import fr.maif.automate.commons.*
import fr.maif.automate.dns.DnsRouter
import fr.maif.automate.dns.impl.OvhDnsManager
import fr.maif.automate.letsencrypt.Certificate
import fr.maif.automate.letsencrypt.impl.LetSEncryptManagerImpl
import fr.maif.automate.letsencrypt.impl.LetSEncryptUserPostgres
import fr.maif.automate.publisher.CertificatePublisher
import fr.maif.automate.publisher.CleverCloudCertificateConsumer
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.reactivex.core.AbstractVerticle
import io.vertx.reactivex.ext.sql.SQLClient
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.RoutingContext
import io.vertx.reactivex.ext.web.client.WebClient
import io.vertx.reactivex.ext.web.handler.BodyHandler
import io.vertx.reactivex.ext.web.handler.StaticHandler
import liquibase.Liquibase
import liquibase.database.Database
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.KeyPair
import java.util.concurrent.TimeUnit


class FakeCertificateConsumer() : CertificatePublisher {
    override fun publishCertificate(
        domain: String,
        privateKey: KeyPair,
        csr: String,
        certificate: Certificate
    ): Single<Either<Error, Unit>> {
        return Observable
            .interval(1, TimeUnit.SECONDS)
            .first(1)
            .map { domain.right() as Either<Error, Unit> }
    }
}

@Suppress("unused")
class MainVerticle : AbstractVerticle() {

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(MainVerticle::class.java)
    }

    override fun stop(stopFuture: Promise<Void>?) {
        LOGGER.info("Server is stopping bye bye")
        super.stop(stopFuture)
    }

    override fun start(startFuture: Promise<Void>) {
        ObjectMapper().registerKotlinModule()
        val config: Config = ConfigFactory.load()
        val letsAutomateConfig = LetsAutomateConfig.load(config)

        LOGGER.info("Configuration loaded $letsAutomateConfig")

        val pgConfig = letsAutomateConfig.postgresConfig
        initDb(pgConfig).subscribe({
            val jdbcClient = JDBCClient.createShared(vertx.delegate, pgConfigToJson(pgConfig))
            val postgresClient = SQLClient.newInstance(jdbcClient)

            val client = WebClient.create(vertx)
            val dnsManager = OvhDnsManager(client, vertx.createDnsClient(), letsAutomateConfig)
            val dnsRouter = DnsRouter(dnsManager)

            val letSEncryptUser = LetSEncryptUserPostgres(postgresClient)

            val certificatePublisher = when (letsAutomateConfig.env) {
                is Dev -> FakeCertificateConsumer()
                is Prod -> CleverCloudCertificateConsumer(letsAutomateConfig.clevercloud, client)
            }

            val letSEncryptManager = LetSEncryptManagerImpl(letsAutomateConfig.letSEncrypt, letSEncryptUser, dnsManager)
            val certificates =
                Certificates(letsAutomateConfig, letSEncryptManager, client, certificatePublisher, postgresClient)
            val certificateRouter = CertificateRouter(certificates)

            val otoroshiConfig = letsAutomateConfig.otoroshi
            val otoroshiHandler = OtoroshiHandler(otoroshiConfig, letsAutomateConfig.env)
            val router: Router = createRouter(letsAutomateConfig, dnsRouter, certificateRouter, otoroshiHandler)

            vertx.createHttpServer()
                .requestHandler(router)
                .listen(letsAutomateConfig.http.port, letsAutomateConfig.http.host) { result ->
                    if (result.succeeded()) {
                        LOGGER.info("Server has started on ${letsAutomateConfig.http.host}:${letsAutomateConfig.http.port}")
                        startFuture.complete()
                    } else {
                        LOGGER.error("Error while starting server", result.cause())
                        startFuture.fail(result.cause())
                    }
                }
        }, { err ->
            LOGGER.error("Error initializing DB ", err)
        })

    }

    private fun pgConfigToJson(pgConfig: PostgresConfig): JsonObject {
        val queryParams = listOf(pgConfig.username.map { "user=${it}" }, pgConfig.password.map { "password=${it}" })
            .mapFilter(::identity)
            .joinToString("&")
        val url = "jdbc:postgresql://${pgConfig.host}:${pgConfig.port}/${pgConfig.database}?${queryParams}"
        return json {
            obj(
                listOf(
                    Some("host" to pgConfig.host),
                    Some("port" to pgConfig.port),
                    Some("database" to pgConfig.database),
                    Some("url" to url),
                    Some("initial_pool_size" to 1),
                    Some("min_pool_size" to 1),
                    Some("max_pool_size" to pgConfig.maxPoolSize),
                    Some("max_idle_time" to 30),
                    pgConfig.username.map { "username" to it },
                    pgConfig.password.map { "password" to it }
                ).flatMap { it.toList() }
            )
        }
    }

    private fun initDb(pgConfig: PostgresConfig): Single<Unit> {

        return Single.fromCallable {
            val datasource = PGSimpleDataSource()
            datasource.serverNames = arrayOf(pgConfig.host)
            datasource.databaseName = pgConfig.database
            datasource.portNumbers = intArrayOf(pgConfig.port)
            pgConfig.username.fold({ Unit }, { u ->
                datasource.user = u
            })
            pgConfig.password.fold({ Unit }, { p ->
                datasource.password = p
            })

            val connection = datasource.connection
            val db: Database =
                DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
            val liquidbase = Liquibase("db_changes.sql", ClassLoaderResourceAccessor(), db)
            liquidbase.update("")
            connection.close()
        }.subscribeOn(Schedulers.io())
    }

    private fun createRouter(
        letsAutomateConfig: LetsAutomateConfig,
        dnsRouter: DnsRouter,
        certificateRouter: CertificateRouter,
        otoroshiHandler: OtoroshiHandler
    ): Router {
        val router = Router.router(vertx)
        router.route().handler(BodyHandler.create())
        router.route().handler(otoroshiHandler)
        router.route("/assets/*").handler(StaticHandler.create("public"))


        router.get("/").handler(handlerRoot(letsAutomateConfig))
        //Domains
        router.get("/api/domains").handler(dnsRouter.listDomains)
        router.post("/api/domains/:domain/records").handler(dnsRouter.createRecord)
        router.put("/api/domains/:domain/records/:recordId}").handler(dnsRouter.updateRecord)
        router.delete("/api/domains/:domain/records/:recordId").handler(dnsRouter.deleteRecord)
        //Certificates
        router.get("/api/certificates").handler(certificateRouter.listCertificates)
        router.get("/api/certificates/:domain/_history").handler(certificateRouter.certificatesHistory)
        router.get("/api/certificates/_events").handler(certificateRouter.streamEvents)
        router.post("/api/certificates/_commands").handler(certificateRouter.applyCommand)
        router.get("/api/certificates/:domain").handler(certificateRouter.getDomain)
        router.get("/*").handler(handlerRoot(letsAutomateConfig))
        return router
    }

    // Handlers
    private fun handlerRoot(letsAutomateConfig: LetsAutomateConfig) = Handler<RoutingContext> { req ->
        val admin = req.get<Administrator>("user")
        val jsonAdmin = admin.toJson().encode()
        val html = """
             <!DOCTYPE html>
              <html>
                <head>
                  <meta charset="UTF-8"/>
                  <title>Let's automate</title>
                  <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no" />
                  <link rel="stylesheet" href="/assets/css/bootstrap.min.css"></link>
                  <link rel="stylesheet" href="/assets/css/bootstrap-theme.min.css"></link>
                  <link rel="icon" type="image/png" href="/assets/css/img/favicon.png"></link>
                  <link href="https://fonts.googleapis.com/css?family=Roboto" rel="stylesheet"></link>
                  <link href="https://fonts.googleapis.com/css?family=Raleway:400,500" rel="stylesheet"></link>
                  <link rel="stylesheet" href="https://use.fontawesome.com/releases/v5.0.13/css/all.css" integrity="sha384-DNOHZ68U8hZfKXOrtjWvjxusGo9WQnrNx2sqG0tfsghAvtVlRW3tvkXWZh58N9jp" crossorigin="anonymous" >
                  <link href="https://maxcdn.bootstrapcdn.com/font-awesome/4.7.0/css/font-awesome.min.css" rel="stylesheet" integrity="sha384-wvfXpqpZZVQGK6TAh5PVlGOfQNHSoD2xbE+QkPxCAFlNEevoEH3Sl0sibVcOQVnN" crossorigin="anonymous"></link>
                </head>
                <body>
                ${
            if (letsAutomateConfig.env == Dev) {
                """<script src="http://localhost:3336/assets/bundle/LetsAutomate.js" type="text/javascript"></script>"""
            } else {
                """<script type="text/javascript" src="/assets/bundle/LetsAutomate.js"></script>"""
            }
        }
                  <div id="app"></div>
                  <script type="application/javascript">
                      LetsAutomate.init(document.getElementById("app"), '$jsonAdmin', '${letsAutomateConfig.logout}');
                  </script>
                </body>
              </html>
        """
        req.response()
            .putHeader("Content-Type", "text/html")
            .end(html)
    }

    private fun handlerDomains(dnsManager: OvhDnsManager) = Handler<RoutingContext> { req ->
        dnsManager.listDomains()
            .toList()
            .subscribe({ json ->
                req.response().endWithJson(json)
            }, { err ->
                req.response().end(err.message)
            })
    }

}