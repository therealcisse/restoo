package name.amadoucisse.restoo

import java.util.concurrent.Executors

import cats.effect.{ ContextShift, IO }
import eu.timepit.refined.types.numeric.NonNegInt
import domain.items._
import domain.entries._
import infra.endpoint.ItemEndpoints.ItemRequest
import io.circe.generic.auto._
import io.circe.literal._
import org.http4s.circe._
import io.circe.syntax._
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.{ BeforeAndAfterAll, FunSpec, Matchers }
import org.http4s._
import org.http4s.client.blaze._
import org.http4s.client.Client
import org.scalatest.OptionValues._

import scala.concurrent.ExecutionContext

class AppServerSpec extends FunSpec with Matchers with ScalaFutures with IntegrationPatience with BeforeAndAfterAll {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  private val serverAccess = sys.props.get("restoo:8080").value

  private def withClient[A](f: Client[IO] ⇒ IO[A]) =
    BlazeClientBuilder[IO](ExecutionContext.fromExecutor(Executors.newCachedThreadPool())).resource
      .use(f)

  describe("items endpoints") {

    implicit val itemDecoder: EntityDecoder[IO, Item] = jsonOf
    implicit val itemOptDecoder: EntityDecoder[IO, Option[Item]] = jsonOf
    implicit val itemsDecoder: EntityDecoder[IO, Seq[Item]] = jsonOf
    implicit val stockDecoder: EntityDecoder[IO, Stock] = jsonOf

    it("should work initially be empty when there are no items") {
      withClient { httpClientIO ⇒
        val result = httpClientIO.expect[Seq[Item]](uri("items"))
        result.unsafeRunSync() shouldBe 'empty

        IO.unit
      }
    }

    it("should do CRUD properly") {
      withClient { httpClient ⇒
        for {
          itemRequest ← IO.pure(
            ItemRequest(
              name = "name",
              priceInCents = 199,
              currency = "MAD",
              category = "category"
            )
          )

          req = Request[IO](Method.POST, uri("items"))
            .withEntity(itemRequest.asJson)
          item ← httpClient.expect[Item](req)
          items ← httpClient
            .expect[Seq[Item]](uri("items").withQueryParam("sort_by", "-updated_at,name"))
          _ = items.size shouldBe 1
          _ = items should contain(item)

          // list: no filter
          listOfItems ← httpClient.expect[Seq[Item]](
            Request[IO](Method.GET, uri("items"))
          )
          _ = listOfItems.size shouldBe 1
          _ = listOfItems should contain(item)

          // list: filter by category
          listOfItems ← httpClient.expect[Seq[Item]](
            Request[IO](
              Method.GET,
              uri("items")
                .withQueryParam("sort_by", "-updated_at,name")
                .withQueryParam("category", itemRequest.category)
            )
          )
          _ = listOfItems.size shouldBe 1
          _ = listOfItems should contain(item)

          // list: filter by unknown category
          listOfItems ← httpClient.expect[Seq[Item]](
            Request[IO](
              Method.GET,
              uri("items")
                .withQueryParam("category", "unknown")
            )
          )
          _ = listOfItems.size shouldBe 0

          updatedItemRequest = itemRequest.copy(name = "heyo")
          updatedItem ← httpClient.expect[Item](
            Request[IO](Method.PUT, uri(s"items/${item.id.value}"))
              .withEntity(updatedItemRequest.asJson)
          )
          retrieved ← httpClient.expect[Option[Item]](uri(s"items/${item.id.value}"))
          _ = retrieved.value shouldBe updatedItem

          // change price
          newPrice = NonNegInt.unsafeFrom(399)
          patchPrice = json"""{"op":"replace","path":"/priceInCents","value":${newPrice.value}}"""
          patchedItem ← httpClient.expect[Item](
            Request[IO](Method.PATCH, uri(s"items/${item.id.value}"))
              .withEntity(patchPrice)
          )
          _ = patchedItem.price.amountInCents shouldEqual newPrice
          retrieved ← httpClient.expect[Option[Item]](uri(s"items/${item.id.value}"))
          _ = retrieved.value shouldBe patchedItem

          // change name
          newName = "Cake"
          patchName = json"""{"op":"replace","path":"/name","value":$newName}"""
          patchedItem ← httpClient.expect[Item](
            Request[IO](Method.PATCH, uri(s"items/${item.id.value}"))
              .withEntity(patchName)
          )
          _ = patchedItem.name shouldEqual Name(newName)
          retrieved ← httpClient.expect[Option[Item]](uri(s"items/${item.id.value}"))
          _ = retrieved.value shouldBe patchedItem

          // change category
          newCategory = "Dessert"
          patchCategory = json"""{"op":"replace","path":"/category","value":$newCategory}"""
          patchedItem ← httpClient.expect[Item](
            Request[IO](Method.PATCH, uri(s"items/${item.id.value}"))
              .withEntity(patchCategory)
          )
          _ = patchedItem.category shouldEqual Category(newCategory)
          retrieved ← httpClient.expect[Option[Item]](uri(s"items/${item.id.value}"))
          _ = retrieved.value shouldBe patchedItem

          stockRequest = Delta(value = 10)
          stock ← httpClient.expect[Stock](
            Request[IO](Method.PUT, uri(s"items/${item.id.value}/stocks").withQueryParam("delta", stockRequest.value))
          )
          _ = stock.item.id shouldBe item.id
          _ = stock.quantity shouldBe 10

          stockRequest = Delta(value = -3)
          stock ← httpClient.expect[Stock](
            Request[IO](Method.PUT, uri(s"items/${item.id.value}/stocks").withQueryParam("delta", stockRequest.value))
          )
          _ = stock.item.id shouldBe item.id
          _ = stock.quantity shouldBe 7

          _ ← httpClient.expect[Unit](Request[IO](Method.DELETE, uri(s"items/${item.id.value}")))
          retrieveStatus ← httpClient.status(Request[IO](Method.GET, uri(s"items/${item.id.value}")))
          _ = retrieveStatus shouldBe Status.NotFound
        } yield ()
      }
    }

  }

  private def uri(path: String): Uri = Uri.unsafeFromString(s"http://$serverAccess/api/v1/$path")

}
