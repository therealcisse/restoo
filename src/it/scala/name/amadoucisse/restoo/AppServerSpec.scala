package name.amadoucisse.restoo

import cats.effect.IO
import domain.items._
import domain.entries._
import infra.endpoint.ItemEndpoints.{ItemRequest, StockRequest}
import io.circe.generic.auto._
import io.circe.literal._
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
        req = Request[IO](Method.POST, uri("items").withQueryParam("sort_by", "-updated_at,name"))
          .withBody(itemRequest)
        item <- httpClient.expect[Item](req)
        items <- httpClient
          .expect[Seq[Item]](uri("items"))
        _ = items.size shouldBe 1
        _ = items should contain(item)

        // list: no filter
        listOfItems <- httpClient.expect[Seq[Item]](
          Request[IO](Method.GET, uri("items"))
        )
        _ = listOfItems.size shouldBe 1
        _ = listOfItems should contain(item)

        // list: filter by category
        listOfItems <- httpClient.expect[Seq[Item]](
          Request[IO](
            Method.GET,
            uri("items")
              .withQueryParam("category", itemRequest.category))
        )
        _ = listOfItems.size shouldBe 1
        _ = listOfItems should contain(item)

        // list: filter by unknown category
        listOfItems <- httpClient.expect[Seq[Item]](
          Request[IO](
            Method.GET,
            uri("items")
              .withQueryParam("category", "unknown"))
        )
        _ = listOfItems.size shouldBe 0

        updatedItemRequest = itemRequest.copy(name = "heyo")
        updatedItem <- httpClient.expect[Item](
          Request[IO](Method.PUT, uri(s"items/${item.id.value.value}"))
            .withBody(updatedItemRequest)
        )
        retrieved <- httpClient.expect[Option[Item]](uri(s"items/${item.id.value.value}"))
        _ = retrieved.value shouldBe updatedItem

        // change price
        newPrice = 3.99
        patchPrice = json"""{"op":"replace","path":"/price","value":${newPrice}}"""
        patchedItem <- httpClient.expect[Item](
          Request[IO](Method.PATCH, uri(s"items/${item.id.value.value}"))
            .withBody(patchPrice)
          )
        _ = patchedItem.priceInCents shouldEqual Cents(newPrice)
        retrieved <- httpClient.expect[Option[Item]](uri(s"items/${item.id.value.value}"))
        _ = retrieved.value shouldBe patchedItem

        // change name
        newName = "Cake"
        patchName = json"""{"op":"replace","path":"/name","value":${newName}}"""
        patchedItem <- httpClient.expect[Item](
          Request[IO](Method.PATCH, uri(s"items/${item.id.value.value}"))
            .withBody(patchName)
          )
        _ = patchedItem.name shouldEqual Name(newName)
        retrieved <- httpClient.expect[Option[Item]](uri(s"items/${item.id.value.value}"))
        _ = retrieved.value shouldBe patchedItem

        // change category
        newCategory = "Dessert"
        patchCategory = json"""{"op":"replace","path":"/category","value":${newCategory}}"""
        patchedItem <- httpClient.expect[Item](
          Request[IO](Method.PATCH, uri(s"items/${item.id.value.value}"))
            .withBody(patchCategory)
          )
        _ = patchedItem.category shouldEqual Category(newCategory)
        retrieved <- httpClient.expect[Option[Item]](uri(s"items/${item.id.value.value}"))
        _ = retrieved.value shouldBe patchedItem

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
