package name.amadoucisse.restoo
package http

import io.circe.Json
import config.SwaggerConf

import eu.timepit.refined.auto._

object SwaggerSpec {
  import Json._

  val ApiVersion = "v1"

  def swaggerSpec(swaggerConf: SwaggerConf): Json = obj(
    "swagger" → fromString("2.0"),
    "info" → obj(
      "description" → fromString("REST API for managing restaurant stock.")
    ),
    "host" → fromString(swaggerConf.host),
    "basePath" → fromString(s"/api/$ApiVersion/items"),
    "schemes" → arr(swaggerConf.schemes.map(fromString): _*),
    "paths" → paths(),
    "definitions" → definitions()
  )

  private def paths() = obj(
    "" → obj(
      "get" → itemsGet(),
      "post" → itemPost(),
    ),
    "/{itemId}" → obj(
      "get" → itemGet(),
      "put" → itemPut(),
      "patch" → itemPatch(),
      "delete" → itemDelete(),
    ),
    "/{itemId}/stocks" → obj(
      "get" → itemStockGet(),
      "put" → itemStockPut(),
    ),
  )

  private def itemsGet() = obj(
    "summary" → fromString("Get a list of items"),
    "description" → fromString(""),
    "operationId" → fromString("listItems"),
    "consumes" → arr(fromString("application/json")),
    "produces" → arr(fromString("application/json")),
    "parameters" → arr(
      obj(
        "in" → fromString("query"),
        "name" → fromString("sort_by"),
        "description" → fromString(
          "Optional comma-separated list of field names to sort by. Prefix a name with `-` or `+` for the corresponding order. No prefix defaults to ascending order."
        ),
        "type" → fromString("string"),
      ),
      obj(
        "in" → fromString("query"),
        "name" → fromString("category"),
        "description" → fromString("Optional category."),
        "type" → fromString("string"),
      ),
    ),
    "responses" → obj(
      "200" → obj(
        "description" → fromString("Success"),
        "schema" → obj(
          "type" → fromString("array"),
          "items" → obj(
            "$ref" → fromString("#/definitions/Item")
          )
        ),
      )
    )
  )

  private def itemGet() = obj(
    "summary" → fromString("Get single item"),
    "description" → fromString(""),
    "operationId" → fromString("getItem"),
    "consumes" → arr(),
    "produces" → arr(fromString("application/json")),
    "parameters" → arr(
      obj(
        "in" → fromString("path"),
        "name" → fromString("itemId"),
        "description" → fromString("Id of the item."),
        "type" → fromString("integer"),
        "format" → fromString("int32"),
        "required" → fromBoolean(true),
      )
    ),
    "responses" → obj(
      "200" → obj(
        "description" → fromString("Success"),
        "schema" → obj(
          "$ref" → fromString("#/definitions/Item")
        ),
      ),
      "404" → obj(
        "description" → fromString("Item not found"),
        "schema" → obj(
          "$ref" → fromString("#/definitions/ApiResponseWrapper")
        ),
      ),
    )
  )

  private def itemPost() = obj(
    "summary" → fromString("Create an item"),
    "description" → fromString(""),
    "operationId" → fromString("createItem"),
    "consumes" → arr(fromString("application/json")),
    "produces" → arr(fromString("application/json")),
    "parameters" → arr(
      obj(
        "in" → fromString("body"),
        "name" → fromString("body"),
        "description" → fromString("ItemRequest object"),
        "required" → fromBoolean(true),
        "schema" → obj(
          "$ref" → fromString("#/definitions/ItemRequest")
        )
      )
    ),
    "responses" → obj(
      "201" → obj(
        "description" → fromString("Success"),
        "schema" → obj(
          "$ref" → fromString("#/definitions/Item")
        ),
      ),
      "409" → obj(
        "description" → fromString("Item already exists"),
        "schema" → obj(
          "$ref" → fromString("#/definitions/ApiResponseWrapper")
        ),
      ),
      "422" → obj(
        "description" → fromString("Validation error"),
        "schema" → obj(
          "$ref" → fromString("#/definitions/FieldErrors"),
        ),
      ),
    )
  )

  private def itemPut() = obj(
    "summary" → fromString("Update an item"),
    "description" → fromString(""),
    "operationId" → fromString("updateItem"),
    "consumes" → arr(fromString("application/json")),
    "produces" → arr(fromString("application/json")),
    "parameters" → arr(
      obj(
        "in" → fromString("path"),
        "name" → fromString("itemId"),
        "description" → fromString("Id of the item."),
        "type" → fromString("integer"),
        "format" → fromString("int32"),
        "required" → fromBoolean(true),
      ),
      obj(
        "in" → fromString("body"),
        "name" → fromString("body"),
        "description" → fromString("Item object"),
        "required" → fromBoolean(true),
        "schema" → obj(
          "$ref" → fromString("#/definitions/ItemRequest")
        )
      )
    ),
    "responses" → obj(
      "200" → obj(
        "description" → fromString("Success"),
        "schema" → obj(
          "$ref" → fromString("#/definitions/Item")
        ),
      ),
      "404" → obj(
        "description" → fromString("Item not found"),
        "schema" → obj(
          "$ref" → fromString("#/definitions/ApiResponseWrapper")
        ),
      ),
      "409" → obj(
        "description" → fromString("Item already exists"),
        "schema" → obj(
          "$ref" → fromString("#/definitions/ApiResponseWrapper")
        ),
      ),
      "422" → obj(
        "description" → fromString("Validation error"),
        "schema" → obj(
          "$ref" → fromString("#/definitions/FieldErrors"),
        ),
      ),
    ),
  )

  private def itemPatch() = obj(
    "summary" → fromString("Patches an item"),
    "description" → fromString(""),
    "operationId" → fromString("patchItem"),
    "consumes" → arr(fromString("application/json")),
    "produces" → arr(fromString("application/json")),
    "parameters" → arr(
      obj(
        "in" → fromString("path"),
        "name" → fromString("itemId"),
        "description" → fromString("Id of the item."),
        "type" → fromString("integer"),
        "format" → fromString("int32"),
        "required" → fromBoolean(true),
      ),
      obj(
        "in" → fromString("body"),
        "name" → fromString("body"),
        "description" → fromString("Json patch object (rfc6902)."),
        "required" → fromBoolean(true),
        "schema" → obj(
          "type" → fromString("array"),
          "items" → obj(
            "$ref" → fromString("#/definitions/JsonPatch")
          ),
        ),
      ),
    ),
    "responses" → obj(
      "200" → obj(
        "description" → fromString("Success"),
        "schema" → obj(
          "$ref" → fromString("#/definitions/Item")
        ),
      ),
      "404" → obj(
        "description" → fromString("Item not found"),
        "schema" → obj(
          "$ref" → fromString("#/definitions/ApiResponseWrapper")
        ),
      ),
      "409" → obj(
        "description" → fromString("Item already exists"),
        "schema" → obj(
          "$ref" → fromString("#/definitions/ApiResponseWrapper")
        ),
      ),
      "422" → obj(
        "description" → fromString("Validation error"),
        "schema" → obj(
          "$ref" → fromString("#/definitions/FieldErrors"),
        ),
      ),
    ),
  )

  private def itemDelete() = obj(
    "summary" → fromString("Delete an item"),
    "description" → fromString(""),
    "operationId" → fromString("deleteItem"),
    "consumes" → arr(),
    "produces" → arr(fromString("application/json")),
    "parameters" → arr(
      obj(
        "in" → fromString("path"),
        "name" → fromString("itemId"),
        "description" → fromString("Id of the item."),
        "type" → fromString("integer"),
        "format" → fromString("int64"),
        "required" → fromBoolean(true),
      )
    ),
    "responses" → obj(
      "204" → obj(
        "description" → fromString("Success"),
      )
    )
  )

  private def itemStockGet() = obj(
    "summary" → fromString("Get item stock"),
    "description" → fromString(""),
    "operationId" → fromString("getItemStock"),
    "consumes" → arr(),
    "produces" → arr(fromString("application/json")),
    "parameters" → arr(
      obj(
        "in" → fromString("path"),
        "name" → fromString("itemId"),
        "description" → fromString("Id of the item."),
        "type" → fromString("integer"),
        "required" → fromBoolean(true),
      )
    ),
    "responses" → obj(
      "200" → obj(
        "description" → fromString("Success"),
        "schema" → obj(
          "$ref" → fromString("#/definitions/Stock")
        ),
      ),
      "404" → obj(
        "description" → fromString("Item not found"),
        "schema" → obj(
          "$ref" → fromString("#/definitions/ApiResponseWrapper")
        ),
      ),
    )
  )

  private def itemStockPut() = obj(
    "summary" → fromString("Update item stock"),
    "description" → fromString(""),
    "operationId" → fromString("updateItemStock"),
    "consumes" → arr(fromString("application/json")),
    "produces" → arr(fromString("application/json")),
    "parameters" → arr(
      obj(
        "in" → fromString("path"),
        "name" → fromString("itemId"),
        "description" → fromString("Id of the item."),
        "type" → fromString("integer"),
        "format" → fromString("int32"),
        "required" → fromBoolean(true),
      ),
      obj(
        "in" → fromString("query"),
        "name" → fromString("delta"),
        "description" → fromString("Increment/decrement value"),
        "type" → fromString("integer"),
        "format" → fromString("int32"),
        "required" → fromBoolean(true),
      )
    ),
    "responses" → obj(
      "200" → obj(
        "description" → fromString("Success"),
        "schema" → obj(
          "$ref" → fromString("#/definitions/Stock")
        ),
      ),
      "404" → obj(
        "description" → fromString("Item not found"),
        "schema" → obj(
          "$ref" → fromString("#/definitions/ApiResponseWrapper")
        ),
      ),
      "409" → obj(
        "description" → fromString("Item out of stock"),
        "schema" → obj(
          "$ref" → fromString("#/definitions/ApiResponseWrapper")
        ),
      ),
    )
  )

  private def definitions() = obj(
    "ItemRequest" → obj(
      "type" → fromString("object"),
      "required" → arr(
        fromString("name"),
        fromString("priceInCents"),
        fromString("currency"),
        fromString("category"),
      ),
      "properties" → obj(
        "name" → obj(
          "type" → fromString("string"),
          "minLength" → fromInt(1),
        ),
        "priceInCents" → obj(
          "type" → fromString("integer"),
          "format" → fromString("int32"),
        ),
        "currency" → obj(
          "$ref" → fromString("#/definitions/CurrencyCode"),
        ),
        "category" → obj(
          "type" → fromString("string"),
        ),
      )
    ),
    "Stock" → obj(
      "type" → fromString("object"),
      "required" → arr(
        fromString("item"),
        fromString("quantity")
      ),
      "properties" → obj(
        "item" → obj(
          "$ref" → fromString("#/definitions/Item")
        ),
        "quantity" → obj(
          "type" → fromString("integer"),
          "format" → fromString("int64"),
        ),
      )
    ),
    "ApiResponseWrapper" → obj(
      "type" → fromString("object"),
      "required" → arr(
        fromString("error"),
      ),
      "properties" → obj(
        "error" → obj(
          "$ref" → fromString("#/definitions/ApiResponse")
        ),
      )
    ),
    "FieldError" → obj(
      "type" → fromString("object"),
      "required" → arr(
        fromString("id"),
        fromString("message"),
        fromString("type"),
      ),
      "properties" → obj(
        "id" → obj(
          "type" → fromString("string"),
        ),
        "message" → obj(
          "type" → fromString("string"),
        ),
        "type" → obj(
          "type" → fromString("string"),
        ),
      )
    ),
    "FieldErrors" → obj(
      "type" → fromString("object"),
      "required" → arr(
        fromString("code"),
        fromString("message"),
        fromString("type"),
        fromString("errors"),
      ),
      "properties" → obj(
        "code" → obj(
          "type" → fromString("string"),
        ),
        "message" → obj(
          "type" → fromString("string"),
        ),
        "type" → obj(
          "type" → fromString("string"),
        ),
        "errors" → obj(
          "type" → fromString("array"),
          "items" → obj("$ref" → fromString("#/definitions/FieldError")),
        ),
      )
    ),
    "ApiResponse" → obj(
      "type" → fromString("object"),
      "required" → arr(
        fromString("code"),
        fromString("message"),
        fromString("type"),
      ),
      "properties" → obj(
        "code" → obj(
          "type" → fromString("string"),
        ),
        "message" → obj(
          "type" → fromString("string"),
        ),
        "type" → obj(
          "type" → fromString("string"),
        ),
      )
    ),
    "Item" → obj(
      "type" → fromString("object"),
      "required" → arr(
        fromString("name"),
        fromString("price"),
        fromString("category"),
        fromString("createdAt"),
        fromString("updatedAt"),
        fromString("id"),
      ),
      "properties" → obj(
        "name" → obj(
          "type" → fromString("string"),
          "minLength" → fromInt(1),
        ),
        "price" → obj(
          "$ref" → fromString("#/definitions/Money"),
        ),
        "category" → obj(
          "type" → fromString("string"),
          "minLength" → fromInt(1),
        ),
        "createdAt" → obj(
          "type" → fromString("string"),
          "format" → fromString("date-time"),
          "readOnly" → fromBoolean(true),
        ),
        "updatedAt" → obj(
          "type" → fromString("string"),
          "format" → fromString("date-time"),
          "readOnly" → fromBoolean(true),
        ),
        "id" → obj(
          "type" → fromString("integer"),
          "format" → fromString("int32"),
          "readOnly" → fromBoolean(true),
        ),
      ),
    ),
    "Op" → obj(
      "type" → fromString("string"),
      "enum" → arr(
        fromString("replace"),
      )
    ),
    "CurrencyCode" → obj(
      "type" → fromString("string"),
      "enum" → arr(
        fromString("EUR"),
        fromString("USD"),
        fromString("MAD"),
        fromString("JYP"),
      )
    ),
    "Money" → obj(
      "type" → fromString("object"),
      "required" → arr(
        fromString("amountInCents"),
        fromString("currency"),
      ),
      "properties" → obj(
        "amountInCents" → obj(
          "type" → fromString("integer"),
          "format" → fromString("int32"),
        ),
        "currency" → obj(
          "$ref" → fromString("#/definitions/CurrencyCode"),
        ),
      )
    ),
    "JsonPatch" → obj(
      "type" → fromString("object"),
      "required" → arr(
        fromString("op"),
        fromString("path"),
        fromString("value"),
      ),
      "properties" → obj(
        "op" → obj(
          "$ref" → fromString("#/definitions/Op"),
        ),
        "path" → obj(
          "type" → fromString("string"),
          "minLength" → fromInt(1),
        ),
        "value" → obj(
          "type" → fromString("string"),
        ),
      )
    ),
  )
}
