/**
 * Creates an Alpine.js data object with list functionality
 */
export function listApp() {
    return {
        chapters: [],
        currentPage: 0,
        currentSize: 10,
        totalPages: 1,
        totalElements: 10,
        searchQuery: '',
        searchNames: '',
        chapterId: '',
        filters: {
            bookId: '',
            chapterNumber: '',
            englishText: false,
            suspiciousWords: false,
            userFilters: false,
            userFiltersList: [],
            warnings: false,
        },
        showDeleteModal: false,
        chapterToDelete: null,
        activeId: '',
        currentRequest: undefined,
        activeSearchTab: 'filters',
        newUserFilter: '',


        initList() {
            this.loadFiltersFromLocalStorage();
            const params = this.fromSearchParams(window.location.search);
            const qBookId = params.get('bookId');
            const chapterId = params.get('chapterId');
            const chapterNumber = params.get('chapterNumber');
            if(qBookId) {
                this.filters.bookId = qBookId;
                this.saveFiltersToLocalStorage(false);
            }
            if(chapterId) {
                this.chapterId = chapterId;
                this.activeId = chapterId;
            }

            this.loadValue('currentPage', 0);
            this.loadValue('activeSearchTab', 'filters');

            if(chapterNumber) {
                this.filters.chapterNumber = chapterNumber;
                this.currentPage = 0;
                this.activeSearchTab = 'filters';
            }

            this.fetchChapters(this.currentPage, () => this.afterInit());
            this.registerHotkeys(this);
        },

        loadFiltersFromLocalStorage() {
            const storedFilters = localStorage.getItem('filters');
            if (storedFilters) {
                this.filters = JSON.parse(storedFilters);
            }
        },

        saveFiltersToLocalStorage(fetchUpdates = true) {
            localStorage.setItem('filters', JSON.stringify(this.filters));
            if(fetchUpdates) {
                this.withDebounce(this, 'currentRequest', () => {
                   this.fetchChapters(0); // Refetch data when filters change
                }, 300);
            }
        },

        setSelectedItem(newId) {
            if(!newId) return;

            this.activeId = newId;
            this.currentChapter = this.getById(this.chapters, this.activeId);
            this.updateContent();
        },

        changeNumberFilter(newValue) {
            //TODO refactor given code, based on active filter tab
            if(this.chapters && this.chapters.length > 1) {
                const isLast = this.chapters.indexOf(this.currentChapter) == this.chapters.length - 1;
                const isFirst = this.chapters.indexOf(this.currentChapter) == 0;

                if((isLast && this.totalPages > this.currentPage && newValue > 0) || (isFirst && this.currentPage > 0 && newValue < 0)) {
                   if(this.currentPage + newValue >= this.totalPages) return;
                   if(this.currentPage + newValue < 0) return;

                   this.withDebounce(this, 'currentRequest', () => {
                      this.fetchChapters(this.currentPage + newValue);
                   }, 300);
                } else {
                    const nextItem = this.getNextById(this.chapters, this.activeId);
                    const prevItem = this.getPrevById(this.chapters, this.activeId);
                    this.setSelectedItem(newValue > 0 ? nextItem?.id : prevItem?.id);
                }
            } else {
                this.activeSearchTab = 'filters';
                this.filters.chapterNumber = '' + Math.max(((this.currentChapter.number || 0) + newValue), 1);
                this.saveFiltersToLocalStorage();
                this.updateContent();
            }
        },

        clearSearchFilters(name = 'englishText') {
            this.searchQuery = '';
            this.searchNames = '';
            this.filters.englishText = false;
            this.filters.suspiciousWords = false;
            this.filters.userFilters = false;
            this.filters.warnings = false;
            this.filters[name] = true;
        },

        fetchChaptersDebounce(page = 0, callback = () => {}) {
            this.withDebounce(this, 'currentRequest', () => {
               this.fetchChapters(page, callback);
            }, 500);
        },

        fetchChapters(page = 0, callback = () => {}) {
            let request = {
               page,
               size: this.currentSize,
               bookId: this.filters.bookId || undefined
            };

            if(this.chapterId) {
                request = {
                    ...request,
                    chapterId: this.chapterId
                };
            }
            else if(this.activeSearchTab === 'filters') {
                request = {
                    ...request,
                    chapterNumber: this.filters.chapterNumber || -1
                };
                if(request.chapterNumber === -1) {
                    delete request.chapterNumber;
                }
            } else if (this.activeSearchTab === 'searchBar') {
                request = {
                    ...request,
                    query: this.searchQuery || undefined,
                    queryNames: this.searchNames || undefined,
                };
            } else if (this.activeSearchTab === 'advancedFilters') {
                request = {
                    ...request,
                    englishText: this.filters.englishText,
                    warnings: this.filters.warnings
                };

                if(this.filters.userFilters) {
                    request.userFilters = this.filters.userFiltersList;
                }

                if(this.filters.suspiciousWords) {
                    const fieldName = this.selectedField === 'translatedText' ? 'suspiciousTranslatedWords' : 'suspiciousOriginalWords';
                    request[fieldName] = this.filters.suspiciousWords;
                }
            }

            doFetchChapters(this, page, request, callback);
        },

        afterFetchChapters() {
            this.changeValue('currentPage', this.currentPage);
            if(this.chapters && this.chapters.length > 0) {
                if(!this.getById(this.chapters, this.activeId)) {
                    this.activeId = this.chapters[0].id;
                }
            } else {
                this.activeId = '';
            }

            if(this.activeId) {
                this.currentChapter = this.getById(this.chapters, this.activeId);
            }

            if(this.showEditNamesPanel) {
                this.openEditNamesPanelCurrent();
            }

            this.updateContent();
            this.fetchHistory();
        },

        addUserFilter() {
            if (this.newUserFilter.trim()) {
                this.filters.userFilters.push(this.newUserFilter.trim());
                this.newUserFilter = '';
                this.saveFiltersToLocalStorage();
            }
        },

        removeUserFilter(index) {
            this.filters.userFilters.splice(index, 1);
            this.saveFiltersToLocalStorage();
        },

        getWarningColor(type) {
            return {
               'EMPTY_FIELD': 'bg-indigo-100 text-indigo-800',
               'LANGUAGE': 'bg-blue-100 text-blue-800',
               'PUNCTUATION': 'bg-yellow-100 text-yellow-800',
               'R18_CONTENT': 'bg-red-100 text-red-800',
               'OTHER': 'bg-gray-100 text-gray-800'
           }[type] || 'bg-gray-100 text-gray-800';
        },

        getWarningLabel(type) {
            const labels = {
                'EMPTY_FIELD': 'Empty field',
                'LANGUAGE': 'Language',
                'PUNCTUATION': 'Punctuation',
                'R18_CONTENT': 'Adult Content',
                'OTHER': 'Other'
            };
            return labels[type] || type;
        },

        deleteChapter(id) {
            this.chapterToDelete = id;
            this.showDeleteModal = true;
        },

        confirmDelete() {
            fetch(`/api/chapters/${this.chapterToDelete}`, { method: 'DELETE' })
                .then(response => response.json()
                    .then(rsp => {
                        if (!response.ok) {
                            console.error('Error removing chapter:', rsp);
                            this.showToast(`Error removing chapter: ${rsp.message || rsp.detail}`, true);
                        } else {
                            this.fetchChapters(this.currentPage);
                            this.cancelDelete();
                        }
                }));
        },

        cancelDelete() {
            this.showDeleteModal = false;
            this.chapterToDelete = null;
        },

        registerHotkeys(app) {
            // Prevent default browser behavior for Alt+Left
            window.addEventListener('keydown', function(e) {
                 if ((e.altKey && e.key === 'ArrowLeft') ||
                    (e.ctrlKey && e.key === 's')) {
                    e.preventDefault();
                }
            });

            if(!app.chapterId) {
                Mousetrap.bind('alt+right', () => {
                    app.changeNumberFilter(1);
                });
                Mousetrap.bind('alt+left', (e) => {
                    e.preventDefault();
                    app.changeNumberFilter(-1);

                    return false;
                });
            }
        },

        afterInit() {
            if(this.readBoolSetting('showEditNamesPanel')) {
                this.openEditNamesPanelCurrent();
            }
        }
    };
}

function doFetchChapters(app, page, params, callback) {
     const queryParams = app.toURLSearchParams({...params}).toString();

     fetch(`/api/chapters?${queryParams}`, {
             headers: {
                'Accept': 'application/json',
             }
         })
         .then(response => {
            response.json()
                .then(rsp => {
                    if (!response.ok) {
                        console.error('Error fetching chapters:', rsp);
                        app.showToast(`Error: ${rsp.message || rsp.detail}`, true);
                    } else {
                        app.currentPage = parseInt(response.headers.get('x-current-page')) || 0;
                        app.totalPages = parseInt(response.headers.get('x-total-pages')) || 1;
                        app.totalElements = parseInt(response.headers.get('x-total-elements')) || 0;

                        app.chapters = rsp;

                        app.afterFetchChapters();
                        callback();
                    }
                });

            return null;
         })
         .catch(error => {
             console.error('Error fetching lines:', error)
             app.showToast(`Error: ${error.message || error.code}`, true);
         });
}