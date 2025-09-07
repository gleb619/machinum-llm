/**
 * Find and return an item by its ID from a collection
 * @param {Array} collection - Array of objects, each with an 'id' property
 * @param {string|number} id - The ID to search for
 * @returns {object|null} - The found item or null if not found
 */
export function getById(collection, id) {
    return getByKey(collection, 'id', id);
}

/**
 * Find and return an item by key from a collection
 * @param {Array} collection - Array of objects, each with an 'id' property
 * @param {string} key - The Key to search for
 * @param {string|number} value - The value to search for
 * @returns {object|null} - The found item or null if not found
 */
export function getByKey(collection, key, value) {
    if (!collection || !Array.isArray(collection) || collection.length === 0) {
        return null;
    }

    return collection.find(item => item[key] === value) || null;
}

/**
 * Find and return the next item after the item with the specified ID
 * If the item with the ID is the last in the collection, returns the first item (circular)
 * @param {Array} collection - Array of objects, each with an 'id' property
 * @param {string|number} id - The ID of the reference item
 * @returns {object|null} - The next item or null if collection is empty or no item with ID exists
 */
export function getNextById(collection, id) {
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
}

/**
 * Find and return the previous item before the item with the specified ID
 * If the item with the ID is the first in the collection, returns the last item (circular)
 * @param {Array} collection - Array of objects, each with an 'id' property
 * @param {string|number} id - The ID of the reference item
 * @returns {object|null} - The previous item or null if collection is empty or no item with ID exists
 */
export function getPrevById(collection, id) {
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
}

export function debounce(callback, delay = 300) {
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
}

export function withDebounce(app, key, fn, delay) {
    if(app[key]) {
        app[key].cancel();
        app[key] = undefined;
    }

    app[key] = debounce(fn, delay);
    app[key]();
}

export function readBoolSetting(key) {
    return readSetting(key) === "true";
}

export function readSetting(key) {
    return localStorage.getItem(key);
}

export function writeSetting(key, value) {
    return localStorage.setItem(key, '' + value);
}

/**
 * Adds a new search parameter to the current page's URL without reloading the page.
 * @param {string} key - The name of the search parameter.
 * @param {string} value - The value of the search parameter.
 */
export function addSearchParam(key, value) {
    const url = new URL(window.location.href);

    const params = new URLSearchParams(url.search);
    params.set(key, value);
    url.search = params.toString();

    window.history.replaceState({}, '', url);
}

/**
 * Removes a specified search parameter from the current URL without reloading the page.
 *
 * @param {string} param - The name of the search parameter to remove from the URL.
 *
 * This function uses the URL and URLSearchParams interfaces to modify the query string
 * in the current browser location. It then updates the URL using the History API's
 * replaceState method, ensuring no page reload occurs.
 */
export function removeSearchParam(param) {
    const url = new URL(window.location.href);
    url.searchParams.delete(param);
    window.history.replaceState({}, '', url);
}

export function formatTime(operationsTime) {
    // Calculate hours, minutes, and seconds
    const hours = Math.floor(operationsTime / 3600);
    const minutes = Math.floor((operationsTime % 3600) / 60);
    const seconds = operationsTime % 60;

    // Helper function to pad numbers with leading zeros
    const pad = (num) => num.toString().padStart(2, '0');

    // Return the formatted string
    return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
}

export function formatBigNumberWithSpaces(number) {
    return new Intl.NumberFormat('en-US', {
        style: 'decimal',
        useGrouping: true
    }).format(number).replace(/,/g, ' ');
}