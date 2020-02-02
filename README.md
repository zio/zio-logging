# ZIO logging

| CI                                       | Issues                                                      | Discord                                   |
|------------------------------------------|-------------------------------------------------------------|-------------------------------------------|
| [![CircleCI][badge-circle]][link-circle] | [![Is it maintained?][badge-maintenance]][link-maintenance] | [![Discord][badge-discord]][link-discord] |


An environmental effect for adding logging into any ZIO application.

Key features:
- Type safe, purely-functional
- Context as first-class citizen implemented on top of `FiberRef`
- Composability between `Logger` via `contramap`
- ZIO Console and SLF4j backends


To learn more about ZIO Logging, check out the following references:

- [Homepage](https://zio.github.io/zio-logging/)
- [Contributor's guide](./.github/CONTRIBUTING.md)
- [License](LICENSE)
- [Issues](https://github.com/zio/zio-logging/issues)
- [Pull Requests](https://github.com/zio/zio-logging/pulls)

[badge-circle]: https://circleci.com/gh/zio/zio-logging/tree/master.svg?style=svg
[badge-discord]: https://img.shields.io/discord/629491597070827530?logo=discord
[badge-maintenance]: http://isitmaintained.com/badge/resolution/zio/zio-logging.svg
[link-circle]: https://circleci.com/gh/zio/zio-logging/tree/master
[link-discord]: https://discord.gg/2ccFBr4
[link-maintenance]: http://isitmaintained.com/project/zio/zio-logging
[link-zio]: https://zio.dev

