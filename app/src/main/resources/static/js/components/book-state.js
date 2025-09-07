/**
 * Creates an Alpine.js data object with bookState functionality
 */
export function bookStateApp() {
    return {
        expanded: false,
        editingState: null,
        jsonMode: false,
        jsonString: '',

        editBookState(book) {
            fetch(`/api/books/${book.id}/state`)
                .then(response => {
                    if (!response.ok) {
                        throw new Error('Failed to fetch book state');
                    }
                    return response.json();
                })
                .then(bookState => {
                    // Update with the latest state from backend
                    this.editingState = JSON.parse(JSON.stringify(bookState));
                    this.jsonString = JSON.stringify(bookState, null, 2);
                })
                .catch(error => {
                    console.error('Error fetching book state:', error);
                    this.showToast(`Error loading book state: ${error.message}`, true);
                    // Fallback to client data if fetch fails
                    this.editingState = JSON.parse(JSON.stringify(book.bookState));
                    this.jsonString = JSON.stringify(book.bookState, null, 2);
                });
        },

        saveBookState(bookId) {
            let updatedState;

            if (this.jsonMode) {
                try {
                    updatedState = JSON.parse(this.jsonString);
                } catch (e) {
                    this.showToast(`Invalid JSON: ${e.message}`, true);
                    return;
                }
            } else {
                updatedState = this.editingState;
            }

            fetch(`/api/books/${bookId}/state`, {
                method: 'PATCH',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(updatedState)
            })
            .then(response => {
                if (!response.ok) {
                    throw new Error('Failed to update book state');
                }

                // Update local data only after successful backend update
                const bookIndex = this.books.findIndex(b => b.id === bookId);
                if (bookIndex !== -1) {
                    this.books[bookIndex].bookState = updatedState;
                    this.closeEditor();
                }
            })
            .catch(error => {
                console.error('Error updating book state:', error);
                this.showToast(`Error saving book state: ${error.message}`, true);
            });
        },

        closeEditor() {
            this.editingState = null;
            this.jsonString = '';
        },

        addProcessedChunk() {
            if (!this.editingState.processedChunks) {
                this.editingState.processedChunks = [];
            }
            this.editingState.processedChunks.push('');
        },

        removeProcessedChunk(index) {
            this.editingState.processedChunks.splice(index, 1);
        },

        toggleJsonMode() {
            if (!this.jsonMode) {
                // Going from form to JSON
                this.jsonString = JSON.stringify(this.editingState, null, 2);
            } else {
                // Going from JSON to form
                try {
                    this.editingState = JSON.parse(this.jsonString);
                } catch (e) {
                    this.showToast(`Invalid JSON: ${e.message}`, true);
                    return; // Don't toggle if JSON is invalid
                }
            }
            this.jsonMode = !this.jsonMode;
        },
    };
}
