package zio

package object logging {

  val logAnnotation: FiberRef.Runtime[Map[String, String]] =
    FiberRef.unsafeMake(Map.empty, identity, (old, newV) => old ++ newV)

  /**
   * Add annotations to log context
   *
   * example of usage:
   * {{{
   *  ZIO.log("my message") @@ annotate("requestId" -> UUID.random.toString)
   * }}}
   */
  def annotate(annotations: (String, String)*): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A] =
        logAnnotation.get.flatMap(old => logAnnotation.locally(old ++ annotations.toMap)(zio))
    }

}
