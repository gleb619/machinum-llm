/**
 * Creates an Alpine.js data object with list functionality
 */
export function lineListApp() {
    return {
        lines: [],
        lineSearchQuery: '',
        lineFilter: '',
        editingLineId: null,
        lineFindText: '',
        lineReplaceWith: '',


        initLineList() {
            this.loadValue('lineSearchQuery', '');
            this.loadValue('lineReplaceWith', '');

            const qLineSearchQuery = this.fromSearchParams(window.location.search).get('lineSearchQuery');
            if(qLineSearchQuery) {
                this.lineSearchQuery = qLineSearchQuery;
                setTimeout(() => {
                    this.fetchSimilarLines(this.lineSearchQuery, [this.selectedField]);
                }, 10);
            }
        },

        get filteredLines() {
            if (!this.lineFilter) return this.lines;
            return this.lines.filter(line =>
                line.originalLine.toLowerCase().includes(this.lineFilter.toLowerCase()) ||
                line.translatedLine.toLowerCase().includes(this.lineFilter.toLowerCase())
            );
        },

        fetchChapterLines(chapterId) {
            return fetch(`/api/chapters/${chapterId}/lines`)
                .then(response => response.json())
                .then(data => {
                    this.lines = data;
                })
                .catch(error => {
                    console.error('Error fetching lines:', error)
                    this.showToast(`Error: ${error.message || error.code}`, true);
                });
        },

        fetchSimilarLines(line, fields) {
            this.lineSearchQuery = line;

            const chapterId = this.currentChapter?.id || this.activeId;
            const url = chapterId ? `/api/chapters/${chapterId}/lines/similar` : `/api/books/${this.filters.bookId}/lines/similar`

            return fetch(url, {
                    method: 'POST',
                    headers: {
                      "Content-Type": "application/json",
                    },
                    body: JSON.stringify({
                        fields: fields,
                        line: line
                    }),
                })
                .then(response => response.json())
                .then(data => {
                    this.lines = data;
                })
                .catch(error => {
                    console.error('Error fetching lines:', error)
                    this.showToast(`Error: ${error.message || error.code}`, true);
                });
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
                        this.lines.splice(index, 1);
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
            if(!this.lines || this.lines.length == 0) {
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
                        ids: this.lines.map(line => line.id)
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
                        this.lines = [];
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
            if (!this.lineFindText || !this.lineReplaceWith) return;
    
            fetch(`/api/lines/replace`, {
                method: 'POST',
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    find: this.lineFindText,
                    replace: this.lineReplaceWith
                })
            })
            .then(response => response.json())
            .then(data => {
                this.lines = data;
                this.showToast('Replacement successful!');
            })
            .catch(error => {
                console.error('Error replacing lines:', error);
                this.showToast(`Error: ${error.message || error.code}`, true);
            });
        },
    
        editLine(lineId) {
            this.editingLineId = lineId;
            const line = this.lines.find(l => l.id === lineId);
            line.originalLineContent = line.originalLine;
            line.translatedLineContent = line.translatedLine;
        },
    
        saveLine(lineId, originalText, translatedText) {
            const line = this.lines.find(l => l.id === lineId);
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

            fetch(`/api/lines/${lineId}`, {
                method: 'PATCH',
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify(request)
            })
            .then(response => {
                this.editingLineId = null;
                if (!response.ok) {
                    response.json()
                        .then(rsp => {
                            if (!rsp.ok) {
                                console.error('Error updating line:', rsp);
                                this.showToast(`Error: ${rsp.message || rsp.detail}`, true);
                            }
                        });
                } else {
                    this.removeById(this.lines, lineId);
                    this.showToast('Line updated successfully!');
                    this.pullChapterContentChangesById(this.line.chapterId);
                }
            })
            .catch(error => {
                console.error('Error updating line:', error);
                this.showToast(`Error: ${error.message || error.code}`, true);
            });
        },

    };
}