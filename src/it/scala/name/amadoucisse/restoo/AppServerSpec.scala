package name.amadoucisse.restoo

import cats.effect.IO
import domain.items._
import domain.entries._
import infra.endpoint.ItemEndpoints.{ItemRequest, StockRequest}
import io.circe.generic.auto._
import org.http4s.circe._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, FunSpec, Matchers}
import org.http4s._
import org.http4s.client.blaze._
import org.scalatest.OptionValues._

class AppServerSpec
    extends FunSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with BeforeAndAfterAll {

  private val serverAccess = sys.props.get("restoo:8080").value
  private val httpClientIO = Http1Client[IO]()

  describe("items endpoints") {

    implicit val itemDecoder: EntityDecoder[IO, Item] = jsonOf
    implicit val itemOptDecoder: EntityDecoder[IO, Option[Item]] = jsonOf
    implicit val itemsDecoder: EntityDecoder[IO, Seq[Item]] = jsonOf
    implicit val itemRequestEncoder: EntityEncoder[IO, ItemRequest] = jsonEncoderOf
    implicit val stockRequestEncoder: EntityEncoder[IO, StockRequest] = jsonEncoderOf
    implicit val stockDecoder: EntityDecoder[IO, Stock] = jsonOf

    it("should work initially be empty when there are no items") {
      val result = httpClientIO.flatMap(_.expect[Seq[Item]](uri("items")))
      result.unsafeRunSync() shouldBe 'empty
    }

    it("should do CRUD properly") {
      (for {
        httpClient <- httpClientIO
        itemRequest = ItemRequest("name", 1.99, "category")
        req = Request[IO](Method.POST, uri("items"))
          .withBody(itemRequest)
        item <- httpClient.expect[Item](req)
        items <- httpClient
          .expect[Seq[Item]](uri("items"))
        _ = items.size shouldBe 1
        _ = items should contain(item)

        updatedItemRequest = itemRequest.copy(name = "heyo")
        updatedItem <- httpClient.expect[Item](
          Request[IO](Method.PUT, uri(s"items/${item.id.value.value}"))
            .withBody(updatedItemRequest)
        )
        retrieved <- httpClient.expect[Option[Item]](uri(s"items/${item.id.value.value}"))
        _ = retrieved.value shouldBe updatedItem

        stockRequest = StockRequest(delta = 10)
        stock <- httpClient.expect[Stock](
          Request[IO](Method.PUT, uri(s"items/${item.id.value.value}/stocks"))
            .withBody(stockRequest)
        )
        _ = stock.item.id shouldBe item.id
        _ = stock.quantity shouldBe 10

        stockRequest = StockRequest(delta = -3)
        stock <- httpClient.expect[Stock](
          Request[IO](Method.PUT, uri(s"items/${item.id.value.value}/stocks"))
            .withBody(stockRequest)
        )
        _ = stock.item.id shouldBe item.id
        _ = stock.quantity shouldBe 7

        _ <- httpClient.expect[Unit](
          Request[IO](Method.DELETE, uri(s"items/${item.id.value.value}")))
        retrieveStatus <- httpClient.status(
          Request[IO](Method.GET, uri(s"items/${item.id.value.value}")))
        _ = retrieveStatus shouldBe Status.NotFound
      } yield ()).unsafeRunSync()
    }

  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    httpClientIO.flatMap(_.shutdown).unsafeRunSync()
  }

  private def uri(path: String): Uri = Uri.unsafeFromString(s"http://$serverAccess/api/v1/$path")

}
