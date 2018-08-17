package name.amadoucisse.restoo
package domain
package items

import http.SortBy

trait ItemRepositoryAlgebra[F[_]] {
  def create(item: Item): F[AppError Either Item]

  def update(item: Item): F[AppError Either Item]

  def get(id: ItemId): F[Option[Item]]

  def findByName(name: Name): F[Option[Item]]

  def delete(itemId: ItemId): F[Unit]

  def list(category: Option[Category], orderBy: Seq[SortBy]): fs2.Stream[F, Item]
}
