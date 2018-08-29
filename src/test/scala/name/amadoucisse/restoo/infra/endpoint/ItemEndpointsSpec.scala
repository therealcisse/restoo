package name.amadoucisse.restoo
package infra
package endpoint

import name.amadoucisse.restoo.config.SwaggerConf
import domain.items._
import domain.entries._
import common.IOAssertion
import service.{ItemService, StockService}
import repository.inmemory.{EntryRepositoryInMemoryInterpreter, ItemRepositoryInMemoryInterpreter}
import cats.effect._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.literal._
import io.circe.Json
import http.{ApiResponseCodes, HttpErrorHandler}
import utils.Validation
import org.http4s._
import org.http4s.circe._
import org.scalatest._
import org.scalatest.prop.PropertyChecks

import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.NonNegDouble

@SuppressWarnings(Array("org.wartremover.warts.Throw", "org.wartremover.warts.OptionPartial"))
class ItemEndpointsSpec
    extends FunSuite
    with Matchers
    with PropertyChecks
    with Arbitraries
    with dsl.Http4sDsl[IO] {

  implicit val httpErrorHandler: HttpErrorHandler[IO] = new HttpErrorHandler[IO]

  test("add item") {

    IOAssertion {
      val itemRepo = ItemRepositoryInMemoryInterpreter[IO]
      val itemService = ItemService(itemRepo)

      val entryRepo = EntryRepositoryInMemoryInterpreter[IO]
      val stockService = StockService(entryRepo, itemRepo)

      val swaggerConf = SwaggerConf("localhost", Nil)

      val itemHttpService = ItemEndpoints.endpoints[IO](itemService, stockService, swaggerConf)

      val item =
        ItemEndpoints.ItemRequest(name = "Item 0", price = 99.99, category = "Food & Drinks")

      for {
        request <- Request[IO](Method.POST, Uri.uri("/")).withBody(item.asJson)
        response <- itemHttpService
          .run(request)
          .getOrElse(fail(s"Request was not handled: $request"))
        _ = response.status shouldEqual Created
      } yield {}
    }

  }

  test("disallow duplicate item names on update") {

    IOAssertion {

      val itemRepo = ItemRepositoryInMemoryInterpreter[IO]
      val itemService = ItemService(itemRepo)

      val entryRepo = EntryRepositoryInMemoryInterpreter[IO]
      val stockService = StockService(entryRepo, itemRepo)

      val swaggerConf = SwaggerConf("localhost", Nil)

      val itemHttpService = ItemEndpoints.endpoints[IO](itemService, stockService, swaggerConf)

      implicit val itemDecoder: EntityDecoder[IO, Item] = jsonOf

      val itemARequest =
        ItemEndpoints.ItemRequest(name = "ItemA", price = 99.99, category = "Food & Drinks")
      val itemBRequest = itemARequest.copy(name = "ItemB")

      for {

        // Add item A
        request <- Request[IO](Method.POST, Uri.uri("/")).withBody(itemARequest.asJson)
        response <- itemHttpService
          .run(request)
          .getOrElse(fail(s"Request was not handled: $request"))
        _ = response.status shouldEqual Created
        itemA <- response.as[Item]
        _ = itemA.name shouldEqual Name(itemARequest.name)

        // Add item B
        request <- Request[IO](Method.POST, Uri.uri("/")).withBody(itemBRequest.asJson)
        response <- itemHttpService
          .run(request)
          .getOrElse(fail(s"Request was not handled: $request"))
        _ = response.status shouldEqual Created
        itemB <- response.as[Item]
        _ = itemB.name shouldEqual Name(itemBRequest.name)

        // Try updating itemB's name to equal itemA's name
        itemToUpdate = itemBRequest.copy(name = itemA.name.value)
        updateRequest <- Request[IO](
          Method.PUT,
          Uri.unsafeFromString("/" + itemB.id.map(_.value.toString).get))
          .withBody(itemToUpdate.asJson)
        updateResponse <- itemHttpService
          .run(updateRequest)
          .getOrElse(fail(s"Request was not handled: $updateRequest"))
        updatedItem <- updateResponse.as[Json]
        _ = updateResponse.status shouldEqual Conflict

        errorResponse = updatedItem.hcursor.downField("error")
        _ = errorResponse.get[String]("code") shouldEqual Right(ApiResponseCodes.CONFLICT)
        _ = errorResponse.get[String]("message") shouldEqual Right("Item name is taken")
        _ = errorResponse.get[String]("type") shouldEqual Right("DuplicateItem")

      } yield {}

    }

  }

  test("disallow duplicate items on create") {

    val itemRepo = ItemRepositoryInMemoryInterpreter[IO]
    val itemService = ItemService(itemRepo)

    val entryRepo = EntryRepositoryInMemoryInterpreter[IO]
    val stockService = StockService(entryRepo, itemRepo)

    val swaggerConf = SwaggerConf("localhost", Nil)

    val itemHttpService = ItemEndpoints.endpoints[IO](itemService, stockService, swaggerConf)

    val item =
      ItemEndpoints.ItemRequest(name = "Item 0", price = 99.99, category = "Food & Drinks")

    // Create an item
    IOAssertion {

      for {
        request <- Request[IO](Method.POST, Uri.uri("/")).withBody(item.asJson)
        response <- itemHttpService
          .run(request)
          .getOrElse(fail(s"Request was not handled: $request"))
        _ = response.status shouldEqual Created
      } yield {}
    }

    // Try adding a duplicate
    IOAssertion {

      for {
        request <- Request[IO](Method.POST, Uri.uri("/")).withBody(item.asJson)
        response <- itemHttpService
          .run(request)
          .getOrElse(fail(s"Request was not handled: $request"))

        _ = response.status shouldEqual Conflict

        responseEntity = response.as[Json].unsafeRunSync().hcursor.downField("error")

        _ = responseEntity.get[String]("code") shouldEqual Right(ApiResponseCodes.CONFLICT)
        _ = responseEntity.get[String]("message") shouldEqual Right("Item already exists")
        _ = responseEntity.get[String]("type") shouldEqual Right("ItemAlreadyExists")
      } yield {}
    }

  }

  test("disallow invalid items") {

    val itemRepo = ItemRepositoryInMemoryInterpreter[IO]
    val itemService = ItemService(itemRepo)

    val entryRepo = EntryRepositoryInMemoryInterpreter[IO]
    val stockService = StockService(entryRepo, itemRepo)

    val swaggerConf = SwaggerConf("localhost", Nil)

    val itemHttpService = ItemEndpoints.endpoints[IO](itemService, stockService, swaggerConf)

    val item = ItemEndpoints.ItemRequest(name = "", price = -99.99, category = "")

    implicit val itemDecoder: EntityDecoder[IO, Item] = jsonOf

    IOAssertion {

      for {
        request <- Request[IO](Method.POST, Uri.uri("/")).withBody(item.asJson)
        response <- itemHttpService
          .run(request)
          .getOrElse(fail(s"Request was not handled: $request"))

        _ = response.status shouldEqual UnprocessableEntity

        responseEntity = response.as[Json].unsafeRunSync().hcursor
        _ = responseEntity.get[String]("code") shouldEqual Right(ApiResponseCodes.VALIDATION_FAILED)
        _ = responseEntity.get[String]("message") shouldEqual Right("Validation failed")
        _ = responseEntity.get[String]("type") shouldEqual Right("FieldErrors")
        _ = responseEntity.get[Vector[Validation.FieldError]]("errors") match {
          case Left(failure) => fail(failure.message)
          case Right(errors) =>
            errors.size shouldEqual 3
        }
      } yield {}
    }

    IOAssertion {

      for {
        createRequest <- Request[IO](Method.POST, Uri.uri("/"))
          .withBody(
            ItemEndpoints.ItemRequest(name = "Name", price = 99.99, category = "Category").asJson)
        createResponse <- itemHttpService
          .run(createRequest)
          .getOrElse(fail(s"Request was not handled: $createRequest"))
        createdItem <- createResponse.as[Item]

        id = createdItem.id.map(_.value.toString).get

        // Invalid update
        request <- Request[IO](Method.PUT, Uri.unsafeFromString("/" + id)).withBody(item.asJson)
        response <- itemHttpService
          .run(request)
          .getOrElse(fail(s"Request was not handled: $request"))

        _ = response.status shouldEqual UnprocessableEntity

        responseEntity = response.as[Json].unsafeRunSync().hcursor
        _ = responseEntity.get[String]("code") shouldEqual Right(ApiResponseCodes.VALIDATION_FAILED)
        _ = responseEntity.get[String]("message") shouldEqual Right("Validation failed")
        _ = responseEntity.get[String]("type") shouldEqual Right("FieldErrors")
        _ = responseEntity.get[Vector[Validation.FieldError]]("errors") match {
          case Left(failure) => fail(failure.message)
          case Right(errors) =>
            errors.size shouldEqual 3
        }

        // Invalid patch
        patches = Vector(
          json"""{"op":"replace","path":"/name","value":${item.name}}""",
          json"""{"op":"replace","path":"/price","value":${item.price}}""",
          json"""{"op":"replace","path":"/category","value":${item.category}}""",
        )
        request <- Request[IO](Method.PATCH, Uri.unsafeFromString("/" + id))
          .withBody(patches.asJson)
        response <- itemHttpService
          .run(request)
          .getOrElse(fail(s"Request was not handled: $request"))

        _ = response.status shouldEqual UnprocessableEntity

        responseEntity = response.as[Json].unsafeRunSync().hcursor
        _ = responseEntity.get[String]("code") shouldEqual Right(ApiResponseCodes.VALIDATION_FAILED)
        _ = responseEntity.get[String]("message") shouldEqual Right("Validation failed")
        _ = responseEntity.get[String]("type") shouldEqual Right("FieldErrors")
        _ = responseEntity.get[Vector[Validation.FieldError]]("errors") match {
          case Left(failure) => fail(failure.message)
          case Right(errors) =>
            errors.size shouldEqual 3
        }

      } yield {}
    }

  }

  test("update item") {

    IOAssertion {

      val itemRepo = ItemRepositoryInMemoryInterpreter[IO]
      val itemService = ItemService(itemRepo)

      val entryRepo = EntryRepositoryInMemoryInterpreter[IO]
      val stockService = StockService(entryRepo, itemRepo)

      val swaggerConf = SwaggerConf("localhost", Nil)

      val itemHttpService = ItemEndpoints.endpoints[IO](itemService, stockService, swaggerConf)

      implicit val itemDecoder: EntityDecoder[IO, Item] = jsonOf

      val item =
        ItemEndpoints.ItemRequest(name = "Cheese Burger", price = 99.99, category = "Food & Drinks")

      for {
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

        _ = createResponse.status shouldEqual Created
        _ = updateResponse.status shouldEqual Ok
        _ = updatedItem.name.value shouldEqual item.name.reverse
        _ = createdItem.id shouldEqual updatedItem.id
      } yield {}
    }

  }

  test("patch item") {

    IOAssertion {

      val itemRepo = ItemRepositoryInMemoryInterpreter[IO]
      val itemService = ItemService(itemRepo)

      val entryRepo = EntryRepositoryInMemoryInterpreter[IO]
      val stockService = StockService(entryRepo, itemRepo)

      val swaggerConf = SwaggerConf("localhost", Nil)

      val itemHttpService = ItemEndpoints.endpoints[IO](itemService, stockService, swaggerConf)

      implicit val itemDecoder: EntityDecoder[IO, Item] = jsonOf

      val item =
        ItemEndpoints.ItemRequest(name = "Cheese Burger", price = 99.99, category = "Food & Drinks")

      for {
        createRequest <- Request[IO](Method.POST, Uri.uri("/")).withBody(item.asJson)
        createResponse <- itemHttpService
          .run(createRequest)
          .getOrElse(fail(s"Request was not handled: $createRequest"))
        createdItem <- createResponse.as[Item]

        id = createdItem.id.map(_.value.toString).get

        // patch name
        newName = "Cake"
        patchName = json"""{"op":"replace","path":"/name","value":$newName}"""
        patchRequest <- Request[IO](Method.PATCH, Uri.unsafeFromString("/" + id))
          .withBody(patchName)
        patchResponse <- itemHttpService
          .run(patchRequest)
          .getOrElse(fail(s"Request was not handled: $patchRequest"))
        _ = patchResponse.status shouldEqual Ok
        patchedItem <- patchResponse.as[Item]
        _ = patchedItem.name shouldEqual Name(newName)

        // patch price
        newPrice = NonNegDouble.unsafeFrom(50.99)
        patchPrice = json"""{"op":"replace","path":"/price","value":${newPrice.value}}"""
        patchRequest <- Request[IO](Method.PATCH, Uri.unsafeFromString("/" + id))
          .withBody(patchPrice)
        patchResponse <- itemHttpService
          .run(patchRequest)
          .getOrElse(fail(s"Request was not handled: $patchRequest"))
        _ = patchResponse.status shouldEqual Ok
        patchedItem <- patchResponse.as[Item]
        _ = patchedItem.priceInCents shouldEqual Cents.fromStandardAmount(newPrice)

        // patch category
        newCategory = "Dessert"
        patchCategory = json"""{"op":"replace","path":"/category","value":$newCategory}"""
        patchRequest <- Request[IO](Method.PATCH, Uri.unsafeFromString("/" + id))
          .withBody(patchCategory)
        patchResponse <- itemHttpService
          .run(patchRequest)
          .getOrElse(fail(s"Request was not handled: $patchRequest"))
        _ = patchResponse.status shouldEqual Ok
        patchedItem <- patchResponse.as[Item]
        _ = patchedItem.category shouldEqual Category(newCategory)

        // Revert all changes
        patches = Vector(
          json"""{"op":"replace","path":"/name","value":${item.name}}""",
          json"""{"op":"replace","path":"/price","value":${item.price}}""",
          json"""{"op":"replace","path":"/category","value":${item.category}}""",
        )
        patchRequest <- Request[IO](Method.PATCH, Uri.unsafeFromString("/" + id))
          .withBody(patches.asJson)
        patchResponse <- itemHttpService
          .run(patchRequest)
          .getOrElse(fail(s"Request was not handled: $patchRequest"))
        _ = patchResponse.status shouldEqual Ok
        patchedItem <- patchResponse.as[Item]
        _ = patchedItem.name shouldEqual Name(item.name)
        _ = patchedItem.priceInCents shouldEqual Cents.fromStandardAmount(
          NonNegDouble.unsafeFrom(item.price))
        _ = patchedItem.category shouldEqual Category(item.category)
      } yield {}
    }

  }

  test("delete item by id") {

    IOAssertion {

      val itemRepo = ItemRepositoryInMemoryInterpreter[IO]
      val itemService = ItemService[IO](itemRepo)

      val entryRepo = EntryRepositoryInMemoryInterpreter[IO]
      val stockService = StockService(entryRepo, itemRepo)

      val swaggerConf = SwaggerConf("localhost", Nil)

      val itemHttpService = ItemEndpoints.endpoints[IO](itemService, stockService, swaggerConf)

      implicit val itemDecoder: EntityDecoder[IO, Item] = jsonOf

      val item =
        ItemEndpoints.ItemRequest(name = "Item 0", price = 99.99, category = "Food & Drinks")

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

        _ = createResponse.status shouldEqual Created
        _ = deleteResponse.status shouldEqual Ok
        _ = getResponse.status shouldEqual NotFound
      } yield {}

    }
  }

  test("add or remove stock") {

    IOAssertion {

      val itemRepo = ItemRepositoryInMemoryInterpreter[IO]
      val itemService = ItemService(itemRepo)

      val entryRepo = EntryRepositoryInMemoryInterpreter[IO]
      val stockService = StockService(entryRepo, itemRepo)

      val swaggerConf = SwaggerConf("localhost", Nil)

      val itemHttpService = ItemEndpoints.endpoints[IO](itemService, stockService, swaggerConf)

      implicit val itemDecoder: EntityDecoder[IO, Item] = jsonOf
      implicit val stockDecoder: EntityDecoder[IO, Stock] = jsonOf

      val item =
        ItemEndpoints.ItemRequest(name = "Cheese Burger", price = 99.99, category = "Food & Drinks")

      val entry = ItemEndpoints.StockRequest(delta = 1)

      for {
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

        addStockRequest <- Request[IO](Method.PUT, Uri.unsafeFromString(path))
          .withBody(entry.asJson)
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

        _ = createResponse.status shouldEqual Created
        _ = stock0.quantity shouldEqual 0
        _ = stock1.quantity shouldEqual entry.delta
        _ = stock2.quantity shouldEqual 0

      } yield {}
    }

  }

  test("no negative stock") {

    IOAssertion {

      val itemRepo = ItemRepositoryInMemoryInterpreter[IO]
      val itemService = ItemService(itemRepo)

      val entryRepo = EntryRepositoryInMemoryInterpreter[IO]
      val stockService = StockService(entryRepo, itemRepo)

      val swaggerConf = SwaggerConf("localhost", Nil)

      val itemHttpService = ItemEndpoints.endpoints[IO](itemService, stockService, swaggerConf)

      implicit val itemDecoder: EntityDecoder[IO, Item] = jsonOf
      implicit val stockDecoder: EntityDecoder[IO, Stock] = jsonOf

      val item =
        ItemEndpoints.ItemRequest(name = "Cheese Burger", price = 99.99, category = "Food & Drinks")

      val entry = ItemEndpoints.StockRequest(delta = -1)

      for {
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

        negStockRequest <- Request[IO](Method.PUT, Uri.unsafeFromString(path))
          .withBody(entry.asJson)
        negStockResponse <- itemHttpService
          .run(negStockRequest)
          .getOrElse(fail(s"Request was not handled: $negStockRequest"))

        _ = createResponse.status shouldEqual Created
        _ = stock.quantity shouldEqual 0
        _ = negStockResponse.status shouldEqual Conflict
      } yield {}
    }

  }
}
