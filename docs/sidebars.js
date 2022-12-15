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
        'slf4j-bridge',
        'testing'
      ]
    }
  ]
};

module.exports = sidebars;
