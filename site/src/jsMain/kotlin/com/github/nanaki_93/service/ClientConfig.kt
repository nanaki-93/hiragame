import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object HttpClientProvider {
    val client: HttpClient by lazy {
        HttpClient(Js) {
            install(HttpCookies) {
                storage = AcceptAllCookiesStorage()
            }

            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                configureRequest { credentials = "include" }
            }
        }
    }
}