package org.slf4j.impl

import org.slf4j.IMarkerFactory
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.spi.MarkerFactoryBinder

class StaticMarkerBinder extends MarkerFactoryBinder {
  override def getMarkerFactory: IMarkerFactory = StaticMarkerBinder.singleton
  override def getMarkerFactoryClassStr: String = StaticMarkerBinder.className
}

object StaticMarkerBinder {
  private val singleton = new BasicMarkerFactory
  private val className = classOf[BasicMarkerFactory].getName
}
