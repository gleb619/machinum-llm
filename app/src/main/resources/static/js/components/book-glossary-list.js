/**
 * Creates an Alpine.js data object with glossary list functionality
 */
export function glossaryListApp() {
    return {
        glossaryList: [],
        glossaryListBackup: [],
        favoriteGlossaryId: null,
        glossaryObjects: {},
        glossaryDebounceTimers: {},
        glossarySaveStates: {},
        glossaryChangeLoader: {},

        get glossaryObject() {
            // Ensure we have a glossary object for the current activeId
            if (!this.glossaryObjects[this.activeId]) {
                this.glossaryObjects[this.activeId] = {
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
                };
            }

            return this.glossaryObjects[this.activeId];
        },

        initGlossaryList() {
            // Only initialize if we have an activeId
            if (!this.activeId) {
                return;
            }

            // Migrate old data format to new book-specific format
            const bookData = localStorage.getItem('glossary-' + this.activeId);
            if (bookData) {
                try {
                    this.glossaryObjects[this.activeId] = JSON.parse(bookData);
                } catch (e) {
                    console.warn('Failed to load glossary data for book', this.activeId, e);
                    // Initialize with defaults
                    delete this.glossaryObjects[this.activeId];
                }
            }

            this.favoriteGlossaryId = this.readSetting('favorite-' + this.activeId) || null;
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
                    fetchMode: "chapters",
                });
            } else if (this.glossaryObject.glossaryActiveTab === 'marked') {
                // For marked tab, get only marked items
                params = new URLSearchParams({
                    page: this.glossaryObject.glossaryCurrentPage,
                    size: this.glossaryObject.glossaryPageSize,
                    fetchMode: "marked",
                });
            } else {
                // For search tab, use fetchMode filtering
                params = new URLSearchParams({
                    page: this.glossaryObject.glossaryCurrentPage,
                    size: this.glossaryObject.glossaryPageSize
                });

                if (this.glossaryObject.glossaryTranslationFilter == 'missing') {
                    params.append('fetchMode', 'missing');
                } else if (this.glossaryObject.glossaryTranslationFilter == 'translated') {
                    params.append('fetchMode', 'translated');
                } else {
                    params.append('fetchMode', 'all');
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
                this.glossaryList = rsp.map(item => {
                    item.details = {};
                    return item;
                });
                this.glossaryListBackup = JSON.parse(JSON.stringify(rsp));
                this.changeValue('glossary-' + this.activeId, this.glossaryObject);

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

        async saveGlossaryChanges(glossary) {
            // Check if glossary has actually changed by comparing with backup
            const originalGlossary = this.glossaryListBackup.find(t => t.id === glossary.id);
            if (!originalGlossary) return;

            const hasChanges = originalGlossary.chapterNumber !== glossary.chapterNumber ||
                               originalGlossary.name !== glossary.name ||
                               originalGlossary.ruName !== glossary.ruName;

            if (!hasChanges) return;

            const { details, ...localGlossary } = glossary || {};

            // Update backup with current values
            const index = this.glossaryListBackup.findIndex(t => t.id === localGlossary.id);
            if (index !== -1) {
                this.glossaryListBackup[index] = JSON.parse(JSON.stringify(localGlossary));
            }

            // Clear any existing timer for this glossary
            if (this.glossaryDebounceTimers[localGlossary.id]) {
                clearTimeout(this.glossaryDebounceTimers[localGlossary.id]);
            }

            this.setSaveState(localGlossary.id, 'saving');

            // Set new timer (debounce to avoid too many requests)
            return new Promise((resolve) => {
                this.glossaryDebounceTimers[localGlossary.id] = setTimeout(async () => {
                    try {
                        await this.updateGlossaryChanges(localGlossary);
                        delete this.glossaryDebounceTimers[localGlossary.id];
                        resolve();
                    } catch (error) {
                        delete this.glossaryDebounceTimers[localGlossary.id];
                        reject(error);
                    }
                }, 500);
            });
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
            if (!glossary.name || glossary.name.trim() === '') {
                return;
            }

            // Set loading state
            glossary.details = glossary.details || {};
            glossary.details.translating = true;

            try {
                // Call your translation API
                const data = await this.translateToRussian(glossary.name);

                // Clean the response (remove markdown formatting if any)
                const cleanData = data ? data.replace(/^```[\s\S]*?```\s*$/gm, '').trim() : '';

                // Update the translated glossary
                glossary.ruName = cleanData;

                // Save the changes
                await this.saveGlossaryChanges(glossary);

                this.showToast('Translation completed', false);
            } catch (error) {
                console.error('Translation error:', error);
                this.showToast(`Translation failed: ${error.message || error.detail || error}`, true);
            } finally {
                // Clear loading state
                glossary.details.translating = false;
            }
        },

        startEditName(glossary) {
            glossary.details.editingName = true;
            glossary.details.nameEdit = glossary.name;
        },

        async updateGlossaryName(glossary) {
            const { details, ...localGlossary } = glossary || {};
            if (details.nameEdit && details.nameEdit.trim()) {
                localGlossary.newName = details.nameEdit.trim();
                this.cancelEditName(glossary);

                await this.updateGlossaryChanges(localGlossary);
                glossary.name = localGlossary.newName;
            } else {
                this.cancelEditName(glossary);
            }

            // Return a promise that resolves when the operation completes
            return Promise.resolve();
        },

        cancelEditName(glossary) {
            glossary.details.editingName = false;
            glossary.details.nameEdit = '';
        },

        setFavoriteGlossary(glossary) {
            if (glossary.id === this.favoriteGlossaryId) {
                this.favoriteGlossaryId = null;
                this.writeSetting('favorite-' + this.activeId, '');
            } else {
                this.favoriteGlossaryId = glossary.id;
                this.writeSetting('favorite-' + this.activeId, glossary.id);
            }
        },

        async refreshGlossaryItemsFromChapters(affectedChapterIds) {
            if (!affectedChapterIds || affectedChapterIds.length === 0) return;

            try {
                // Build query parameters for the chapterIds
                const params = new URLSearchParams();
                affectedChapterIds.forEach(chapterId => {
                    params.append('chapterIds', chapterId);
                });

                const response = await fetch(`/api/books/${this.activeId}/chapters-glossary?${params.toString()}`);

                if (!response.ok) {
                    console.error('Error fetching updated glossary items:', response);
                    return;
                }

                const updatedGlossaryItems = await response.json();

                // Update glossary items in the list that match the updated items
                // Match by name and category since glossary items are unique by name+category per chapter
                updatedGlossaryItems.forEach(updatedItem => {
                    const existingIndex = this.glossaryList.findIndex(item =>
                        item.name === updatedItem.name &&
                        item.category === updatedItem.category &&
                        affectedChapterIds.includes(item.chapterId)
                    );

                    if (existingIndex >= 0) {
                        // Preserve details and other UI state, only update the backend data
                        const existingDetails = {...this.glossaryList[existingIndex].details};
                        this.glossaryList[existingIndex] = updatedItem;
                        this.glossaryList[existingIndex].details = existingDetails;
                    }
                });

                this.showToast('Glossary list updated with new translations', false);

            } catch (error) {
                console.error('Error refreshing glossary items:', error);
                this.showToast('Failed to refresh glossary items', true);
            }
        },

        async refreshGlossaryItem(glossaryId) {
            const response = await fetch(`/api/glossary/${glossaryId}`);
            if (!response.ok) {
                console.error('Error fetching updated glossary items:', response);
                return;
            }

            const updatedGlossary = await response.json();
            const existingIndex = this.glossaryList.findIndex(item => item.id === glossaryId);
            if (existingIndex >= 0) {
                // Preserve details and other UI state, only update the backend data
                const existingDetails = {...this.glossaryList[existingIndex].details};
                this.glossaryList[existingIndex] = updatedGlossary;
                this.glossaryList[existingIndex].details = existingDetails;
            }
        },

        async toggleGlossaryMark(glossary) {
            if (!this.activeId) return;

            try {
                const newMarkedState = !glossary?.marked;

                const response = await fetch(`/api/books/${this.activeId}/glossary/${glossary.id}/properties`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({
                        field: 'mark',
                        value: newMarkedState
                    })
                });

                if (!response.ok) {
                    const rsp = await response.json();
                    console.error('Error toggling glossary mark:', rsp);
                    this.showToast(`Error: ${rsp.message || rsp.detail}`, true);
                    return;
                }

                glossary.marked = newMarkedState;
            } catch (error) {
                console.error('Error:', error);
                this.showToast(`Failed to toggle mark: ${error.message || error.detail || error}`, true);
            }
        }

    };
}
