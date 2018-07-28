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
class StockEndpointsSpec
    extends FunSuite
    with Matchers
    with PropertyChecks
    with Arbitraries
    with Http4sDsl[IO] {

  test("add or remove stock") {

    val itemRepo = ItemRepositoryInMemoryInterpreter[IO]
    val itemValidation = ItemValidationInterpreter[IO](itemRepo)
    val itemService = ItemService(itemRepo, itemValidation)

    val entryRepo = EntryRepositoryInMemoryInterpreter[IO]
    val stockService = StockService(entryRepo, itemRepo)

    val itemHttpService = ItemEndpoints.endpoints[IO](itemService)
    val stockHttpService = StockEndpoints.endpoints[IO](stockService)

    implicit val itemDecoder: EntityDecoder[IO, Item] = jsonOf
    implicit val stockDecoder: EntityDecoder[IO, Stock] = jsonOf

    val item =
      ItemEndpoints.ItemRequest(name = "Cheese Burger", price = 99.99, category = "Food & Drinks")

    val entry = StockEndpoints.StockRequest(delta = 1)

    (for {
      createRequest <- Request[IO](Method.POST, Uri.uri("/")).withBody(item.asJson)
      createResponse <- itemHttpService
        .run(createRequest)
        .getOrElse(fail(s"Request was not handled: $createRequest"))
      createdItem <- createResponse.as[Item]

      path = "/" + createdItem.id.map(_.value.toString).get + "/stocks"

      getStockRequest = Request[IO](Method.GET, Uri.unsafeFromString(path))

      getStock0Response <- stockHttpService
        .run(getStockRequest)
        .getOrElse(fail(s"Request was not handled: $getStockRequest"))
      stock0 <- getStock0Response.as[Stock]

      addStockRequest <- Request[IO](Method.PUT, Uri.unsafeFromString(path)).withBody(entry.asJson)
      _ <- stockHttpService
        .run(addStockRequest)
        .getOrElse(fail(s"Request was not handled: $addStockRequest"))

      getStock1Response <- stockHttpService
        .run(getStockRequest)
        .getOrElse(fail(s"Request was not handled: $getStockRequest"))
      stock1 <- getStock1Response.as[Stock]

      removeStockRequest <- Request[IO](Method.PUT, Uri.unsafeFromString(path))
        .withBody(entry.copy(delta = -1 * entry.delta).asJson)
      _ <- stockHttpService
        .run(removeStockRequest)
        .getOrElse(fail(s"Request was not handled: $removeStockRequest"))

      getStock2Response <- stockHttpService
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

    val itemHttpService = ItemEndpoints.endpoints[IO](itemService)
    val stockHttpService = StockEndpoints.endpoints[IO](stockService)

    implicit val itemDecoder: EntityDecoder[IO, Item] = jsonOf
    implicit val stockDecoder: EntityDecoder[IO, Stock] = jsonOf

    val item =
      ItemEndpoints.ItemRequest(name = "Cheese Burger", price = 99.99, category = "Food & Drinks")

    val entry = StockEndpoints.StockRequest(delta = -1)

    (for {
      createRequest <- Request[IO](Method.POST, Uri.uri("/")).withBody(item.asJson)
      createResponse <- itemHttpService
        .run(createRequest)
        .getOrElse(fail(s"Request was not handled: $createRequest"))
      createdItem <- createResponse.as[Item]

      path = "/" + createdItem.id.map(_.value.toString).get + "/stocks"

      getStockRequest = Request[IO](Method.GET, Uri.unsafeFromString(path))

      getStockResponse <- stockHttpService
        .run(getStockRequest)
        .getOrElse(fail(s"Request was not handled: $getStockRequest"))
      stock <- getStockResponse.as[Stock]

      negStockRequest <- Request[IO](Method.PUT, Uri.unsafeFromString(path)).withBody(entry.asJson)
      negStockResponse <- stockHttpService
        .run(negStockRequest)
        .getOrElse(fail(s"Request was not handled: $negStockRequest"))

    } yield {

      stock.quantity shouldEqual 0
      negStockResponse.status shouldEqual Conflict
    }).unsafeRunSync

  }
}
