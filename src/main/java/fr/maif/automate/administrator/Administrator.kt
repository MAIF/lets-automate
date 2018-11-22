package fr.maif.automate.administrator

import arrow.core.*
import arrow.typeclasses.binding
import com.auth0.jwt.interfaces.DecodedJWT
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj


fun <T> Map<String, T>.getOption(key: String): Option<T> = this.get(key).toOption()


data class Administrator(val id: String, val email: String, val isAdmin: Boolean) {
    fun toJson(): JsonObject =
        json { obj(
                "id" to id,
                "email" to email,
                "isAdmin" to isAdmin
        )}

    companion object {

        fun fromOtoroshiJwtToken(jwt: DecodedJWT): Option<Administrator> {
            val claims = jwt.claims
            return claims.getOption("name").map{ it.asString() }.map {
                val userId = claims.getOption("user_id").map{it.asString()}.orElse { claims.get("user_id").toOption().map { it.asString() } }.getOrElse { "NA" }
                val email  = claims.getOption("email").map{ it.asString()}.getOrElse { "NA" }
                val isAdmin = claims
                        .getOption("izanami_admin")
                        .map{it.asString()}
                        .flatMap { Try{ it.toBoolean() }.toOption() }
                        .getOrElse { false }

                Administrator(userId, email, isAdmin)
            }
        }
    }
}

