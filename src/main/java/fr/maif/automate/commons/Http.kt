package fr.maif.automate.commons

import io.vertx.core.json.Json
import io.vertx.reactivex.core.http.HttpServerResponse


/**
 * Extension to the HTTP response to output JSON objects.
 */
fun HttpServerResponse.endWithJson(obj: Any) {
    this.putHeader("Content-Type", "application/json; charset=utf-8").end(Json.encodePrettily(obj))
}