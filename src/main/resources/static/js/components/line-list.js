/**
 * Creates an Alpine.js data object with list functionality
 */
export function lineListApp() {
    return {
        lineObject: {
            lines: [],
            lineSearchQuery: '',
            lineFilter: '',
            editingLineId: null,
            lineFindText: '',
            lineReplaceWith: '',
            currentScope: 'chapter',
            selectedField: 'text',
            lineMatchCase: false,
            lineMatchWholeWord: false,
            lineUseRegex: false,
        },


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
            return this.lineObject.lines.filter(line =>
                line.originalLine.toLowerCase().includes(this.lineObject.lineFilter.toLowerCase()) ||
                line.translatedLine.toLowerCase().includes(this.lineObject.lineFilter.toLowerCase())
            );
        },

        fetchChapterLines(chapterId) {
            return fetch(`/api/chapters/${chapterId}/lines`)
                .then(response => response.json())
                .then(data => {
                    this.lineObject.lines = data;
                })
                .catch(error => {
                    console.error('Error fetching lines:', error)
                    this.showToast(`Error: ${error.message || error.code}`, true);
                });
        },

        async fetchSimilarLines(line, fields) {

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

                if(response.ok) {
                    this.lineObject.lines = data;
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
        
        performReplace() {
            if (!this.lineObject.lineSearchQuery || !this.lineObject.lineReplaceWith) return;
    
            fetch(`/api/books/${this.filters.bookId}/lines/replace`, {
                method: 'POST',
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    find: this.lineObject.lineSearchQuery,
                    replace: this.lineObject.lineReplaceWith,
                    ids: this.filteredLines.map(line => line.id)
                })
            })
            .then(response => response.json())
            .then(data => {
                this.lineObject.lines = data;
                this.showToast('Replacement successful!');
            })
            .catch(error => {
                console.error('Error replacing lines:', error);
                this.showToast(`Error: ${error.message || error.code}`, true);
            });
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

            if(Object.keys(request).length === 0) return;

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
                    }
                } else {
                    this.removeById(this.lineObject.lines, lineId);
                    this.showToast('Line updated successfully!');
                    await this.pullChapterContentChangesById(line.chapterId);
                }
            } catch (error) {
                console.error('Error updating line:', error);
                this.showToast(`Error: ${error.message || error.code}`, true);
            }
        },

        highlightLineMatch(text, query) {
            if (!text || !query) return text;

            const escapedQuery = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
            const regex = new RegExp(`(${escapedQuery})`, 'gi');

            return text.replace(regex, '<mark class="bg-yellow-200">$1</mark>');
        }

    };
}