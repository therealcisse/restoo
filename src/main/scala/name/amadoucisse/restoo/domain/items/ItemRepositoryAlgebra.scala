package name.amadoucisse.restoo
package domain
package items

import http.{ Page, SortBy }

trait ItemRepositoryAlgebra[F[_]] {
  def create(item: Item): F[Item]

  def update(item: Item): F[Item]

  def get(id: ItemId): F[Item]

  def findByName(name: Name): F[Item]

  def delete(itemId: ItemId): F[Unit]

  def list(category: Option[Category], orderBy: Seq[SortBy], limit: Option[Page]): fs2.Stream[F, Item]
}
