/**
 * Creates an Alpine.js data object with titles titles functionality
 */
export function titleListApp() {
    return {
        titles: [],
        titlesBackup: [],
        //titlesFiltered: [],
        titleFilterTerm: '',
        titleCurrentPage: 0,
        titlePageSize: 20,
        titleTotalPages: 0,
        titleTotalElements: 0,
        titleSortColumn: 'number',
        titleSortDirection: 'asc',
        titleEditingIndex: null,
        titleEditBackup: null,

        initTitleList() {
            this.fetchTitles();
        },

        fetchTitles() {
            fetch(`/api/books/${this.activeId}/chapters-titles?page=${this.titleCurrentPage}&size=${this.titlePageSize}`)
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

        editTitle(index) {
            this.titleEditingIndex = index;
            this.titleEditBackup = JSON.parse(JSON.stringify(this.titlesFiltered[index]));
        },

        saveEdit(title) {
            this.titleEditingIndex = null;
            this.titleEditBackup = null;
            this.saveTitleChanges(title);
        },

        cancelEdit() {
            if (this.titleEditBackup && this.titleEditingIndex !== null) {
                this.titlesFiltered[this.titleEditingIndex] = this.titleEditBackup;
            }
            this.titleEditingIndex = null;
            this.titleEditBackup = null;
        },

        saveTitleChanges(title) {
            fetch(`/api/chapters/${title.id}/title`, {
                method: 'PATCH',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(title)
            })
            .then(response => response.json()
                .then(rsp => {
                    if (response.status != 204) {
                        console.error('Error fetching chapters:', rsp);
                        this.showToast(`Error: ${rsp.message || rsp.detail}`, true);
                    }
                }))
            .catch(error => {
                console.error('Error:', error);
                this.showToast(`Failed to save changes: ${error.message || error.detail || error}`, true);
            });
        }
    };
}