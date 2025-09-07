/**
 * Creates an Alpine.js data object with drawer functionality
 */
export function chapterDrawerApp() {
    return {
        showPanel: false,
        editing: true,
        currentChapter: { id: '', title: '', bookId: '', names: [] },
        showEditNamesPanel: false,
        nameFilter: '',
        isSaving: false,
        targetName: undefined,


        initChapterDrawer() {
            this.registerDrawerHotkeys(this);
        },

        openCreatePanel() {
            this.editing = false;
            this.currentChapter = { id: '', title: '', bookId: '', names: [] };
            this.showPanel = true;
        },

        toggleEditCurrentChapter() {
            this.showPanel = !this.showPanel;
            if(this.showPanel) {
                const currentChap = this.chapters.find(item => item.id === this.activeId);
                if(currentChap) {
                    this.editChapter(currentChap);
                }
            }
        },

        editChapter(chapter) {
            if(!chapter) return;
            this.activeId = chapter.id;
            this.editing = true;
            this.currentChapter = { ...chapter };
            this.showPanel = true;
        },

        closePanel() {
            this.showPanel = false;
        },

        toggleEditNamesPanel() {
            this.showEditNamesPanel = !this.showEditNamesPanel;
            localStorage.setItem('showEditNamesPanel', '' + this.showEditNamesPanel);
            if(this.showEditNamesPanel) {
                this.openEditNamesPanelCurrent();
            }
        },

        openEditNamesPanelCurrent() {
            this.openEditNamesPanel(this.getById(this.chapters, this.activeId));
        },

        openEditNamesPanel(chapter) {
            if(!chapter){
                return;
            }

            this.activeId = chapter.id;
            localStorage.setItem('showEditNamesPanel', 'true');
            this.currentChapter = JSON.parse(JSON.stringify(chapter));
            this.originalNames = JSON.parse(JSON.stringify(chapter.names));
            this.showEditNamesPanel = true;
        },

        closeEditNamesPanel() {
            localStorage.setItem('showEditNamesPanel', 'false');
            this.showEditNamesPanel = false;
        },

        get filteredNames() {
            return this.currentChapter.names.filter(name =>
                name.name.toLowerCase().includes(this.nameFilter.toLowerCase()) ||
                name.category.toLowerCase().includes(this.nameFilter.toLowerCase()) ||
                name.description.toLowerCase().includes(this.nameFilter.toLowerCase()) ||
                name.ruName.toLowerCase().includes(this.nameFilter.toLowerCase())
            );
        },

        addName() {
            this.currentChapter.names.push({ name: '', category: '', description: '', references: [], metadata: {} });
        },

        pasteName() {
            if(!this.getByKey(this.currentChapter.names, 'name', this.targetName.name)) {
                this.currentChapter.names.push(JSON.parse(JSON.stringify(this.targetName)));
            } else {
                console.warn("Collection already contains name: '", this.targetName.name, "'");
            }
        },

        removeName(nameToRemove) {
            this.currentChapter.names = this.currentChapter.names.filter(name => name.name !== nameToRemove);
        },

        copyName(nameToCopy) {
            this.targetName = JSON.parse(JSON.stringify(nameToCopy));
        },

        copyNamesToClipboard() {
            const formattedText = this.filteredNames.map(objectName =>
                `${objectName.name} - It's a ${objectName.category}; ${objectName.description}; Translation is \`${objectName.ruName}\`;`
            ).join('\n');
            navigator.clipboard.writeText(formattedText);
            this.showToast(`Copied!`);
        },

        addNewWarning() {
            if(!this.currentChapter.warnings) {
                this.currentChapter.warnings = [];
            }
            this.currentChapter.warnings.push({
                type: 'EMPTY_FIELD',
                text: '',
                metadata: {}
            });
        },

        removeWarning(index) {
            if(index > -1) {
                this.currentChapter.warnings.splice(index, 1)
            }
        },

        saveChanges() {
            if(this.showEditNamesPanel) {
                this.saveNames();
            } else if(this.showPanel) {
                this.saveChapter();
            } else {
                console.error("Panel doesn't opened");
            }
        },

        saveNames() {
            this.isSaving = true;
            fetch(`/api/chapters/${this.currentChapter.id}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(this.currentChapter)
            })
            .then(response => response.json()
                .then(rsp => {
                    if (!response.ok) {
                        console.error('Error saving names:', rsp);
                        this.showToast(`Error saving names: ${rsp.message || rsp.detail}`, true);
                    } else {
                        this.fetchChapters(this.currentPage);
                        setTimeout(() => {
                            this.isSaving = false;
                        }, 1000);
                        //this.closeEditNamesPanel();
                    }
                }));
        },

        cancelEditNames() {
            this.currentChapter.names = JSON.parse(JSON.stringify(this.originalNames)); // Restore original names
            this.closeEditNamesPanel();
        },

        async translatedTitle() {
            if (!this.currentChapter.title) return;

            const translatedText = await this.translateToRussian(this.currentChapter.title);
            this.currentChapter.translatedTitle = translatedText;
        },

        async handleGenerateSummary() {
            try {
                const summary = await this.executeOperation('summarize');
                const chapter = await this.fetchChapter(this.activeId);
                this.currentChapter.summary = chapter.summary;
            } catch (error) {
                console.error('Failed to generate summary:', error);
                this.showToast(`Failed to generate summary: ${error.message || error.detail || error}`, true);
            }
        },

        registerDrawerHotkeys(app) {
            Mousetrap.bind('ctrl+s', function(e) {
                e.preventDefault(); // Prevent browser save dialog
                app.saveChanges();

                return false;
            });
            Mousetrap.bind('ctrl+e', function(e) {
                e.preventDefault(); // Prevent browser save dialog
                app.toggleEditCurrentChapter();

                return false;
            });
            Mousetrap.bind('ctrl+shift+e', function(e) {
                e.preventDefault(); // Prevent browser save dialog
                app.toggleEditNamesPanel();

                return false;
            });
        },

    };
}
