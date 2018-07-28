package name.amadoucisse.restoo
package infra
package endpoint

import domain.items._
import service.ItemService
import repository.inmemory.ItemRepositoryInMemoryInterpreter

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
    val itemHttpService = ItemEndpoints.endpoints[IO](itemService)

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
    val itemHttpService = ItemEndpoints.endpoints[IO](itemService)

    implicit val itemDecoder: EntityDecoder[IO, Item] = jsonOf

    val item = ItemEndpoints.ItemRequest(name = "Cheese Burger", price = 99.99, category = "Food & Drinks")

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
    val itemHttpService: HttpService[IO] = ItemEndpoints.endpoints(itemService)

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
}
