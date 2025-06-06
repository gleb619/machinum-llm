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
        debounceTimers: {},

        initGlossaryList() {
            this.loadValue('glossaryTranslationFilter', 'all');
            this.fetchGlossary();
        },

        fetchGlossary() {
            if(!this.activeId) return;

            const params = new URLSearchParams({
                page: this.glossaryCurrentPage,
                size: this.glossaryPageSize,
                missingTranslation: (this.glossaryTranslationFilter === 'missing'),
            });

            if (!(this.glossaryTranslationFilter in ['missing'])) {
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

        saveGlossaryChanges(glossary) {
            // Check if glossary has actually changed by comparing with backup
            const originalGlossary = this.glossaryListBackup.find(t => t.id === glossary.id);
            if (!originalGlossary) return;

            const hasChanges = originalGlossary.chapterNumber !== glossary.chapterNumber ||
                               originalGlossary.glossary !== glossary.glossary ||
                               originalGlossary.ruName !== glossary.ruName;

            if (!hasChanges) return;

            // Update backup with current values
            const index = this.glossaryListBackup.findIndex(t => t.id === glossary.id);
            if (index !== -1) {
                this.glossaryListBackup[index] = JSON.parse(JSON.stringify(glossary));
            }

            // Clear any existing timer for this glossary
            if (this.debounceTimers[glossary.id]) {
                clearTimeout(this.debounceTimers[glossary.id]);
            }

            // Set new timer (debounce to avoid too many requests)
            this.debounceTimers[glossary.id] = setTimeout(() => {
                this.updateGlossaryChanges(glossary);
                delete this.debounceTimers[glossary.id];
            }, 500);
        },

        updateGlossaryChanges(glossary) {
            fetch(`/api/chapters/${glossary.id}/glossary`, {
                method: 'PATCH',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(glossary)
            })
            .then(response => {
                if (response.status !== 204) {
                    response.json()
                      .then(rsp => {
                        console.error('Error saving glossary:', rsp);
                        this.showToast(`Error: ${rsp.message || rsp.detail}`, true);
                      });
                }
            })
            .catch(error => {
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