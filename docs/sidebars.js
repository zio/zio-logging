const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO Logging",
      link: { type: "doc", id: "index" },
      items: [
        'formatting-log-records',
        'logger-context-and-annotations',
        'log-filter',
        'console-logger',
        'file-logger',
        'jpl',
        'slf4j2',
        'slf4j1',
        'slf4j2-bridge',
        'slf4j1-bridge',
        'metrics',
        'testing'
      ]
    }
  ]
};

module.exports = sidebars;
