export function initEditorDirective() {
    Alpine.directive('editor', (el, { expression, modifiers }, { evaluate, evaluateLater, effect, cleanup }) => {
        const app = {
            activeLine: null,
            dropdown: null,
            dropdownMode: false,
            settings: evaluate(el.getAttribute('x-settings')) || {},
            customSettings: {},
            isUpdatingFromEditor: false,
            isUpdatingFromAlpine: false,
            previousSettingsHash: '',
        };

        const getVarValue = evaluateLater(expression);
        const setValue = (value) => Alpine.$data(el)[expression] = value;

        const editor = CodeMirror(el, app.settings);

        if (el.hasAttribute('x-settings')) {
            handleDynamicSettings(editor, el, app, effect, evaluateLater);
        }

        const parent = editor.getWrapperElement().parentElement;

//        const createDropdown = () => createDropdownMenu(parent, handleLineAction, closeDropdown);
//        const handleLineAction = (action) => handleLineActions(editor, app.activeLine, el, action);
//        const dispatchEvent = (code, lineContent) => dispatchCustomEvent(el, code, app.activeLine, lineContent);
//        const hideDropdown = (e) => hideDropdownMenu(app.dropdown, e, closeDropdown);
//        const closeDropdown = () => closeDropdownMenu(app.dropdown, app.dropdownMode);
//        const showDropdown = (lineNumber, pos) => showDropdownMenu(app, lineNumber, pos, app.dropdown, createDropdown, parent, closeDropdown);
//        const destroyEditor = () => destroyEditorInstance(app.dropdown);

//        editor.on('cursorActivity', (cm) => handleCursorActivity(app, cm));
        editor.on('change', (instance) => handleChange(instance, setValue, app));
        const highlightEnglishText = () => highlightEnglishLines(editor);
        effect(() => {
            syncAlpineData(getVarValue, editor, app);
            if (app.settings.highlightEnglish) {
                highlightEnglishText();
            }
        });
//        editor.on('mousedown', (cm, e) => handleMouseDown(cm, e, parent, app.dropdown, hideDropdown, showDropdown));


//        const clearHighlights = () => clearHighlightedLines(editor);
        const configureContextMenu = () => createContextMenu(app, el, editor);

        cleanup(() => {
//            destroyEditor();
            editor.getWrapperElement().remove();
        });

        applyInitialStyling(editor, app.settings, highlightEnglishText);
        configureContextMenu();
    });

}

function getRelativeCoordinates(event, referenceElement) {
    const position = { x: event.pageX, y: event.pageY };
    const offset = { left: referenceElement.offsetLeft, top: referenceElement.offsetTop };

    let reference = referenceElement.offsetParent;
    while (reference) {
        offset.left += reference.offsetLeft;
        offset.top += reference.offsetTop;
        reference = reference.offsetParent;
    }

    return { x: position.x - offset.left, y: position.y - offset.top };
}

function handleDynamicSettings(editor, el, app, effect, evaluateLater) {
    const getSettings = evaluateLater(el.getAttribute('x-settings'));
    effect(() => {
        getSettings(value => {
            const { fontSize, lineHeight, fontFamily, ...codeMirrorSettings } = value || {};
            app.customSettings = { fontSize, lineHeight, fontFamily };
            app.settings = codeMirrorSettings;

            const settingsHash = hashObject(codeMirrorSettings);
            const customSettingsHash = hashObject(app.customSettings);
            const combinedHash = settingsHash + customSettingsHash;

            if (editor && combinedHash !== app.previousHash) {
                app.previousHash = combinedHash;

                Object.keys(app.settings).forEach(key => editor.setOption(key, app.settings[key]));
                setTimeout(() => {
                    applyCustomStyling(editor.getWrapperElement(), app.customSettings);
                    if (app.settings.highlightEnglish) {
                        highlightEnglishLines(editor);
                    } else {
                        clearHighlightedLines(editor);
                    }
                }, 10);
            }
        });
    });
}

function applyCustomStyling(wrapperEl, styles) {
    if (styles.fontSize) wrapperEl.style.fontSize = `${styles.fontSize}px`;
    if (styles.lineHeight) wrapperEl.style.lineHeight = styles.lineHeight;
    if (styles.fontFamily) wrapperEl.style.fontFamily = styles.fontFamily;
}

/*
function createDropdownMenu(parent, handleLineAction, closeDropdown) {
    const dropdown = document.createElement('div');
    dropdown.className = 'absolute z-20 bg-white shadow-lg rounded border-2 border-solid border-blue-500 p-2 hidden';
    dropdown.innerHTML = `
        <div class="flex">
            <button type="button" class="absolute top-0 right-0 w-4 h-4 text-red-500 hover:text-red-700">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                    <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
                </svg>
            </button>
            <ul>
                <li data-action="findSame" class="hover:bg-gray-100 border-b-2 border-indigo-100 px-2 py-1 cursor-pointer">Find the same line in a book</li>
                <li data-action="delete" class="hover:bg-gray-100 border-b-2 border-indigo-100 px-2 py-1 cursor-pointer">Delete line</li>
                <li data-action="analyze" class="hover:bg-gray-100 border-b-2 border-indigo-100 px-2 py-1 cursor-pointer">Analyze</li>
                <li data-action="copy" class="hover:bg-gray-100 px-2 py-1 cursor-pointer">Copy</li>
            </ul>
        </div>
    `;

    parent.appendChild(dropdown);

    dropdown.querySelector('button:not([data-action])').addEventListener('click', closeDropdown);

    dropdown.querySelectorAll('li[data-action]').forEach(btn => {
        btn.addEventListener('click', (e) => {
            const action = e.target.getAttribute('data-action');
            handleLineAction(action);
            closeDropdown();
        });
    });

    return dropdown;
}

function handleLineActions(editor, activeLine, el, action) {
    if (!editor || activeLine === null) return;

    const lineContent = editor.getLine(activeLine);

    switch (action) {
        case 'delete':
            editor.replaceRange('', { line: activeLine, ch: 0 }, { line: activeLine, ch: lineContent.length });
            break;
        case 'findSame':
            dispatchCustomEvent(el, 'findSame', activeLine, lineContent);
            break;
        case 'analyze':
            dispatchCustomEvent(el, 'analyze', activeLine, lineContent);
            break;
        case 'copy':
            navigator.clipboard.writeText(lineContent);
            break;
    }
}

function dispatchCustomEvent(el, code, lineNumber, lineContent) {
    el.dispatchEvent(new CustomEvent('editor-event', {
        detail: { code, lineNumber: lineNumber + 1, content: lineContent }
    }));
}

function hideDropdownMenu(dropdown, e, closeDropdown) {
    if (dropdown && !dropdown.contains(e.target)) {
        closeDropdown();
    }
}

function closeDropdownMenu(dropdown, dropdownMode) {
    if (dropdown) {
        dropdown.classList.add('hidden');
        document.removeEventListener('click', hideDropdownMenu);
        dropdownMode = false;
    }
}

function showDropdownMenu(app, lineNumber, pos, dropdown, createDropdown, parent, closeDropdown) {
    if (!dropdown) {
        app.dropdown = createDropdown();
    }

    app.activeLine = lineNumber;

    app.dropdown.style.top = `${pos.bottom}px`;
    app.dropdown.style.left = `${pos.left}px`;
    app.dropdown.classList.remove('hidden');

    Alpine.nextTick(() => {
        setTimeout(() => {
            document.addEventListener('click', (e) => hideDropdownMenu(app.dropdown, e, closeDropdown));
        }, 100);
    });
}

function destroyEditorInstance(dropdown) {
    if (dropdown) {
        dropdown.remove();
        dropdown = null;
    }
}

function handleCursorActivity(app, cm) {
    const cursor = cm.getCursor();
    app.activeLine = cursor.line;
}
*/

function handleChange(instance, setValue, app) {
    if (app.isUpdatingFromAlpine) return;
    app.isUpdatingFromEditor = true;

    const value = instance.getValue();
    setValue(value);
    setTimeout(() => {
        app.isUpdatingFromEditor = false;
    }, 10);
}

function syncAlpineData(getVarValue, editor, app) {
    getVarValue(value => {
        if (app.isUpdatingFromEditor) return;

        if (value !== editor.getValue()) {
            app.isUpdatingFromAlpine = true;

            // Save cursor position
            const cursor = editor.getCursor();
            const scrollInfo = editor.getScrollInfo();

            editor.setValue(value || '');

            // Restore cursor position
            editor.setCursor(cursor);
            editor.scrollTo(scrollInfo.left, scrollInfo.top);

            setTimeout(() => {
                app.isUpdatingFromAlpine = false;
            }, 10);
        }
    });
}

/*
function handleMouseDown(cm, e, parent, dropdown, hideDropdown, showDropdown) {
    const pos = cm.coordsChar({ top: e.clientY, left: e.clientX }, 'page');
    const lineNumber = pos.line;
    const rect = e.target.getBoundingClientRect();
    const coords = getRelativeCoordinates(e, parent);

    if (dropdown) {
        hideDropdown(e);
    }

    showDropdown(lineNumber, { bottom: coords.y + rect.height, left: e.clientX - rect.left });
}
*/

function highlightEnglishLines(editor) {
    const doc = editor.getDoc();
    const lines = doc.lineCount();

    for (let i = 0; i < lines; i++) {
        const lineContent = doc.getLine(i);
        const isEnglish = checkLineIsEnglish(lineContent);
        const isOriginal = checkOriginalLine(lineContent);
        const isTranslated = checkTranslatedLine(lineContent);

        if (isEnglish || isOriginal || isTranslated) {
            editor.addLineClass(i, "text", "suspicious-line transition-colors duration-200 hover:bg-yellow-300")

            if(isEnglish) {
                editor.addLineClass(i, "text", "sl-english");
            }

            if(isOriginal) {
                editor.addLineClass(i, "text", "sl-original");
            }

            if(isTranslated) {
                editor.addLineClass(i, "text", "sl-translated");
            }
        }
    }

    markSuspiciousGutters();
}

function markSuspiciousGutters() {
  const gutterWrappers = document.querySelectorAll('.CodeMirror-gutter-wrapper');

  gutterWrappers.forEach(wrapper => {
    const nextSibling = wrapper.nextElementSibling;
    if (nextSibling && nextSibling.classList.contains('suspicious-line')) {
      wrapper.classList.add('suspicious-gutter');
    }
  });
}

function clearHighlightedLines(editor) {
    const doc = editor.getDoc();
    const lines = doc.lineCount();

    for (let i = 0; i < lines; i++) {
        editor.removeLineClass(i, "text", "suspicious-line");
    }
}

function applyInitialStyling(editor, settings, highlightEnglishText) {
    applyCustomStyling(editor.getWrapperElement(), settings);
    if (settings.highlightEnglish) highlightEnglishText();
}

// Function to check for russian text contains english letters
function checkLineIsEnglish(line) {
  if (!line || typeof line !== 'string') return false;

  return /[A-Za-z]+/.test(line);
}

// Function to check for suspicious advertising content in English text
function checkOriginalLine(line) {
  if (!line || typeof line !== 'string') return false;

  const englishPatterns = [
    /subscribe|follow\s+.*\s+(channel|page|account)/i,
    /read|join\s+us\s+at/i,
    /discord\s+server|telegram|whatsapp\s+group/i,
    /support\s+us\s+on\s+(patreon|ko-fi|paypal)/i,
    /click|link\s+here|download\s+app|visit\s+(site|store)/i,
    /promo\s+code|sponsored|affiliate\s+link/i,
    /donate|tip|monetization/i,
    /\bad\b|announcement|advertisement/i,
    /follow\s+us\s+on\s+(facebook|instagram|tiktok|youtube)/i,
    /exclusive|bonus\s+content\s+for\s+subscribers/i,
    /rate|review|share\s+this\s+(chapter|book)/i,
    /turn\s+on\s+notifications|stay\s+tuned/i,
    /limited\s+offer|early\s+access|paid\s+chapters/i,
    /commercial\s+break|check\s+out/i,
    /@\w+|https?:\/\/|www\.|\.com|\.org|\.net/i,
    /#\w+|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/i
  ];

  return englishPatterns.some(pattern => pattern.test(line));
}

// Function to check for suspicious advertising content in Russian text
function checkTranslatedLine(line) {
  if (!line || typeof line !== 'string') return false;

  const russianPatterns = [
    /подписывайтесь|подписаться|подпишись|подписка/i,
    /читайте нас|присоединяйтесь к нам|найдите нас/i,
    /discord[- ]сервер|вайбер|viber|whatsapp|телеграм|telegram|vk|вконтакте|одноклассники/i,
    /поддержите автора|поддержка автора|поддержать нас/i,
    /юmoney|patreon|donationalerts|сбербанк|тинькофф/i,
    /ссылка для доступа|скачать приложение|перейти по ссылке/i,
    /промокод|рекламное предложение|партнерская программа/i,
    /донаты|пожертвования|купите кофе автору/i,
    /эксклюзивный контент|для подписчиков/i,
    /рейтинг книги|оставьте отзыв|litres|амазон|amazon/i,
    /включите уведомления|не пропустите обновления/i,
    /ранний доступ|платные главы|доступ за деньги/i,
    /рекламная пауза|спонсорский блок|партнерский материал/i,
    /извините за рекламу|это не часть сюжета/i,
    /vk\.com|t\.me|mail\.ru/i,
    /#[а-яА-Яa-zA-Z0-9_]+/i,
    /tiktok|youtube|rutube|zen|яндекс/i,
    /ozon|wildberries|twitch/i,
    /перевод от команды|переведено для/i,
    /автор просит поддержать|главы выходят быстрее/i,
    /переходите по ссылке|акция только для|первых \d+ читателей/i,
    /отключите блокировщик|реклама помогает/i,
    /planeta\.ru|boomstarter|краудфандинг/i,
    /[a-z]+\.[a-z]{2,3}/i,
    /\+7[0-9]{10}|8[0-9]{10}/i,
    /vpn|torrent|пиратск/i,
    /ищите нас в соцсетях/i
  ];

  return russianPatterns.some(pattern => pattern.test(line));
}

function createContextMenu(app, el, editor) {
    if(el._codemirror) {
        delete el._codemirror;
    }
    el._codemirror = editor;
    const parent = editor.getWrapperElement().parentElement;

    el.addEventListener('click', (e) => {
      if (e.target.classList.contains('CodeMirror-linenumber')) {
        const coords = getRelativeCoordinates(e, parent);
        const lineNumber = parseInt(e.target.textContent);

        if (!isNaN(lineNumber)) {
          const lineContent = editor.getLine(lineNumber - 1);

          // Trigger the line number click event with line number and click position
          const event = new CustomEvent('linenumberclick', {
            detail: {
              lineNumber: lineNumber - 1,  // Convert to 0-based index
              lineContent: lineContent,
              x: coords.x,
              y: coords.y
            }
          });
          el.dispatchEvent(event);
        }
      }
    });
}

function hashObject(obj) {
  if (obj === null || typeof obj !== 'object') {
    // Handle non-objects or null gracefully, perhaps return a default hash or hash their string representation.
    // For simplicity here, we'll stringify them directly.
     const str = String(obj);
     let hash = 0;
     for (let i = 0; i < str.length; i++) {
        const char = str.charCodeAt(i);
        hash = ((hash << 5) - hash) + char;
        hash |= 0; // Convert to 32bit integer
     }
     return (hash >>> 0).toString(36);
  }

  let jsonString;
  try {
    // Attempt to stringify with sorted keys for consistency
    const sortedKeys = Object.keys(obj).sort();
    jsonString = JSON.stringify(obj, sortedKeys);
  } catch (e) {
    // Fallback for complex objects (like those with circular references or non-serializable values)
    // This fallback is imperfect and might not produce consistent hashes for equivalent but complex objects.
    try {
        jsonString = JSON.stringify(obj);
    } catch (e2) {
        // If even basic stringify fails, use a very basic string representation.
        jsonString = Object.prototype.toString.call(obj);
    }
  }

  let hash = 0;
  if (jsonString.length === 0) {
    return '0';
  }
  for (let i = 0; i < jsonString.length; i++) {
    const char = jsonString.charCodeAt(i);
    hash = ((hash << 5) - hash) + char;
    hash |= 0; // Convert to 32bit integer
  }

  // Convert to positive unsigned 32-bit integer before converting to base 36
  const positiveHash = (hash >>> 0);

  // Convert to base 36 (0-9a-z)
  return positiveHash.toString(36);
}