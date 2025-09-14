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
            lineUseRegex: false
        },

        initGlossaryDetails() {
            this.loadValue('glossaryConfigTabs', this.glossaryConfigTabs);
        },

        async toggleGlossaryDetails(glossary) {
            glossary.details.expanded = !glossary.details.expanded;
            glossary.details.searchText = glossary.name;
            glossary.details.lineSearchTerm = glossary.name;
            this.glossary.details.replaceTextSearch = glossary.name;
            this.glossary.details.replaceSummarySearch = glossary.name;

            if (glossary.details.expanded && !glossary.details.relatedItems) {
                this.fetchRelatedLines(glossary);
                const relatedItems = await this.fetchRelatedGlossaryItems(glossary);
                if(relatedItems) {
                    glossary.details.compareData = this.glossaryCompareItems(glossary, relatedItems[0]);
                }
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
                const relatedItems = await response.json();
                glossary.details.relatedItems = [...relatedItems];
                return Promise.resolve([...relatedItems]);
            } catch (error) {
                console.error('Error fetching related items:', error);
                this.showToast(`Failed to fetch related items: ${error.message || error.detail || error}`, true);
                return Promise.reject(error);
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

        async replaceGlossaryText() {
            const {replaceTextSearch, replaceTextReplacement} = this.glossary.details;
            if (!replaceTextSearch || !replaceTextReplacement) {
                this.showToast('Book ID, Search Term and Replacement Text are required', true);
                return Promise.resolve();
            }

            try {
                const response = await fetch(`/api/books/${this.activeId}/replace-text`, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({bookId: this.activeId, replaceTextSearch, replaceTextReplacement})
                });

                if (!response.ok) {
                    const error = await response.json();
                    this.showToast(`Error: ${error.message || 'Failed to replace text'}`, true);
                    return Promise.resolve();
                }

                this.showToast('Text replaced successfully', false);
                return Promise.resolve();
            } catch (error) {
                console.error('Error replacing text:', error);
                this.showToast(`Failed to replace text: ${error.message || error}`, true);
                return Promise.reject(error);
            }
        },

        async replaceSummary() {
            const {replaceSummarySearch, replaceSummaryReplacement} = this.glossary.details;
            if (!replaceSummarySearch || !replaceSummaryReplacement) {
                this.showToast('Book ID, Search Term and Replacement Text are required', true);
                return Promise.resolve();
            }

            try {
                const response = await fetch(`/api/books/${this.activeId}/replace-summary`, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({bookId: this.activeId, replaceSummarySearch, replaceSummaryReplacement})
                });

                if (!response.ok) {
                    const error = await response.json();
                    this.showToast(`Error: ${error.message || 'Failed to replace summary'}`, true);
                    return Promise.resolve();
                }

                this.showToast('Summary replaced successfully', false);
                return Promise.resolve();
            } catch (error) {
                console.error('Error replacing summary:', error);
                this.showToast(`Failed to replace summary: ${error.message || error}`, true);
                return Promise.reject(error);
            }
        },

        async updateGlossaryRuName() {
            const {updateRuNameOldRuName, updateRuNameNewRuName} = this.glossary.details;
            if (!updateRuNameOldRuName || !updateRuNameNewRuName) {
                this.showToast('Book ID, Old Russian Name and New Russian Name are required', true);
                return Promise.resolve();
            }

            try {
                const response = await fetch(`/api/books/${this.activeId}/update-ru-name`, {
                    method: 'PUT',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({
                        bookId: this.activeId,
                        oldRuName: updateRuNameOldRuName,
                        newRuName: updateRuNameNewRuName,
                        returnIds: false
                    })
                });

                if (!response.ok) {
                    const error = await response.json();
                    this.showToast(`Error: ${error.message || 'Failed to update Russian name'}`, true);
                    return Promise.resolve();
                }

                this.showToast('Russian name updated successfully', false);
                return Promise.resolve();
            } catch (error) {
                console.error('Error updating Russian name:', error);
                this.showToast(`Failed to update Russian name: ${error.message || error}`, true);
                return Promise.reject(error);
            }
        },

        async changeGlossaryName(glossaryTo, glossaryFrom) {
          // Update the origin glossary
          glossaryTo.details.nameEdit = glossaryFrom.name;
        
          // Save the changes asynchronously with loading state management
          return withLoadingState(() => this.updateGlossaryName(glossaryTo), glossaryTo);
        },
        
        async changeGlossaryTranslatedName(glossaryTo, glossaryFrom) {
          // Update the translated glossary
          glossaryTo.ruName = glossaryFrom.ruName;
        
          // Save the changes with loading state management
          return withLoadingState(() => this.saveGlossaryChanges(glossaryTo), glossaryTo);
        },
        
        async changeGlossaryText(glossaryTo, glossaryFrom) {
          this.glossaryConfigTabs.activeTab = 'replaceText';
          glossaryTo.details.replaceTextSearch = glossaryTo.name;
          glossaryTo.details.replaceTextReplacement = glossaryFrom.name;
        
          // Save the changes with loading state management
          return withLoadingState(() => this.replaceGlossaryText(), glossaryTo);
        },
        
        async changeGlossarySummary(glossaryTo, glossaryFrom) {
          this.glossaryConfigTabs.activeTab = 'replaceSummary';
          glossaryTo.details.replaceSummarySearch = glossaryTo.name;
          glossaryTo.details.replaceSummaryReplacement = glossaryFrom.name;
        
          // Save the changes with loading state management
          return withLoadingState(() => this.replaceSummary(), glossaryTo);
        },

        async applyAllGlossaryChanges(glossaryTo, glossaryFrom) {
            await this.changeGlossaryName(glossaryTo, glossaryFrom);
            await this.changeGlossaryTranslatedName(glossaryTo, glossaryFrom);
            await this.changeGlossaryText(glossaryTo, glossaryFrom);
            await this.changeGlossarySummary(glossaryTo, glossaryFrom);
            this.glossaryConfigTabs.activeTab = 'current';
        },

        glossaryCompareItems(item1, item2) {
            const summary = [];

            if (item1.name === item2.name) {
                summary.push(`Both items refer to the same character: ${item1.name}`);
            } else {
                summary.push(`Comparing different entities: ${item1.name} vs ${item2.name}`);
            }

            if (item1.category === item2.category) {
                summary.push(`Same category: ${item1.category}`);
            } else {
                summary.push(`Different categories: ${item1.category} vs ${item2.category}`);
            }

            const chapterDiff = Math.abs(item1.chapterNumber - item2.chapterNumber);
            summary.push(`Chapter difference: ${chapterDiff} chapters apart`);

            if (item1.description === item2.description) {
                summary.push('Identical descriptions');
            } else {
                summary.push('Different descriptions - possible character development or different aspects');
            }

            return { item1, item2, summary };
        }

    };
}

// Utility function to handle loading state management
const withLoadingState = async function(asyncFunction, glossary) {
  try {
    glossary.details.jobLoading = true;
    const result = await asyncFunction();
    return result;
  } finally {
    glossary.details.jobLoading = false;
  }
}