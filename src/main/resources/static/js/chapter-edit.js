// Import core functions
import { getById, getByKey, getNextById, getPrevById, readBoolSetting, writeSetting, readSetting } from './core.js';
import { analyzeText, analyzeTokens } from './report.js';

/**
 * Creates an Alpine.js data object with edit functionality
 */
export function editApp() {
    return {
        selectedField: 'translatedText',
        currentContent: '',
        originalContent: '',
        history: [],
        analysis: {},
        fieldsForAnalysis: [ "text", "translatedText" ],
        isSidebarCollapsed: false,
        actionSettingsOpen: false,
        chapterActiveTab: 'editor',
        requestTemplate: {
            shouldPersist: true,
            operationName: ''
        },
        isExecuting: false, // Track if an operation is in progress
        startTime: null,   // Track the start time of the operation
        timePassed: '00:00:00',     // Track how much time has passed
        chapterFontSize: localStorage.getItem('chapterFontSize') || 16,

        initEdit() {
            this.updateContent();
            this.loadState('isSidebarCollapsed');
            if(readSetting('selectedField')) {
                this.selectField(readSetting('selectedField'));
            }

            this.registerEditHotkeys(this);
            const requestTemplate = localStorage.getItem('requestTemplate');
            if (requestTemplate) {
              this.requestTemplate = JSON.parse(requestTemplate);
            }
            this.loadValue('chapterActiveTab', 'editor');
        },

        fetchChapter(chapterId) {
            return fetch(`/api/chapters/${chapterId}`)
                .then(response => {
                    if (!response.ok) {
                        console.error('Error fetching chapter by id:', rsp);
                        this.showToast(`Error: ${rsp.message || rsp.detail}`, true);
                    }

                    return response.json();
                });
        },

        pullChapterContentChangesById(chapterId) {
           this.fetchChapter(chapterId)
                .then(rsp => {
                    const chapterToUpdate = this.getById(this.chapters, chapterId);
                    if (chapterToUpdate) {
                        Object.assign(chapterToUpdate, rsp);
                        this.updateContent();
                        this.fetchHistory();
                    }
                });
        },

        async handleTranslateText() {
            try {
                const summary = await this.executeOperation('translate');
                this.pullChapterContentChangesById(this.activeId);
            } catch (error) {
                console.error('Failed to translate text:', error);
                this.showToast(`Failed to translate text: ${error.message || error.detail || error}`, true);
            }
        },

        async updateContent() {
            this.analysis = {};
            if(this.currentChapter) {
                this.currentContent = this.currentChapter[this.selectedField] || '';
                this.originalContent = this.currentChapter[this.selectedField] || '';
                this.fieldsForAnalysis.forEach(field => {
                    this.analysis[field] = analyzeText(this.currentChapter[field] || "");
                });
                this.fieldsForAnalysis.forEach(async (field) => {
                    this.analysis[field] = await analyzeTokens(this.currentChapter[field] || "", this.analysis[field]);
                });
            } else {
                Alpine.nextTick(() => {
                    this.currentContent = '';
                    this.originalContent = '';
                });
            }
        },

        fetchHistory() {
            if(!this.activeId) {
                return;
            }
            this.history = [];

            fetch(`/api/chapters/${this.activeId}/history/${this.selectedField}`, { method: 'GET' })
                .then(response => response.json()
                    .then(rsp => {
                        if (!response.ok) {
                            console.error('History fetch error:', rsp);
                            this.showToast(`History fetch error: ${rsp.message || rsp.detail}`, true);
                        } else {
                            this.history = (rsp || []);
                        }
                    }));
        },

        selectVersion(number) {
            fetch(`/api/chapters/${this.activeId}/history/${this.selectedField}/at?number=${number}`, { method: 'GET' })
                .then(response => response.json()
                    .then(rsp => {
                        if (!response.ok) {
                            console.error('Content fetch error:', rsp);
                            this.showToast(`Content fetch error: ${rsp.message || rsp.detail}`, true);
                        } else {
                            this.currentContent = rsp;
                        }
                    }));
        },

        selectField(field) {
            this.selectedField = field;
            this.updateContent();
            writeSetting('selectedField', this.selectedField);
            this.fetchHistory();
        },

        saveEditorChanges() {
            this.currentChapter[this.selectedField] = this.currentContent;
            this.saveChapter(() => {
                this.fetchHistory();
            });
        },

        saveChapter(callback = () => {}) {
            this.isSaving = true;
            const method = this.editing ? 'PUT' : 'POST';
            const url = this.editing ? `/api/chapters/${this.currentChapter.id}` : '/api/chapters';
            fetch(url, {
                method: method,
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(this.currentChapter)
            })
            .then(response => response.json()
                .then(rsp => {
                    if (!response.ok) {
                        console.error('Error saving chapter:', rsp);
                        this.showToast(`Error saving chapter: ${rsp.message || rsp.detail}`, true);
                    } else {
                        this.fetchChapters(this.currentPage);
                        setTimeout(() => {
                            this.showToast(`Changes have been saved`);
                            this.isSaving = false;
                        }, 1000);
                        if(callback) {
                            callback();
                        }
                    }
                }));
        },

        selectTab(tabName) {
            this.changeValue('chapterActiveTab', tabName);
            if(this.chapterActiveTab === 'preview') {
                this.previewChanges();
            }
            if(this.chapterActiveTab === 'editor') {
                setTimeout(() => {
                    const target = document.querySelector('div[x-editor="currentContent"]');
                    const editor = target._codemirror;
                    if(editor) {
                        editor.refresh();
                    }
                }, 10);
            }
        },

        previewChanges() {
            setTimeout(() => {
                const target = document.getElementById('merge-view');
                target.innerHTML = '';

                const diff = new diff_match_patch();
                const patches = diff.patch_make(this.originalContent, this.currentContent);
                const prettyHtml = diff.patch_toText(patches);
                target.style.height = target.parentElement.clientHeight + 'px';

                const mergeView = CodeMirror.MergeView(target, {
                    value: this.currentContent,
                    origLeft: null,
                    orig: this.originalContent,
                    lineNumbers: true,
                    mode: 'text/plain',
                    highlightDifferences: true,
                    connect: 'align',
                    collapseIdentical: false,
                    allowEditingOriginals: false,
                });

                this.clickOnElement(target);
            }, 10);
        },

        async getAnalysis(text) {
          const result = analyzeText(text);
          return await analyzeTokens(text, result);
        },

        get propertyNames() {
            if(Object.keys(this.analysis).length > 0) {
                const first = Object.keys(this.analysis)[0];
                if(first && this.analysis[first]) {
                    return Object.keys(this.analysis[first]);
                } else {
                    return [];
                }
            } else {
                return [];
            }
        },

        camelCaseToWords(value) {
          const result = value.replace(/([A-Z])/g, ' $1');
          return result.charAt(0).toUpperCase() + result.slice(1);
        },

        formatValue(value) {
          if(value === undefined) return '-';
          if (typeof value === 'number') {
            return (Number.isInteger(value) ? value : value.toFixed(2)).toString().replace(/\B(?=(\d{3})+(?!\d))/g, " ");
          }
          return value;
        },

        persistTemplate() {
            localStorage.setItem('requestTemplate', JSON.stringify(this.requestTemplate));
        },

        async executeOperation(operationName) {
            this.isExecuting = true;
            this.startTime = Date.now();
            this.timePassed = '00:00:00';

            const updateTimer = setInterval(() => {
                const elapsedSeconds = Math.floor((Date.now() - this.startTime) / 1000);
                const hours = String(Math.floor(elapsedSeconds / 3600)).padStart(2, '0');
                const minutes = String(Math.floor((elapsedSeconds % 3600) / 60)).padStart(2, '0');
                const seconds = String(elapsedSeconds % 60).padStart(2, '0');
                this.timePassed = `${hours}:${minutes}:${seconds}`;
            }, 1000);

            let retries = 0;
            const maxRetries = 10; // Maximum number of retries
            const retryDelayBase = 2000; // Base delay in milliseconds
            const request = Object.assign({}, this.requestTemplate);
            request.operationName = operationName;

            while (retries < maxRetries) {
                try {
                    const response = await fetch(`/api/chapters/${this.activeId}/execute`, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify(request)
                    });

                    // Check if the backend responded with a success status
                    if (response.status > 199 && response.status < 300) {
                        console.log(`Executed operation: ${operationName}`);
                        if(this.fetchChapters) {
                            this.fetchChapters();
                        }
                        break; // Exit the retry loop on success
                    }
                } catch (error) {
                    const response = error.response;
                    if (response && response.headers.get('x-retry-at')) {
                        // Extract the retry time from the X-Retry-At header
                        const retryAt = new Date(response.headers.get('x-retry-at')).getTime();
                        const currentTime = Date.now();
                        const delay = Math.max(retryAt - currentTime, retryDelayBase * Math.pow(2, retries));

                        console.log(`Backend busy. Retrying in ${delay}ms...`);
                        await new Promise(resolve => setTimeout(resolve, delay)); // Wait before retrying
                        retries++;
                    } else {
                        console.error(`Error executing operation: ${operationName}`, error);
                        break; // Exit the retry loop on non-retryable errors
                    }
                }
            }

            clearInterval(updateTimer);
            this.isExecuting = false;

            if (retries === maxRetries) {
                console.error(`Max retries exceeded for operation: ${operationName}`);
            }
        },

        registerEditHotkeys(app) {
            Mousetrap.bind('ctrl+shift+s', function(e) {
                e.preventDefault(); // Prevent browser save dialog
                app.saveEditorChanges();

                return false;
            });
        },

    };
}