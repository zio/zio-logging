package zio.logging.slf4j

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

class TestAppender extends AppenderBase[ILoggingEvent] {
  override def append(event: ILoggingEvent): Unit =
    TestAppender.appendEvent(event)
}

object TestAppender {

  private val logEventsRef: AtomicReference[List[ILoggingEvent]] = new AtomicReference[List[ILoggingEvent]](Nil)

  def reset(): Unit = logEventsRef.set(Nil)

  def events: List[ILoggingEvent] = logEventsRef.get().reverse

  @tailrec
  def appendEvent(event: ILoggingEvent): Unit = {
    val old = logEventsRef.get()
    if (logEventsRef.compareAndSet(old, event :: old)) ()
    else appendEvent(event)
  }
}
