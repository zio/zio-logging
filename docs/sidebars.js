const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "Getting Started",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: []
    },
    {
      type: "category",
      label: "Loggers",
      collapsed: false,
      items: ["console-logger", "file-logger", "reconfigurable-logger"]
    },
    {
      type: "category",
      label: "Formatting and Context",
      collapsed: false,
      items: ["formatting-log-records", "logger-context-and-annotations", "log-filter"]
    },
    {
      type: "category",
      label: "Integrations",
      collapsed: false,
      items: ["slf4j2", "slf4j1", "slf4j2-bridge", "slf4j1-bridge", "jul-bridge", "jpl"]
    },
    {
      type: "category",
      label: "Operations",
      collapsed: false,
      items: ["metrics", "testing"]
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
    },
    {
      type: "category",
      label: "Reference",
      collapsed: false,
      items: [
        "reference-logger-layers",
        "reference-log-format",
        "reference-log-filter",
        "reference-log-annotations",
        "reference-configuration",
        "reference-integrations"
      ]
    }
  ]
};

module.exports = sidebars;
