/**
 * Creates an Alpine.js data object with list functionality
 */
export function lineListApp() {
    return {
        lineObject: {
            lines: [],
            lineSearchQuery: '',
            lineFilter: '',
            replaceText: '',
            editingLineId: null,
            lineFindText: '',
            lineReplaceWith: '',
            currentScope: 'chapter',
            selectedField: 'text',
            lineMatchCase: false,
            lineMatchWholeWord: false,
            lineUseRegex: false,
            lineFilteredMatchCase: false,
            lineFilteredMatchWholeWord: false,
            lineFilteredUseRegex: false,
        },
        lineLoading: false,
        replaceLoading: false,

        lineCurrentPage: 0,
        lineTotalPages: 1,
        lineTotalElements: 0,
        linePageSize: 100,


        initLineList() {
            this.loadValue('lineObject', this.lineObject);

            const qLineSearchQuery = this.fromSearchParams(window.location.search).get('lineSearchQuery');
            if(qLineSearchQuery) {
                this.lineObject.lineSearchQuery = qLineSearchQuery;
                setTimeout(() => {
                    this.fetchSimilarLines(this.lineObject.lineSearchQuery, [this.lineObject.selectedField]);
                }, 10);
            }
        },

        get filteredLines() {
            if (!this.lineObject.lineFilter) return this.lineObject.lines;

            const filterText = this.lineObject.lineFilter;
            const matchCase = this.lineObject.lineFilteredMatchCase;

            return this.lineObject.lines.filter(line => {
                const originalLine = line?.originalLine || '';
                const translatedLine = line?.translatedLine || '';

                if (this.lineObject.lineFilteredUseRegex) {
                    try {
                        const regex = new RegExp(filterText, matchCase ? '' : 'i');
                        return regex.test(originalLine) || regex.test(translatedLine);
                    } catch (e) {
                        return false;
                    }
                }

                if (this.lineObject.lineFilteredMatchWholeWord) {
                    const escapedFilter = filterText.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
                    const wordRegex = new RegExp(`(?:^|\s|[.,!?"'])\\b${escapedFilter}\\b(?:$|\s|[.,!?"'])`, matchCase ? 'u' : 'iu');
                    const result = (wordRegex.test(originalLine) || wordRegex.test(translatedLine));

                    if(!result) {
                        const originalMatchResult = originalLine.match(wordRegex);
                        const translatedMatchResult = translatedLine.match(wordRegex);

                        if(originalMatchResult && originalMatchResult[0]) {
                            return true;
                        }
                        if(translatedMatchResult && translatedMatchResult[0]) {
                            return true;
                        }
                    } else {
                        return result;
                    }

                    return findWholeWord(originalLine, filterText, matchCase) ||
                           findWholeWord(translatedLine, filterText, matchCase);
                }

                if (matchCase) {
                    return originalLine.includes(filterText) || translatedLine.includes(filterText);
                } else {
                    const lowerFilter = filterText.toLowerCase();
                    return originalLine.toLowerCase().includes(lowerFilter) ||
                           translatedLine.toLowerCase().includes(lowerFilter);
                }
            });
        },

        async fetchChapterLines(chapterId) {
            this.lineLoading = true;
            try {
                const response = await fetch(`/api/chapters/${chapterId}/lines`);
                const data = await response.json();
                this.lineObject.lines = data;
                this.lineCurrentPage = parseInt(response.headers.get('x-current-page')) || 0;
                this.lineTotalPages = parseInt(response.headers.get('x-total-pages')) || 1;
                this.lineTotalElements = parseInt(response.headers.get('x-total-elements')) || 0;

                setTimeout(() => this.lineLoading = false, 100);
            } catch (error) {
                console.error('Error fetching lines:', error);
                this.showToast(`Error: ${error.message || error.code}`, true);
            }
        },

        async fetchSimilarLines(line, fields) {
            this.lineLoading = true;
            this.lineObject.lineSearchQuery = line;

            let url;
            if(this.lineObject.currentScope == 'chapter') {
                const chapterId = this.currentChapter?.id || this.activeId;
                url = chapterId ? `/api/chapters/${chapterId}/lines/similar` : `/api/books/${this.filters.bookId}/lines/similar`
            } else {
                url = `/api/books/${this.filters.bookId}/lines/similar`
            }

            try {
                const response = await fetch(url, {
                    method: 'POST',
                    headers: {
                      "Content-Type": "application/json",
                    },
                    body: JSON.stringify({
                        fields: fields,
                        line: line,
                        matchCase: this.lineObject.lineMatchCase,
                        matchWholeWord: this.lineObject.lineMatchWholeWord,
                        useRegex: this.lineObject.lineUseRegex
                    }),
                });

                const data = await response.json();
                setTimeout(() => this.lineLoading = false, 100);

                if(response.ok) {
                    this.lineObject.lines = data;
                    this.lineCurrentPage = parseInt(response.headers.get('x-current-page')) || 0;
                    this.lineTotalPages = parseInt(response.headers.get('x-total-pages')) || 1;
                    this.lineTotalElements = parseInt(response.headers.get('x-total-elements')) || 0;
                    this.changeValue('lineObject', this.lineObject);
                    return Promise.resolve(data);
                } else {
                    throw new Error('Failed to fetch similar lines');
                }
            } catch (error) {
                console.error('Error fetching lines:', error)
                this.showToast(`Error: ${error.message || error.code}`, true);
                return Promise.reject(error);
            }
        },

        removeOriginalLine(lineId, index) {
            this.removeLine(lineId, index, ['text']);
        },

        removeTranslatedLine(lineId, index) {
            this.removeLine(lineId, index, ['translatedText']);
        },

        removeLine(lineId, index, fields = ['text', 'translatedText']) {
            if (confirm("Are you sure you want to delete this line?")) {
                fetch(`/api/lines/${lineId}`, {
                    method: 'DELETE',
                    headers: {
                      "Content-Type": "application/json",
                    },
                    body: JSON.stringify({
                        fields: fields,
                        ids: [lineId]
                    })
                })
                .then(response => {
                    if (response.ok) {
                        this.lineObject.lines.splice(index, 1);
                        this.showToast('Line removed successfully!');
                    } else {
                        response.json()
                            .then(rsp => {
                                console.error('Error removing line:', rsp);
                                this.showToast(`Error: ${rsp.message || rsp.detail}`, true);
                            });
                    }
                })
                .catch(error => {
                    console.error('Error removing line:', error);
                    this.showToast(`Error: ${error.message || error.code}`, true);
                });
            }
        },

        removeAllOriginalLines() {
            this.removeAllLines(['text']);
        },

        removeAllTranslatedLines() {
            this.removeAllLines(['translatedText']);
        },

        removeAllLines(fields = ['text', 'translatedText']) {
            if(!this.lineObject.lines || this.lineObject.lines.length == 0) {
                return;
            }

            if (confirm("Are you sure you want to delete all lines?")) {
                fetch(`/api/lines`, {
                    method: 'DELETE',
                    headers: {
                      "Content-Type": "application/json",
                    },
                    body: JSON.stringify({
                        fields: fields,
                        ids: this.lineObject.lines.map(line => line.id)
                    })
                })
                .then(response => {
                    if (!response.ok) {
                        response.json()
                            .then(rsp => {
                                console.error('Error removing lines:', rsp);
                                this.showToast(`Error: ${rsp.message || rsp.detail}`, true);

                                this.fetchChapters(this.currentPage);
                            });
                    } else {
                        this.lineObject.lines = [];
                        this.showToast('All lines removed successfully!');
                    }
                })
                .catch(error => {
                    console.error('Error removing lines:', error)
                    this.showToast(`Error: ${error.message || error.code}`, true);
                });
            }
        },
        
        async performReplace() {
            if (!this.lineObject.lineSearchQuery || !this.lineObject.lineReplaceWith) return;
            this.lineLoading = true;

            try {
                const response = await fetch(`/api/books/${this.filters.bookId}/lines/replace`, {
                    method: 'POST',
                    headers: {
                        "Content-Type": "application/json",
                    },
                    body: JSON.stringify({
                        find: this.lineObject.lineSearchQuery,
                        replace: this.lineObject.lineReplaceWith,
                        ids: this.filteredLines.map(line => line.id)
                    })
                });

                this.showToast('Replacement successful!');
                //TODO await until pg refresh mat view is complete before fetching again
                await new Promise(resolve => setTimeout(resolve, 1000));
                await this.fetchSimilarLines(this.lineObject.lineSearchQuery, [this.lineObject.selectedField]);
            } catch (error) {
                console.error('Error replacing lines:', error);
                this.showToast(`Error: ${error.message || error.code}`, true);
                this.lineLoading = false;
            }
        },
    
        editLine(lineId) {
            this.lineObject.editingLineId = lineId;
            const line = this.lineObject.lines.find(l => l.id === lineId);
            line.originalLineContent = line.originalLine;
            line.translatedLineContent = line.translatedLine;
        },

        removeLineItem(lineId) {
            this.lineObject.lines = this.lineObject.lines.filter(line => line.id !== lineId);
        },

        async applyReplaceChanges(lineId) {
            this.replaceLoading = true;
            const line = this.lineObject.lines.find(l => l.id === lineId);
            const temp = {
                originalLine: '',
                translatedLine: '',
            };

            if(line?.originalLine) {
                temp.originalLine = line.originalLine.replace(this.lineObject.lineFilter, this.lineObject.replaceText);
            }

            if(line?.translatedLine) {
                temp.translatedLine = line.translatedLine.replace(this.lineObject.lineFilter, this.lineObject.replaceText);
            }

            try {
                const changeResult = await this.saveLine(lineId, temp.originalLine, temp.translatedLine);
            } catch(ignore) {
                this.removeLineItem(lineId);
            } finally {
                setTimeout(() => {
                  this.replaceLoading = false;
                }, 100);
            }
        },

        async saveLine(lineId, originalText, translatedText) {
            const line = this.lineObject.lines.find(l => l.id === lineId);
            const request = {
                id: lineId,
                originalLine: originalText,
                translatedLine: translatedText
            };

            if(request.originalLine === line.originalLine) {
                delete request.originalLine;
            }
            if(request.translatedLine === line.translatedLine) {
                delete request.translatedLine;
            }

            if(Object.keys(request).length === 1) return Promise.resolve();

            try {
                const response = await fetch(`/api/lines/${lineId}`, {
                    method: 'PATCH',
                    headers: {
                        "Content-Type": "application/json",
                    },
                    body: JSON.stringify(request)
                });

                this.lineObject.editingLineId = null;

                if (!response.ok) {
                    const rsp = await response.json();
                    if (!rsp.ok) {
                        console.error('Error updating line:', rsp);
                        this.showToast(`Error: ${rsp.message || rsp.detail}`, true);
                        return Promise.reject(new Error(rsp.message || rsp.detail));
                    }
                } else {
                    this.removeById(this.lineObject.lines, lineId);
                    this.showToast('Line updated successfully!');
                    await this.pullChapterContentChangesById(line.chapterId);
                }

                return Promise.resolve();
            } catch (error) {
                console.error('Error updating line:', error);
                this.showToast(`Error: ${error.message || error.code}`, true);
                return Promise.reject(error);
            }
        },

        highlightLineMatch(text, remoteQuery, localQuery) {
            if (!text || !remoteQuery) return text;

            const escapedQuery = remoteQuery.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
            const regex = new RegExp(`(${escapedQuery})`, 'gi');

            let result = text.replace(regex, '<mark class="bg-yellow-100">$1</mark>');

            if (localQuery) {
                const escapedLocalQuery = localQuery.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
                const localRegex = new RegExp(`(${escapedLocalQuery})`, 'gi');
                result = result.replace(localRegex, '<mark class="bg-yellow-300">$1</mark>');
            }

            return result;
        },

        get linePaginatedLines() {
            const start = this.lineCurrentPage * this.linePageSize;
            const end = start + this.linePageSize;
            return this.lineObject.lines.slice(start, end);
        },

        get lineStartIndex() {
            return this.lineCurrentPage * this.linePageSize;
        },

        get lineEndIndex() {
            return this.lineStartIndex + this.linePageSize;
        },

        get linePageNumbers() {
            const pages = [];
            const maxVisible = 5;
            const halfVisible = Math.floor(maxVisible / 2);

            let start = Math.max(1, this.lineCurrentPage - halfVisible);
            let end = Math.min(this.lineTotalPages, start + maxVisible - 1);

            if (end - start + 1 < maxVisible) {
                start = Math.max(1, end - maxVisible + 1);
            }

            if (start > 1) {
                pages.push(1);
                if (start > 2) pages.push('...');
            }

            for (let i = start; i <= end; i++) {
                pages.push(i);
            }

            if (end < this.lineTotalPages) {
                if (end < this.lineTotalPages - 1) pages.push('...');
                pages.push(this.lineTotalPages);
            }

            return pages;
        },

        async lineGoToPage(page) {
            if (page >= 1 && page <= this.lineTotalPages && page !== this.lineCurrentPage) {
                this.lineCurrentPage = page;
                await this.lineFetchData();
            }
        },

    };
}

/**
 * Checks if a line contains a specific whole word.
 * This function splits the line by any character that is NOT a Unicode letter or number,
 * effectively isolating words from all punctuation and spacing.
 * @param {string} line The text to search within.
 * @param {string} filterText The whole word to find.
 * @param {boolean} matchCase Whether the search is case-sensitive.
 * @returns {boolean} True if the whole word is found.
 */
const findWholeWord = (line, filterText, matchCase) => {
    // This regex splits the line by one or more characters that are NOT Unicode letters or numbers.
    // The 'u' flag is essential for this to work with languages like Russian.
    const words = line.split(/[^\p{L}\p{N}]+/u);

    if (matchCase) {
        // Check if any of the resulting words are an exact match.
        return words.includes(filterText);
    } else {
        // For case-insensitive search, convert both to lower case before comparing.
        const lowerFilter = filterText.toLowerCase();
        return words.some(word => word.toLowerCase() === lowerFilter);
    }
};