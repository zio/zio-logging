package zio.logging.internal

import zio.logging.JsonGenerator
import zio.logging.JsonGenerator.{ ArrayGenerator, ObjectGenerator }
import zio.test.TestAspect._
import zio.test._

import scala.annotation.tailrec

object JsonValidatorSpec extends ZIOSpecDefault {

  private def simpleValueGen() = Gen.oneOf(
    JsonGenerator.validStringGen,
    JsonGenerator.validNumberGen,
    JsonGenerator.validBooleanGen,
    JsonGenerator.validNullGen
  )

  private def simpleValueWithEmptyBlock() = Gen.oneOf(
    simpleValueGen(),
    ObjectGenerator().generate(List.empty),
    ArrayGenerator().generate(List.empty)
  )

  private def nestedJsonGen(
    objectGenerator: ObjectGenerator,
    arrayGenerator: ArrayGenerator,
    valueGen: Gen[Any, String],
    listOf: Gen[Any, String] => Gen[Any, List[String]],
    depth: Int
  ) = {
    @tailrec
    def inner(gen: Gen[Any, String], depth: Int): Gen[Any, String] =
      if (depth <= 0) gen
      else
        inner(
          listOf(gen).flatMap { l =>
            Gen.oneOf(objectGenerator.generate(l), arrayGenerator.generate(l))
          },
          depth - 1
        )

    inner(valueGen, depth)
  }

  val spec: Spec[Environment, Any] = suite("JsonValidator")(
    test("deeply nested json") {
      val keyGenerator = Gen.string(Gen.alphaNumericChar).map("\"" + _ + "\"")
      check(
        nestedJsonGen(
          ObjectGenerator().copy(keyGenerator = keyGenerator),
          ArrayGenerator(),
          Gen.const("{}"),
          Gen.listOfN(1),
          100
        )
      ) { json =>
        assert(JsonValidator.isJson(json))(Assertion.isTrue)
      }
    } @@ samples(2),
    test("Simple valid json objects") {
      val simpleValues = Gen.listOf(simpleValueWithEmptyBlock())

      val simpleJson =
        simpleValues.flatMap(ObjectGenerator().generate)

      check(simpleJson) { json =>
        assert(JsonValidator.isJson(json))(Assertion.isTrue)
      }

    } @@ samples(50),
    test("Valid nested json objects") {
      val maxDepth = 5
      val json     = Gen
        .int(1, maxDepth)
        .flatMap(depth =>
          nestedJsonGen(
            ObjectGenerator(),
            ArrayGenerator(),
            simpleValueWithEmptyBlock(),
            Gen.listOf1,
            depth
          )
        )

      check(json) { json =>
        assert(JsonValidator.isJson(json))(Assertion.isTrue)
      }

    } @@ samples(30),
    test("Invalid json strings") {
      assert(JsonValidator.isJson("This is not JSON."))(Assertion.isFalse)
      assert(JsonValidator.isJson(" "))(Assertion.isFalse)
      assert(JsonValidator.isJson("{]"))(Assertion.isFalse)
      assert(JsonValidator.isJson("{"))(Assertion.isFalse)
      assert(JsonValidator.isJson("}"))(Assertion.isFalse)
      assert(JsonValidator.isJson("]"))(Assertion.isFalse)
      assert(JsonValidator.isJson("{{]}"))(Assertion.isFalse)

      val invalidCommaGen =
        Gen.oneOf(
          Gen.const(",,"),
          Gen.const(", ,"),
          Gen.char.filterNot(_ == ',').map(_.toString)
        )

      val invalidColonGenerator = Gen.oneOf(
        Gen.const("::"),
        Gen.const(": :"),
        Gen.const(" "),
        Gen.alphaNumericChar.map(_.toString)
      )

      val invalidWhiteSpaceGenerator =
        Gen.stringBounded(1, 2)(
          Gen.oneOf(
            Gen.char.filterNot(c =>
              JsonGenerator.validWhitespaceCharacters.contains(c) ||
                c == ','
            )
          )
        )

      val invalidStringGenerator = Gen.oneOf(
        Gen.const("\"\"\""),
        Gen.const("\""),
        Gen.alphaNumericChar.map("'" + _ + "'")
      )

      val invalidObjectKeyGenerator = Gen.oneOf(
        invalidStringGenerator,
        JsonGenerator.validNullGen,
        JsonGenerator.validWhitespaceGen,
        JsonGenerator.validNumberGen
      )

      val invalidBooleanOrNull = Gen.oneOf(
        Gen.const("truee"),
        Gen.const("falsee"),
        Gen.const("tru"),
        Gen.const("fals"),
        Gen.const("t"),
        Gen.const("f"),
        Gen.const("nulll"),
        Gen.const("nul"),
        Gen.const("n")
      )

      val digitsWithoutZero = Gen.string1(Gen.numericChar.filterNot(_ == '0'))

      val invalidNumber = Gen.oneOf(
        digitsWithoutZero.map("+" + _),
        digitsWithoutZero.map("--" + _),
        Gen.string1(Gen.numericChar).map("0" + _),
        digitsWithoutZero.map(_ + "."),
        digitsWithoutZero.map("." + _),
        digitsWithoutZero.map(_ + "e"),
        digitsWithoutZero.map(_ + "E")
      )

      check(simpleValueGen()) { simpleValue =>
        assert(JsonValidator.isJson(simpleValue))(Assertion.isFalse)
      } && check {
        Gen
          .listOfN(2)(simpleValueGen())
          .flatMap { l =>
            Gen.oneOf(
              ObjectGenerator()
                .copy(commaGenerator = invalidCommaGen)
                .generate(l),
              ArrayGenerator()
                .copy(commaGenerator = invalidCommaGen)
                .generate(l)
            )

          }
      } { json =>
        assert(JsonValidator.isJson(json))(Assertion.isFalse)
      } && check {
        Gen
          .listOfN(2)(simpleValueGen())
          .flatMap { l =>
            Gen.oneOf(
              ObjectGenerator()
                .copy(whitespaceGenerator = invalidWhiteSpaceGenerator)
                .generate(l),
              ArrayGenerator()
                .copy(whitespaceGenerator = invalidWhiteSpaceGenerator)
                .generate(l)
            )
          }
      } { json =>
        assert(JsonValidator.isJson(json))(Assertion.isFalse)
      } && check {
        Gen
          .listOfN(2)(simpleValueGen())
          .flatMap { l =>
            ObjectGenerator()
              .copy(colonGenerator = invalidColonGenerator)
              .generate(l)
          }
      } { json =>
        assert(JsonValidator.isJson(json))(Assertion.isFalse)
      } &&
      check {
        simpleValueGen().flatMap { v =>
          Gen.oneOf(
            ObjectGenerator()
              .copy(keyGenerator = invalidObjectKeyGenerator)
              .generate(List(v)),
            invalidStringGenerator.flatMap(invalidString =>
              ArrayGenerator()
                .generate(List(v, invalidString))
            )
          )
        }
      } { json =>
        assert(JsonValidator.isJson(json))(Assertion.isFalse)
      } && check {
        Gen.oneOf(invalidBooleanOrNull, invalidNumber).flatMap { v =>
          Gen.oneOf(
            ObjectGenerator()
              .generate(List(v)),
            ArrayGenerator()
              .generate(List(v))
          )
        }
      } { json =>
        assert(JsonValidator.isJson(json))(Assertion.isFalse)
      }
    } @@ samples(50),
    test("json example") {
      val json =
        """
          |{
          |  "name": "John Doe",
          |  "age": 42,
          |  "weight": 100.5,
          |  "isEmployed": true,
          |  "addresses": [
          |    {
          |      "street": "123 Main St",
          |      "city": "Anytown",
          |      "state": "CA",
          |      "zip": "12345"
          |    },
          |    {
          |      "street": "456 Elm St",
          |      "city": "Othertown",
          |      "state": "NY",
          |      "zip": "67890"
          |    }
          |  ],
          |  "phoneNumbers": [
          |    "+1-555-123-4567",
          |    "+1-555-987-6543"
          |  ],
          |  "notes": null
          |}
          |""".stripMargin

      assert(JsonValidator.isJson(json))(Assertion.isTrue)
    }
  )
}
