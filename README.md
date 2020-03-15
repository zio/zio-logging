# ZIO logging

| CI                                       | Release                                                               |  Issues                                                     | Discord                                   |
|------------------------------------------|-----------------------------------------------------------------------|--------------------------------------------------------------|-------------------------------------------|
| [![CircleCI][badge-circle]][link-circle] | [![Release Artifacts][badge-sonatype-releases]][link-sonatype-releases] | [![Is it maintained?][badge-maintenance]][link-maintenance] | [![Discord][badge-discord]][link-discord] |


An environmental effect for adding logging into any ZIO application.

Key features:
- Type safe, purely-functional
- Context as first-class citizen implemented on top of `FiberRef`
- Composability between `Logger` via `contramap`
- ZIO Console, SLF4j, JS-Console and JS HTTP endpoint backends


To learn more about ZIO Logging, check out the following references:

- [Homepage](https://zio.github.io/zio-logging/)
- [Contributor's guide](./.github/CONTRIBUTING.md)
- [License](LICENSE)
- [Issues](https://github.com/zio/zio-logging/issues)
- [Pull Requests](https://github.com/zio/zio-logging/pulls)

[badge-sonatype-releases]: https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-logging_2.12.svg "Sonatype Releases"
[badge-circle]: https://circleci.com/gh/zio/zio-logging/tree/master.svg?style=svg
[badge-discord]: https://img.shields.io/discord/629491597070827530?logo=discord
[badge-maintenance]: http://isitmaintained.com/badge/resolution/zio/zio-logging.svg
[link-sonatype-releases]: https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-logging_2.12/ "Sonatype Releases"
[link-circle]: https://circleci.com/gh/zio/zio-logging/tree/master
[link-discord]: https://discord.gg/2ccFBr4
[link-maintenance]: http://isitmaintained.com/project/zio/zio-logging
[link-zio]: https://zio.dev

