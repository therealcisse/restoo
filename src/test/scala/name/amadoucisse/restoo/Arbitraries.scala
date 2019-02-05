package name.amadoucisse.restoo

import java.time.Instant

import eu.timepit.refined.scalacheck.numeric._
import eu.timepit.refined.types.numeric.NonNegInt
import domain._
import domain.entries._
import domain.items._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import org.scalacheck._
import org.scalacheck.Arbitrary.arbitrary

trait Arbitraries {

  implicit val instant: Arbitrary[Instant] = Arbitrary[Instant] {
    for {
      millis ← Gen.posNum[Long]
    } yield Instant.ofEpochMilli(millis)
  }

  implicit val item: Arbitrary[Item] = Arbitrary[Item] {
    for {
      name ← arbitrary[String]
      price ← arbitrary[NonNegInt]
      category ← arbitrary[String]
      createdAt ← arbitrary[Instant]
      updatedAt ← arbitrary[Instant]
      id ← Gen.posNum[Long]
    } yield
      Item(
        Name(name),
        Money(price, Refined.unsafeApply("MAD")),
        Category(category),
        DateTime(createdAt),
        DateTime(updatedAt),
        id = ItemId(id)
      )
  }

  implicit val entry: Arbitrary[Entry] = Arbitrary[Entry] {
    for {
      itemId ← Gen.posNum[Long]
      delta ← Gen.choose(-10, 100)
      timestamp ← arbitrary[Instant]
      id ← Gen.posNum[Long]
    } yield
      Entry(
        ItemId(itemId),
        Delta(delta),
        DateTime(timestamp),
        id = EntryId(id)
      )
  }
}

object Arbitraries extends Arbitraries
