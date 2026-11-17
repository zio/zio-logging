const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO Logging",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [
        "getting-started",
        {
          type: "category",
          label: "Reference",
          collapsed: false,
          items: [
            {
              type: "category",
              label: "Loggers",
              collapsed: true,
              items: ["console-logger", "file-logger", "reconfigurable-logger", "reference-logger-layers"]
            },
            {
              type: "category",
              label: "Formatting and Context",
              collapsed: true,
              items: [
                "formatting-log-records",
                "reference-log-format",
                "log-filter",
                "reference-log-filter",
                "logger-context-and-annotations",
                "reference-log-annotations"
              ]
            },
            {
              type: "category",
              label: "Operations",
              collapsed: true,
              items: ["metrics", "testing"]
            },
            "reference-configuration"
          ]
        },
        {
          type: "category",
          label: "Integrations",
          collapsed: true,
          items: ["reference-integrations", "slf4j2", "slf4j1", "slf4j2-bridge", "slf4j1-bridge", "jul-bridge", "jpl"]
        },
        {
          type: "category",
          label: "Tutorials",
          collapsed: false,
          items: ["tutorial", "tutorial-service-logging"]
        },
        {
          type: "category",
          label: "How-to Guides",
          collapsed: false,
          items: [
            "how-to-json-logs",
            "how-to-log-levels-from-config",
            "how-to-file-logging-rotation",
            "how-to-bridge-java-logs",
            "how-to-test-log-output",
            "how-to-correlation-id",
            "how-to-logback-log4j2",
            "how-to-change-levels-at-runtime",
            "how-to-custom-log-format"
          ]
        }
      ]
    }
  ]
};

module.exports = sidebars;
