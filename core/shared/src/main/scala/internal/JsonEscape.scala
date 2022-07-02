package zio.logging.internal

import scala.annotation.switch

object JsonEscape {
  def apply(s: CharSequence): CharSequence = {
    val sb  = new java.lang.StringBuilder
    var i   = 0
    val len = s.length
    while (i < len) {
      (s.charAt(i): @switch) match {
        case '"'  => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\b' => sb.append("\\b")
        case '\f' => sb.append("\\f")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case c    =>
          if (c < ' ') sb.append("\\u%04x".format(c.toInt))
          else sb.append(c)
      }
      i += 1
    }
    sb
  }
}
