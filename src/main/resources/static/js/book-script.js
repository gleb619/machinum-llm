// app.js
import { initApp } from './init.js';
import { utilsApp } from './utils.js';
import { listApp } from './book-list.js';
import { bookChartApp } from './components/book-chart.js';
import { processorSettingsApp } from './components/book-editor-sidebar.js';
import { bookStateApp } from './components/book-state.js';
import { bookReportApp } from './components/book-report.js';
import { titleListApp } from './components/book-title-list.js';
import { glossaryListApp } from './components/book-glossary-list.js';
import { sidebarChapterSelectorApp } from './components/sidebar-chap-selector.js';

const startTime = new Date().getTime();

/**
 * Creates the main application with combined functionality
 * from list and edit modules
 */
export function app() {
    let combinedApp = {};

    // Combine the list and edit functions using Object.assign
    try {
        // Add computed properties
        // In manually initialized Alpine.js components, we need to define getters explicitly
        Object.defineProperties(combinedApp, {
            ...Object.getOwnPropertyDescriptors(initApp()),
            ...Object.getOwnPropertyDescriptors(utilsApp()),
            ...Object.getOwnPropertyDescriptors(listApp()),
            ...Object.getOwnPropertyDescriptors(bookChartApp()),
            ...Object.getOwnPropertyDescriptors(processorSettingsApp()),
            ...Object.getOwnPropertyDescriptors(bookStateApp()),
            ...Object.getOwnPropertyDescriptors(bookReportApp()),
            ...Object.getOwnPropertyDescriptors(titleListApp()),
            ...Object.getOwnPropertyDescriptors(glossaryListApp()),
            ...Object.getOwnPropertyDescriptors(sidebarChapterSelectorApp()),
        });
    } catch(e) {
        debugger;
        console.error("error: ", e);
    }

    return combinedApp;
}

document.addEventListener("DOMContentLoaded", function() {
  const currentTime = new Date().getTime();
  const elapsedTime = currentTime - startTime;
  const remainingTime = Math.max(10, 500 - elapsedTime);

  showLoader(remainingTime);
});

document.addEventListener("DOMContentLoaded", function(event) {
    Alpine.data('app', app);
    Alpine.start();
});

//TODO move fn to init.js file
window.showLoader = function(remainingTime) {
  const splashScreen = document.getElementById('splashScreen');
  const mainContent = document.getElementById('mainContent');
  splashScreen.style.display = 'flex';
  splashScreen.style.opacity = '1';
  mainContent.style.opacity = '0';

  setTimeout(() => {
      splashScreen.style.opacity = '0';
      mainContent.style.opacity = '1';

      setTimeout(() => {
          splashScreen.style.display = 'none';
      }, 250);
  }, remainingTime);
}