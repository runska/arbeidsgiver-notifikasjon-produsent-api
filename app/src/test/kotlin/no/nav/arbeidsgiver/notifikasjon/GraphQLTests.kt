package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.ktor.http.*
import io.ktor.server.testing.*

val objectMapper = jacksonObjectMapper()

fun TestApplicationRequest.setJsonBody(body: Any) {
    setBody(objectMapper.writeValueAsString(body))
}

inline fun <reified T> TestApplicationResponse.getTypedContent(name: String): T{
    if (this.content == null) {
        throw NullPointerException("content is null. status:${status()}")
    }
    val tree = objectMapper.readTree(this.content!!)
    val node = tree.get("data").get(name)
    return objectMapper.convertValue(node)
}

fun TestApplicationResponse.getGraphqlErrors(): List<JsonNode> {
    if (this.content == null) {
        throw NullPointerException("content is null. status:${status()}")
    }
    val tree = objectMapper.readTree(this.content!!)
    val errors = tree.get("errors")
    return errors?.elements()?.asSequence()?.toList() ?: emptyList()
}

class GraphQLTests : DescribeSpec({
    val engine by ktorEngine()

    describe("POST /api/graphql") {
        lateinit var response: TestApplicationResponse
        lateinit var query: String

        beforeEach {
            response = engine.handleRequest(HttpMethod.Post, "/api/graphql") {
                addHeader(HttpHeaders.Authorization, "Bearer $tokenDingsToken")
                addHeader(HttpHeaders.ContentType, "application/json")
                addHeader(HttpHeaders.Accept, "application/json")
                setJsonBody(GraphQLRequest(
                        query = query
                ))
            }.response
        }

        context("Mutation.nyBeskjed") {
            query = """
                mutation {
                    nyBeskjed(nyBeskjed: {
                        lenke: "http://foo.bar",
                        tekst: "hello world",
                        merkelapp: "tag",
                        mottaker: {
                            fnr: {
                                fodselsnummer: "12345678910",
                                virksomhetsnummer: "42"
                            } 
                        }
                    }) {
                        id
                    }
                }
            """.trimIndent()

            it("status is 200 OK") {
                response.status() shouldBe HttpStatusCode.OK
            }
            it("response inneholder ikke feil") {
                response.getGraphqlErrors() should beEmpty()
            }
            it("it returns id") {
                response.getTypedContent<BeskjedResultat>("nyBeskjed").id shouldNot beBlank()
            }
        }
    }
})

