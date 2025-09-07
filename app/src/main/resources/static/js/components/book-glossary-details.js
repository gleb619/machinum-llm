/**
 * Creates an Alpine.js data object with glossary details functionality
 */
export function glossaryDetailsApp() {
    return {
        glossaryConfigTabs: {
            activeTab: 'current',
            chapterStart: 1,
            chapterEnd: 999999,
            topK: 5,
            minScore: 0.1,
            lineMatchCase: false,
            lineMatchWholeWord: false,
            lineUseRegex: false,
            replaceTextSearch: '',
            replaceTextReplacement: '',
            replaceSummarySearch: '',
            replaceSummaryReplacement: '',
            updateRuNameOldRuName: '',
            updateRuNameNewRuName: '',
            updateRuNameReturnIds: false
        },

        initGlossaryDetails() {
            this.loadValue('glossaryConfigTabs', this.glossaryConfigTabs);
        },

        toggleGlossaryDetails(glossary) {
            glossary.details.expanded = !glossary.details.expanded;
            glossary.details.searchText = glossary.name;
            glossary.details.lineSearchTerm = glossary.name;
            this.glossaryConfigTabs.replaceTextSearch = glossary.name;
            this.glossaryConfigTabs.replaceSummarySearch = glossary.name;

            if (glossary.details.expanded && !glossary.details.relatedItems) {
                this.fetchRelatedGlossaryItems(glossary);
                this.fetchRelatedLines(glossary);
            }
        },

        async fetchRelatedGlossaryItems(glossary) {
            glossary.details.searching = true;
            if (!this.activeId) return;

            let {chapterStart, chapterEnd, topK, minScore} = this.glossaryConfigTabs;
            if (this.glossaryConfigTabs.activeTab !== 'current') {
                chapterStart = parseInt(chapterStart) || 1;
                chapterEnd = parseInt(chapterEnd) || 999999;
                topK = parseInt(topK) || 5;
                minScore = parseFloat(minScore) || 0.1;
            }

            const params = new URLSearchParams({
                bookId: this.activeId,
                searchText: glossary.details.searchText || glossary.name,
                chapterStart,
                chapterEnd,
                topK,
                minScore
            });

            try {
                const response = await fetch(`/api/books/${this.activeId}/glossary/search?${params.toString()}`);
                if (!response.ok) {
                    const rsp = await response.json();
                    console.error('Error fetching related glossary items:', rsp);
                    this.showToast(`Error: ${rsp.message || rsp.detail}`, true);
                    return;
                }
                glossary.details.relatedItems = await response.json();
            } catch (error) {
                console.error('Error fetching related items:', error);
                this.showToast(`Failed to fetch related items: ${error.message || error.detail || error}`, true);
            } finally {
                glossary.details.searching = false;
            }
        },

        async fetchRelatedLines(glossary) {
            if (!glossary.details.lineSearchTerm?.trim()) {
                glossary.details.relatedLines = [];
                return;
            }

            if (!this.activeId) return;

            try {
                glossary.details.loadingLines = true;

                const response = await fetch(`/api/books/${this.activeId}/lines/similar?page=0&size=10`, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({
                        fields: ["text"],
                        line: glossary.details.lineSearchTerm,
                        matchCase: false,
                        matchWholeWord: false,
                        useRegex: false
                    })
                });

                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }

                glossary.details.relatedLines = await response.json();
            } catch (error) {
                console.error('Error fetching related lines:', error);
                glossary.details.relatedLines = [];
            } finally {
                glossary.details.loadingLines = false;
            }
        },

        async replaceText() {
            const {replaceTextSearch, replaceTextReplacement} = this.glossaryConfigTabs;
            if (!replaceTextSearch || !replaceTextReplacement) {
                this.showToast('Book ID, Search Term and Replacement Text are required', true);
                return;
            }

            try {
                const response = await fetch('/replace-text', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({bookId: this.activeId, replaceTextSearch, replaceTextReplacement})
                });

                if (!response.ok) {
                    const error = await response.json();
                    this.showToast(`Error: ${error.message || 'Failed to replace text'}`, true);
                    return;
                }

                this.showToast('Text replaced successfully', false);
            } catch (error) {
                console.error('Error replacing text:', error);
                this.showToast(`Failed to replace text: ${error.message || error}`, true);
            }
        },

        async replaceSummary() {
            const {replaceSummarySearch, replaceSummaryReplacement} = this.glossaryConfigTabs;
            if (!replaceSummarySearch || !replaceSummaryReplacement) {
                this.showToast('Book ID, Search Term and Replacement Text are required', true);
                return;
            }

            try {
                const response = await fetch('/replace-summary', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({bookId: this.activeId, replaceSummarySearch, replaceSummaryReplacement})
                });

                if (!response.ok) {
                    const error = await response.json();
                    this.showToast(`Error: ${error.message || 'Failed to replace summary'}`, true);
                    return;
                }

                this.showToast('Summary replaced successfully', false);
            } catch (error) {
                console.error('Error replacing summary:', error);
                this.showToast(`Failed to replace summary: ${error.message || error}`, true);
            }
        },

        async updateGlossaryRuName() {
            const {updateRuNameOldRuName, updateRuNameNewRuName} = this.glossaryConfigTabs;
            if (!updateRuNameOldRuName || !updateRuNameNewRuName) {
                this.showToast('Book ID, Old Russian Name and New Russian Name are required', true);
                return;
            }

            try {
                const response = await fetch('/update-ru-name', {
                    method: 'PUT',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({
                        bookId: this.activeId,
                        updateRuNameOldRuName,
                        updateRuNameNewRuName,
                        returnIds: this.glossaryConfigTabs.updateRuNameReturnIds
                    })
                });

                if (!response.ok) {
                    const error = await response.json();
                    this.showToast(`Error: ${error.message || 'Failed to update Russian name'}`, true);
                    return;
                }

                this.showToast('Russian name updated successfully', false);
            } catch (error) {
                console.error('Error updating Russian name:', error);
                this.showToast(`Failed to update Russian name: ${error.message || error}`, true);
            }
        },

        changeGlossaryName(glossaryTo, glossaryFrom) {
          // Update the origin glossary
          glossaryTo.details.nameEdit = glossaryFrom.name;

          // Save the changes
          this.updateGlossaryName(glossaryTo);
        },

        changeGlossaryTranslatedName(glossaryTo, glossaryFrom) {
            // Update the translated glossary
            glossaryTo.ruName = glossaryFrom.ruName;

            // Save the changes
            this.saveGlossaryChanges(glossaryTo);
        },

    };
}