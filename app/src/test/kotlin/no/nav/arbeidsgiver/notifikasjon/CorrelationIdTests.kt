import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.ktor.http.*
import no.nav.arbeidsgiver.notifikasjon.getWithHeader
import no.nav.arbeidsgiver.notifikasjon.ktorEngine

class CorrelationIdTest : DescribeSpec({
    val engine by ktorEngine()

    describe("correlation id handling") {
        context("when no callid given") {
            val response = engine.getWithHeader("/internal/alive")
            it("generates callid for us") {
                response.headers[HttpHeaders.XCorrelationId] shouldNot beBlank()
            }
        }

        context("with callid") {
            val callid = "1234"

            context("with header name:") {
                forAll("callid", "CALLID", "call-id") { headerName ->
                    val response = engine.getWithHeader( "/internal/alive", headerName to callid)
                    it("it replies with callid: $callid from $headerName") {
                        response.headers[HttpHeaders.XCorrelationId] shouldBe callid
                    }
                }
            }
        }
    }
})
