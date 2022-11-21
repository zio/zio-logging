package org.slf4j.impl

import org.slf4j.IMarkerFactory
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.spi.MarkerFactoryBinder

class StaticMarkerBinder extends MarkerFactoryBinder {
  private val markerFactory = new BasicMarkerFactory

  override def getMarkerFactory: IMarkerFactory = markerFactory
  override def getMarkerFactoryClassStr: String = classOf[BasicMarkerFactory].getName
}

object StaticMarkerBinder {
  private val singleton = new StaticMarkerBinder

  def getSingleton: StaticMarkerBinder = singleton
}
