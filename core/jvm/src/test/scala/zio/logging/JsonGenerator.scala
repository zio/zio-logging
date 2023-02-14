package zio.logging

import zio.logging.GenExt.GenExtOps
import zio.test.Gen

import scala.annotation.tailrec

private object GenExt {

  implicit class GenExtOps(gen: Gen[Any, String]) {

    def andThen(that: Gen[Any, String]): Gen[Any, String] =
      (gen zipWith that)(_ + _)
  }
}

private final case class BlockGenerator(
  whitespaceGenerator: Gen[Any, String],
  commaGenerator: Gen[Any, String],
  oneItem: String => Gen[Any, String],
  openBracketGenerator: Gen[Any, String] = Gen.const("{"),
  closedBracketGenerator: Gen[Any, String] = Gen.const("}")
) {
  def generate(values: List[String]): Gen[Any, String] =
    contentInsideBlockGen(values).flatMap { insideObject =>
      (openBracketGenerator zipWith closedBracketGenerator)(_ + insideObject + _)
    }

  def contentInsideBlockGen(values: List[String]): Gen[Any, String] = {
    @tailrec
    def inner(values: List[String], gen: Gen[Any, String]): Gen[Any, String] =
      values match {
        case Nil          => whitespaceGenerator
        case head :: Nil  => oneItem(head) andThen gen
        case head :: tail =>
          inner(
            tail,
            for {
              comma <- commaGenerator
              item  <- oneItem(head)
              value <- gen
            } yield comma + item + value
          )
      }

    inner(values, Gen.const(""))
  }
}

object JsonGenerator {
  final case class ObjectGenerator(
    whitespaceGenerator: Gen[Any, String] = JsonGenerator.validWhitespaceGen,
    keyGenerator: Gen[Any, String] = JsonGenerator.validStringGen,
    commaGenerator: Gen[Any, String] = Gen.const(","),
    colonGenerator: Gen[Any, String] = Gen.const(":"),
    openBracketGenerator: Gen[Any, String] = Gen.const("{"),
    closedBracketGenerator: Gen[Any, String] = Gen.const("}")
  ) {

    def generate(values: List[String]): Gen[Any, String] =
      BlockGenerator(
        whitespaceGenerator,
        commaGenerator,
        oneItem,
        openBracketGenerator,
        closedBracketGenerator
      ).generate(values)

    private def oneItem(value: String) =
      for {
        w1 <- whitespaceGenerator
        k  <- keyGenerator
        w2 <- whitespaceGenerator
        c  <- colonGenerator
        w3 <- whitespaceGenerator
        v  <- Gen.const(value)
        w4 <- whitespaceGenerator
      } yield w1 + k + w2 + c + w3 + v + w4
  }

  final case class ArrayGenerator(
    whitespaceGenerator: Gen[Any, String] = JsonGenerator.validWhitespaceGen,
    commaGenerator: Gen[Any, String] = Gen.const(","),
    openBracketGenerator: Gen[Any, String] = Gen.const("["),
    closedBracketGenerator: Gen[Any, String] = Gen.const("]")
  ) {

    def generate(values: List[String]): Gen[Any, String] =
      BlockGenerator(
        whitespaceGenerator,
        commaGenerator,
        oneItem,
        openBracketGenerator,
        closedBracketGenerator
      ).generate(values)

    private def oneItem(value: String) =
      for {
        w1 <- whitespaceGenerator
        v  <- Gen.const(value)
        w2 <- whitespaceGenerator
      } yield w1 + v + w2
  }

  def validWhitespaceCharacters: Set[Char] = Set(' ', '\t', '\r', '\n')

  def validWhitespaceGen: Gen[Any, String] =
    Gen.string(Gen.oneOf(validWhitespaceCharacters.map(Gen.const(_)).toSeq: _*))

  def validNumberGen: Gen[Any, String] = {
    val firstPart = (
      Gen.oneOf(Gen.const("-"), Gen.const("")) andThen
        Gen.oneOf(
          Gen.const("0"),
          ((Gen.stringN(1)(Gen.char('1', '9')) andThen
            Gen.string(Gen.numericChar)))
        )
    )

    val fractionPart = Gen.oneOf(
      Gen.const(""),
      (Gen.const(".") andThen Gen.string1(Gen.numericChar))
    )

    val exponentPart = for {
      e      <- Gen.oneOf(Gen.const("e"), Gen.const("E"))
      sign   <- Gen.oneOf(Gen.const("+"), Gen.const("-"), Gen.const(""))
      digits <- Gen.string1(Gen.numericChar)
    } yield (e + sign + digits)

    firstPart andThen fractionPart andThen exponentPart
  }

  def validStringGen: Gen[Any, String] = {
    val allowedChar = Gen
      .weighted(
        (Gen.printableChar, 0.5),
        (Gen.unicodeChar, 0.2),
        (Gen.char, 0.3)
      )
      .filterNot(c => c == '\\' || c == '"')

    val backSlashed    = List("\\\"", "\\\\", "\\/", "\\b", "\\f", "\\n", "\\r", "\\t")
    val backSlashedGen = (Gen.oneOf(backSlashed.map(Gen.const(_)): _*))

    val validString = (for {
      string          <- Gen.string(allowedChar)
      backSlashedChar <- Gen.weighted((backSlashedGen, 0.1), (Gen.const(""), 0.9))
      newString       <- Gen.int(0, string.length).flatMap { pos =>
                           Gen.const(string.substring(0, pos) + backSlashedChar + string.substring(pos))
                         }
    } yield newString).filterNot(_.endsWith("\""))

    validString.map("\"" + _ + "\"")
  }

  def validBooleanGen: Gen[Any, String] = Gen.oneOf(Gen.const("true"), Gen.const("false"))

  def validNullGen: Gen[Any, String] = Gen.const("null")

}
