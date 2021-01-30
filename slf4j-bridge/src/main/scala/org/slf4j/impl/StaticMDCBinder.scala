package org.slf4j.impl

import org.slf4j.helpers.BasicMDCAdapter
import org.slf4j.spi.MDCAdapter

class StaticMDCBinder {
  def getMDCA(): MDCAdapter = StaticMDCBinder.singleton

  def getMDCAdapterClassStr(): String = StaticMDCBinder.className
}

object StaticMDCBinder {
  private val singleton: MDCAdapter = new BasicMDCAdapter
  private val className: String     = classOf[BasicMDCAdapter].getName
}
