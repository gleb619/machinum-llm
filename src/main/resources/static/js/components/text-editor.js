/**
 * Creates an Alpine.js data object with COdeMirror functionality
 */
export function textEditorApp() {
    return {
        highlightSuspicious: false,
        hideNonSuspicious: true,
        editor: null,
        currentLineNumber: 0,
        currentLineContent: '',
        contextMenuVisible: false,
        contextMenuX: 0,
        contextMenuY: 0,
        selectionContent: '',
        selectionRange: {start: 0, end: 0},
        selectionMenuVisible: false,
        selectionMenuX: 0,
        selectionMenuY: 0,

        initTextEditor() {
            this.loadState('highlightSuspicious');
            this.loadState('hideNonSuspicious');
            this.configureContextMenu();
        },

        configureContextMenu() {
            this.$nextTick(() => {
                // Initialize CodeMirror
                const editorEl = document.querySelector('div[x-editor="currentContent"]');
                this.editor = editorEl._codemirror;

                // Listen for line number clicks
                editorEl.addEventListener('linenumberclick', (e) => {
                  this.showContextMenu(e.detail.lineNumber, e.detail.lineContent, e.detail.x, e.detail.y);
                });

                // Listen for text selection
                this.editor.on('cursorActivity', () => {
                  const selection = this.editor.getSelection();
                  if (selection && selection.length > 0) {
                    this.selectionContent = selection;
                    this.selectionMenuVisible = true;
                    const selectionStartPos = this.editor.getCursor('from');
                    const selectionEndPos = this.editor.getCursor('to');
                    this.selectionRange = { start: selectionStartPos.line, end: selectionEndPos.line };
                  } else {
                    this.selectionContent = '';
                    this.selectionMenuVisible = false;
                  }
                });

                // Close context menu on escape key
                document.addEventListener('keydown', (e) => {
                  if (e.key === 'Escape') {
                    this.contextMenuVisible = false;
                    this.selectionMenuVisible = false;
                  }
                });
            });
        },

        showContextMenu(lineNumber, lineContent, x, y) {
          this.currentLineNumber = lineNumber;
          this.currentLineContent = lineContent;
          this.contextMenuX = x;
          this.contextMenuY = y;
          this.contextMenuVisible = true;
        },

        /*line actions*/

        findSameLine() {
          this.fetchSimilarLines(this.currentLineContent, [this.selectedField])
              .then(_ => {
                  this.addSearchParam('lineSearchQuery', this.currentLineContent);
                  this.selectTab('lines');
              })
          this.contextMenuVisible = false;
        },

        copyLine() {
          navigator.clipboard.writeText(this.currentLineContent);
          this.contextMenuVisible = false;
        },

        deleteLine() {
          const from = { line: this.currentLineNumber, ch: 0 };
          const to = { line: this.currentLineNumber + 1, ch: 0 };
          this.editor.replaceRange('', from, to);
          this.contextMenuVisible = false;
        },

        duplicateLine() {
          const line = this.editor.getLine(this.currentLineNumber);
          const from = { line: this.currentLineNumber, ch: 0 };
          const to = { line: this.currentLineNumber + 1, ch: 0 };
          this.editor.replaceRange(line + '\n', to, to);
          this.contextMenuVisible = false;
        },

        insertLineAbove() {
          const from = { line: this.currentLineNumber, ch: 0 };
          this.editor.replaceRange('\n', from, from);
          this.editor.setCursor({ line: this.currentLineNumber, ch: 0 });
          this.contextMenuVisible = false;
        },

        insertLineBelow() {
          const from = { line: this.currentLineNumber + 1, ch: 0 };
          this.editor.replaceRange('\n', from, from);
          this.editor.setCursor({ line: this.currentLineNumber + 1, ch: 0 });
          this.contextMenuVisible = false;
        },

        /*select actions*/

        findSameSelection() {
          this.fetchSimilarLines(this.selectionContent, [this.selectedField])
              .then(_ => {
                  this.addSearchParam('lineSearchQuery', this.selectionContent);
                  this.selectTab('lines');
              })
          this.selectionMenuVisible = false;
        },

        findSameSelectionAndPrepareTranslation() {
          Promise.all([
              this.fetchSimilarLines(this.selectionContent, [this.selectedField]),
              this.translateToRussian(this.selectionContent)
            ]).then(results => {
              this.lineReplaceWith = results[1];
              this.addSearchParam('lineSearchQuery', this.selectionContent);
              this.selectTab('lines');
            });
            this.selectionMenuVisible = false;
        },

        copySelection() {
          const selection = this.editor.getSelection();
          navigator.clipboard.writeText(selection);
          this.selectionMenuVisible = false;
        },

        removeSelection() {
          this.editor.replaceSelection('');
          this.selectionMenuVisible = false;
        },

        translateSelection() {
          if (!this.selectionContent || this.selectionContent.length === 0) {
              return;
            }
    
          const selection = this.selectionContent;
          const cursor = this.editor.getCursor();
    
          // Call the translation function and handle the promise
          this.translateToRussian(selection)
            .then(translatedText => {
                this.editor.replaceSelection(translatedText);
            })
            .catch(error => {
              console.error('Translation error:', error);
              this.showToast(`Error: ${error.message || error.detail}`, true);
            });
          this.selectionMenuVisible = false;
        },

    };
}