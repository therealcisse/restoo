package name.amadoucisse.restoo
package domain
package items

import eu.timepit.refined.api.Refined
import eu.timepit.refined.boolean.Or
import eu.timepit.refined.types.numeric.NonNegInt
import eu.timepit.refined.W
import eu.timepit.refined.generic.Equal

final case class Money private (amountInCents: Int, currency: String)

object Money {
  def apply(amount: NonNegInt, currency: Currency): Money = Money(
    amount.value,
    currency.value
  )

  type Currency = String Refined CurrencyCode

  type CurrencyCode =
    Equal[W.`"EUR"`.T] Or
      Equal[W.`"USD"`.T] Or
      Equal[W.`"MAD"`.T] Or
      Equal[W.`"JYP"`.T]

}
