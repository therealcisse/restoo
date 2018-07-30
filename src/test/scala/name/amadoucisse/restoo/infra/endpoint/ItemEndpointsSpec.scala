package name.amadoucisse.restoo
package infra
package endpoint

import domain.items._
import domain.entries._
import service.{ItemService, StockService}
import repository.inmemory.{EntryRepositoryInMemoryInterpreter, ItemRepositoryInMemoryInterpreter}

import cats.effect._

import io.circe.generic.auto._
import io.circe.syntax._

import org.http4s._
import org.http4s.dsl._
import org.http4s.circe._

import org.scalatest._
import org.scalatest.prop.PropertyChecks

@SuppressWarnings(Array("org.wartremover.warts.Throw", "org.wartremover.warts.OptionPartial"))
class ItemEndpointsSpec
    extends FunSuite
    with Matchers
    with PropertyChecks
    with Arbitraries
    with Http4sDsl[IO] {

  test("add item") {

    val itemRepo = ItemRepositoryInMemoryInterpreter[IO]
    val itemValidation = ItemValidationInterpreter[IO](itemRepo)
    val itemService = ItemService(itemRepo, itemValidation)

    val entryRepo = EntryRepositoryInMemoryInterpreter[IO]
    val stockService = StockService(entryRepo, itemRepo)

    val itemHttpService = ItemEndpoints.endpoints[IO](itemService, stockService)

    val item = ItemEndpoints.ItemRequest(name = "Item 0", price = 99.99, category = "Food & Drinks")

    (for {
      request <- Request[IO](Method.POST, Uri.uri("/")).withBody(item.asJson)
      response <- itemHttpService
        .run(request)
        .getOrElse(fail(s"Request was not handled: $request"))
    } yield {
      response.status shouldEqual Ok
    }).unsafeRunSync

  }

  test("update item") {

    val itemRepo = ItemRepositoryInMemoryInterpreter[IO]
    val itemValidation = ItemValidationInterpreter[IO](itemRepo)
    val itemService = ItemService(itemRepo, itemValidation)

    val entryRepo = EntryRepositoryInMemoryInterpreter[IO]
    val stockService = StockService(entryRepo, itemRepo)

    val itemHttpService = ItemEndpoints.endpoints[IO](itemService, stockService)

    implicit val itemDecoder: EntityDecoder[IO, Item] = jsonOf

    val item =
      ItemEndpoints.ItemRequest(name = "Cheese Burger", price = 99.99, category = "Food & Drinks")

    (for {
      createRequest <- Request[IO](Method.POST, Uri.uri("/")).withBody(item.asJson)
      createResponse <- itemHttpService
        .run(createRequest)
        .getOrElse(fail(s"Request was not handled: $createRequest"))
      createdItem <- createResponse.as[Item]

      itemToUpdate = item.copy(name = createdItem.name.value.reverse)
      updateRequest <- Request[IO](
        Method.PUT,
        Uri.unsafeFromString("/" + createdItem.id.map(_.value.toString).get))
        .withBody(itemToUpdate.asJson)
      updateResponse <- itemHttpService
        .run(updateRequest)
        .getOrElse(fail(s"Request was not handled: $updateRequest"))
      updatedItem <- updateResponse.as[Item]
    } yield {

      updateResponse.status shouldEqual Ok
      updatedItem.name.value shouldEqual item.name.reverse
      createdItem.id shouldEqual updatedItem.id
    }).unsafeRunSync

  }

  test("delete item by id") {

    val itemRepo = ItemRepositoryInMemoryInterpreter[IO]
    val itemValidation = ItemValidationInterpreter[IO](itemRepo)
    val itemService = ItemService[IO](itemRepo, itemValidation)

    val entryRepo = EntryRepositoryInMemoryInterpreter[IO]
    val stockService = StockService(entryRepo, itemRepo)

    val itemHttpService: HttpService[IO] = ItemEndpoints.endpoints(itemService, stockService)

    implicit val itemDecoder: EntityDecoder[IO, Item] = jsonOf

    val item = ItemEndpoints.ItemRequest(name = "Item 0", price = 99.99, category = "Food & Drinks")

    for {
      createRequest <- Request[IO](Method.POST, Uri.uri("/"))
        .withBody(item.asJson)
      createResponse <- itemHttpService
        .run(createRequest)
        .getOrElse(fail(s"Request was not handled: $createRequest"))
      createdItem <- createResponse.as[Item]

      deleteResponse <- itemHttpService
        .run(
          Request[IO](
            Method.DELETE,
            Uri.unsafeFromString("/" + createdItem.id.map(_.value.toString).get)))
        .getOrElse(fail(s"Delete request was not handled"))

      getResponse <- itemHttpService
        .run(
          Request[IO](
            Method.GET,
            Uri.unsafeFromString("/" + createdItem.id.map(_.value.toString).get)))
        .getOrElse(fail(s"Get request was not handled"))
    } yield {
      createResponse.status shouldEqual Ok
      deleteResponse.status shouldEqual Ok
      getResponse.status shouldEqual NotFound
    }
  }

  test("add or remove stock") {

    val itemRepo = ItemRepositoryInMemoryInterpreter[IO]
    val itemValidation = ItemValidationInterpreter[IO](itemRepo)
    val itemService = ItemService(itemRepo, itemValidation)

    val entryRepo = EntryRepositoryInMemoryInterpreter[IO]
    val stockService = StockService(entryRepo, itemRepo)

    val itemHttpService = ItemEndpoints.endpoints[IO](itemService, stockService)

    implicit val itemDecoder: EntityDecoder[IO, Item] = jsonOf
    implicit val stockDecoder: EntityDecoder[IO, Stock] = jsonOf

    val item =
      ItemEndpoints.ItemRequest(name = "Cheese Burger", price = 99.99, category = "Food & Drinks")

    val entry = ItemEndpoints.StockRequest(delta = 1)

    (for {
      createRequest <- Request[IO](Method.POST, Uri.uri("/")).withBody(item.asJson)
      createResponse <- itemHttpService
        .run(createRequest)
        .getOrElse(fail(s"Request was not handled: $createRequest"))
      createdItem <- createResponse.as[Item]

      path = "/" + createdItem.id.map(_.value.toString).get + "/stocks"

      getStockRequest = Request[IO](Method.GET, Uri.unsafeFromString(path))

      getStock0Response <- itemHttpService
        .run(getStockRequest)
        .getOrElse(fail(s"Request was not handled: $getStockRequest"))
      stock0 <- getStock0Response.as[Stock]

      addStockRequest <- Request[IO](Method.PUT, Uri.unsafeFromString(path)).withBody(entry.asJson)
      _ <- itemHttpService
        .run(addStockRequest)
        .getOrElse(fail(s"Request was not handled: $addStockRequest"))

      getStock1Response <- itemHttpService
        .run(getStockRequest)
        .getOrElse(fail(s"Request was not handled: $getStockRequest"))
      stock1 <- getStock1Response.as[Stock]

      removeStockRequest <- Request[IO](Method.PUT, Uri.unsafeFromString(path))
        .withBody(entry.copy(delta = -1 * entry.delta).asJson)
      _ <- itemHttpService
        .run(removeStockRequest)
        .getOrElse(fail(s"Request was not handled: $removeStockRequest"))

      getStock2Response <- itemHttpService
        .run(getStockRequest)
        .getOrElse(fail(s"Request was not handled: $getStockRequest"))
      stock2 <- getStock2Response.as[Stock]

    } yield {

      stock0.quantity shouldEqual 0
      stock1.quantity shouldEqual entry.delta
      stock2.quantity shouldEqual 0
    }).unsafeRunSync

  }

  test("no negative stock") {

    val itemRepo = ItemRepositoryInMemoryInterpreter[IO]
    val itemValidation = ItemValidationInterpreter[IO](itemRepo)
    val itemService = ItemService(itemRepo, itemValidation)

    val entryRepo = EntryRepositoryInMemoryInterpreter[IO]
    val stockService = StockService(entryRepo, itemRepo)

    val itemHttpService = ItemEndpoints.endpoints[IO](itemService, stockService)

    implicit val itemDecoder: EntityDecoder[IO, Item] = jsonOf
    implicit val stockDecoder: EntityDecoder[IO, Stock] = jsonOf

    val item =
      ItemEndpoints.ItemRequest(name = "Cheese Burger", price = 99.99, category = "Food & Drinks")

    val entry = ItemEndpoints.StockRequest(delta = -1)

    (for {
      createRequest <- Request[IO](Method.POST, Uri.uri("/")).withBody(item.asJson)
      createResponse <- itemHttpService
        .run(createRequest)
        .getOrElse(fail(s"Request was not handled: $createRequest"))
      createdItem <- createResponse.as[Item]

      path = "/" + createdItem.id.map(_.value.toString).get + "/stocks"

      getStockRequest = Request[IO](Method.GET, Uri.unsafeFromString(path))

      getStockResponse <- itemHttpService
        .run(getStockRequest)
        .getOrElse(fail(s"Request was not handled: $getStockRequest"))
      stock <- getStockResponse.as[Stock]

      negStockRequest <- Request[IO](Method.PUT, Uri.unsafeFromString(path)).withBody(entry.asJson)
      negStockResponse <- itemHttpService
        .run(negStockRequest)
        .getOrElse(fail(s"Request was not handled: $negStockRequest"))

    } yield {

      stock.quantity shouldEqual 0
      negStockResponse.status shouldEqual Conflict
    }).unsafeRunSync

  }
}
