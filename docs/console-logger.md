---
id: console-logger
title: "Console Logger"
---

## Colorful Console Logger With Log Filtering

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
package zio.logging.example

import zio.logging.consoleLogger
import zio.{ Cause, Config, ConfigProvider, ExitCode, LogLevel, Runtime, Scope, URIO, ZIO, ZIOAppDefault, ZLayer }

object ConsoleColoredApp extends ZIOAppDefault {

  val logPattern = "%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %level [%fiberId] %name:%line %message %cause}"

  val configProvider: ConfigProvider = ConfigProvider.fromMap(
    Map(
      "logger/pattern"                                             -> logPattern,
      "logger/filter/rootLevel"                                    -> LogLevel.Info.label,
      "logger/filter/mappings/zio.logging.example.LivePingService" -> LogLevel.Debug.label
    ),
    "/"
  )

  override val bootstrap: ZLayer[Any, Config.Error, Unit] =
    Runtime.removeDefaultLoggers >>> Runtime.setConfigProvider(configProvider) >>> consoleLogger()

  private def ping(address: String): URIO[PingService, Unit] =
    PingService
      .ping(address)
      .foldZIO(
        e => ZIO.logErrorCause(s"ping: $address - error", Cause.fail(e)),
        r => ZIO.logInfo(s"ping: $address - result: $r")
      )

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _ <- ping("127.0.0.1")
      _ <- ping("x8.8.8.8")
    } yield ExitCode.success).provide(LivePingService.layer)

}
```

Expected console output:

```
2023-02-28T23:30:30+0100 DEBUG [zio-fiber-4] zio.logging.example.LivePingService:37 ping: /127.0.0.1 
2023-02-28T23:30:30+0100 INFO [zio-fiber-4] zio.logging.example.ConsoleColoredApp:42 ping: 127.0.0.1 - result: true 
2023-02-28T23:30:30+0100 ERROR [zio-fiber-4] zio.logging.example.LivePingService:36 ping: x8.8.8.8 - invalid address error Exception in thread "zio-fiber-4" java.net.UnknownHostException: x8.8.8.8: nodename nor servname provided, or not known
	at java.base/java.net.Inet6AddressImpl.lookupAllHostAddr(Native Method)
	at java.base/java.net.InetAddress$PlatformNameService.lookupAllHostAddr(InetAddress.java:929)
	at java.base/java.net.InetAddress.getAddressesFromNameService(InetAddress.java:1529)
	at java.base/java.net.InetAddress$NameServiceAddresses.get(InetAddress.java:848)
	at java.base/java.net.InetAddress.getAllByName0(InetAddress.java:1519)
	at java.base/java.net.InetAddress.getAllByName(InetAddress.java:1378)
	at java.base/java.net.InetAddress.getAllByName(InetAddress.java:1306)
	at java.base/java.net.InetAddress.getByName(InetAddress.java:1256)
	at zio.logging.example.LivePingService.$anonfun$ping$2(PingService.scala:35)
	at zio.ZIOCompanionVersionSpecific.$anonfun$attempt$1(ZIOCompanionVersionSpecific.scala:100)
	at java.net.Inet6AddressImpl.lookupAllHostAddr(Native Method)
	at java.net.InetAddress$PlatformNameService.lookupAllHostAddr(InetAddress.java:929)
	at java.net.InetAddress.getAddressesFromNameService(InetAddress.java:1529)
	at java.net.InetAddress$NameServiceAddresses.get(InetAddress.java:848)
	at java.net.InetAddress.getAllByName0(InetAddress.java:1519)
	at java.net.InetAddress.getAllByName(InetAddress.java:1378)
	at java.net.InetAddress.getAllByName(InetAddress.java:1306)
	at java.net.InetAddress.getByName(InetAddress.java:1256)
	at zio.logging.example.LivePingService.ping(PingService.scala:35)
	at zio.logging.example.LivePingService.ping(PingService.scala:36)
	at zio.logging.example.LivePingService.ping(PingService.scala:33)
	at zio.logging.example.ConsoleColoredApp.ping(ConsoleColoredApp.scala:40)
	at zio.logging.example.ConsoleColoredApp.run(ConsoleColoredApp.scala:48)
	at zio.logging.example.ConsoleColoredApp.run(ConsoleColoredApp.scala:49)
2023-02-28T23:30:30+0100 ERROR [zio-fiber-4] zio.logging.example.ConsoleColoredApp:41 ping: x8.8.8.8 - error Exception in thread "zio-fiber-" java.net.UnknownHostException: x8.8.8.8: nodename nor servname provided, or not known
	at java.base/java.net.Inet6AddressImpl.lookupAllHostAddr(Native Method)
	at java.base/java.net.InetAddress$PlatformNameService.lookupAllHostAddr(InetAddress.java:929)
	at java.base/java.net.InetAddress.getAddressesFromNameService(InetAddress.java:1529)
	at java.base/java.net.InetAddress$NameServiceAddresses.get(InetAddress.java:848)
	at java.base/java.net.InetAddress.getAllByName0(InetAddress.java:1519)
	at java.base/java.net.InetAddress.getAllByName(InetAddress.java:1378)
	at java.base/java.net.InetAddress.getAllByName(InetAddress.java:1306)
	at java.base/java.net.InetAddress.getByName(InetAddress.java:1256)
	at zio.logging.example.LivePingService.$anonfun$ping$2(PingService.scala:35)
	at zio.ZIOCompanionVersionSpecific.$anonfun$attempt$1(ZIOCompanionVersionSpecific.scala:100)
```

## JSON Console Logger 

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
package zio.logging.example

import zio.logging.{ LogAnnotation, consoleJsonLogger }
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppDefault, _ }

import java.util.UUID

object ConsoleJsonApp extends ZIOAppDefault {

  final case class User(firstName: String, lastName: String) {
    def toJson: String = s"""{"first_name":"$firstName","last_name":"$lastName"}""".stripMargin
  }

  private val userLogAnnotation = LogAnnotation[User]("user", (_, u) => u, _.toJson)
  private val uuid              = LogAnnotation[UUID]("uuid", (_, i) => i, _.toString)

  val configProvider: ConfigProvider = ConfigProvider.fromMap(
    Map(
      "logger/pattern/timestamp" -> "%timestamp{yyyy-MM-dd'T'HH:mm:ssZ}",
      "logger/pattern/level"     -> "%level",
      "logger/pattern/fiberId"   -> "%fiberId",
      "logger/pattern/kvs"       -> "%kvs",
      "logger/pattern/message"   -> "%message",
      "logger/pattern/cause"     -> "%cause",
      "logger/pattern/name"      -> "%name",
      "logger/filter/rootLevel"  -> LogLevel.Info.label
    ),
    "/"
  )

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> Runtime.setConfigProvider(configProvider) >>> consoleJsonLogger()

  private val uuids = List.fill(2)(UUID.randomUUID())

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      traceId <- ZIO.succeed(UUID.randomUUID())
      _       <- ZIO.foreachPar(uuids) { uId =>
        {
          ZIO.logInfo("Starting operation") *>
            ZIO.sleep(500.millis) *>
            ZIO.logInfo("Stopping operation")
        } @@ userLogAnnotation(User("John", "Doe")) @@ uuid(uId)
      } @@ LogAnnotation.TraceId(traceId)
      _       <- ZIO.logInfo("Done")
    } yield ExitCode.success

}
```

Expected console output:

```
{"name":"zio.logging.example.ConsoleJsonApp","timestamp":"2023-02-28T23:31:11+0100","kvs":{"trace_id":"9e86ce3a-cd1a-4478-805e-1e6c6be8373b","uuid":"7460f96c-69d0-4345-a642-0c527f38c8a4","user":{"first_name":"John","last_name":"Doe"}},"level":"INFO","message":"Starting operation","fiberId":"zio-fiber-6"}
{"name":"zio.logging.example.ConsoleJsonApp","timestamp":"2023-02-28T23:31:11+0100","kvs":{"trace_id":"9e86ce3a-cd1a-4478-805e-1e6c6be8373b","uuid":"ae830e5b-6844-4779-85b6-2da75bc17dc6","user":{"first_name":"John","last_name":"Doe"}},"level":"INFO","message":"Starting operation","fiberId":"zio-fiber-5"}
{"name":"zio.logging.example.ConsoleJsonApp","timestamp":"2023-02-28T23:31:11+0100","kvs":{"trace_id":"9e86ce3a-cd1a-4478-805e-1e6c6be8373b","uuid":"ae830e5b-6844-4779-85b6-2da75bc17dc6","user":{"first_name":"John","last_name":"Doe"}},"level":"INFO","message":"Stopping operation","fiberId":"zio-fiber-5"}
{"name":"zio.logging.example.ConsoleJsonApp","timestamp":"2023-02-28T23:31:11+0100","kvs":{"trace_id":"9e86ce3a-cd1a-4478-805e-1e6c6be8373b","uuid":"7460f96c-69d0-4345-a642-0c527f38c8a4","user":{"first_name":"John","last_name":"Doe"}},"level":"INFO","message":"Stopping operation","fiberId":"zio-fiber-6"}
{"name":"zio.logging.example.ConsoleJsonApp","timestamp":"2023-02-28T23:31:11+0100","kvs":,"level":"INFO","message":"Done","fiberId":"zio-fiber-4"}
```
