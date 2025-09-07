function listApp() {
    return {
        // currentChapter: {
        //     translatedText: "Initial translated text.\nLine 2.\nLine 3.",
        //     fixedTranslatedText: "Initial fixed translated text.\nLine 2.\nLine 3."
        // },
        selectedField: 'translatedText',
        currentContent: '',
        history: [],
        isSidebarCollapsed: false,

        init() {
            this.currentContent = this.currentChapter[this.selectedField];
            this.history.push(this.currentContent);
            this.initializeEditor();
        },

        initializeEditor() {
            // Initialize Prism Live editor
            const editor = document.getElementById('editor');
            editor.value = this.currentContent;
            Prism.Live.init(editor); // Initialize Prism Live on the textarea <button class="citation-flag" data-index="1">
        },

        toggleSidebar() {
            this.isSidebarCollapsed = !this.isSidebarCollapsed;
        },

        selectField(field) {
            this.saveChanges(); // Save changes before switching fields
            this.selectedField = field;
            this.currentContent = this.currentChapter[field];
            this.history = [this.currentContent]; // Reset history for the new field
            this.initializeEditor(); // Reinitialize the editor with the new content
        },

        saveChanges() {
            const editor = document.getElementById('editor');
            this.currentContent = editor.value; // Get the current content from the editor
            this.history.push(this.currentContent);
            this.currentChapter[this.selectedField] = this.currentContent;
            console.log(`Saved changes to ${this.selectedField}`);
        },

        revertToVersion(index) {
            const editor = document.getElementById('editor');
            this.currentContent = this.history[index];
            editor.value = this.currentContent; // Update the editor content
            Prism.Live.init(editor); // Reinitialize Prism Live to reflect changes <button class="citation-flag" data-index="1">
            console.log(`Reverted to version ${index + 1}`);
        },

        deleteVersion(index) {
            this.history.splice(index, 1);
            console.log(`Deleted version ${index + 1}`);
        }
    };
}

export {
    listApp
};