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
      id ← Gen.option(Gen.posNum[Int])
    } yield
      Item(
        Name(name),
        Money(price, Refined.unsafeApply("MAD")),
        Category(category),
        OccurredAt(createdAt),
        OccurredAt(updatedAt),
        id.map(ItemId(_))
      )
  }

  implicit val entry: Arbitrary[Entry] = Arbitrary[Entry] {
    for {
      itemId ← Gen.posNum[Int]
      delta ← Gen.choose(-10, 100)
      timestamp ← arbitrary[Instant]
      id ← Gen.option(Gen.posNum[Int])
    } yield
      Entry(
        ItemId(itemId),
        Delta(delta),
        OccurredAt(timestamp),
        id.map(EntryId(_))
      )
  }
}

object Arbitraries extends Arbitraries
