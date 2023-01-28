const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO Logging",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [
        'formatting-log-records',
        'logger-context-and-annotations',
        'log-filter',
        'console-logger',
        'jpl',
        'slf4j',
        'slf4j1-bridge',
        'slf4j2-bridge',
        'metrics',
        'testing'
      ]
    }
  ]
};

module.exports = sidebars;
