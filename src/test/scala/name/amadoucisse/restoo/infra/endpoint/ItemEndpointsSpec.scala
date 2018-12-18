package name.amadoucisse.restoo
package infra
package endpoint

import domain.items._
import domain.entries._
import common.IOAssertion
import service.{ ItemService, StockService }
import repository.inmemory.{ EntryRepositoryInMemoryInterpreter, ItemRepositoryInMemoryInterpreter }
import cats.effect.IO
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.literal._
import io.circe.Json
import http.{ ApiResponseCodes, AppHttpErrorHandler, HttpErrorHandler }
import utils.Validation
import org.http4s._
import org.http4s.circe._
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
    with IOExecution {

  implicit val H: HttpErrorHandler[IO, AppError] = new AppHttpErrorHandler[IO]

  test("add item") {

    IOAssertion {

      for {
        itemRepo ← ItemRepositoryInMemoryInterpreter[IO]
        itemService = ItemService(itemRepo)

        entryRepo ← EntryRepositoryInMemoryInterpreter[IO]
        stockService = StockService(entryRepo, itemRepo)

        itemHttpService ← ItemEndpoints.endpoints[IO](itemService, stockService)

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
        itemRepo ← ItemRepositoryInMemoryInterpreter[IO]
        itemService = ItemService(itemRepo)

        entryRepo ← EntryRepositoryInMemoryInterpreter[IO]
        stockService = StockService(entryRepo, itemRepo)

        itemHttpService ← ItemEndpoints.endpoints[IO](itemService, stockService)

        implicit0(itemDecoder: EntityDecoder[IO, Item]) = jsonOf[IO, Item]

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
        updateRequest = Request[IO](Method.PUT, Uri.unsafeFromString("/" + itemB.id.map(_.value.toString).get))
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
        itemRepo ← ItemRepositoryInMemoryInterpreter[IO]
        itemService = ItemService(itemRepo)

        entryRepo ← EntryRepositoryInMemoryInterpreter[IO]
        stockService = StockService(entryRepo, itemRepo)

        itemHttpService ← ItemEndpoints.endpoints[IO](itemService, stockService)

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
        itemRepo ← ItemRepositoryInMemoryInterpreter[IO]
        itemService = ItemService(itemRepo)

        entryRepo ← EntryRepositoryInMemoryInterpreter[IO]
        stockService = StockService(entryRepo, itemRepo)

        itemHttpService ← ItemEndpoints.endpoints[IO](itemService, stockService)

        item = ItemEndpoints.ItemRequest(name = "", priceInCents = -9999, currency = "MAD", category = "")

        implicit0(itemDecoder: EntityDecoder[IO, Item]) = jsonOf[IO, Item]

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

          id = createdItem.id.map(_.value.toString).get

          // Invalid update
          request = Request[IO](Method.PUT, Uri.unsafeFromString("/" + id)).withEntity(item.asJson)
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
          request = Request[IO](Method.PATCH, Uri.unsafeFromString("/" + id))
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

        itemRepo ← ItemRepositoryInMemoryInterpreter[IO]
        itemService = ItemService(itemRepo)

        entryRepo ← EntryRepositoryInMemoryInterpreter[IO]
        stockService = StockService(entryRepo, itemRepo)

        itemHttpService ← ItemEndpoints.endpoints[IO](itemService, stockService)

        implicit0(itemDecoder: EntityDecoder[IO, Item]) = jsonOf[IO, Item]

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

        itemToUpdate = item.copy(name = createdItem.name.value.reverse)
        updateRequest = Request[IO](Method.PUT, Uri.unsafeFromString("/" + createdItem.id.map(_.value.toString).get))
          .withEntity(itemToUpdate.asJson)
        updateResponse ← itemHttpService
          .run(updateRequest)
          .getOrElse(fail(s"Request was not handled: $updateRequest"))
        updatedItem ← updateResponse.as[Item]

        _ = createResponse.status shouldEqual Created
        _ = updateResponse.status shouldEqual Ok
        _ = updatedItem.name.value shouldEqual item.name.reverse
        _ = createdItem.id shouldEqual updatedItem.id
      } yield {}
    }

  }

  test("patch item") {

    IOAssertion {

      for {

        itemRepo ← ItemRepositoryInMemoryInterpreter[IO]
        itemService = ItemService(itemRepo)

        entryRepo ← EntryRepositoryInMemoryInterpreter[IO]
        stockService = StockService(entryRepo, itemRepo)

        itemHttpService ← ItemEndpoints.endpoints[IO](itemService, stockService)

        implicit0(itemDecoder: EntityDecoder[IO, Item]) = jsonOf[IO, Item]

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

        id = createdItem.id.map(_.value.toString).get

        // patch name
        newName = "Cake"
        patchName = json"""{"op":"replace","path":"/name","value":$newName}"""
        patchRequest = Request[IO](Method.PATCH, Uri.unsafeFromString("/" + id))
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
        patchRequest = Request[IO](Method.PATCH, Uri.unsafeFromString("/" + id))
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
        patchRequest = Request[IO](Method.PATCH, Uri.unsafeFromString("/" + id))
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
        patchRequest = Request[IO](Method.PATCH, Uri.unsafeFromString("/" + id))
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

        itemRepo ← ItemRepositoryInMemoryInterpreter[IO]
        itemService = ItemService[IO](itemRepo)

        entryRepo ← EntryRepositoryInMemoryInterpreter[IO]
        stockService = StockService(entryRepo, itemRepo)

        itemHttpService ← ItemEndpoints.endpoints[IO](itemService, stockService)

        implicit0(itemDecoder: EntityDecoder[IO, Item]) = jsonOf[IO, Item]

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
        createdItem ← createResponse.as[Item]

        deleteResponse ← itemHttpService
          .run(Request[IO](Method.DELETE, Uri.unsafeFromString("/" + createdItem.id.map(_.value.toString).get)))
          .getOrElse(fail(s"Delete request was not handled"))

        getResponse ← itemHttpService
          .run(Request[IO](Method.GET, Uri.unsafeFromString("/" + createdItem.id.map(_.value.toString).get)))
          .getOrElse(fail(s"Get request was not handled"))

        _ = createResponse.status shouldEqual Created
        _ = deleteResponse.status shouldEqual NoContent
        _ = getResponse.status shouldEqual NotFound
      } yield {}

    }
  }

  test("add or remove stock") {

    IOAssertion {

      for {
        itemRepo ← ItemRepositoryInMemoryInterpreter[IO]
        itemService = ItemService(itemRepo)

        entryRepo ← EntryRepositoryInMemoryInterpreter[IO]
        stockService = StockService(entryRepo, itemRepo)

        itemHttpService ← ItemEndpoints.endpoints[IO](itemService, stockService)

        implicit0(itemDecoder: EntityDecoder[IO, Item]) = jsonOf[IO, Item]
        implicit0(stockDecoder: EntityDecoder[IO, Stock]) = jsonOf[IO, Stock]

        item = ItemEndpoints.ItemRequest(
          name = "Cheese Burger",
          priceInCents = 9999,
          currency = "MAD",
          category = "Food & Drinks"
        )

        entry = ItemEndpoints.StockRequest(delta = 1)

        createRequest ← IO.pure(Request[IO](Method.POST, Uri.uri("/")).withEntity(item.asJson))
        createResponse ← itemHttpService
          .run(createRequest)
          .getOrElse(fail(s"Request was not handled: $createRequest"))
        createdItem ← createResponse.as[Item]

        path = "/" + createdItem.id.map(_.value.toString).get + "/stocks"

        getStockRequest = Request[IO](Method.GET, Uri.unsafeFromString(path))

        getStock0Response ← itemHttpService
          .run(getStockRequest)
          .getOrElse(fail(s"Request was not handled: $getStockRequest"))
        stock0 ← getStock0Response.as[Stock]

        addStockRequest = Request[IO](Method.PUT, Uri.unsafeFromString(path))
          .withEntity(entry.asJson)
        _ ← itemHttpService
          .run(addStockRequest)
          .getOrElse(fail(s"Request was not handled: $addStockRequest"))

        getStock1Response ← itemHttpService
          .run(getStockRequest)
          .getOrElse(fail(s"Request was not handled: $getStockRequest"))
        stock1 ← getStock1Response.as[Stock]

        removeStockRequest = Request[IO](Method.PUT, Uri.unsafeFromString(path))
          .withEntity(entry.copy(delta = -1 * entry.delta).asJson)
        _ ← itemHttpService
          .run(removeStockRequest)
          .getOrElse(fail(s"Request was not handled: $removeStockRequest"))

        getStock2Response ← itemHttpService
          .run(getStockRequest)
          .getOrElse(fail(s"Request was not handled: $getStockRequest"))
        stock2 ← getStock2Response.as[Stock]

        _ = createResponse.status shouldEqual Created
        _ = stock0.quantity shouldEqual 0
        _ = stock1.quantity shouldEqual entry.delta
        _ = stock2.quantity shouldEqual 0

      } yield {}
    }

  }

  test("no negative stock") {

    IOAssertion {

      for {
        itemRepo ← ItemRepositoryInMemoryInterpreter[IO]
        itemService = ItemService(itemRepo)

        entryRepo ← EntryRepositoryInMemoryInterpreter[IO]
        stockService = StockService(entryRepo, itemRepo)

        itemHttpService ← ItemEndpoints.endpoints[IO](itemService, stockService)

        implicit0(itemDecoder: EntityDecoder[IO, Item]) = jsonOf[IO, Item]
        implicit0(stockDecoder: EntityDecoder[IO, Stock]) = jsonOf[IO, Stock]

        item = ItemEndpoints.ItemRequest(
          name = "Cheese Burger",
          priceInCents = 9999,
          currency = "MAD",
          category = "Food & Drinks"
        )

        entry = ItemEndpoints.StockRequest(delta = -1)

        createRequest ← IO.pure(Request[IO](Method.POST, Uri.uri("/")).withEntity(item.asJson))
        createResponse ← itemHttpService
          .run(createRequest)
          .getOrElse(fail(s"Request was not handled: $createRequest"))
        createdItem ← createResponse.as[Item]

        path = "/" + createdItem.id.map(_.value.toString).get + "/stocks"

        getStockRequest = Request[IO](Method.GET, Uri.unsafeFromString(path))

        getStockResponse ← itemHttpService
          .run(getStockRequest)
          .getOrElse(fail(s"Request was not handled: $getStockRequest"))
        stock ← getStockResponse.as[Stock]

        negStockRequest = Request[IO](Method.PUT, Uri.unsafeFromString(path))
          .withEntity(entry.asJson)
        negStockResponse ← itemHttpService
          .run(negStockRequest)
          .getOrElse(fail(s"Request was not handled: $negStockRequest"))

        _ = createResponse.status shouldEqual Created
        _ = stock.quantity shouldEqual 0
        _ = negStockResponse.status shouldEqual Conflict
      } yield {}
    }

  }
}
