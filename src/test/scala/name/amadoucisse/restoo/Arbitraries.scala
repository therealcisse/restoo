package name.amadoucisse.restoo

import java.time.Instant

import org.scalacheck._
import org.scalacheck.Arbitrary.arbitrary

import domain._
import domain.items._
import domain.entries._

trait Arbitraries {

  implicit val instant: Arbitrary[Instant] = Arbitrary[Instant] {
    for {
      millis <- Gen.posNum[Long]
    } yield Instant.ofEpochMilli(millis)
  }

  implicit val item: Arbitrary[Item] = Arbitrary[Item] {
    for {
      name <- arbitrary[String]
      price <- arbitrary[Double]
      category <- arbitrary[String]
      createdAt <- arbitrary[Instant]
      updatedAt <- arbitrary[Instant]
      id <- Gen.option(Gen.posNum[Int])
    } yield
      Item(
        Name(name),
        Cents(price),
        Category(category),
        OccurredAt(createdAt),
        OccurredAt(updatedAt),
        id.map(ItemId(_))
      )
  }

  implicit val entry: Arbitrary[Entry] = Arbitrary[Entry] {
    for {
      itemId <- Gen.posNum[Int]
      delta <- Gen.choose(-10, 100)
      timestamp <- arbitrary[Instant]
      id <- Gen.option(Gen.posNum[Int])
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