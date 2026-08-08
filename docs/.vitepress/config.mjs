import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'SmartCare Capture',
  description: 'Public documentation (English)',

  // Markdown lives in docs/en-public/, not docs/ root
  srcDir: 'en-public',

  base: process.env.READTHEDOCS_CANONICAL_URL
    ? new URL(process.env.READTHEDOCS_CANONICAL_URL).pathname.replace(/\/$/, '')
    : '/',

  themeConfig: {
    nav: [
      { text: 'Home', link: '/' },
      { text: 'Overview', link: '/1-overview/PROJECT-OVERVIEW' },
      { text: 'Architecture', link: '/2-architecture/ARCHITECTURE-OVERVIEW' },
      { text: 'Modules', link: '/3-modules/MODULES-INDEX' },
    ],

    sidebar: [
      {
        text: 'Introduction',
        items: [{ text: 'Documentation home', link: '/' }],
      },
      {
        text: 'Overview',
        items: [
          {
            text: 'Project overview',
            link: '/1-overview/PROJECT-OVERVIEW',
            items: [
              {
                text: 'What is this app?',
                link: '/1-overview/PROJECT-OVERVIEW#what-is-this-app',
              },
              {
                text: 'The problem it solves',
                link: '/1-overview/PROJECT-OVERVIEW#the-problem-it-solves',
              },
              {
                text: 'Who uses the app?',
                link: '/1-overview/PROJECT-OVERVIEW#who-uses-the-app',
              },
            ],
          },
        ],
      },
      {
        text: 'Architecture',
        items: [
          { text: 'Architecture overview', link: '/2-architecture/ARCHITECTURE-OVERVIEW' },
          { text: '1 — App startup', link: '/2-architecture/flow-01-app-startup' },
          { text: '2 — Bluetooth', link: '/2-architecture/flow-02-bluetooth-connection' },
          { text: '3 — Data recording', link: '/2-architecture/flow-03-data-recording' },
          { text: '4 — Annotation', link: '/2-architecture/flow-04-annotation' },
          { text: '5 — File upload', link: '/2-architecture/flow-05-file-upload' },
          { text: '6 — File management', link: '/2-architecture/flow-06-file-management' },
        ],
      },
      {
        text: 'Modules',
        items: [
          { text: 'Modules index', link: '/3-modules/MODULES-INDEX' },
          { text: 'UI / Recording', link: '/3-modules/module-ui-recording' },
          { text: 'Device / Bluetooth', link: '/3-modules/module-device-bluetooth' },
          { text: 'Data storage', link: '/3-modules/module-data-storage' },
          { text: 'Network / Upload', link: '/3-modules/module-network-upload' },
        ],
      },
      {
        text: 'Functions',
        items: [
          { text: 'Functions index', link: '/4-functions/' },
          { text: 'UI / Recording', link: '/4-functions/functions-ui-recording' },
          { text: 'Device / Bluetooth', link: '/4-functions/functions-device-bluetooth' },
          { text: 'Data storage', link: '/4-functions/functions-data-storage' },
        ],
      },
    ],

    socialLinks: [
      {
        icon: 'github',
        link: 'https://github.com/Oucru-Innovations/SmartCare-Capture',
      },
    ],
  },
})
