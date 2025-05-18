// app.js
import { initApp } from './init.js';
import { utilsApp } from './utils.js';
import { listApp } from './chapter-list.js';
import { chapterDrawerApp } from './components/chapter-drawer.js';
import { editApp } from './chapter-edit.js';
import { initEditorDirective } from './directive/editor-directive.js';
import { lineListApp } from './components/line-list.js';
import { textEditorApp } from './components/text-editor.js';

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
            ...Object.getOwnPropertyDescriptors(chapterDrawerApp()),
            ...Object.getOwnPropertyDescriptors(editApp()),
            ...Object.getOwnPropertyDescriptors(lineListApp()),
            ...Object.getOwnPropertyDescriptors(textEditorApp()),
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
    initEditorDirective();

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
