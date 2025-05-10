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
        filters: {
            bookId: '',
            chapterNumber: '',
            englishText: false,
            suspiciousWords: false,
            userFilters: false,
            userFiltersList: []
        },
        showPanel: false,
        editing: true,
        currentChapter: { id: '', title: '', bookId: '', names: [] },
        showEditNamesPanel: false,
        nameFilter: '',
        showDeleteModal: false,
        chapterToDelete: null,
        isSaving: false,
        activeId: '',
        targetName: undefined,
        currentRequest: undefined,
        activeSearchTab: 'filters',
        newUserFilter: '',


        initList() {
            this.loadFiltersFromLocalStorage();

            const qBookId = new URLSearchParams(window.location.search).get('bookId');
            if(qBookId) {
                this.filters.bookId = qBookId;
                this.saveFiltersToLocalStorage(false);
            }

            this.loadValue('currentPage', 0);
            this.loadValue('activeSearchTab', 'filters');

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
            if(this.chapters && this.chapters.length > 1) {
                const isLast = this.chapters.indexOf(this.currentChapter) == this.chapters.length - 1;
                const isFirst = this.chapters.indexOf(this.currentChapter) == 0;

                if((isLast && this.totalPages > this.currentPage && newValue > 0) || (isFirst && this.currentPage > 0 && newValue < 0)) {
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

        clearSearchFilters() {
            this.searchQuery = '';
            this.searchNames = '';
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

            if(this.activeSearchTab === 'filters') {
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
                    englishText: this.filters.englishText
                };

                if(this.filters.userFilters) {
                    request.userFilters = this.filters.userFiltersList;
                }

                if(this.filters.suspiciousWords) {
                    const fieldName = this.selectedField == 'translatedText' ? 'suspiciousOriginalWords' : 'suspiciousTranslatedWords';
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

        openCreatePanel() {
            this.editing = false;
            this.currentChapter = { id: '', title: '', bookId: '', names: [] };
            this.showPanel = true;
        },

        editChapter(chapter) {
            this.activeId = chapter.id;
            this.editing = true;
            this.currentChapter = { ...chapter };
            this.showPanel = true;
        },

        saveChapter(callback = () => {}) {
            this.isSaving = true;
            const method = this.editing ? axios.put : axios.post;
            const url = this.editing ? `/api/chapters/${this.currentChapter.id}` : '/api/chapters';
            method(url, this.currentChapter)
                .then(() => {
                    this.fetchChapters(this.currentPage);
                    setTimeout(() => {
                        this.isSaving = false;
                    }, 1000);
                    if(callback) {
                        callback();
                    }
                });
        },

        closePanel() {
            this.showPanel = false;
        },

        toggleEditNamesPanel() {
            this.showEditNamesPanel = !this.showEditNamesPanel;
            localStorage.setItem('showEditNamesPanel', '' + this.showEditNamesPanel);
            if(this.showEditNamesPanel) {
                this.openEditNamesPanelCurrent();
            }
        },

        openEditNamesPanelCurrent() {
            this.openEditNamesPanel(this.getById(this.chapters, this.activeId));
        },

        openEditNamesPanel(chapter) {
            if(!chapter){
                return;
            }

            this.activeId = chapter.id;
            localStorage.setItem('showEditNamesPanel', 'true');
            this.currentChapter = JSON.parse(JSON.stringify(chapter));
            this.originalNames = JSON.parse(JSON.stringify(chapter.names));
            this.showEditNamesPanel = true;
        },

        closeEditNamesPanel() {
            localStorage.setItem('showEditNamesPanel', 'false');
            this.showEditNamesPanel = false;
        },

        get filteredNames() {
            return this.currentChapter.names.filter(name =>
                name.name.toLowerCase().includes(this.nameFilter.toLowerCase()) ||
                name.category.toLowerCase().includes(this.nameFilter.toLowerCase()) ||
                name.description.toLowerCase().includes(this.nameFilter.toLowerCase()) ||
                name.ruName.toLowerCase().includes(this.nameFilter.toLowerCase())
            );
        },

        addName() {
            this.currentChapter.names.push({ name: '', category: '', description: '', references: [], metadata: {} });
        },

        pasteName() {
            if(!this.getByKey(this.currentChapter.names, 'name', this.targetName.name)) {
                this.currentChapter.names.push(JSON.parse(JSON.stringify(this.targetName)));
            } else {
                console.warn("Collection already contains name: '", this.targetName.name, "'");
            }
        },

        removeName(nameToRemove) {
            this.currentChapter.names = this.currentChapter.names.filter(name => name.name !== nameToRemove);
        },

        copyName(nameToCopy) {
            this.targetName = JSON.parse(JSON.stringify(nameToCopy));
        },

        editName(name) {
            // Implement logic to edit a specific name (e.g., open a modal or inline edit).
            alert(`Editing name: ${name.name}`);
        },

        saveChanges() {
            if(this.showEditNamesPanel) {
                this.saveNames();
            } else if(this.showEditNamesPanel) {
                this.saveChapter();
            } else {
                console.error("Panel doesn't opened");
            }
        },

        saveNames() {
            this.isSaving = true;
            axios.put(`/api/chapters/${this.currentChapter.id}`, this.currentChapter)
                .then(() => {
                    this.fetchChapters(this.currentPage);
                    setTimeout(() => {
                        this.isSaving = false;
                    }, 1000);
                    //this.closeEditNamesPanel();
                })
                .catch(error => {
                    console.error("Error saving names:", error);
                });
        },

        cancelEditNames() {
            this.currentChapter.names = JSON.parse(JSON.stringify(this.originalNames)); // Restore original names
            this.closeEditNamesPanel();
        },

        deleteChapter(id) {
            this.chapterToDelete = id;
            this.showDeleteModal = true;
        },

        confirmDelete() {
            axios.delete(`/api/chapters/${this.chapterToDelete}`)
                .then(() => {
                    this.fetchChapters(this.currentPage);
                    this.cancelDelete();
                });
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

            Mousetrap.bind('alt+right', () => {
                app.changeNumberFilter(1);
            });
            Mousetrap.bind('alt+left', (e) => {
                e.preventDefault();
                app.changeNumberFilter(-1);

                return false;
            });
            Mousetrap.bind('ctrl+s', function(e) {
                e.preventDefault(); // Prevent browser save dialog
                app.saveChanges();

                return false;
            });
            Mousetrap.bind('ctrl+e', function(e) {
                e.preventDefault(); // Prevent browser save dialog
                app.toggleEditNamesPanel();

                return false;
            });
        },

        afterInit() {
            if(this.readBoolSetting('showEditNamesPanel')) {
                this.openEditNamesPanelCurrent();
            }
        }
    };
}

function doFetchChapters(app, page, params, callback) {
     app.totalPages = 1;
     app.totalElements = 0;
     const queryParams = new URLSearchParams(params).toString();

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