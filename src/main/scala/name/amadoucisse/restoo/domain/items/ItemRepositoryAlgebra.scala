package name.amadoucisse.restoo
package domain
package items

trait ItemRepositoryAlgebra[F[_]] {
  def create(item: Item): F[Item]

  def update(item: Item): F[ItemNotFoundError.type Either Item]

  def get(id: ItemId): F[ItemNotFoundError.type Either Item]

  def findByName(name: Name): F[ItemNotFoundError.type Either Item]

  def delete(itemId: ItemId): F[ItemNotFoundError.type Either Unit]

  def list(): fs2.Stream[F, Item]
}
