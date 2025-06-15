// Import core functions
//import { getById, debounce, removeSearchParam } from './core.js';

/**
 * Creates an Alpine.js data object with list functionality
 */
export function listApp() {
    return {
        books: [],
        currentPage: 0,
        totalPages: 1,
        totalElements: 0,
        searchQuery: '',
        filters: {
            bookId: '',
        },
        currentBook: { id: '', title: '', bookState: {} },
        showDeleteModal: false,
        isUploading: false,
        bookToDelete: null,
        activeId: localStorage.getItem('activeId') || '',
        booksActionDropDownOpen: false,
        actionDropDownOpen: {},
        overwrite: false,
        file: undefined,
        importTranslationFile: undefined,
        importGlossaryTranslateFile: undefined,
        importChapterFile: undefined,
        currentRequest: undefined,
        bookLoading: true,


        initList() {
            const params = this.fromSearchParams(window.location.search);
            const qBookId = params.get('bookId');
            if(qBookId) {
                this.activeId = qBookId;
                this.filters.bookId = qBookId;
            }

            this.fetchBooks(0);
            this.registerHotkeys(this);
            this.registerChangeListener(this, "fileInput", this.uploadBook);
            this.registerChangeListener(this, "importTranslationInput", this.importTranslation);
            this.registerChangeListener(this, "importGlossaryTranslateInput", this.importGlossaryTranslate);
            this.registerChangeListener(this, "importChapterInput", this.importChapter);
        },

        fetchBooksDebounce(page = 0, callback = () => {}) {
            if(this.currentRequest) {
                this.currentRequest.cancel();
                this.currentRequest = undefined;
            }

            this.currentRequest = this.debounce(() => {
                this.fetchBooks(page, callback);
            }, 500);

            this.currentRequest();
        },

        fetchBooks(page = 0, callback = () => {}) {
            this.bookLoading = true;
            const params = {
                page,
                query: this.searchQuery || undefined,
                bookId: this.filters.bookId || undefined,
            };

            fetch('/api/books?' + this.toURLSearchParams({...params}).toString(), {
                method: 'GET',
            })
            .then(response => response.json()
                .then(rsp => {
                    if (!response.ok) {
                        console.error('Error fetching chapters:', rsp);
                        this.showToast(`Error: ${rsp.message || rsp.detail}`, true);
                    } else {
                        this.currentPage = parseInt(response.headers.get('x-current-page')) || 0;
                        this.totalPages = parseInt(response.headers.get('x-total-pages')) || 1;
                        this.totalElements = parseInt(response.headers.get('x-total-elements')) || 0;

                        this.books = rsp;
                        this.afterFetchBooks();
                        callback();
                    }
                }));

            this.gracefulStop('bookLoading');
        },

        afterFetchBooks() {
//            this.actionDropDownOpen = {};
            this.changeActiveItem(this.activeId);
        },

        changeActiveItem(bookId) {
            const isNewBook = bookId !== this.activeId;

            if(this.books && this.books.length > 0) {
                const item = this.getById(this.books, bookId);
                if(item) {
                    this.activeId = item.id;
                } else {
                    this.activeId = this.books[0].id;
                }
                localStorage.setItem('activeId', this.activeId);
            } else {
                this.activeId = '';
            }

            if(this.activeId) {
                this.currentBook = this.getById(this.books, this.activeId);
            }

            if(isNewBook) {
                this.fetchTitles();
                this.fetchGlossary();
                this.bookReportLoadData();
                this.bookReportLoadHeatmapData();
            }
        },

        editBook(book) {
            if(book && book.id) {
                showLoader(500);
                window.location.href = "/chapters?bookId=" + book.id;
            }
        },

        deleteBook(id) {
            this.bookToDelete = id;
            this.showDeleteModal = true;
        },

        confirmDelete() {
            fetch(`/api/books/${this.bookToDelete}`, {
                method: 'DELETE'
            })
            .then(() => {
                this.activeId = '';
                this.filters.bookId = '';
                this.fetchBooks(this.currentPage);
                this.cancelDelete();
                this.removeSearchParam('bookId');
            });
        },

        cancelDelete() {
            this.showDeleteModal = false;
            this.bookToDelete = null;
        },

        registerHotkeys(app) {
            // Prevent default browser behavior for Alt+Left
            window.addEventListener('keydown', function(e) {
                 if ((e.altKey && e.key === 'ArrowLeft') ||
                    (e.ctrlKey && e.key === 's')) {
                    e.preventDefault();
                }
            });

//            Mousetrap.bind('alt+right', () => {
//                app.changeNumberFilter(1);
//            });
//            Mousetrap.bind('alt+left', (e) => {
//                e.preventDefault();
//                app.changeNumberFilter(-1);
//
//                return false;
//            });
            Mousetrap.bind('ctrl+e', function(e) {
                e.preventDefault(); // Prevent browser save dialog
                app.editBook(app.currentBook);

                return false;
            });
        },

        uploadBook(app, formData) {
            fetch('/api/books/upload?overwrite=' + (app.overwrite ? 'true' : 'false'), {
              method: 'POST',
              body: formData,
            }).then(response => {
                if (response.ok) {
                  app.fetchBooks(0);
                  app.changeActiveItem(response.id);
                  console.info('File uploaded successfully');
                } else {
                  console.error('Upload failed: ' + response.statusText);
                  app.showToast(`File upload failed!`, true);
                }
            });
        },

        executeWithMeasure(fn) {
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

            fn(() => {
                clearInterval(updateTimer);
                this.isExecuting = false;
            });
        },

        handleAction(action, bookId, ref = undefined) {
            this.activeId = bookId;

            switch (action) {
              case 'import-glossary':
                console.info(`Import Glossary for book: ${book.title}`);
                // Add API call logic here
                break;
              case 'export-glossary':
                console.info(`Export Glossary for book: ${book.title}`);
                // Add API call logic here
                break;
              case 'import-translation':
                if(ref) {
                    ref.click();
                }
                break;
              case 'export-translation':
                console.info(`Export Translation for book: ${book.title}`);
                // Add API call logic here
                break;
              case 'import-glossary-translate':
                if(ref) {
                    ref.click();
                }
                break;
              default:
                console.error('Unknown action');
                this.showToast(`Unknown action`, true);
            }
        },

        importTranslation(app, formData) {
            fetch(`/api/books/${app.activeId}/upload/translation`, {
              method: 'POST',
              body: formData,
            }).then(response => {
                if (response.ok) {
                  console.info('Translation uploaded successfully');
                } else {
                  console.error('Upload failed: ' + response.statusText);
                  app.showToast('Upload failed: ' + response.statusText, true);
                }
            });
        },

        importGlossaryTranslate(app, formData) {
            fetch(`/api/books/${app.activeId}/upload/glossary-translation`, {
              method: 'POST',
              body: formData,
            }).then(response => {
                if (response.ok) {
                  console.info('Glossary translation uploaded successfully');
                } else {
                  console.error('Upload failed: ' + response.statusText);
                  app.showToast('Upload failed: ' + response.statusText, true);
                }
            });
        },

        importChapter(app, formData) {
            fetch(`/api/books/${app.activeId}/upload/chapters`, {
              method: 'POST',
              body: formData,
            }).then(response => {
                if (response.ok) {
                  console.info('Chapter uploaded successfully');
                } else {
                  console.error('Upload failed: ' + response.statusText);
                  app.showToast('Upload failed: ' + response.statusText, true);
                }
            });
        },

        registerChangeListener(app, selector, fn) {
            const input = document.querySelector(`[x-ref="${selector}"]`);
            input.addEventListener('change', async (e) => {
              const file = e.target.files[0];
              if (!file) return;

              const formData = new FormData();
              formData.append('file', file);
              formData.append('fileName', file.name.replace(/\.[^/.]+$/, ""));

              try {
                app.isUploading = true;
                fn(app, formData);

              } catch (error) {
                console.error('Error during upload: ' + error.message);
                app.showToast(`${file.name} upload failed!`, true);

              } finally {
                app.isUploading = false;
                input.value = null
              }
            });
        },


    };
}
