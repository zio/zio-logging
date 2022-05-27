package zio.logging.internal

import scala.collection.mutable

object JsonEscape {
  def apply(s: String): String =
    escape(s, new mutable.StringBuilder()).toString()

  private def escape(s: String, sb: mutable.StringBuilder): mutable.StringBuilder = {
    if (s != null || s.isEmpty)
      for { c <- s } c match {
        case '\\'               => sb.append('\\').append(c)
        case '"'                => sb.append('\\').append(c)
        case '/'                => sb.append('\\').append(c)
        case '\b'               => sb.append("\\b")
        case '\t'               => sb.append("\\t")
        case '\n'               => sb.append("\\n")
        case '\f'               => sb.append("\\f")
        case '\r'               => sb.append("\\r")
        case ctrl if ctrl < ' ' => sb.append("\\u").append("000").append(Integer.toHexString(ctrl.toInt))
        case _                  => sb.append(c)
      }
    sb
  }
}
