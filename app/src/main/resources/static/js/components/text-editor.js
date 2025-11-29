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
        editorFindText: '',
        editorReplaceText: '',
        editorMatchCase: false,
        editorMatchWholeWord: false,
        editorUseRegex: false,
        editorShowFindReplace: false,
        relText: '',

        initTextEditor() {
            this.loadState('highlightSuspicious');
            this.loadState('hideNonSuspicious');
            this.loadState('editorShowFindReplace');
            this.configureContextMenu();
        },

        configureContextMenu() {
            this.$nextTick(() => {
                // Detect editor type
                const editorEl = document.querySelector('div[x-editor="currentContent"]') ||
                                document.querySelector('div[x-monaco-editor="currentContent"]');

                if (!editorEl) return;

                // Check if it's CodeMirror
                if (editorEl._codemirror) {
                    this.configureCodeMirrorContextMenu(editorEl);
                } else {
                    // Assume it's Monaco (has x-monaco-editor attribute)
                    this.configureMonacoContextMenu(editorEl);
                }

                // Close context menu on escape key
                document.addEventListener('keydown', (e) => {
                  if (e.key === 'Escape') {
                    this.contextMenuVisible = false;
                    this.selectionMenuVisible = false;
                  }
                });
            });
        },

        configureCodeMirrorContextMenu(editorEl) {
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
        },

        configureMonacoContextMenu(editorEl) {
            // Store reference to Monaco editor element
            this.monacoEditorEl = editorEl;

            // Listen for line number clicks from Monaco
            editorEl.addEventListener('linenumberclick', (e) => {
              this.showContextMenu(e.detail.lineNumber, e.detail.lineContent, e.detail.x, e.detail.y);
            });

            // Listen for selection changes from Monaco
            editorEl.addEventListener('selectionchange', (e) => {
              this.handleMonacoSelectionChange(e.detail);
            });

            // Listen for selection clear from Monaco
            editorEl.addEventListener('selectionclear', () => {
              this.selectionContent = '';
              this.selectionMenuVisible = false;
            });
        },

        handleMonacoSelectionChange(detail) {
            this.selectionContent = detail?.selectedText ?? '';
            //TODO: this one doesn't work at all, due `detail` is empty
            //this.selectionMenuVisible = this.selectionContent != '';
            // Position the selection menu at the calculated coordinates
            if (detail?.x && detail?.y) {
                // Update the selection menu position directly via Alpine
                Alpine.nextTick(() => {
                    const selectionMenu = document.querySelector('div[data-content-menu="text-selection"]');
                    if (selectionMenu) {
                        selectionMenu.style.top = `${detail.y}px`;
                        selectionMenu.style.left = `${detail.x}px`;
                        selectionMenu.style.position = 'fixed';
                    }
                });
            }
        },

        getMonacoEditor() {
            if (this.monacoEditorEl && this.monacoEditorEl._monacoDirective) {
                return this.monacoEditorEl._monacoDirective.editor;
            }
            return null;
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
          if (this.editor) {
            // CodeMirror
            const from = { line: this.currentLineNumber, ch: 0 };
            const to = { line: this.currentLineNumber + 1, ch: 0 };
            this.editor.replaceRange('', from, to);
          } else if (this.monacoEditorEl) {
            // Monaco
            const monacoEditor = this.getMonacoEditor();
            if (monacoEditor) {
              monacoEditor.executeEdits('', [{
                range: new window.monaco.Range(this.currentLineNumber + 1, 1, this.currentLineNumber + 2, 1),
                text: ''
              }]);
            }
          }
          this.contextMenuVisible = false;
        },

        duplicateLine() {
          if (this.editor) {
            // CodeMirror
            const line = this.editor.getLine(this.currentLineNumber);
            const from = { line: this.currentLineNumber, ch: 0 };
            const to = { line: this.currentLineNumber + 1, ch: 0 };
            this.editor.replaceRange(line + '\n', to, to);
          } else if (this.monacoEditorEl) {
            // Monaco
            const monacoEditor = this.getMonacoEditor();
            if (monacoEditor) {
              const lineContent = monacoEditor.getModel().getLineContent(this.currentLineNumber + 1);
              monacoEditor.executeEdits('', [{
                range: new window.monaco.Range(this.currentLineNumber + 2, 1, this.currentLineNumber + 2, 1),
                text: lineContent + '\n'
              }]);
            }
          }
          this.contextMenuVisible = false;
        },

        insertLineAbove() {
          if (this.editor) {
            // CodeMirror
            const from = { line: this.currentLineNumber, ch: 0 };
            this.editor.replaceRange('\n', from, from);
            this.editor.setCursor({ line: this.currentLineNumber, ch: 0 });
          } else if (this.monacoEditorEl) {
            // Monaco
            const monacoEditor = this.getMonacoEditor();
            if (monacoEditor) {
              monacoEditor.executeEdits('', [{
                range: new window.monaco.Range(this.currentLineNumber + 1, 1, this.currentLineNumber + 1, 1),
                text: '\n'
              }]);
              monacoEditor.setPosition({ lineNumber: this.currentLineNumber + 1, column: 1 });
            }
          }
          this.contextMenuVisible = false;
        },

        insertLineBelow() {
          if (this.editor) {
            // CodeMirror
            const from = { line: this.currentLineNumber + 1, ch: 0 };
            this.editor.replaceRange('\n', from, from);
            this.editor.setCursor({ line: this.currentLineNumber + 1, ch: 0 });
          } else if (this.monacoEditorEl) {
            // Monaco
            const monacoEditor = this.getMonacoEditor();
            if (monacoEditor) {
              monacoEditor.executeEdits('', [{
                range: new window.monaco.Range(this.currentLineNumber + 2, 1, this.currentLineNumber + 2, 1),
                text: '\n'
              }]);
              monacoEditor.setPosition({ lineNumber: this.currentLineNumber + 2, column: 1 });
            }
          }
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
          navigator.clipboard.writeText(this.selectionContent);
          this.selectionMenuVisible = false;
        },

        removeSelection() {
          if (this.editor) {
            // CodeMirror
            this.editor.replaceSelection('');
          } else if (this.monacoEditorEl) {
            // Monaco
            const monacoEditor = this.getMonacoEditor();
            if (monacoEditor) {
              monacoEditor.executeEdits('', [{
                range: monacoEditor.getSelection(),
                text: ''
              }]);
            }
          }
          this.selectionMenuVisible = false;
        },

        translateSelection() {
          if (!this.selectionContent || this.selectionContent.length === 0) {
              return;
            }

          const selection = this.selectionContent;

          // Call the translation function and handle the promise
          this.translateToRussian(selection)
            .then(translatedText => {
                if (this.editor) {
                  // CodeMirror
                  this.editor.replaceSelection(translatedText);
                } else if (this.monacoEditorEl) {
                  // Monaco
                  const monacoEditor = this.getMonacoEditor();
                  if (monacoEditor) {
                    monacoEditor.executeEdits('', [{
                      range: monacoEditor.getSelection(),
                      text: translatedText
                    }]);
                  }
                }
            })
            .catch(error => {
              console.error('Translation error:', error);
              this.showToast(`Error: ${error.message || error.detail}`, true);
            });
          this.selectionMenuVisible = false;
        },

        editorReplaceAll() {
            if (!this.editorFindText) return;

            try {
                let find = this.editorFindText;
                let flags = 'g'; // Global replacement

                if (!this.editorMatchCase) {
                    flags += 'i'; // Case-insensitive
                }

                if (this.editorUseRegex) {
                    // Use the user's regex as is
                } else {
                    // Escape special regex characters
                    find = find.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
                }

                if (this.editorMatchWholeWord && !this.editorUseRegex) {
                    find = `\\b${find}\\b`;
                }

                const regex = new RegExp(find, flags);
                this.currentContent = this.currentContent.replace(regex, this.editorReplaceText);
            } catch (e) {
                console.error('Invalid Regex:', e);
                this.showToast(`The regular expression you entered is invalid: ${error.message || error.detail}`, true);
            }
        },

        showOriginContent() {
            const lines = this.currentChapter.text.split(/\r?\n|\r|\n/g);
            if(!lines) {
                return;
            }

            const currentLineIndex = this.currentLineNumber - 1;
            const nextLineIndex = this.currentLineNumber;
            const afterNextLineIndex = this.currentLineNumber + 1;

            let combinedLines = '';

            if (currentLineIndex >= 0 && currentLineIndex < lines.length) {
                combinedLines += lines[currentLineIndex];
            }

            if (nextLineIndex >= 0 && nextLineIndex < lines.length) {
                combinedLines += lines[nextLineIndex];
            }

            if (afterNextLineIndex >= 0 && afterNextLineIndex < lines.length) {
                combinedLines += lines[afterNextLineIndex];
            }

            this.relText = combinedLines;
        },

    };
}
