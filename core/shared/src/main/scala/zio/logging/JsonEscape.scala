package zio.logging

object JsonEscape {
  def jsonEscaped(s: String): String =
    escape(s, new StringBuilder()).toString()

  def jsonEscapedQuoted(s: String): String =
    escape(s, new StringBuilder("\"")).append("\"").toString()

  private def escape(s: String, sb: StringBuilder): StringBuilder = {
    if (s != null)
      for { c <- s } c match {
        case '\\'               => sb.append('\\').append(c)
        case '"'                => sb.append('\\').append(c)
        case '/'                => sb.append('\\').append(c)
        case '\b'               => sb.append("\\b")
        case '\t'               => sb.append("\\t")
        case '\n'               => sb.append("\\n")
        case '\f'               => sb.append("\\f")
        case '\r'               => sb.append("\\r")
        case ctrl if ctrl < ' ' => sb.append("\\u").append("000").append(Integer.toHexString(ctrl))
        case _                  => sb.append(c)
      }
    sb
  }
}
