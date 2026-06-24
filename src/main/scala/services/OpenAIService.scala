package services

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

import io.circe.parser._
import io.circe.syntax._
import models.Recipe
import models.RecipeModels._

import scala.concurrent.{ExecutionContext, Future}

/** Calls the OpenAI Chat Completions API to generate recipe suggestions.
  * Uses Java's built-in HttpClient to avoid extra dependencies.
  */
class OpenAIService(apiKey: String, model: String = "gpt-4o-mini", timeoutSeconds: Int = 45)(
  implicit ec: ExecutionContext
) {

  private val httpClient: HttpClient =
    HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build()

  private val OpenAIUrl = "https://api.openai.com/v1/chat/completions"

  // ── Public API ─────────────────────────────────────────────────────────────

  def generateRecipes(
    ingredients:         List[String],
    dietaryRestrictions: List[String]
  ): Future[List[Recipe]] = Future {
    val prompt        = buildPrompt(ingredients, dietaryRestrictions)
    val requestBody   = buildOpenAIPayload(prompt)
    val responseBody  = callOpenAI(requestBody)
    parseRecipes(responseBody)
  }

  // ── Prompt engineering ─────────────────────────────────────────────────────

  private def buildPrompt(ingredients: List[String], restrictions: List[String]): String = {
    val ingredientList  = ingredients.mkString(", ")
    val dietaryClause   =
      if (restrictions.isEmpty) ""
      else s"\nIMPORTANT: ALL three recipes MUST strictly be ${restrictions.mkString(", ")}."

    s"""You are a professional chef and culinary expert.
       |Generate exactly 3 distinct, creative, and practical recipes using some or all of these ingredients: $ingredientList.$dietaryClause
       |
       |Rules:
       |- Each recipe must be genuinely different (different cuisine/cooking method).
       |- Use metric measurements (g, ml, kg, L, °C).
       |- Provide 5-8 ingredients and 5-8 clear, numbered steps per recipe.
       |
       |Return a JSON object with the following structure (no markdown, no extra text):
       |{
       |  "recipes": [
       |    {
       |      "title": "Recipe Title",
       |      "description": "One to two sentence description",
       |      "prepTime": "15 minutes",
       |      "cookTime": "30 minutes",
       |      "servings": 4,
       |      "dietaryInfo": ["vegan"],
       |      "ingredients": [
       |        { "name": "ingredient name", "amount": "200g" }
       |      ],
       |      "steps": [
       |        { "stepNumber": 1, "instruction": "Detailed step description." }
       |      ]
       |    }
       |  ]
       |}""".stripMargin
  }

  // ── HTTP call ──────────────────────────────────────────────────────────────

  private def buildOpenAIPayload(userPrompt: String): String = {
    val escapedPrompt = io.circe.Json.fromString(userPrompt).noSpaces
    s"""{
       |  "model": "$model",
       |  "response_format": { "type": "json_object" },
       |  "messages": [
       |    {
       |      "role": "system",
       |      "content": "You are a professional chef. Always respond with valid JSON only."
       |    },
       |    {
       |      "role": "user",
       |      "content": $escapedPrompt
       |    }
       |  ],
       |  "temperature": 0.85,
       |  "max_tokens": 3500
       |}""".stripMargin
  }

  private def callOpenAI(body: String): String = {
    val request = HttpRequest.newBuilder()
      .uri(URI.create(OpenAIUrl))
      .header("Content-Type", "application/json")
      .header("Authorization", s"Bearer $apiKey")
      .timeout(Duration.ofSeconds(timeoutSeconds.toLong))
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build()

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

    response.statusCode() match {
      case 200 => // ok
      case 401 =>
        throw new RuntimeException(
          "OpenAI API key is invalid or missing. " +
          "Set the OPENAI_API_KEY environment variable to your key from " +
          "https://platform.openai.com/api-keys and restart the server."
        )
      case 429 =>
        throw new RuntimeException(
          "OpenAI rate limit exceeded. Please wait a moment and try again."
        )
      case 503 =>
        throw new RuntimeException(
          "OpenAI service is temporarily unavailable. Please try again shortly."
        )
      case code =>
        throw new RuntimeException(
          s"OpenAI API error $code: ${response.body().take(300)}"
        )
    }

    response.body()
  }

  // ── Response parsing ───────────────────────────────────────────────────────

  private def parseRecipes(openAiResponse: String): List[Recipe] = {
    val content = extractMessageContent(openAiResponse)

    // Strip potential markdown code fences the model may still emit
    val cleaned = content
      .replaceAll("(?s)```json\\s*", "")
      .replaceAll("(?s)```\\s*", "")
      .trim

    parse(cleaned)
      .flatMap(_.hcursor.downField("recipes").as[List[Recipe]])
      .fold(
        err => throw new RuntimeException(s"Failed to parse recipes JSON: $err\nRaw content: ${cleaned.take(400)}"),
        identity
      )
  }

  private def extractMessageContent(raw: String): String =
    parse(raw)
      .flatMap(
        _.hcursor
          .downField("choices")
          .downArray
          .downField("message")
          .downField("content")
          .as[String]
      )
      .fold(
        err => throw new RuntimeException(s"Cannot extract content from OpenAI response: $err"),
        identity
      )
}

