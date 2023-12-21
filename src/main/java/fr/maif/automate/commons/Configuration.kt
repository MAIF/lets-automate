package fr.maif.automate.commons

import arrow.core.Option
import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import java.util.concurrent.TimeUnit


data class LetsAutomateConfig(
        val http: HttpConfig,
        val env: Env,
        val ovh: Ovh,
        val logout: String,
        val certificates: CertificatesConfig,
        val letSEncrypt: LetSEncryptConfig,
        val postgresConfig: PostgresConfig,
        val clevercloud: CleverConfig,
        val teams: TeamsConfig,
        val otoroshi: OtoroshiConfig) {
    companion object {
        fun load(config: Config): LetsAutomateConfig {
            return LetsAutomateConfig(
                    http = HttpConfig.load(config),
                    env = Env.load(config),
                    ovh = Ovh.load(config),
                    logout = config.getString("logout"),
                    certificates = CertificatesConfig.load(config),
                    letSEncrypt = LetSEncryptConfig.load(config),
                    postgresConfig = PostgresConfig.load(config),
                    clevercloud = CleverConfig.load(config),
                    teams = TeamsConfig.load(config),
                    otoroshi = OtoroshiConfig.load(config)
            )
        }
    }
}

data class HttpConfig(val host: String, val port: Int) {
    companion object {
        fun load(config: Config): HttpConfig =
                HttpConfig(
                        config.getString("http.host"),
                        config.getInt("http.port")
                )
    }
}

data class CertificatesConfig(val pollingInterval: Interval) {
    companion object {
        fun load(config: Config): CertificatesConfig =
                CertificatesConfig(
                        pollingInterval = Interval.fromJson(config.getObject("certificates.pollingInterval"))
                )
    }
}

data class TeamsConfig(
        val url: String
) {
    companion object {
        fun load(config: Config): TeamsConfig =
                TeamsConfig(
                        url = config.getString("teams.url")
                )
    }
}

data class Ovh(
        val applicationKey: String,
        val applicationSecret: String,
        val consumerKey: String,
        val host: String
) {
    companion object {
        fun load(config: Config): Ovh =
                Ovh(
                        applicationKey = config.getString("ovh.applicationKey"),
                        applicationSecret = config.getString("ovh.applicationSecret"),
                        consumerKey = config.getString("ovh.consumerKey"),
                        host = config.getString("ovh.host")
                )
    }
}

data class Interval(val period: Long, val unit: TimeUnit) {
    companion object {
        fun fromJson(obj: ConfigObject): Interval {
            val conf = obj.toConfig()
            return Interval(
                    conf.getLong("period"),
                    TimeUnit.valueOf(conf.getString("unit"))
            )
        }
    }
}

data class LetSEncryptConfig(val server: String, val accountId: String) {
    companion object {
        fun load(config: Config): LetSEncryptConfig =
                LetSEncryptConfig(
                        server = config.getString("letsencrypt.server"),
                        accountId = config.getString("letsencrypt.accountId")
                )
    }
}

data class PostgresConfig(val host: String, val port: Int, val database: String, val username: Option<String>, val password: Option<String>, val maxPoolSize: Int) {
    companion object {
        fun load(config: Config): PostgresConfig =
                PostgresConfig(
                        host = config.getString("postgres.host"),
                        port = config.getInt("postgres.port"),
                        database = config.getString("postgres.database"),
                        username = Option(config.getString("postgres.username")),
                        password = Option(config.getString("postgres.password")),
                        maxPoolSize = config.getInt("postgres.maxPoolSize")
                )
    }
}

data class CleverConfig(val host: String, val consumerKey: String, val consumerSecret: String, val clientToken: String, val clientSecret: String) {
    companion object {
        fun load(config: Config): CleverConfig =
                CleverConfig(
                        host = config.getString("clevercloud.host"),
                        consumerKey = config.getString("clevercloud.consumerKey"),
                        consumerSecret = config.getString("clevercloud.consumerSecret"),
                        clientToken = config.getString("clevercloud.clientToken"),
                        clientSecret = config.getString("clevercloud.clientSecret")
                )
    }
}


sealed class Env {
    companion object {
        fun load(config: Config): Env =
                when (config.getString("env")) {
                    "dev" -> Dev
                    "prod" -> Prod
                    else -> throw IllegalArgumentException("Env type unknown ${config.getString("env")}")
                }
    }
}

object Dev : Env()
object Prod : Env()


data class OtoroshiConfig(
        val headerRequestId: String,
        val headerGatewayStateResp: String,
        val headerGatewayState: String,
        val headerClaim: String,
        val sharedKey: String,
        val issuer: String,
        val providerMonitoringHeader: String) {
    companion object {
        fun load(config: Config): OtoroshiConfig =
                OtoroshiConfig(
                        config.getString("otoroshi.headerRequestId"),
                        config.getString("otoroshi.headerGatewayStateResp"),
                        config.getString("otoroshi.headerGatewayState"),
                        config.getString("otoroshi.headerClaim"),
                        config.getString("otoroshi.sharedKey"),
                        config.getString("otoroshi.issuer"),
                        config.getString("otoroshi.providerMonitoringHeader")
                )
    }
}