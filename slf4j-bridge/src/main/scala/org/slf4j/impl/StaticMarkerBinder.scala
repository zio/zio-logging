/*
 * Copyright 2019-2026 John A. De Goes and the ZIO Contributors
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
package org.slf4j.impl

import org.slf4j.IMarkerFactory
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.spi.MarkerFactoryBinder

/**
 * The marker factory binder that SLF4J 1.7 looks up in a binding; it provides SLF4J's `BasicMarkerFactory`.
 */
class StaticMarkerBinder extends MarkerFactoryBinder {
  private val markerFactory = new BasicMarkerFactory

  override def getMarkerFactory: IMarkerFactory = markerFactory
  override def getMarkerFactoryClassStr: String = classOf[BasicMarkerFactory].getName
}

/**
 * Holds the single `StaticMarkerBinder` instance.
 */
object StaticMarkerBinder {
  private val singleton = new StaticMarkerBinder

  /**
   * Returns the single `StaticMarkerBinder` instance.
   */
  def getSingleton: StaticMarkerBinder = singleton
}
