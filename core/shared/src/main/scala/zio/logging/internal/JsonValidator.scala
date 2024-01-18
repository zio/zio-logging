/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio.logging.internal

import scala.annotation.tailrec

object JsonValidator {

  def isJson(string: String): Boolean = {
    val startIdx = stringCouldBeJson(string)
    if (startIdx == NOT_A_JSON) false
    else
      checkJson(
        string,
        startIdx,
        STATE_EXPECTING_VALUE,
        if (string(startIdx) == '{') OBJECT
        else ARRAY
      ) == string.length
  }

  private def checkJson(
    string: String,
    startIdx: Int,
    startState: Byte,
    blockType: Boolean
  ): Int = {
    var idx   = startIdx
    var state = startState

    while (state != STATE_BLOCK_END && idx < string.length) {
      val c = string(idx)

      if (isWhitespace(c)) idx += 1
      else if (state == STATE_EXPECTING_VALUE) {

        if (c == '{') idx = checkJson(string, idx + 1, STATE_BLOCK_START, OBJECT)
        else if (c == '[') idx = checkJson(string, idx + 1, STATE_BLOCK_START, ARRAY)
        else idx = skipSimpleValue(string, idx)

        state = STATE_VALUE_PROCESSED

      } else if (state == STATE_VALUE_PROCESSED)
        if (c == ',' && blockType == OBJECT) {
          idx = handleObjectKey(string, skipWhitespaces(string, idx + 1))
          state = STATE_EXPECTING_VALUE
        } else if (c == ',' && blockType == ARRAY) {
          idx += 1
          state = STATE_EXPECTING_VALUE
        } else if ((c == '}' && blockType == OBJECT) || (c == ']' && blockType == ARRAY)) {
          idx += 1
          state = STATE_BLOCK_END
        } else
          idx = NOT_A_JSON
      else if (state == STATE_BLOCK_START)
        if (((c == '}' && blockType == OBJECT) || (c == ']' && blockType == ARRAY))) {
          idx += 1
          state = STATE_BLOCK_END
        } else if (blockType == OBJECT) {
          idx = handleObjectKey(string, skipWhitespaces(string, idx))
          state = STATE_EXPECTING_VALUE
        } else
          state = STATE_EXPECTING_VALUE
      else idx = NOT_A_JSON
    }

    idx
  }

  private def skipSimpleValue(string: String, startIdx: Int) =
    if (startIdx < string.length) {
      val c = string(startIdx)
      if (c == '"')
        skipString(string, startIdx)
      else if (isDigit(c) || c == '-')
        skipNumber(string, startIdx)
      else if (c == 'f' || c == 'n' || c == 't')
        skipBooleanOrNull(string, startIdx)
      else NOT_A_JSON
    } else NOT_A_JSON

  private def handleObjectKey(string: String, startIdx: Int) = {
    val idx = skipWhitespaces(string, skipString(string, startIdx))
    if (idx < string.length && string(idx) == ':') idx + 1
    else NOT_A_JSON
  }

  private def stringCouldBeJson(string: String): Int = {
    val idxStart = skipWhitespaces(string, 0)
    val idxEnd   = skipWhitespacesBackwards(string, string.length - 1)

    if (idxStart >= string.length || idxEnd <= 0) NOT_A_JSON
    else if (string.charAt(idxStart) == '{' & string.charAt(idxEnd) == '}') idxStart
    else if (string.charAt(idxStart) == '[' & string.charAt(idxEnd) == ']') idxStart
    else NOT_A_JSON
  }

  private def skipBooleanOrNull(string: String, idx: Int): Int =
    if (idx + 3 >= string.length) NOT_A_JSON
    else {
      val c1       = string(idx)
      val c2       = string(idx + 1)
      val c3       = string(idx + 2)
      val c4       = string(idx + 3)
      val fifthIsE = (idx + 4 < string.length) && string(idx + 4) == 'e'

      if (c1 == 't' && c2 == 'r' && c3 == 'u' && c4 == 'e')
        idx + 4
      else if (c1 == 'f' && c2 == 'a' && c3 == 'l' && c4 == 's' && fifthIsE)
        idx + 5
      else if (c1 == 'n' && c2 == 'u' && c3 == 'l' && c4 == 'l')
        idx + 4
      else
        NOT_A_JSON
    }

  private def skipString(string: String, startIdx: Int): Int = {
    @tailrec def inner(string: String, idx: Int, isBackslashed: Boolean): Int =
      if (idx >= string.length) NOT_A_JSON
      else if (!isBackslashed && string(idx) == '"') idx + 1
      else inner(string, idx + 1, !isBackslashed && string(idx) == '\\')

    if (startIdx < string.length && string(startIdx) == '"')
      inner(string, startIdx + 1, false)
    else NOT_A_JSON
  }

  @tailrec private def skipWhitespaces(string: String, idx: Int): Int =
    if (idx >= string.length) NOT_A_JSON
    else if (isWhitespace(string.charAt(idx))) skipWhitespaces(string, idx + 1)
    else idx

  @inline private def isWhitespace(c: Char) =
    c <= ' ' && whitespaceLookup(c)

  @inline private def isDigit(c: Char) = c >= '0' && c <= '9'

  @tailrec private def skipWhitespacesBackwards(string: String, idx: Int): Int =
    if (idx >= string.length || idx <= 0) idx
    else if (!isWhitespace(string.charAt(idx))) idx
    else skipWhitespacesBackwards(string, idx - 1)

  @tailrec private def skipDigits(string: String, idx: Int): Int =
    if (idx >= string.length) NOT_A_JSON
    else if (!isDigit(string.charAt(idx))) idx
    else skipDigits(string, idx + 1)

  private def skipExponentPart(string: String, startIdx: Int) =
    if (startIdx < string.length && (string(startIdx) == 'e' || string(startIdx) == 'E')) {
      val idxOfDigitMaybe = if (startIdx + 1 < string.length) {
        val c = string(startIdx + 1)
        if (c == '-' | c == '+') startIdx + 2
        else startIdx + 1
      } else NOT_A_JSON

      if (idxOfDigitMaybe < string.length && isDigit(string(idxOfDigitMaybe))) skipDigits(string, idxOfDigitMaybe + 1)
      else NOT_A_JSON
    } else startIdx

  private def skipFractionPart(string: String, startIdx: Int) =
    if (startIdx < string.length && string(startIdx) == '.') {
      val digitSkippedIdx = skipDigits(string, startIdx + 1)
      if (digitSkippedIdx == startIdx + 1) NOT_A_JSON
      else digitSkippedIdx
    } else startIdx

  private def skipNumber(string: String, startIdx: Int): Int = {
    def inner(string: String, startIdx: Int, minusAllowed: Boolean): Int = {
      val idxFractionPart = if (startIdx < string.length) {
        val c = string(startIdx)

        if (c == '0') startIdx + 1
        else if (isDigit(c)) skipDigits(string, startIdx + 1)
        else if (c == '-' && minusAllowed) inner(string, startIdx + 1, false)
        else NOT_A_JSON
      } else NOT_A_JSON

      skipExponentPart(string, skipFractionPart(string, idxFractionPart))
    }

    inner(string, startIdx, true)

  }

  private val OBJECT = false
  private val ARRAY  = true

  private val NOT_A_JSON = Int.MaxValue

  private val STATE_EXPECTING_VALUE: Byte = 0
  private val STATE_BLOCK_START: Byte     = 1
  private val STATE_VALUE_PROCESSED: Byte = 2
  private val STATE_BLOCK_END: Byte       = 3

  private val whitespaceLookup: Array[Boolean] = {
    val lookup = Array.fill(33)(false)
    lookup(' ') = true
    lookup('\t') = true
    lookup('\n') = true
    lookup('\r') = true
    lookup
  }

}
