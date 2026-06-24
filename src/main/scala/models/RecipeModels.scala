package models

import io.circe.{Decoder, Encoder, HCursor}
import io.circe.generic.semiauto._

// ── HTTP Request / Response wire types ─────────────────────────────────────

case class RecipeRequest(
  ingredients:         List[String],
  dietaryRestrictions: List[String]
)

case class RecipeIngredient(name: String, amount: String)

case class RecipeStep(stepNumber: Int, instruction: String)

case class Recipe(
  title:       String,
  description: String,
  prepTime:    String,
  cookTime:    String,
  servings:    Int,
  dietaryInfo: List[String],
  ingredients: List[RecipeIngredient],
  steps:       List[RecipeStep]
)

case class RecipeResponse(recipes: List[Recipe], generationTimeMs: Long)

case class ErrorResponse(error: String)

// ── Circe codecs ────────────────────────────────────────────────────────────

object RecipeModels {

  implicit val ingredientDecoder: Decoder[RecipeIngredient] = deriveDecoder
  implicit val ingredientEncoder: Encoder[RecipeIngredient] = deriveEncoder

  implicit val stepDecoder: Decoder[RecipeStep] = deriveDecoder
  implicit val stepEncoder: Encoder[RecipeStep] = deriveEncoder

  // Lenient recipe decoder: treats missing/null dietaryInfo as empty list
  implicit val recipeDecoder: Decoder[Recipe] = (c: HCursor) =>
    for {
      title       <- c.downField("title").as[String]
      description <- c.downField("description").as[String]
      prepTime    <- c.downField("prepTime").as[String]
      cookTime    <- c.downField("cookTime").as[String]
      servings    <- c.downField("servings").as[Int]
      dietaryInfo <- c.downField("dietaryInfo").as[Option[List[String]]].map(_.getOrElse(Nil))
      ingredients <- c.downField("ingredients").as[List[RecipeIngredient]]
      steps       <- c.downField("steps").as[List[RecipeStep]]
    } yield Recipe(title, description, prepTime, cookTime, servings, dietaryInfo, ingredients, steps)

  implicit val recipeEncoder: Encoder[Recipe] = deriveEncoder

  implicit val requestDecoder:  Decoder[RecipeRequest]  = deriveDecoder
  implicit val responseEncoder: Encoder[RecipeResponse] = deriveEncoder
  implicit val errorEncoder:    Encoder[ErrorResponse]  = deriveEncoder
}

