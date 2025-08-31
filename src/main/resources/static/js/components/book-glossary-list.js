/**
 * Creates an Alpine.js data object with glossary list functionality
 */
export function glossaryListApp() {
    return {
        glossaryList: [],
        glossaryListBackup: [],
        glossaryObject: {
            glossaryTranslationFilter: 'all',
            glossaryFilterTerm: '',
            glossaryCurrentPage: 0,
            glossaryPageSize: 50,
            glossaryTotalPages: 0,
            glossaryTotalElements: 0,
            glossarySortColumn: 'chapterNumber',
            glossarySortDirection: 'asc',
            glossaryActiveTab: 'search',
            glossaryStartChapter: 1,
            glossaryEndChapter: 10
        },
        glossaryDebounceTimers: {},
        glossarySaveStates: {},
        glossaryChangeLoader: {},

        initGlossaryList() {
            this.loadValue('glossaryObject', this.glossaryObject);
            this.fetchGlossary();
        },

        async fetchGlossary() {
            if(!this.activeId) return;

            let params;

            // Handle different fetching strategies based on activeTab
            if (this.glossaryObject.glossaryActiveTab === 'range') {
                // For range tab, use startChapter and endChapter in queryParams
                params = new URLSearchParams({
                    page: this.glossaryObject.glossaryCurrentPage,
                    size: this.glossaryObject.glossaryPageSize,
                    fromChapter: this.glossaryObject.glossaryStartChapter,
                    toChapter: this.glossaryObject.glossaryEndChapter,
                    translationMode: "chapters",
                });
            } else {
                // For search tab, use translationMode filtering
                params = new URLSearchParams({
                    page: this.glossaryObject.glossaryCurrentPage,
                    size: this.glossaryObject.glossaryPageSize
                });

                if (this.glossaryObject.glossaryTranslationFilter == 'missing') {
                    params.append('translationMode', 'missing');
                } else if (this.glossaryObject.glossaryTranslationFilter == 'translated') {
                    params.append('translationMode', 'translated');
                } else {
                    params.append('translationMode', 'all');
                }
            }

            try {
                const response = await fetch(`/api/books/${this.activeId}/glossary?${params.toString()}`);

                if (!response.ok) {
                    const rsp = await response.json();
                    console.error('Error fetching glossaryList:', rsp);
                    this.showToast(`Error: ${rsp.message || rsp.detail}`, true);
                    return;
                }

                const rsp = await response.json();
                this.glossaryObject.glossaryCurrentPage = parseInt(response.headers.get('x-current-page')) || 0;
                this.glossaryObject.glossaryTotalPages = parseInt(response.headers.get('x-total-pages')) || 1;
                this.glossaryObject.glossaryTotalElements = parseInt(response.headers.get('x-total-elements')) || 0;
                this.glossaryList = rsp;
                this.glossaryListBackup = JSON.parse(JSON.stringify(rsp));
                this.changeValue('glossaryObject', this.glossaryObject);

            } catch (error) {
                console.error('Error:', error);
                this.showToast(`Failed to fetch chapters: ${error.message || error.detail || error}`, true);
            }
        },

        get glossaryListFiltered() {
            let filtered = this.glossaryList;

            if (this.glossaryObject.glossaryFilterTerm.trim() !== '') {
                const term = this.glossaryObject.glossaryFilterTerm.toLowerCase();
                filtered = filtered.filter(glossary =>
                    glossary.name.toLowerCase().includes(term) ||
                    glossary.category.toLowerCase().includes(term) ||
                    glossary.ruName.toLowerCase().includes(term) ||
                    glossary.chapterNumber.toString().includes(term)
                );
            }

            filtered.sort((a, b) => {
                let comparison = 0;

                if (this.glossaryObject.glossarySortColumn === 'chapterNumber') {
                    comparison = a.chapterNumber - b.chapterNumber;
                } else {
                    const aValue = a[this.glossaryObject.glossarySortColumn].toLowerCase();
                    const bValue = b[this.glossaryObject.glossarySortColumn].toLowerCase();

                    if (aValue < bValue) {
                        comparison = -1;
                    } else if (aValue > bValue) {
                        comparison = 1;
                    }
                }

                return this.glossaryObject.glossarySortDirection === 'asc' ? comparison : -comparison;
            });

            return filtered;
        },

        sortGlossaryBy(column) {
            if (this.glossaryObject.glossarySortColumn === column) {
                this.glossaryObject.glossarySortDirection = this.glossaryObject.glossarySortDirection === 'asc' ? 'desc' : 'asc';
            } else {
                this.glossaryObject.glossarySortColumn = column;
                this.glossaryObject.glossarySortDirection = 'asc';
            }
        },

        changeGlossaryPage(page) {
            if (page >= 0 && page < this.glossaryObject.glossaryTotalPages) {
                this.glossaryObject.glossaryCurrentPage = page;
                this.fetchGlossary();
            }
        },

        get glossaryPages() {
            const pages = [];
            const maxVisiblePages = 5;

            let startPage = Math.max(0, this.glossaryObject.glossaryCurrentPage - Math.floor(maxVisiblePages / 2));
            let endPage = Math.min(this.glossaryObject.glossaryTotalPages - 1, startPage + maxVisiblePages - 1);

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

        async updateGlossaryChanges(glossary) {
            this.glossaryChangeLoader[glossary.id] = true;
            try {
                const response = await fetch(`/api/chapters/${glossary.chapterId}/glossary`, {
                    method: 'PATCH',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify(glossary)
                });

                if (response.status === 204) {
                    this.setSaveState(glossary.id, 'success');
                } else {
                    this.setSaveState(glossary.id, 'error');
                    const rsp = await response.json();
                    console.error('Error saving glossary:', rsp);
                    this.showToast(`Error: ${rsp.message || rsp.detail}`, true);
                }
            } catch (error) {
                this.setSaveState(glossary.id, 'error');
                console.error('Error:', error);
                this.showToast(`Failed to save changes: ${error.message || error.detail || error}`, true);
            } finally {
                setTimeout(() => {
                    this.glossaryChangeLoader[glossary.id] = false;
                }, 100);
            }
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

        startEditName(glossary) {
            glossary.editingName = true;
            glossary.nameEdit = glossary.name;
        },

        async updateGlossaryName(glossary) {
            const { editingName, nameEdit, ...localGlossary } = glossary || {};
            if (nameEdit && nameEdit.trim()) {
                localGlossary.newName = nameEdit.trim();
                this.cancelEditName(glossary);

                await this.updateGlossaryChanges(localGlossary);
                glossary.name = localGlossary.newName;
            } else {
                this.cancelEditName(glossary);
            }
        },

        cancelEditName(glossary) {
            glossary.editingName = false;
            glossary.nameEdit = '';
        },

    };
}