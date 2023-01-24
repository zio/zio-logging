module org.slf4j.zio {
    requires org.slf4j;
    provides org.slf4j.spi.SLF4JServiceProvider with zio.logging.slf4j.bridge.ZioSLF4JServiceProvider;
}