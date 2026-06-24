import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import com.typesafe.config.ConfigFactory
import server.RecipeServer
import services.OpenAIService

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn
import scala.util.{Failure, Success}

object Main {
  def main(args: Array[String]): Unit = {

    val config = ConfigFactory.load()

    implicit val system: ActorSystem[Nothing] =
      ActorSystem(Behaviors.empty, "recipe-generator")
    implicit val ec: ExecutionContextExecutor = system.executionContext

    val apiKey         = config.getString("openai.api-key")
    val model          = config.getString("openai.model")
    val timeoutSeconds = config.getInt("openai.timeout-seconds")

    if (apiKey == "YOUR_OPENAI_API_KEY_HERE") {
      println("⚠️  WARNING: No OpenAI API key found.")
      println("   Set environment variable OPENAI_API_KEY before generating recipes.")
      println("   Example:  export OPENAI_API_KEY=\"sk-proj-...\"")
    } else if (!apiKey.startsWith("sk-")) {
      println("⚠️  WARNING: The API key does not look like a valid OpenAI key (should start with 'sk-').")
      println("   Get your key from: https://platform.openai.com/api-keys")
    } else {
      println(s"  API key  → ${apiKey.take(8)}… (loaded)")
    }

    val openAIService = new OpenAIService(apiKey, model, timeoutSeconds)
    val recipeServer  = new RecipeServer(openAIService)

    val host = config.getString("server.host")
    val port = config.getInt("server.port")

    val bindingFuture = Http().newServerAt(host, port).bind(recipeServer.routes)

    bindingFuture.onComplete {
      case Success(binding) =>
        val addr = binding.localAddress
        println(s"")
        println(s"  🍳  Smart Recipe Generator")
        println(s"  ─────────────────────────────────────────")
        println(s"  Web UI   → http://${addr.getHostString}:${addr.getPort}/")
        println(s"  Health   → http://${addr.getHostString}:${addr.getPort}/health")
        println(s"  API      → POST http://${addr.getHostString}:${addr.getPort}/api/generate-recipes")
        println(s"")
        println(s"  Press RETURN to stop the server.")
      case Failure(ex) =>
        println(s"Failed to start server: ${ex.getMessage}")
        system.terminate()
    }

    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}
