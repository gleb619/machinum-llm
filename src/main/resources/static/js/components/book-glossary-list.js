/**
 * Creates an Alpine.js data object with glossary list functionality
 */
export function glossaryListApp() {
    return {
        glossaryList: [],
        glossaryListBackup: [],
        glossaryTranslationFilter: 'all',
        glossaryFilterTerm: '',
        glossaryCurrentPage: 0,
        glossaryPageSize: 50,
        glossaryTotalPages: 0,
        glossaryTotalElements: 0,
        glossarySortColumn: 'chapterNumber',
        glossarySortDirection: 'asc',
        glossaryDebounceTimers: {},
        glossarySaveStates: {},

        initGlossaryList() {
            this.loadValue('glossaryTranslationFilter', 'all');
            this.loadValue('glossaryCurrentPage', 0);
            this.fetchGlossary();
        },

        fetchGlossary() {
            if(!this.activeId) return;

            const params = new URLSearchParams({
                page: this.glossaryCurrentPage,
                size: this.glossaryPageSize
            });

            if (this.glossaryTranslationFilter == 'missing') {
                params.append('translationMode', '0');
            } else if (this.glossaryTranslationFilter == 'translated') {
                params.append('translationMode', '1');
            } else {
                params.append('allGlossary', 'true');
            }

            fetch(`/api/books/${this.activeId}/glossary?${params.toString()}`)
                .then(response => response.json()
                   .then(rsp => {
                      if (!response.ok) {
                          console.error('Error fetching glossaryList:', rsp);
                          this.showToast(`Error: ${rsp.message || rsp.detail}`, true);
                      } else {
                          this.glossaryCurrentPage = parseInt(response.headers.get('x-current-page')) || 0;
                          this.glossaryTotalPages = parseInt(response.headers.get('x-total-pages')) || 1;
                          this.glossaryTotalElements = parseInt(response.headers.get('x-total-elements')) || 0;
                          this.glossaryList = rsp;
                          this.glossaryListBackup = JSON.parse(JSON.stringify(rsp));
                          this.changeValue('glossaryCurrentPage', this.glossaryCurrentPage);
                      }
                }))
                .catch(error => {
                    console.error('Error:', error);
                    this.showToast(`Failed to fetch chapters: ${error.message || error.detail || error}`, true);
                });
        },

        get glossaryListFiltered() {
            let filtered = this.glossaryList;

            if (this.glossaryFilterTerm.trim() !== '') {
                const term = this.glossaryFilterTerm.toLowerCase();
                filtered = filtered.filter(glossary =>
                    glossary.name.toLowerCase().includes(term) ||
                    glossary.category.toLowerCase().includes(term) ||
                    glossary.ruName.toLowerCase().includes(term) ||
                    glossary.chapterNumber.toString().includes(term)
                );
            }

            filtered.sort((a, b) => {
                let comparison = 0;

                if (this.glossarySortColumn === 'chapterNumber') {
                    comparison = a.chapterNumber - b.chapterNumber;
                } else {
                    const aValue = a[this.glossarySortColumn].toLowerCase();
                    const bValue = b[this.glossarySortColumn].toLowerCase();

                    if (aValue < bValue) {
                        comparison = -1;
                    } else if (aValue > bValue) {
                        comparison = 1;
                    }
                }

                return this.glossarySortDirection === 'asc' ? comparison : -comparison;
            });

            return filtered;
        },

        sortGlossaryBy(column) {
            if (this.glossarySortColumn === column) {
                this.glossarySortDirection = this.glossarySortDirection === 'asc' ? 'desc' : 'asc';
            } else {
                this.glossarySortColumn = column;
                this.glossarySortDirection = 'asc';
            }
        },

        changeGlossaryPage(page) {
            if (page >= 0 && page < this.glossaryTotalPages) {
                this.glossaryCurrentPage = page;
                this.fetchGlossary();
            }
        },

        get glossaryPages() {
            const pages = [];
            const maxVisiblePages = 5;

            let startPage = Math.max(0, this.glossaryCurrentPage - Math.floor(maxVisiblePages / 2));
            let endPage = Math.min(this.glossaryTotalPages - 1, startPage + maxVisiblePages - 1);

            // Adjust start page if we're near the end
            if (endPage - startPage < maxVisiblePages - 1) {
                startPage = Math.max(0, endPage - maxVisiblePages + 1);
            }

            for (let i = startPage; i <= endPage; i++) {
                pages.push(i);
            }

            return pages;
        },
        
        getSaveStateClass(glossaryId) {
            const state = this.glossarySaveStates[glossaryId];
            switch(state) {
                case 'saving': return 'bg-amber-50';
                case 'success': return 'bg-green-50';
                case 'error': return 'bg-red-50';
                default: return '';
            }
        },
    
        getSaveBorderClass(glossaryId) {
            const state = this.glossarySaveStates[glossaryId];
            switch(state) {
                case 'saving': return 'border-amber-400 animate-pulse';
                case 'success': return 'border-green-500 animate-ping';
                case 'error': return 'border-red-500 animate-pulse';
                default: return '';
            }
        },
    
        setSaveState(glossaryId, state) {
            this.glossarySaveStates[glossaryId] = state;

            if (state === 'success' || state === 'error') {
                setTimeout(() => {
                    this.glossarySaveStates[glossaryId] = null;
                }, 500);
            }
        },

        saveGlossaryChanges(glossary) {
            // Check if glossary has actually changed by comparing with backup
            const originalGlossary = this.glossaryListBackup.find(t => t.id === glossary.id);
            if (!originalGlossary) return;

            const hasChanges = originalGlossary.chapterNumber !== glossary.chapterNumber ||
                               originalGlossary.name !== glossary.name ||
                               originalGlossary.ruName !== glossary.ruName;

            if (!hasChanges) return;

            // Update backup with current values
            const index = this.glossaryListBackup.findIndex(t => t.id === glossary.id);
            if (index !== -1) {
                this.glossaryListBackup[index] = JSON.parse(JSON.stringify(glossary));
            }

            // Clear any existing timer for this glossary
            if (this.glossaryDebounceTimers[glossary.id]) {
                clearTimeout(this.glossaryDebounceTimers[glossary.id]);
            }

            this.setSaveState(glossary.id, 'saving');

            // Set new timer (debounce to avoid too many requests)
            this.glossaryDebounceTimers[glossary.id] = setTimeout(() => {
                this.updateGlossaryChanges(glossary);
                delete this.glossaryDebounceTimers[glossary.id];
            }, 500);
        },

        updateGlossaryChanges(glossary) {
            fetch(`/api/chapters/${glossary.chapterId}/glossary`, {
                method: 'PATCH',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(glossary)
            })
            .then(response => {
                if (response.status === 204) {
                    this.setSaveState(glossary.id, 'success');
                } else {
                    this.setSaveState(glossary.id, 'error');
                    response.json()
                      .then(rsp => {
                        console.error('Error saving glossary:', rsp);
                        this.showToast(`Error: ${rsp.message || rsp.detail}`, true);
                      });
                }
            })
            .catch(error => {
                this.setSaveState(glossary.id, 'error');
                console.error('Error:', error);
                this.showToast(`Failed to save changes: ${error.message || error.detail || error}`, true);
            });
        },

        async handleTranslateGlossary(glossary) {
            if (!glossary.glossary || glossary.glossary.trim() === '') {
                return;
            }

            try {
                // Call your translation API
                const data = await this.translateToRussian(glossary.glossary);

                // Update the translated glossary
                glossary.ruName = data;

                // Save the changes
                this.saveGlossaryChanges(glossary);

                this.showToast('Translation completed', false);
            } catch (error) {
                console.error('Translation error:', error);
                this.showToast(`Translation failed: ${error.message}`, true);
            }
        },
    };
}