package server

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import io.circe.parser._
import io.circe.syntax._
import models.{ErrorResponse, RecipeRequest, RecipeResponse}
import models.RecipeModels._
import services.{InputSanitizer, OpenAIService}

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

class RecipeServer(openAIService: OpenAIService)(implicit system: ActorSystem[_]) {

  implicit val ec: ExecutionContextExecutor = system.executionContext

  // ── Security headers ─────────────────────────────────────────────────────

  private val securityHeaders: List[HttpHeader] = List(
    `Cache-Control`(CacheDirectives.`no-store`),
    RawHeader("X-Content-Type-Options", "nosniff"),
    RawHeader("X-Frame-Options", "DENY"),
    RawHeader("X-XSS-Protection", "1; mode=block"),
    RawHeader("Referrer-Policy", "strict-origin-when-cross-origin")
  )

  // ── JSON helpers ──────────────────────────────────────────────────────────

  private def jsonOk(json: String): HttpResponse =
    HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, json))

  private def jsonError(msg: String, status: StatusCode = StatusCodes.InternalServerError): HttpResponse =
    HttpResponse(status, entity = HttpEntity(ContentTypes.`application/json`,
      ErrorResponse(msg).asJson.noSpaces))

  // ── Exception handler ─────────────────────────────────────────────────────

  implicit val exHandler: ExceptionHandler = ExceptionHandler {
    case ex: Exception =>
      complete(jsonError(s"Internal error: ${ex.getMessage}"))
  }

  // ── Routes ────────────────────────────────────────────────────────────────

  val routes: Route = handleExceptions(exHandler) {
    respondWithHeaders(securityHeaders) {
      concat(

        // Single-page app root
        pathSingleSlash {
          getFromResource("static/index.html")
        },

        // Static assets
        pathPrefix("static") {
          getFromResourceDirectory("static")
        },

        // Health probe
        path("health") {
          get { complete("OK") }
        },

        // Recipe generation
        path("api" / "generate-recipes") {
          post {
            entity(as[String]) { rawBody =>
              parse(rawBody).flatMap(_.as[RecipeRequest]) match {

                case Left(err) =>
                  complete(jsonError(
                    s"Invalid request body: ${err.getMessage}", StatusCodes.BadRequest))

                case Right(req) =>
                  val ingredients  = InputSanitizer.sanitizeIngredients(req.ingredients)
                  val restrictions = InputSanitizer.sanitizeDietaryRestrictions(req.dietaryRestrictions)

                  if (ingredients.isEmpty)
                    complete(jsonError(
                      "Please provide at least one valid ingredient.", StatusCodes.BadRequest))
                  else {
                    val t0 = System.currentTimeMillis()
                    onComplete(openAIService.generateRecipes(ingredients, restrictions)) {
                      case Success(recipes) =>
                        val ms = System.currentTimeMillis() - t0
                        complete(jsonOk(RecipeResponse(recipes, ms).asJson.noSpaces))
                      case Failure(ex) =>
                        complete(jsonError(ex.getMessage))
                    }
                  }
              }
            }
          }
        }
      )
    }
  }
}
