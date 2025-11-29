import { fuzzyAddon } from './fuzzy-panel.js';

/**
 * Creates an Alpine.js data object with glossary details functionality
 */
export function glossaryDetailsApp() {
    return {
        ...fuzzyAddon(),
        glossaryConfigTabs: {
            activeTab: 'config',
            chapterStart: 1,
            chapterEnd: 999999,
            topK: 20,
            minScore: 0.1,
            showConfigInputs: true,
            showSearchInputs: true,
            lineMatchCase: false,
            lineMatchWholeWord: false,
            lineUseRegex: false,
            selectedAlgorithm: 'all'
        },

        initGlossaryDetails() {
            this.loadValue('glossaryConfigTabs', this.glossaryConfigTabs);
        },

        async toggleGlossaryDetails(glossary) {
            glossary.details.expanded = !glossary.details.expanded;
            glossary.details.searchText = glossary.details.searchText || glossary.name;
            glossary.details.filterText = '';
            glossary.details.lineSearchTerm = glossary.details.lineSearchTerm || glossary.name;
            glossary.details.replaceTextSearch = glossary.name;
            glossary.details.replaceSummarySearch = glossary.name;
            glossary.details.updateRuNameOldRuName = glossary.ruName || '';
            glossary.details.updateRuNameNewRuName = '';
            glossary.details.updateRuNameNameFilter = '';
            glossary.details.updateRuNameAffectedChapters = [];

            // Initialize fuzzy mode state
            glossary.details.fuzzyTextInput = glossary.details.fuzzyTextInput || '';
            glossary.details.fuzzyPreview = glossary.details.fuzzyPreview || '';
            glossary.details.fuzzyText = glossary.details.fuzzyText || null;
            glossary.details.activeNGrams = glossary.details.activeNGrams || [];
            glossary.details.customNGramInput = glossary.details.customNGramInput || '';
            glossary.details.previewStats = glossary.details.previewStats || {
                text: '',
                length: 0,
                minLen: 0,
                maxLen: 0,
                totalNGrams: 0,
                filteredNGrams: 0,
                quality: { level: 'None' },
                prediction: 0,
                removedCount: 0,
                removalReasons: []
            };

            if (glossary.details.expanded && !glossary.details.relatedItems) {
                this.fetchRelatedLines(glossary);
                let relatedItems;

                if(this.glossaryConfigTabs.selectedAlgorithm === 'fuzzy') {
                    // Ensure fuzzy mode is properly initialized for fuzzy tab
                    if (!glossary.details.fuzzyTextInput && (glossary.details.searchText || glossary.name)) {
                        glossary.details.fuzzyTextInput = glossary.details.searchText || glossary.name;
                        this.updateFuzzyPreview(glossary);
                    }
                    relatedItems = await this.applyFuzzyFormat(glossary);
                } else {
                    relatedItems = await this.fetchRelatedGlossaryItems(glossary);
                }

                if(relatedItems) {
                    glossary.details.compareData = this.glossaryCompareItems(glossary, relatedItems[0]);
                }
            }
        },

        resetGlossaryConfig() {
            Object.assign(this.glossaryConfigTabs, {
                chapterStart: 1,
                chapterEnd: 999999,
                topK: 20,
                minScore: 0.1,
            });
            this.changeValue('glossaryConfigTabs', this.glossaryConfigTabs);
        },

        async fetchRelatedGlossaryItems(glossary) {
            glossary.details.searching = true;
            if (!this.activeId) return;

            const requestBody = {
                searchText: glossary.details.searchText || glossary.name,
                fuzzyText: glossary.details.fuzzyText || null,
                chapterStart: parseInt(this.glossaryConfigTabs.chapterStart) || 1,
                chapterEnd: parseInt(this.glossaryConfigTabs.chapterEnd) || 999999,
                topK: parseInt(this.glossaryConfigTabs.topK) || 20,
                minScore: parseFloat(this.glossaryConfigTabs.minScore) || 0.1,
                algorithm: this.glossaryConfigTabs.selectedAlgorithm || 'all',
            };

            try {
                const response = await fetch(`/api/books/${this.activeId}/glossary/search`, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify(requestBody)
                });
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

        async replaceGlossaryText(glossary) {
            const {replaceTextSearch, replaceTextReplacement} = glossary.details;
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

        async replaceSummary(glossary) {
            const {replaceSummarySearch, replaceSummaryReplacement} = glossary.details;
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

        async previewUpdateRuName(glossary) {
            const {updateRuNameOldRuName, updateRuNameNewRuName, updateRuNameNameFilter} = glossary.details;
            if (!updateRuNameOldRuName || !updateRuNameNewRuName) {
                this.showToast('Old Russian Name and New Russian Name are required', true);
                return Promise.resolve();
            }

            try {
                const requestBody = {
                    bookId: this.activeId,
                    oldRuName: updateRuNameOldRuName,
                    newRuName: updateRuNameNewRuName
                };
                if (updateRuNameNameFilter?.trim()) {
                    requestBody.nameFilter = updateRuNameNameFilter.trim();
                }

                const response = await fetch(`/api/books/${this.activeId}/preview-update-ru-name`, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify(requestBody)
                });

                if (!response.ok) {
                    const error = await response.json();
                    this.showToast(`Error: ${error.message || 'Failed to preview affected chapters'}`, true);
                    return Promise.resolve();
                }

                const affectedChapters = await response.json();
                glossary.details.updateRuNameAffectedChapters = [...affectedChapters];
                this.showToast(`Preview complete: ${affectedChapters.length} chapters will be affected`, false);
                return Promise.resolve();
            } catch (error) {
                console.error('Error previewing chapters:', error);
                this.showToast(`Failed to preview chapters: ${error.message || error}`, true);
                return Promise.reject(error);
            }
        },

        async updateGlossaryRuName(glossary) {
            const {updateRuNameOldRuName, updateRuNameNewRuName, updateRuNameNameFilter} = glossary.details;
            if (!updateRuNameOldRuName || !updateRuNameNewRuName) {
                this.showToast('Book ID, Old Russian Name and New Russian Name are required', true);
                return Promise.resolve();
            }

            try {
                const requestBody = {
                    bookId: this.activeId,
                    oldRuName: updateRuNameOldRuName,
                    newRuName: updateRuNameNewRuName,
                    returnIds: true
                };
                if (updateRuNameNameFilter?.trim()) {
                    requestBody.nameFilter = updateRuNameNameFilter.trim();
                }

                const response = await fetch(`/api/books/${this.activeId}/update-ru-name`, {
                    method: 'PUT',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify(requestBody)
                });

                if (!response.ok) {
                    const error = await response.json();
                    this.showToast(`Error: ${error.message || 'Failed to update Russian name'}`, true);
                    return Promise.resolve();
                }

                const affectedChapterIds = await response.json();

                // Refresh glossary items from affected chapters
                if (affectedChapterIds && affectedChapterIds.length > 0) {
                    await this.refreshGlossaryItemsFromChapters(affectedChapterIds);
                    await this.refreshGlossaryItem(glossary.id);
                }

                this.showToast('Russian name updated successfully', false);
                // Clear affected chapters after successful update
                glossary.details.updateRuNameAffectedChapters = [];
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

        async changeGlossaryAlternativeName(glossaryTo, glossaryFrom) {
            const alternativeName = glossaryFrom.name;
            try {
                return withLoadingState(async () => {
                    glossaryTo.details.jobLoading = true;

                    const response = await fetch(`/api/books/${this.activeId}/glossary/${glossaryTo.id}/properties`, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            field: 'alternativeName',
                            value: alternativeName
                        })
                    });

                    if (!response.ok) {
                        const error = await response.json();
                        this.showToast(`Error updating alternative name: ${error.message || 'Unknown error'}`, true);
                        return Promise.reject(error);
                    }

                    await this.refreshGlossaryItem(glossaryTo.id);
                    this.showToast('Alternative name updated successfully', false);
                    return Promise.resolve();
                }, glossaryTo);
            } catch (error) {
                console.error('Error updating alternative name:', error);
                this.showToast(`Failed to update alternative name: ${error.message || error}`, true);
                return Promise.reject(error);
            }
        },
        
        async changeGlossaryText(glossaryTo, glossaryFrom) {
          this.glossaryConfigTabs.activeTab = 'replaceText';
          glossaryTo.details.replaceTextSearch = glossaryTo.name;
          glossaryTo.details.replaceTextReplacement = glossaryFrom.name;
        
          // Save the changes with loading state management
          return withLoadingState(() => this.replaceGlossaryText(glossaryTo), glossaryTo);
        },
        
        async changeGlossarySummary(glossaryTo, glossaryFrom) {
          this.glossaryConfigTabs.activeTab = 'replaceSummary';
          glossaryTo.details.replaceSummarySearch = glossaryTo.name;
          glossaryTo.details.replaceSummaryReplacement = glossaryFrom.name;
        
          // Save the changes with loading state management
          return withLoadingState(() => this.replaceSummary(glossaryTo), glossaryTo);
        },

        async applyAllGlossaryChanges(glossaryTo, glossaryFrom) {
            await this.changeGlossaryName(glossaryTo, glossaryFrom);
            await this.changeGlossaryTranslatedName(glossaryTo, glossaryFrom);
            await this.changeGlossaryText(glossaryTo, glossaryFrom);
            await this.changeGlossarySummary(glossaryTo, glossaryFrom);
            this.glossaryConfigTabs.activeTab = 'config';
        },

        glossaryCompareItems(item1, item2) {
            const summary = [];

            if (item1?.name === item2?.name) {
                summary.push(`Both items refer to the same character: ${item1?.name}`);
            } else {
                summary.push(`Comparing different entities: ${item1?.name} vs ${item2?.name}`);
            }

            if (item1?.category === item2?.category) {
                summary.push(`Same category: ${item1?.category}`);
            } else {
                summary.push(`Different categories: ${item1?.category} vs ${item2?.category}`);
            }

            const chapterDiff = Math.abs(item1?.chapterNumber - item2?.chapterNumber);
            summary.push(`Chapter difference: ${chapterDiff} chapters apart`);

            if (item1?.description === item2?.description) {
                summary.push('Identical descriptions');
            } else {
                summary.push('Different descriptions - possible character development or different aspects');
            }

            return { item1, item2, summary };
        },

        glossaryFilteredRelatedItems(glossary) {
            if(!glossary.details?.filterText || glossary.details?.filterText == '') {
                return glossary.details.relatedItems;
            }

            return glossary.details.relatedItems?.filter(item =>item?.name?.toLowerCase().includes(glossary.details.filterText?.toLowerCase()) ||
                item.ruName?.toLowerCase().includes(glossary.details.filterText.toLowerCase()) ||
                item.description.toLowerCase().includes(glossary.details.filterText.toLowerCase()));
        },

        copyItemMarkdown(item) {
            if (!item) return;

            const sb = [];
            sb.push(item.name);
            if (item.alternativeName) {
                sb.push(` (${item.alternativeName})`);
            }
            sb.push(` - it's a ${item.category}`);
            sb.push(`; Example of usage: ${item.description}`);
            if (item.references && item.references.length > 0) {
                sb.push(`; References: ${item.references.join(', ')}`);
            }
            if (item.ruName) {
                sb.push(`; Russian translation is: \`${item.ruName}\`;`);
            }

            const markdown = sb.join('');

            navigator.clipboard.writeText(markdown).then(() => {
                this.showToast('Markdown copied to clipboard', false);
            }).catch(err => {
                console.error('Failed to copy: ', err);
                this.showToast('Failed to copy to clipboard', true);
            });
        },

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
