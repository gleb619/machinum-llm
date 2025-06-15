export function utilsApp() {
  return {
      debounce(callback, delay = 300) {
          let timeoutId;

          const debounced = function(...args) {
              var context = this;
              clearTimeout(timeoutId);

              timeoutId = setTimeout(() => callback.apply(context, args), delay);
          };

          debounced.cancel = function() {
              clearTimeout(timeoutId);
          };

          return debounced;
      },

      withDebounce(key, fn, delay) {
          if(this[key]) {
              this[key].cancel();
              this[key] = undefined;
          }

          this[key] = debounce(fn, delay);
          this[key]();
      },

      showToast(message, isError = false) {
          Toastify({
              text: message,
              duration: 3000,
              close: true,
              gravity: "top",
              position: "right",
              backgroundColor: isError ? "#e53e3e" : "#10b981",
              stopOnFocus: true
          }).showToast();
      },

      changeState(name) {
        const newValue = !this[name];
        localStorage.setItem(name, newValue);
        this[name] = newValue;
      },

      loadState(name) {
        const currValue = JSON.parse(localStorage.getItem(name));
        this[name] = !!currValue;
      },

      changeValue(name, newValue) {
        const valueToStore = typeof newValue === 'object' ? JSON.stringify(newValue) : newValue;
        localStorage.setItem(name, valueToStore);
        this[name] = newValue;
      },

      loadValue(name, defaultValue) {
        const currValue = localStorage.getItem(name);
        try {
          this[name] = JSON.parse(currValue) || defaultValue;
        } catch (e) {
          this[name] = currValue || defaultValue;
        }
      },

      /**
       * Find and return an item by its ID from a collection
       * @param {Array} collection - Array of objects, each with an 'id' property
       * @param {string|number} id - The ID to search for
       * @returns {object|null} - The found item or null if not found
       */
      getById(collection, id) {
          return this.getByKey(collection, 'id', id);
      },
      
      /**
       * Find and return an item by key from a collection
       * @param {Array} collection - Array of objects, each with an 'id' property
       * @param {string} key - The Key to search for
       * @param {string|number} value - The value to search for
       * @returns {object|null} - The found item or null if not found
       */
      getByKey(collection, key, value) {
          if (!collection || !Array.isArray(collection) || collection.length === 0) {
              return null;
          }
      
          return collection.find(item => item[key] === value) || null;
      },
      
      /**
       * Find and return the next item after the item with the specified ID
       * If the item with the ID is the last in the collection, returns the first item (circular)
       * @param {Array} collection - Array of objects, each with an 'id' property
       * @param {string|number} id - The ID of the reference item
       * @returns {object|null} - The next item or null if collection is empty or no item with ID exists
       */
      getNextById(collection, id) {
          if (!collection || !Array.isArray(collection) || collection.length === 0) {
              return null;
          }
      
          const currentIndex = collection.findIndex(item => item.id === id);
      
          // If item not found, return null
          if (currentIndex === -1) {
              return null;
          }
      
          // If current item is the last item, return the first item (circular)
          if (currentIndex === collection.length - 1) {
              return collection[0];
          }
      
          // Otherwise return the next item
          return collection[currentIndex + 1];
      },
      
      /**
       * Find and return the previous item before the item with the specified ID
       * If the item with the ID is the first in the collection, returns the last item (circular)
       * @param {Array} collection - Array of objects, each with an 'id' property
       * @param {string|number} id - The ID of the reference item
       * @returns {object|null} - The previous item or null if collection is empty or no item with ID exists
       */
      getPrevById(collection, id) {
          if (!collection || !Array.isArray(collection) || collection.length === 0) {
              return null;
          }
      
          const currentIndex = collection.findIndex(item => item.id === id);
      
          // If item not found, return null
          if (currentIndex === -1) {
              return null;
          }
      
          // If current item is the first item, return the last item (circular)
          if (currentIndex === 0) {
              return collection[collection.length - 1];
          }
      
          // Otherwise return the previous item
          return collection[currentIndex - 1];
      },

      removeById(arr, id) {
        const objWithIdIndex = arr.findIndex((obj) => obj.id === id);
        arr.splice(objWithIdIndex, 1);
      },
      
      debounce(callback, delay = 300) {
          let timeoutId;
      
          const debounced = function(...args) {
              var context = this;
              clearTimeout(timeoutId);
      
              timeoutId = setTimeout(() => callback.apply(context, args), delay);
          };
      
          debounced.cancel = function() {
              clearTimeout(timeoutId);
          };
      
          return debounced;
      },
      
      withDebounce(app, key, fn, delay) {
          if(app[key]) {
              app[key].cancel();
              app[key] = undefined;
          }
      
          app[key] = this.debounce(fn, delay);
          app[key]();
      },
      
      readBoolSetting(key) {
          return this.readSetting(key) === "true";
      },
      
      readSetting(key) {
          return localStorage.getItem(key);
      },
      
      writeSetting(key, value) {
          return localStorage.setItem(key, '' + value);
      },

      clickOnElement(target) {
          setTimeout(() => {
            if (document.createEvent) {
                var evt = document.createEvent('MouseEvents');
                evt.initEvent('click', true, false);
                target.dispatchEvent(evt);
            } else if( document.createEventObject ) {
                target.fireEvent('onclick') ;
            } else if (typeof node.onclick == 'function' ) {
                target.onclick();
            }
         }, 10);
      },

      /**
       * Adds a new search parameter to the current page's URL without reloading the page.
       * @param {string} key - The name of the search parameter.
       * @param {string} value - The value of the search parameter.
       */
      addSearchParam(key, value) {
          const url = new URL(window.location.href);
      
          const params = new URLSearchParams(url.search);
          params.set(key, value);
          url.search = params.toString();
      
          window.history.replaceState({}, '', url);
      },
      
      /**
       * Removes a specified search parameter from the current URL without reloading the page.
       *
       * @param {string} param - The name of the search parameter to remove from the URL.
       *
       * This function uses the URL and URLSearchParams interfaces to modify the query string
       * in the current browser location. It then updates the URL using the History API's
       * replaceState method, ensuring no page reload occurs.
       */
      removeSearchParam(param) {
          const url = new URL(window.location.href);
          url.searchParams.delete(param);
          window.history.replaceState({}, '', url);
      },
      
      formatTime(operationsTime) {
          // Calculate hours, minutes, and seconds
          const hours = Math.floor(operationsTime / 3600);
          const minutes = Math.floor((operationsTime % 3600) / 60);
          const seconds = operationsTime % 60;
      
          // Helper function to pad numbers with leading zeros
          const pad = (num) => num.toString().padStart(2, '0');
      
          // Return the formatted string
          return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
      },
      
      formatBigNumberWithSpaces(number) {
          return new Intl.NumberFormat('en-US', {
              style: 'decimal',
              useGrouping: true
          }).format(number).replace(/,/g, ' ');
      },

      fromSearchParams(query) {
        const currentParams = new URLSearchParams(window.location.search);
        const queryObject = Object.fromEntries(currentParams.entries());
        return this.toURLSearchParams(queryObject);
      },

      toURLSearchParams(query) {
          for (let param in query) {
            if (query[param] === undefined
              || query[param] === null
              || query[param] === ""
            ) {
              delete query[param];
            }
          }

          return new URLSearchParams(query);
      },

      //TODO move to another js file
      translateToRussian(text) {
          return fetch('/api/translate/to-russian', {
                      method: 'POST',
                      headers: {
                          'Content-Type': 'application/json'
                      },
                      body: JSON.stringify({ text: text })
                 }).then(response => response.text());
      },

      gracefulStop(varName) {
          setTimeout(() => this[varName] = false, 300);
      },

  }
}
