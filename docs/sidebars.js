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
      label: "Tutorial",
      collapsed: false,
      items: ["tutorial"]
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
      label: "How-to Guides",
      collapsed: false,
      items: [
        "how-to-json-logs",
        "how-to-log-levels-from-config",
        "how-to-file-logging-rotation",
        "how-to-bridge-java-logs"
      ]
    },
    {
      type: "category",
      label: "Reference",
      collapsed: false,
      items: ["reference-log-format", "reference-log-filter", "reference-configuration"]
    }
  ]
};

module.exports = sidebars;
