package fr.maif.automate.certificate.write

import arrow.core.*
import fr.maif.automate.commons.Error
import fr.maif.automate.commons.eventsourcing.Command
import io.reactivex.Single
import io.vertx.core.json.JsonObject

sealed class CertificateCommand : Command {
    companion object {
        fun fromJson(json: JsonObject): CertificateCommand =
                when(json.getString("type")) {
                    nameOf(CreateCertificate::class) -> CreateCertificate.fromJson(json.getJsonObject("command"))
                    nameOf(OrderCertificate::class) -> OrderCertificate.fromJson(json.getJsonObject("command"))
                    nameOf(StartRenewCertificate::class) -> StartRenewCertificate.fromJson(json.getJsonObject("command"))
                    nameOf(RenewCertificate::class) -> RenewCertificate.fromJson(json.getJsonObject("command"))
                    nameOf(PublishCertificate::class) -> PublishCertificate.fromJson(json.getJsonObject("command"))
                    nameOf(DeleteCertificate::class) -> DeleteCertificate.fromJson(json.getJsonObject("command"))
                    else -> throw IllegalArgumentException("Unknown command $json")
                }

        private val regex = "^((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+".toRegex()

        fun validateSubdomain(domain: String, subdomain: Option<String>): Either<Error, Option<String>> = subdomain
                .map {s ->
                    Either.cond(!regex.matches(s), { subdomain }, { Error("Subdomain should end with ${domain}") })
                }
                .getOrElse { subdomain.right() }
    }
}
data class CreateCertificate(val domain: String, val subdomain: Option<String>, val wildcard: Boolean): CertificateCommand() {
    companion object {
        fun fromJson(json: JsonObject): CertificateCommand =
                CreateCertificate(
                        json.getString("domain"),
                        json.getString("subdomain").toOption().filter { it.isNotBlank() },
                        json.getBoolean("wildcard")
                )

        fun validate(command: CreateCertificate, state: State.AllCertificates): Either<Error, CreateCertificate> {
            val (domain, subdomain) = command
            return Either.cond(state.get(State.Key(domain, subdomain)).isEmpty(), { command }, { Error("Subdomain already exist") })
                    .flatMap { _ ->
                        validateSubdomain(domain, command.subdomain).map { _ ->  command}
                    }
        }


    }
}
data class OrderCertificate(val domain: String, val subdomain: Option<String>, val wildcard: Boolean): CertificateCommand() {
    companion object {
        fun fromJson(json: JsonObject): CertificateCommand =
                OrderCertificate(
                        json.getString("domain"),
                        json.getString("subdomain").toOption().filter { it.isNotBlank() },
                        json.getBoolean("wildcard")
                )

        fun validate(command: OrderCertificate, state: State.AllCertificates): Either<Error, OrderCertificate> {
            val (domain, subdomain) = command
            return Either.cond(state.get(State.Key(domain, subdomain)).isDefined(), { command }, { Error("Domain $domain should be created") })
                    .flatMap { _ ->
                        validateSubdomain(domain, command.subdomain).map { _ ->  command}
                    }
        }
    }
}
data class StartRenewCertificate(val domain: String, val subdomain: Option<String>, val wildcard: Boolean): CertificateCommand() {
    companion object {

        fun fromJson(json: JsonObject): CertificateCommand =
                StartRenewCertificate(
                        json.getString("domain"),
                        json.getString("subdomain").toOption().filter { it.isNotBlank() },
                        json.getBoolean("wildcard")
                )

        fun validate(command: StartRenewCertificate, state: State.AllCertificates): Either<Error, StartRenewCertificate> {
            val (domain, subdomain) = command
            return Either.cond(state.get(State.Key(domain, subdomain)).isDefined(), { command }, { Error("Domain $domain should be created") })
                    .flatMap { _ ->
                        validateSubdomain(domain, command.subdomain).map { _ ->  command}
                    }
        }
    }
}
data class RenewCertificate(val domain: String, val subdomain: Option<String>, val wildcard: Boolean): CertificateCommand() {
    companion object {

        fun fromJson(json: JsonObject): CertificateCommand =
                RenewCertificate(
                        json.getString("domain"),
                        json.getString("subdomain").toOption().filter { it.isNotBlank() },
                        json.getBoolean("wildcard")
                )

        fun validate(command: RenewCertificate, state: State.AllCertificates): Either<Error, RenewCertificate> {
            val (domain, subdomain) = command
            return state.get(State.Key(domain, subdomain)).toEither { Error("Domain $domain should be created") }
                    .flatMap { s ->
                        validateSubdomain(domain, command.subdomain).map { _ ->  command}
//                        .flatMap { _ ->
//                            Either.cond(!s.reordered, {command}, {Error("Invalid state, certificate should be reordered first")})
//                        }
                    }
        }
    }
}
data class PublishCertificate(val domain: String, val subdomain: Option<String>): CertificateCommand() {
    companion object {
        fun fromJson(json: JsonObject): CertificateCommand =
                PublishCertificate(
                        json.getString("domain"),
                        json.getString("subdomain").toOption().filter { it.isNotBlank() }
                )

        fun validate(command: PublishCertificate, state: State.AllCertificates): Either<Error, PublishCertificate> {
            val (domain, subdomain) = command
            return state.get(State.Key(domain, subdomain)).toEither { Error("Domain $domain should be created") }
                    .flatMap { c ->
                        Either.cond(!(c.certificate == null ||
                                        c.csr == null ||
                                        c.domain == null ||
                                        c.privateKey == null ||
                                        c.wildcard == null), { command }, { Error("Domain $domain should be created") })
                    }
        }

    }
}
data class DeleteCertificate(val domain: String, val subdomain: Option<String>): CertificateCommand() {
    companion object {
        fun fromJson(json: JsonObject): CertificateCommand =
                DeleteCertificate(
                        json.getString("domain"),
                        json.getString("subdomain").toOption().filter { it.isNotBlank() }
                )

        fun validate(command: DeleteCertificate, state: State.AllCertificates): Either<Error, DeleteCertificate> {
            val (domain, subdomain) = command
            return state.get(State.Key(domain, subdomain)).toEither { Error("Domain $domain should be created") }
                    .map { _ -> command }
        }

    }
}

