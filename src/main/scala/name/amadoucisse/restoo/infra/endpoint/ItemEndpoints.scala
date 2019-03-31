package name.amadoucisse.restoo
package infra
package endpoint

import scala.language.higherKinds
import cats.effect.{ Clock, Sync }
import cats.implicits._
import cats.Apply
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.dsl.Http4sDsl
import org.http4s.{ HttpRoutes, QueryParamDecoder }
import domain._
import domain.items._
import domain.entries._
import config.{ AppConf, SwaggerConf }
import http.{ HttpErrorHandler, JsonPatch, OrderBy, Page, SortBy, SwaggerSpec }
import service.{ IdService, ItemService, StockService }
import utils.Validation
import eu.timepit.refined._
import eu.timepit.refined.auto._

import java.util.concurrent.TimeUnit
import java.time.Instant

import com.ccadllc.cedi.dtrace._
import com.ccadllc.cedi.dtrace.interop.http4s.server._

import cats.mtl.ApplicativeAsk

final class ItemEndpoints[F[_]: Sync: Clock: TraceSystem](implicit headerCodec: HeaderCodec)
    extends Http4sDsl[F]
    with Codecs {
  import ItemEndpoints._

  private def createEndpoint(itemService: ItemService[F], idService: IdService[F]): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ POST → Root ⇒
        val action = for {
          itemRequest ← req
            .as[ItemRequest]
            .newSpan(Span.Name("decode-item"))

          (name, price, category) ← itemRequest.validate
            .newSpan(Span.Name("validate-item"))

          newId ← idService.newItemId
            .newAnnotatedSpan(Span.Name("generate-item-id")) {
              case Right(id) ⇒ Vector(Note.long("item-id", id.value))
            }

          now ← Clock[F]
            .monotonic(TimeUnit.MILLISECONDS)
            .newAnnotatedSpan(Span.Name("get-current-time")) {
              case Right(time) ⇒ Vector(Note.long("current-time", time))
            }

          item = Item(
            name = name,
            price = price,
            category = category,
            createdAt = DateTime(Instant.ofEpochMilli(now)),
            updatedAt = DateTime(Instant.ofEpochMilli(now)),
            id = newId,
          )

          _ ← itemService
            .createItem(item)
            .newSpan(Span.Name("create-item"))
        } yield item

        tracedAction(req, Span.Name("create-item-endpoint"))(action) >>= { item ⇒
          Created(item.asJson)
        }

    }

  private def updateEndpoint(itemService: ItemService[F]): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ PUT → (Root / ItemId(id)) ⇒
        val action = for {
          item ← itemService
            .getItem(id)
            .newAnnotatedSpan(Span.Name("get-item"), Note.long("id", id.value)) {
              case Right(item) ⇒ Vector(Note.long("item-id", item.id.value), Note.string("item-name", item.name.value))
            }

          itemRequest ← req
            .as[ItemRequest]
            .newSpan(Span.Name("decode-item"))

          (name, price, category) ← itemRequest.validate
            .newSpan(Span.Name("validate-item"))

          now ← Clock[F]
            .monotonic(TimeUnit.MILLISECONDS)
            .newAnnotatedSpan(Span.Name("get-current-time")) {
              case Right(time) ⇒ Vector(Note.long("current-time", time))
            }

          updated = item.copy(
            name = name,
            price = price,
            category = category,
            updatedAt = DateTime(Instant.ofEpochMilli(now))
          )

          _ ← itemService
            .update(updated)
            .newSpan(Span.Name("update-item"), Note.long("item-id", updated.id.value))
        } yield updated

        tracedAction(
          req,
          Span.Name("update-item-endpoint"),
          Note.long("item-id", id.value),
        )(action) >>= { item ⇒
          Ok(item.asJson)
        }
    }

  private def patchEndpoint(itemService: ItemService[F]): HttpRoutes[F] = {

    @annotation.tailrec
    def isValidJsonPatchForItem(patches: Vector[JsonPatch]): Boolean =
      patches match {
        case xs :+ s ⇒
          refineV[Item.PatchableField](s.path) match {
            case Right(_) ⇒ isValidJsonPatchForItem(xs)
            case Left(_)  ⇒ false
          }

        case _ ⇒ true
      }

    HttpRoutes.of[F] {
      case req @ PATCH → (Root / ItemId(id)) ⇒
        val action = for {

          patches ← req
            .as[Vector[JsonPatch]]
            .ensureOr(patches ⇒ AppError.invalidJsonPatch(s"Could not create request from json patch : $patches")) {
              patches ⇒
                patches.nonEmpty && isValidJsonPatchForItem(patches)
            }
            .newSpan(Span.Name("decode-json-patches"))

          item ← itemService
            .getItem(id)
            .newAnnotatedSpan(Span.Name("get-item"), Note.long("id", id.value)) {
              case Right(item) ⇒ Vector(Note.long("item-id", item.id.value), Note.string("item-name", item.name.value))
            }

          itemRequest ← {
            val unitR = ItemRequest.fromItem(item)

            val result = patches.foldLeft(unitR.asJson) { (input, patch) ⇒
              patch.applyPatch(input)
            }

            val maybeItemRequest = result.as[ItemRequest]

            maybeItemRequest.fold(
              r ⇒
                Sync[F].raiseError(
                  AppError
                    .invalidJsonPatch(s"Error : ${r.message}. Could not create request from json patch : $patches")
              ),
              Sync[F].pure
            )
          }.newSpan(Span.Name("apply-patches"))

          now ← Clock[F]
            .monotonic(TimeUnit.MILLISECONDS)
            .newAnnotatedSpan(Span.Name("get-current-time")) {
              case Right(time) ⇒ Vector(Note.long("current-time", time))
            }

          (name, price, category) ← itemRequest.validate
            .newSpan(Span.Name("validate-item"))

          updated = item.copy(
            name = name,
            price = price,
            category = category,
            updatedAt = DateTime(Instant.ofEpochMilli(now))
          )
          _ ← itemService
            .update(updated)
            .newSpan(Span.Name("update-item"), Note.long("item-id", updated.id.value))
        } yield updated

        tracedAction(
          req,
          Span.Name("patch-item-endpoint"),
          Note.long("item-id", id.value),
        )(action) >>= { item ⇒
          Ok(item.asJson)
        }
    }
  }

  private def listEndpoint(itemService: ItemService[F]): HttpRoutes[F] = {
    implicit val categoryQueryParamDecoder: QueryParamDecoder[Category] =
      QueryParamDecoder[String].map(Category(_))

    implicit val offsetQueryParamDecoder: QueryParamDecoder[Instant] =
      QueryParamDecoder[Long].map(Instant.ofEpochMilli)

    object OptionalMarkerQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Instant]("marker")

    object OptionalLimitQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("limit")

    object OptionalCategoryQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Category]("category")

    implicit val orderByQueryParamDecoder: QueryParamDecoder[List[SortBy]] =
      QueryParamDecoder[String].map(OrderBy.fromString)

    object OptionalOrderByQueryParamMatcher extends OptionalQueryParamDecoderMatcher[List[SortBy]]("sort_by")

    @annotation.tailrec
    def isValidOrderByForItem(orderBy: Seq[SortBy]): Boolean =
      orderBy match {
        case s :: xs ⇒
          refineV[Item.SortableField](s.name.value) match {
            case Right(_) ⇒ isValidOrderByForItem(xs)
            case Left(_)  ⇒ false
          }

        case _ ⇒ true
      }

    HttpRoutes.of[F] {
      case req @ GET → Root :? OptionalCategoryQueryParamMatcher(maybeCategory) +& OptionalOrderByQueryParamMatcher(
            maybeOrderBy
          ) +& OptionalMarkerQueryParamMatcher(marker) +& OptionalLimitQueryParamMatcher(limit) ⇒
        val orderBy = maybeOrderBy.getOrElse(Nil)

        if (isValidOrderByForItem(orderBy)) {
          val action = itemService
            .list(maybeCategory, orderBy, Page(marker, limit))
            .newAnnotatedSpan(
              Span.Name("get-items"),
              Note.string("category", maybeCategory.map(_.value)),
              Note.long("limit", limit.map(_.toLong)),
              Note.long("marker", marker.map(_.toEpochMilli)),
            ) {
              case Right(items) ⇒ Vector(Note.long("items-count", items.size.toLong))
            }

          tracedAction(
            req,
            Span.Name("list-items-endpoint")
          )(action) >>= { items ⇒
            Ok(items.asJson)
          }

        } else BadRequest(s"Invalid value for `sort_by` parameter: $orderBy")

    }
  }

  private def deleteItemEndpoint(itemService: ItemService[F]): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ DELETE → (Root / ItemId(id)) ⇒
        val action = itemService
          .deleteItem(id)
          .newSpan(Span.Name("delete-item"), Note.long("id", id.value))

        tracedAction(
          req,
          Span.Name("delete-item-endpoint"),
          Note.long("item-id", id.value)
        )(action) >> NoContent()
    }

  private def getItemEndpoint(itemService: ItemService[F]): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ GET → (Root / ItemId(id)) ⇒
        val action = itemService
          .getItem(id)
          .newAnnotatedSpan(Span.Name("get-item"), Note.long("id", id.value)) {
            case Right(item) ⇒ Vector(Note.long("item-id", item.id.value), Note.string("item-name", item.name.value))
          }

        tracedAction(
          req,
          Span.Name("get-item-endpoint"),
          Note.long("item-id", id.value)
        )(action) >>= { item ⇒
          Ok(item.asJson)
        }
    }

  private def createStockEntryEndpoint(stockService: StockService[F]): HttpRoutes[F] = {
    implicit val deltaQueryParamDecoder: QueryParamDecoder[Delta] =
      QueryParamDecoder[Int].map(Delta(_))

    object DeltaQueryParamMatcher extends ValidatingQueryParamDecoderMatcher[Delta]("delta")

    HttpRoutes.of[F] {
      case req @ PUT → (Root / ItemId(itemId) / "stocks") :? DeltaQueryParamMatcher(deltaValidated) ⇒
        deltaValidated.fold(
          _ ⇒ BadRequest("unable to parse argument `delta`"), { delta ⇒
            val action = stockService
              .createEntry(itemId, delta)
              .newAnnotatedSpan(
                Span.Name("create-stock-entry"),
                Note.long("id", itemId.value),
                Note.long("delta", delta.value.toLong),
              ) {
                case Right(stock) ⇒ Vector(Note.long("quantity", stock.quantity))
              }

            tracedAction(
              req,
              Span.Name("change-item-stock"),
              Note.long("item-id", itemId.value),
              Note.long("stock-amount", delta.value.toLong)
            )(action) >>= { stock ⇒
              Ok(stock.asJson)
            }

          }
        )

    }
  }

  private def getStockEndpoint(stockService: StockService[F]): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ GET → (Root / ItemId(itemId) / "stocks") ⇒
        val action = stockService
          .getStock(itemId)
          .newAnnotatedSpan(
            Span.Name("get-item-stock"),
            Note.long("id", itemId.value),
          ) {
            case Right(stock) ⇒ Vector(Note.long("quantity", stock.quantity))
          }

        tracedAction(
          req,
          Span.Name("get-item-stock-endpoint"),
          Note.long("item-id", itemId.value)
        )(action) >>= { item ⇒
          Ok(item.asJson)
        }
    }

  private def getSwaggerSpec(swaggerConf: SwaggerConf): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET → (Root / "swagger-spec.json") ⇒
        Ok(SwaggerSpec.swaggerSpec(swaggerConf))
    }

  def endpoints(itemService: ItemService[F],
                stockService: StockService[F],
                idService: IdService[F],
                swaggerConf: SwaggerConf)(
      implicit H: HttpErrorHandler[F, AppError],
  ): HttpRoutes[F] =
    H.handle {
      createEndpoint(itemService, idService) <+>
        patchEndpoint(itemService) <+>
        updateEndpoint(itemService) <+>
        deleteItemEndpoint(itemService) <+>
        getItemEndpoint(itemService) <+>
        listEndpoint(itemService) <+>
        createStockEntryEndpoint(stockService) <+>
        getStockEndpoint(stockService) <+>
        getSwaggerSpec(swaggerConf)
    }
}

object ItemEndpoints {
  def endpoints[F[_]: Sync: Clock: ApplicativeAsk[?[_], AppConf]: TraceSystem](itemService: ItemService[F],
                                                                               stockService: StockService[F],
                                                                               idService: IdService[F],
  )(
      implicit H: HttpErrorHandler[F, AppError],
      headerCodec: HeaderCodec,
  ): F[HttpRoutes[F]] =
    for {
      swaggerConf ← AppConf.swaggerConf[F]
    } yield new ItemEndpoints[F].endpoints(itemService, stockService, idService, swaggerConf)

  final case class ItemRequest(
      name: String,
      priceInCents: Int,
      currency: String,
      category: String,
  ) {

    def validate[F[_]: Clock: Sync]: F[(Name, Money, Category)] = {
      import Validation._
      import cats.data.ValidatedNec
      import eu.timepit.refined.collection.NonEmpty
      import eu.timepit.refined.numeric.NonNegative

      val item: ValidatedNec[FieldError, (Name, Money, Category)] = Apply[ValidatedNec[FieldError, ?]]
        .map4(
          refineV[NonEmpty](name)
            .leftMap(_ ⇒ FieldError("item.name", "error.empty"))
            .toValidatedNec,
          refineV[NonNegative](priceInCents)
            .leftMap(_ ⇒ FieldError("item.priceInCents", "error.negative"))
            .toValidatedNec,
          refineV[Money.CurrencyCode](currency)
            .leftMap(_ ⇒ FieldError("item.currency", "error.currency"))
            .toValidatedNec,
          refineV[NonEmpty](category)
            .leftMap(_ ⇒ FieldError("item.category", "error.empty"))
            .toValidatedNec,
        ) { (name, priceInCents, currency, category) ⇒
          (
            Name(name),
            Money(priceInCents, currency),
            Category(category),
          )
        }

      item.fold(errors ⇒ Sync[F].raiseError(AppError.validationFailed(errors)), Sync[F].pure)
    }
  }

  object ItemRequest {

    def fromItem(item: Item) =
      ItemRequest(
        name = item.name.value,
        priceInCents = item.price.amountInCents,
        currency = item.price.currency,
        category = item.category.value,
      )

  }

}
