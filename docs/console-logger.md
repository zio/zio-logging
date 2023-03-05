---
id: console-logger
title: "Console Logger"
---

## Configuration

ConsoleLoggerConfig

## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples)



### Colorful Console Logger With Log Filtering

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
package zio.logging.example

import zio.logging.consoleLogger
import zio.{ Cause, Config, ConfigProvider, ExitCode, LogLevel, Runtime, Scope, URIO, ZIO, ZIOAppDefault, ZLayer }

object ConsoleColoredApp extends ZIOAppDefault {

  val logPattern = "%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %cause}"

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
2023-03-05T12:24:05+0100 DEBUG   [zio-fiber-4] zio.logging.example.LivePingService:37 ping: /127.0.0.1 
2023-03-05T12:24:05+0100 INFO    [zio-fiber-4] zio.logging.example.ConsoleColoredApp:43 ping: 127.0.0.1 - result: true 
2023-03-05T12:24:05+0100 ERROR   [zio-fiber-4] zio.logging.example.LivePingService:36 ping: x8.8.8.8 - invalid address error Exception in thread "zio-fiber-4" java.net.UnknownHostException: x8.8.8.8: nodename nor servname provided, or not known
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
	at zio.logging.example.ConsoleColoredApp.ping(ConsoleColoredApp.scala:41)
	at zio.logging.example.ConsoleColoredApp.run(ConsoleColoredApp.scala:49)
	at zio.logging.example.ConsoleColoredApp.run(ConsoleColoredApp.scala:50)
2023-03-05T12:24:05+0100 ERROR   [zio-fiber-4] zio.logging.example.ConsoleColoredApp:42 ping: x8.8.8.8 - error Exception in thread "zio-fiber-" java.net.UnknownHostException: x8.8.8.8: nodename nor servname provided, or not known
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

### JSON Console Logger 

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

  val logPattern =
    "%label{timestamp}{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ}} %label{level}{%level} %label{fiberId}{%fiberId} %label{message}{%message} %label{cause}{%cause} %label{name}{%name} %kvs"

  val configProvider: ConfigProvider = ConfigProvider.fromMap(
    Map(
      "logger/pattern"          -> logPattern,
      "logger/filter/rootLevel" -> LogLevel.Info.label
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
{"timestamp":"2023-03-05T12:30:22+0100","level":"INFO","fiberId":"zio-fiber-6","message":"Starting operation","cause":,"name":"zio.logging.example.ConsoleJsonApp","trace_id":"48b6d93c-a129-45cc-a9af-8190dde406f3","uuid":"2519aff9-b433-474c-acca-fdccb6fb0f14","user":{"first_name":"John","last_name":"Doe"}}
{"timestamp":"2023-03-05T12:30:22+0100","level":"INFO","fiberId":"zio-fiber-5","message":"Starting operation","cause":,"name":"zio.logging.example.ConsoleJsonApp","trace_id":"48b6d93c-a129-45cc-a9af-8190dde406f3","uuid":"b6663fb1-65cb-449a-8709-994700eca856","user":{"first_name":"John","last_name":"Doe"}}
{"timestamp":"2023-03-05T12:30:23+0100","level":"INFO","fiberId":"zio-fiber-5","message":"Stopping operation","cause":,"name":"zio.logging.example.ConsoleJsonApp","trace_id":"48b6d93c-a129-45cc-a9af-8190dde406f3","uuid":"b6663fb1-65cb-449a-8709-994700eca856","user":{"first_name":"John","last_name":"Doe"}}
{"timestamp":"2023-03-05T12:30:23+0100","level":"INFO","fiberId":"zio-fiber-6","message":"Stopping operation","cause":,"name":"zio.logging.example.ConsoleJsonApp","trace_id":"48b6d93c-a129-45cc-a9af-8190dde406f3","uuid":"2519aff9-b433-474c-acca-fdccb6fb0f14","user":{"first_name":"John","last_name":"Doe"}}
{"timestamp":"2023-03-05T12:30:23+0100","level":"INFO","fiberId":"zio-fiber-4","message":"Done","cause":,"name":"zio.logging.example.ConsoleJsonApp"}
```
