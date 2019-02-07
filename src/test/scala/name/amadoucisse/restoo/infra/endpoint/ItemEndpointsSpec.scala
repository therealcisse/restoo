package name.amadoucisse.restoo
package infra
package endpoint

import domain.items._
import domain.entries._
import common.IOAssertion
import service.{ IdService, ItemService, StockService }
import repository.inmemory.{
  EntryRepositoryInMemoryInterpreter,
  IdRepositoryInMemoryInterpreter,
  ItemRepositoryInMemoryInterpreter
}
import cats.effect.IO
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.literal._
import io.circe._
import http.{ ApiResponseCodes, AppHttpErrorHandler, HttpErrorHandler }
import utils.Validation
import org.http4s._
import org.scalatest._
import org.scalatest.prop.PropertyChecks
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.NonNegInt
import domain.AppError
import com.olegpy.meow.hierarchy._

@SuppressWarnings(Array("org.wartremover.warts.Throw", "org.wartremover.warts.OptionPartial"))
class ItemEndpointsSpec
    extends FunSuite
    with Matchers
    with PropertyChecks
    with Arbitraries
    with dsl.Http4sDsl[IO]
    with IOExecution
    with Codecs {

  implicit val H: HttpErrorHandler[IO, AppError] = new AppHttpErrorHandler[IO]

  private def httpEndpoint(): IO[HttpRoutes[IO]] =
    for {
      itemRepo ← ItemRepositoryInMemoryInterpreter[IO]
      itemService = ItemService(itemRepo)

      idRepo ← IdRepositoryInMemoryInterpreter[IO]
      entryRepo ← EntryRepositoryInMemoryInterpreter[IO]
      stockService = StockService(entryRepo, itemRepo, idRepo)

      idService = IdService(idRepo)

      itemHttpService ← ItemEndpoints.endpoints[IO](itemService, stockService, idService)

    } yield itemHttpService

  test("add item") {

    IOAssertion {

      for {
        itemHttpService ← httpEndpoint()

        item = ItemEndpoints.ItemRequest(
          name = "Item 0",
          priceInCents = 9999,
          currency = "MAD",
          category = "Food & Drinks"
        )

        request = Request[IO](Method.POST, Uri.uri("/")).withEntity(item.asJson)
        response ← itemHttpService
          .run(request)
          .getOrElse(fail(s"Request was not handled: $request"))
        _ = response.status shouldEqual Created
      } yield {}

    }

  }

  test("disallow duplicate item names on update") {

    IOAssertion {

      for {
        itemHttpService ← httpEndpoint()

        itemARequest = ItemEndpoints.ItemRequest(
          name = "ItemA",
          priceInCents = 9999,
          currency = "MAD",
          category = "Food & Drinks"
        )
        itemBRequest = itemARequest.copy(name = "ItemB")

        // Add item A
        request ← IO.pure(Request[IO](Method.POST, Uri.uri("/")).withEntity(itemARequest.asJson))
        response ← itemHttpService
          .run(request)
          .getOrElse(fail(s"Request was not handled: $request"))
        _ = response.status shouldEqual Created
        itemA ← response.as[Item]
        _ = itemA.name shouldEqual Name(itemARequest.name)

        // Add item B
        request = Request[IO](Method.POST, Uri.uri("/")).withEntity(itemBRequest.asJson)
        response ← itemHttpService
          .run(request)
          .getOrElse(fail(s"Request was not handled: $request"))
        _ = response.status shouldEqual Created
        itemB ← response.as[Item]
        _ = itemB.name shouldEqual Name(itemBRequest.name)

        // Try updating itemB's name to equal itemA's name
        itemToUpdate = itemBRequest.copy(name = itemA.name.value)
        updateRequest = Request[IO](Method.PUT, Uri.unsafeFromString(s"/${itemB.id.value}"))
          .withEntity(itemToUpdate.asJson)
        updateResponse ← itemHttpService
          .run(updateRequest)
          .getOrElse(fail(s"Request was not handled: $updateRequest"))
        updatedItem ← updateResponse.as[Json]
        _ = updateResponse.status shouldEqual Conflict

        errorResponse = updatedItem.hcursor.downField("error")
        _ = errorResponse.get[String]("code") shouldEqual Right(ApiResponseCodes.CONFLICT)
        _ = errorResponse.get[String]("type") shouldEqual Right("ItemAlreadyExists")

      } yield {}

    }

  }

  test("disallow duplicate items on create") {

    IOAssertion {

      for {
        itemHttpService ← httpEndpoint()

        item = ItemEndpoints.ItemRequest(
          name = "Item 0",
          priceInCents = 9999,
          currency = "MAD",
          category = "Food & Drinks"
        )
        request ← IO.pure(Request[IO](Method.POST, Uri.uri("/")).withEntity(item.asJson))

        // Create an item
        _ ← for {
          response ← itemHttpService
            .run(request)
            .getOrElse(fail(s"Request was not handled: $request"))
          _ = response.status shouldEqual Created
        } yield ()

        // Try adding a duplicate
        _ ← for {
          request ← IO.pure(Request[IO](Method.POST, Uri.uri("/")).withEntity(item.asJson))
          response ← itemHttpService
            .run(request)
            .getOrElse(fail(s"Request was not handled: $request"))

          _ = response.status shouldEqual Conflict

          responseEntity = response.as[Json].unsafeRunSync().hcursor.downField("error")

          _ = responseEntity.get[String]("code") shouldEqual Right(ApiResponseCodes.CONFLICT)
          _ = responseEntity.get[String]("type") shouldEqual Right("ItemAlreadyExists")
        } yield {}

      } yield {}
    }

  }

  test("disallow invalid items") {

    IOAssertion {

      for {
        itemHttpService ← httpEndpoint()

        item = ItemEndpoints.ItemRequest(name = "", priceInCents = -9999, currency = "MAD", category = "")

        _ ← for {
          request ← IO.pure(Request[IO](Method.POST, Uri.uri("/")).withEntity(item.asJson))
          response ← itemHttpService
            .run(request)
            .getOrElse(fail(s"Request was not handled: $request"))

          _ = response.status shouldEqual UnprocessableEntity

          responseEntity = response.as[Json].unsafeRunSync().hcursor
          _ = responseEntity.get[String]("code") shouldEqual Right(ApiResponseCodes.VALIDATION_FAILED)
          _ = responseEntity.get[String]("type") shouldEqual Right("FieldErrors")
          _ = responseEntity.get[Vector[Validation.FieldError]]("errors") match {
            case Left(failure) ⇒ fail(failure.message)
            case Right(errors) ⇒
              errors.size shouldEqual 3
          }
        } yield {}

        _ ← for {
          createRequest ← IO.pure(
            Request[IO](Method.POST, Uri.uri("/"))
              .withEntity(
                ItemEndpoints
                  .ItemRequest(name = "Name", priceInCents = 9999, currency = "MAD", category = "Category")
                  .asJson
              )
          )
          createResponse ← itemHttpService
            .run(createRequest)
            .getOrElse(fail(s"Request was not handled: $createRequest"))
          createdItem ← createResponse.as[Item]

          id = createdItem.id.value

          // Invalid update
          request = Request[IO](Method.PUT, Uri.unsafeFromString(s"/$id")).withEntity(item.asJson)
          response ← itemHttpService
            .run(request)
            .getOrElse(fail(s"Request was not handled: $request"))

          _ = response.status shouldEqual UnprocessableEntity

          responseEntity = response.as[Json].unsafeRunSync().hcursor
          _ = responseEntity.get[String]("code") shouldEqual Right(ApiResponseCodes.VALIDATION_FAILED)
          _ = responseEntity.get[String]("type") shouldEqual Right("FieldErrors")
          _ = responseEntity.get[Vector[Validation.FieldError]]("errors") match {
            case Left(failure) ⇒ fail(failure.message)
            case Right(errors) ⇒
              errors.size shouldEqual 3
          }

          // Invalid patch
          patches = Vector(
            json"""{"op":"replace","path":"/name","value":${item.name}}""",
            json"""{"op":"replace","path":"/priceInCents","value":${item.priceInCents}}""",
            json"""{"op":"replace","path":"/category","value":${item.category}}""",
          )
          request = Request[IO](Method.PATCH, Uri.unsafeFromString(s"/$id"))
            .withEntity(patches.asJson)
          response ← itemHttpService
            .run(request)
            .getOrElse(fail(s"Request was not handled: $request"))

          _ = response.status shouldEqual UnprocessableEntity

          responseEntity = response.as[Json].unsafeRunSync().hcursor
          _ = responseEntity.get[String]("code") shouldEqual Right(ApiResponseCodes.VALIDATION_FAILED)
          _ = responseEntity.get[String]("type") shouldEqual Right("FieldErrors")
          _ = responseEntity.get[Vector[Validation.FieldError]]("errors") match {
            case Left(failure) ⇒ fail(failure.message)
            case Right(errors) ⇒
              errors.size shouldEqual 3
          }

        } yield {}

      } yield {}

    }

  }

  test("update item") {

    IOAssertion {

      for {

        itemHttpService ← httpEndpoint()

        item = ItemEndpoints.ItemRequest(
          name = "Cheese Burger",
          priceInCents = 9999,
          currency = "MAD",
          category = "Food & Drinks"
        )

        createRequest ← IO.pure(Request[IO](Method.POST, Uri.uri("/")).withEntity(item.asJson))
        createResponse ← itemHttpService
          .run(createRequest)
          .getOrElse(fail(s"Request was not handled: $createRequest"))
        _ = createResponse.status shouldEqual Created
        createdItem ← createResponse.as[Item]
        _ = createdItem.id shouldEqual createdItem.id

        itemToUpdate = item.copy(name = createdItem.name.value.reverse)
        updateRequest = Request[IO](Method.PUT, Uri.unsafeFromString(s"/${createdItem.id.value}"))
          .withEntity(itemToUpdate.asJson)
        updateResponse ← itemHttpService
          .run(updateRequest)
          .getOrElse(fail(s"Request was not handled: $updateRequest"))
        _ = updateResponse.status shouldEqual Ok
        updatedItem ← updateResponse.as[Item]
        _ = updatedItem.name.value shouldEqual item.name.reverse

      } yield {}
    }

  }

  test("patch item") {

    IOAssertion {

      for {

        itemHttpService ← httpEndpoint()

        item = ItemEndpoints.ItemRequest(
          name = "Cheese Burger",
          priceInCents = 9999,
          currency = "MAD",
          category = "Food & Drinks"
        )

        createRequest ← IO.pure(Request[IO](Method.POST, Uri.uri("/")).withEntity(item.asJson))
        createResponse ← itemHttpService
          .run(createRequest)
          .getOrElse(fail(s"Request was not handled: $createRequest"))
        createdItem ← createResponse.as[Item]

        uri = Uri.unsafeFromString(s"/${createdItem.id.value}")

        // patch name
        newName = "Cake"
        patchName = json"""{"op":"replace","path":"/name","value":$newName}"""
        patchRequest = Request[IO](Method.PATCH, uri)
          .withEntity(patchName)
        patchResponse ← itemHttpService
          .run(patchRequest)
          .getOrElse(fail(s"Request was not handled: $patchRequest"))
        _ = patchResponse.status shouldEqual Ok
        patchedItem ← patchResponse.as[Item]
        _ = patchedItem.name shouldEqual Name(newName)

        // patch price
        newPrice = NonNegInt.unsafeFrom(5099)
        patchPrice = json"""{"op":"replace","path":"/priceInCents","value":${newPrice.value}}"""
        patchRequest = Request[IO](Method.PATCH, uri)
          .withEntity(patchPrice)
        patchResponse ← itemHttpService
          .run(patchRequest)
          .getOrElse(fail(s"Request was not handled: $patchRequest"))
        _ = patchResponse.status shouldEqual Ok
        patchedItem ← patchResponse.as[Item]
        _ = patchedItem.price.amountInCents shouldEqual newPrice.value

        // patch category
        newCategory = "Dessert"
        patchCategory = json"""{"op":"replace","path":"/category","value":$newCategory}"""
        patchRequest = Request[IO](Method.PATCH, uri)
          .withEntity(patchCategory)
        patchResponse ← itemHttpService
          .run(patchRequest)
          .getOrElse(fail(s"Request was not handled: $patchRequest"))
        _ = patchResponse.status shouldEqual Ok
        patchedItem ← patchResponse.as[Item]
        _ = patchedItem.category shouldEqual Category(newCategory)

        // Revert all changes
        patches = Vector(
          json"""{"op":"replace","path":"/name","value":${item.name}}""",
          json"""{"op":"replace","path":"/priceInCents","value":${item.priceInCents}}""",
          json"""{"op":"replace","path":"/category","value":${item.category}}""",
        )
        patchRequest = Request[IO](Method.PATCH, uri)
          .withEntity(patches.asJson)
        patchResponse ← itemHttpService
          .run(patchRequest)
          .getOrElse(fail(s"Request was not handled: $patchRequest"))
        _ = patchResponse.status shouldEqual Ok
        patchedItem ← patchResponse.as[Item]
        _ = patchedItem.name shouldEqual Name(item.name)
        _ = patchedItem.price.amountInCents shouldEqual item.priceInCents
        _ = patchedItem.category shouldEqual Category(item.category)
      } yield {}
    }

  }

  test("delete item by id") {

    IOAssertion {
      for {

        itemHttpService ← httpEndpoint()

        item = ItemEndpoints.ItemRequest(
          name = "Item 0",
          priceInCents = 9999,
          currency = "MAD",
          category = "Food & Drinks"
        )

        createRequest ← IO.pure(
          Request[IO](Method.POST, Uri.uri("/"))
            .withEntity(item.asJson)
        )
        createResponse ← itemHttpService
          .run(createRequest)
          .getOrElse(fail(s"Request was not handled: $createRequest"))
        _ = createResponse.status shouldEqual Created
        createdItem ← createResponse.as[Item]

        uri = Uri.unsafeFromString(s"/${createdItem.id.value}")

        deleteResponse ← itemHttpService
          .run(Request[IO](Method.DELETE, uri))
          .getOrElse(fail(s"Delete request was not handled"))
        _ = deleteResponse.status shouldEqual NoContent

        getResponse ← itemHttpService
          .run(Request[IO](Method.GET, uri))
          .getOrElse(fail(s"Get request was not handled"))
        _ = getResponse.status shouldEqual NotFound
      } yield {}

    }
  }

  test("add or remove stock") {

    IOAssertion {

      for {
        itemHttpService ← httpEndpoint()

        item = ItemEndpoints.ItemRequest(
          name = "Cheese Burger",
          priceInCents = 9999,
          currency = "MAD",
          category = "Food & Drinks"
        )

        entry = Delta(value = 1)

        createRequest ← IO.pure(Request[IO](Method.POST, Uri.uri("/")).withEntity(item.asJson))
        createResponse ← itemHttpService
          .run(createRequest)
          .getOrElse(fail(s"Request was not handled: $createRequest"))
        createdItem ← createResponse.as[Item]
        _ = createResponse.status shouldEqual Created

        uri = Uri.unsafeFromString(s"/${createdItem.id.value}/stocks")

        getStockRequest = Request[IO](Method.GET, uri)

        // get current stock quantity ==> 0
        getStock0Response ← itemHttpService
          .run(getStockRequest)
          .getOrElse(fail(s"Request was not handled: $getStockRequest"))
        _ = getStock0Response.status shouldEqual Ok
        stock0 ← getStock0Response.as[Stock]
        _ = stock0.quantity shouldEqual 0

        // add `entry.delta` items
        addStockRequest = Request[IO](Method.PUT, uri.withQueryParam("delta", entry.value))
        _ ← itemHttpService
          .run(addStockRequest)
          .getOrElse(fail(s"Request was not handled: $addStockRequest"))

        // get current stock quantity ==> `entry.delta`
        getStock1Response ← itemHttpService
          .run(getStockRequest)
          .getOrElse(fail(s"Request was not handled: $getStockRequest"))
        _ = getStock1Response.status shouldEqual Ok
        stock1 ← getStock1Response.as[Stock]
        _ = stock1.quantity shouldEqual entry.value

        // substract `entry.delta` items
        removeStockRequest = Request[IO](Method.PUT, uri.withQueryParam("delta", -1 * entry.value))
        _ ← itemHttpService
          .run(removeStockRequest)
          .getOrElse(fail(s"Request was not handled: $removeStockRequest"))

        // get current stock ==> 0
        getStock2Response ← itemHttpService
          .run(getStockRequest)
          .getOrElse(fail(s"Request was not handled: $getStockRequest"))
        _ = getStock2Response.status shouldEqual Ok
        stock2 ← getStock2Response.as[Stock]
        _ = stock2.quantity shouldEqual 0

      } yield {}
    }
  }

  test("no negative stock") {

    IOAssertion {

      for {
        itemHttpService ← httpEndpoint()

        item = ItemEndpoints.ItemRequest(
          name = "Cheese Burger",
          priceInCents = 9999,
          currency = "MAD",
          category = "Food & Drinks"
        )

        entry = Delta(value = -1)

        createRequest ← IO.pure(Request[IO](Method.POST, Uri.uri("/")).withEntity(item.asJson))
        createResponse ← itemHttpService
          .run(createRequest)
          .getOrElse(fail(s"Request was not handled: $createRequest"))
        _ = createResponse.status shouldEqual Created
        createdItem ← createResponse.as[Item]

        uri = Uri.unsafeFromString(s"/${createdItem.id.value}/stocks")

        getStockRequest = Request[IO](Method.GET, uri)

        getStockResponse ← itemHttpService
          .run(getStockRequest)
          .getOrElse(fail(s"Request was not handled: $getStockRequest"))
        _ = getStockResponse.status shouldEqual Ok
        stock ← getStockResponse.as[Stock]
        _ = stock.quantity shouldEqual 0

        negStockRequest = Request[IO](Method.PUT, uri.withQueryParam("delta", entry.value))
        negStockResponse ← itemHttpService
          .run(negStockRequest)
          .getOrElse(fail(s"Request was not handled: $negStockRequest"))
        _ = negStockResponse.status shouldEqual Conflict

      } yield {}
    }

  }

}
