package name.amadoucisse.restoo
package domain
package items

import http.{ Page, SortBy }

trait ItemRepositoryAlgebra[F[_]] {
  def create(item: Item): F[Unit]

  def update(item: Item): F[Unit]

  def get(id: ItemId): F[Item]

  def findByName(name: Name): F[Item]

  def delete(itemId: ItemId): F[Unit]

  def list(category: Option[Category], orderBy: Seq[SortBy], page: Page): fs2.Stream[F, Item]
}
