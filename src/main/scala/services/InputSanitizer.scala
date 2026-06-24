package services

/** Sanitizes all user-supplied text to prevent injection attacks,
  * script injection, and prompt-injection into the LLM.
  */
object InputSanitizer {

  private val MaxIngredientLength = 50
  private val MaxIngredients      = 30

  /** Allowed characters in a single ingredient name. */
  private val AllowedChars = """[^a-zA-Z0-9\s\-',.()]""".r

  /** Allowed dietary restriction labels (whitelist). */
  private val AllowedRestrictions: Set[String] =
    Set("vegan", "vegetarian", "gluten-free", "nut-free", "dairy-free", "keto", "paleo", "halal", "kosher")

  /** Strip HTML tags, control characters, and other dangerous content. */
  private def stripDangerousContent(raw: String): String =
    raw
      .replaceAll("<[^>]*>", "")          // HTML tags
      .replaceAll("&[a-zA-Z]+;", "")      // HTML entities
      .replaceAll("[\\x00-\\x1F\\x7F]", "") // control chars

  /** Sanitize a single ingredient string.
    * Returns None if the result is empty or too long.
    */
  def sanitizeIngredient(input: String): Option[String] = {
    val cleaned = AllowedChars
      .replaceAllIn(stripDangerousContent(input), "")
      .trim

    if (cleaned.isEmpty || cleaned.length > MaxIngredientLength) None
    else Some(cleaned)
  }

  /** Sanitize a list of ingredient strings.
    * Deduplicates, limits to MaxIngredients, and drops invalid entries.
    */
  def sanitizeIngredients(ingredients: List[String]): List[String] =
    ingredients
      .take(MaxIngredients)
      .flatMap(sanitizeIngredient)
      .map(_.toLowerCase)
      .distinct

  /** Accept only whitelisted dietary restriction labels. */
  def sanitizeDietaryRestrictions(restrictions: List[String]): List[String] =
    restrictions
      .map(_.toLowerCase.trim)
      .filter(AllowedRestrictions.contains)
      .distinct
}

