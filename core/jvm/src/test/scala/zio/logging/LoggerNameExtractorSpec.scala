package zio.logging

import zio.test._
import zio.{ FiberRefs, Trace }

object LoggerNameExtractorSpec extends ZIOSpecDefault {
  val spec: Spec[Environment, Any] = suite("LoggerNameExtractorSpec")(
    test("annotation") {
      val extractor = LoggerNameExtractor.annotation("my_logger_name")
      check(Gen.alphaNumericString) { value =>
        val result = extractor(
          Trace.empty,
          FiberRefs.empty,
          Map("my_logger_name" -> value)
        )
        assertTrue(result == Some(value))
      }
    },
    test("annotationOrTrace") {
      val extractor = LoggerNameExtractor.annotationOrTrace("my_logger_name")
      check(Gen.alphaNumericString, Gen.alphaNumericString, Gen.boolean) { (trace, annotation, hasAnnotation) =>
        val annotations = if (hasAnnotation) Map("my_logger_name" -> annotation) else Map.empty[String, String]
        val value       =
          if (hasAnnotation) annotation
          else {
            val last = trace.lastIndexOf(".")
            if (last > 0) {
              trace.substring(0, last)
            } else trace
          }
        val result      = extractor(
          Trace.apply(trace, "", 0),
          FiberRefs.empty,
          annotations
        )
        assertTrue(result == Some(value))
      }
    },
    test("loggerNameAnnotationOrTrace") {
      val extractor = LoggerNameExtractor.loggerNameAnnotationOrTrace
      check(Gen.alphaNumericString, Gen.alphaNumericString, Gen.boolean) { (trace, annotation, hasAnnotation) =>
        val annotations = if (hasAnnotation) Map(loggerNameAnnotationKey -> annotation) else Map.empty[String, String]
        val value       =
          if (hasAnnotation) annotation
          else {
            val last = trace.lastIndexOf(".")
            if (last > 0) {
              trace.substring(0, last)
            } else trace
          }
        val result      = extractor(
          Trace.apply(trace, "", 0),
          FiberRefs.empty,
          annotations
        )
        assertTrue(result == Some(value))
      }
    },
    test("trace") {
      val extractor = LoggerNameExtractor.trace
      check(Gen.alphaNumericString) { trace =>
        val last   = trace.lastIndexOf(".")
        val value  = if (last > 0) {
          trace.substring(0, last)
        } else trace
        val result = extractor(
          Trace.apply(trace, "", 0),
          FiberRefs.empty,
          Map.empty
        )
        assertTrue(result == Some(value))
      }
    }
  )
}
