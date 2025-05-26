/**
 * Creates an Alpine.js data object with titles titles functionality
 */
export function titleListApp() {
    return {
        titles: [],
        titlesBackup: [],
        titlesTranslationFilter: 'all',
        titleFilterTerm: '',
        titleCurrentPage: 0,
        titlePageSize: 20,
        titleTotalPages: 0,
        titleTotalElements: 0,
        titleSortColumn: 'number',
        titleSortDirection: 'asc',
        debounceTimers: {},

        initTitleList() {
            this.loadValue('titlesTranslationFilter', 'all');
            this.fetchTitles();
        },

        fetchTitles() {
            const params = new URLSearchParams({
                page: this.titleCurrentPage,
                size: this.titlePageSize,
                missingTranslation: (this.titlesTranslationFilter === 'missing'),
                aberrationTranslation: (this.titlesTranslationFilter === 'aberration'),
            });

            if (!(this.titlesTranslationFilter in ['missing', 'aberration'])) {
                params.append('allTitles', 'true');
            }

            fetch(`/api/books/${this.activeId}/chapters-titles?${params.toString()}`)
                .then(response => response.json()
                   .then(rsp => {
                      if (!response.ok) {
                          console.error('Error fetching titles:', rsp);
                          this.showToast(`Error: ${rsp.message || rsp.detail}`, true);
                      } else {
                          this.titleCurrentPage = parseInt(response.headers.get('x-current-page')) || 0;
                          this.titleTotalPages = parseInt(response.headers.get('x-total-pages')) || 1;
                          this.titleTotalElements = parseInt(response.headers.get('x-total-elements')) || 0;
                          this.titles = rsp;
                          this.titlesBackup = JSON.parse(JSON.stringify(rsp));
                      }
                }))
                .catch(error => {
                    console.error('Error:', error);
                    this.showToast(`Failed to fetch chapters: ${error.message || error.detail || error}`, true);
                });
        },

        get titlesFiltered() {
            let filtered = this.titles;

            if (this.titleFilterTerm.trim() !== '') {
                const term = this.titleFilterTerm.toLowerCase();
                filtered = filtered.filter(title =>
                    title.title.toLowerCase().includes(term) ||
                    title.translatedTitle.toLowerCase().includes(term) ||
                    title.number.toString().includes(term)
                );
            }

            filtered.sort((a, b) => {
                let comparison = 0;

                if (this.titleSortColumn === 'number') {
                    comparison = a.number - b.number;
                } else {
                    const aValue = a[this.titleSortColumn].toLowerCase();
                    const bValue = b[this.titleSortColumn].toLowerCase();

                    if (aValue < bValue) {
                        comparison = -1;
                    } else if (aValue > bValue) {
                        comparison = 1;
                    }
                }

                return this.titleSortDirection === 'asc' ? comparison : -comparison;
            });

            return filtered;
        },

        sortTitleBy(column) {
            if (this.titleSortColumn === column) {
                this.titleSortDirection = this.titleSortDirection === 'asc' ? 'desc' : 'asc';
            } else {
                this.titleSortColumn = column;
                this.titleSortDirection = 'asc';
            }
        },

        changeTitlePage(page) {
            if (page >= 0 && page < this.titleTotalPages) {
                this.titleCurrentPage = page;
                this.fetchTitles();
            }
        },

        get titlePages() {
            const pages = [];
            const maxVisiblePages = 5;

            let startPage = Math.max(0, this.titleCurrentPage - Math.floor(maxVisiblePages / 2));
            let endPage = Math.min(this.titleTotalPages - 1, startPage + maxVisiblePages - 1);

            // Adjust start page if we're near the end
            if (endPage - startPage < maxVisiblePages - 1) {
                startPage = Math.max(0, endPage - maxVisiblePages + 1);
            }

            for (let i = startPage; i <= endPage; i++) {
                pages.push(i);
            }

            return pages;
        },

        saveTitleChanges(title) {
            // Check if title has actually changed by comparing with backup
            const originalTitle = this.titlesBackup.find(t => t.id === title.id);
            if (!originalTitle) return;

            const hasChanges = originalTitle.number !== title.number ||
                               originalTitle.title !== title.title ||
                               originalTitle.translatedTitle !== title.translatedTitle;

            if (!hasChanges) return;

            // Update backup with current values
            const index = this.titlesBackup.findIndex(t => t.id === title.id);
            if (index !== -1) {
                this.titlesBackup[index] = JSON.parse(JSON.stringify(title));
            }

            // Clear any existing timer for this title
            if (this.debounceTimers[title.id]) {
                clearTimeout(this.debounceTimers[title.id]);
            }

            // Set new timer (debounce to avoid too many requests)
            this.debounceTimers[title.id] = setTimeout(() => {
                this.updateTitleChanges(title);
                delete this.debounceTimers[title.id];
            }, 500);
        },

        updateTitleChanges(title) {
            fetch(`/api/chapters/${title.id}/title`, {
                method: 'PATCH',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(title)
            })
            .then(response => {
                if (response.status !== 204) {
                    response.json()
                      .then(rsp => {
                        console.error('Error saving title:', rsp);
                        this.showToast(`Error: ${rsp.message || rsp.detail}`, true);
                      });
                }
            })
            .catch(error => {
                console.error('Error:', error);
                this.showToast(`Failed to save changes: ${error.message || error.detail || error}`, true);
            });
        },

        async handleTranslateTitle(title) {
            if (!title.title || title.title.trim() === '') {
                return;
            }

            try {
                // Call your translation API
                const data = await this.translateToRussian(title.title);

                // Update the translated title
                title.translatedTitle = data;

                // Save the changes
                this.saveTitleChanges(title);

                this.showToast('Translation completed', false);
            } catch (error) {
                console.error('Translation error:', error);
                this.showToast(`Translation failed: ${error.message}`, true);
            }
        },
    };
}