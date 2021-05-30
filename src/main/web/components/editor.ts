import { html, css, LitElement } from 'lit';
import { customElement, property, query } from 'lit/decorators.js';
import { ifDefined } from 'lit/directives/if-defined.js';

import { connect, store, GCEStore } from '../app/store';

import CodeMirror, {
	Hints,
	Hint,
	Token,
	Editor,
	Position,
	EditorConfiguration,
	EditorChange,
} from 'codemirror';

import {
	Annotation,
	SyncLintStateOptions,
} from 'codemirror/addon/lint/lint';

import 'codemirror/mode/groovy/groovy.js';
import 'codemirror/addon/hint/show-hint.js';
import 'codemirror/addon/lint/lint.js';
import 'codemirror/addon/display/fullscreen.js';

const editorCSS = css`'!cssx|codemirror/lib/codemirror.css'`;
const hintCSS = css`'!cssx|codemirror/addon/hint/show-hint.css'`;
const lintCSS = css`'!cssx|codemirror/addon/lint/lint.css'`;
const fullscreenCSS = css`'!cssx|codemirror/addon/display/fullscreen.css'`;


@customElement('groovy-editor')
export class GroovyEditorElement extends LitElement {

	editorElement: HTMLDivElement = document.createElement("div");
	hintElement: HTMLDivElement = document.createElement("div");
	editor?: Editor;
	hasErrors = false;

	@property({ type: String, attribute: "hint-path" })
	hintPath: string = "/api/gce/hint";

	@property({ type: String, attribute: "validate-path" })
	validatePath: string = "/api/gce/validate";

	@property({ type: String, attribute: "script-name" })
	scriptName?: string;

	@property({ type: String, attribute: "lint-delay" })
	lintDelay: number = 2000;

	resolveValidation?: Function = undefined;

	//holds intitial script value until the editor is created
	_script: string = "";

	@property({ type: String, attribute: false })
	set script(script: string) {
		let oldVal = undefined;
		if (script && this.editor) {
			oldVal = this.editor.getValue();
			this.editor.setValue(script);
		} else {
			oldVal = this._script;
			this._script = script;
		}

		this.requestUpdate('script', oldVal)
	}


	get script() {
		return this.editor ? this.editor.getValue() : this._script;
	}


	static get styles() {
		return [editorCSS, hintCSS, lintCSS, fullscreenCSS, css` 
					textarea {
						width: 100%;		
					}
					
					.CodeMirror-hint-entered {
					 color: #170;
					}
					.CodeMirror-hint-arg {
					 color: gray;
					}
					li.CodeMirror-hint-active {
					 background: rgb(201, 233, 195);
					}
	 
					`];
	}


	firstUpdated() {
		//only the script option will be available to be updated after the editor is initialized.
		const config: EditorConfiguration = {
			value: this._script,
			tabSize: 3,
			lineNumbers: true,
			mode: "groovy",
			gutters: ['CodeMirror-lint-markers'],
			extraKeys: {
				"F11": this.fullscreen.bind(this),
				"Esc": this.exitFullscreen.bind(this),
				"Ctrl-S": this.save.bind(this),
				"Ctrl-Space": "autocomplete"
			},
			hintOptions: { hint: this.groovyHint.bind(this), container: this.hintElement },
			lint: <SyncLintStateOptions<Object>>{ /*lintOnChange: false*/ delay: this.lintDelay, selfContain: true, tooltips: true, getAnnotations: this.groovyLint.bind(this), onUpdateLinting: this.lintComplete.bind(this) }
		};
		this.editor = CodeMirror(this.editorElement, config);
		this.editor.on("changes", (e: Editor, c: Array<EditorChange>) => { this.dispatchEvent(new CustomEvent(`editor-update`, { composed: true, detail: { changes: c } })) });
	}


	render() {
		return html`${this.editorElement}${this.hintElement}`;
	}


	lintComplete() {
		if (this.resolveValidation) {
			this.resolveValidation();
			this.resolveValidation = undefined;
		}
	}

	async validate() {
		if (this.editor) {
			const semaphore = new Promise(resolve => {
				this.resolveValidation = resolve;
			});
			this.editor.performLint();
			await semaphore;
			return !this.hasErrors;
		}
		return false;
	}

	//Codemirror accepts PromiseLike return type so no need to use a formal AsyncHintFunction 
	async groovyHint(cm: Editor) {
		const cur = cm.getCursor();
		let token = cm.getTokenAt(cur);

		let start = token.start;
		let end = cur.ch;

		let word = token.string.slice(0, end - start);

		const hints: Hints = {
			list: [],
			from: CodeMirror.Pos(cur.line, start),
			to: CodeMirror.Pos(cur.line, end),
		};
		//console.log("hint requested", cur, word, token, start, end);

		const hintRequest = <HintRequest>{
			...cur,
			name: this.scriptName,
			script: this.script
		};
		const response = await fetch(this.hintPath, {
			method: 'POST',
			headers: {
				'Accept': 'application/json',
				'Content-Type': 'application/json'
			},
			body: JSON.stringify(hintRequest)
		});
		if (!response.ok) {
			throw Error(response.statusText);
		}
		const hintResult: HintResponse = await response.json();
		for (let hint of hintResult.hints) {
			hints.list.push(<Hint>{
				displayText: hint.displayed,
				text: hint.value,
				render: (element, data, cur) => {
					//console.log("render hint", cm, self, data);
					if (hint.entered[1] > 0) {
						let displayedValue = element.appendChild(document.createElement("span"));
						displayedValue.classList.add("CodeMirror-hint-arg");
						displayedValue.textContent = hint.displayed.substring(0, hint.entered[0]);

						let enteredValue = element.appendChild(document.createElement("span"));
						enteredValue.classList.add("CodeMirror-hint-entered");
						enteredValue.textContent = hint.displayed.substring(hint.entered[0], hint.entered[0] + hint.entered[1]);

						displayedValue = element.appendChild(document.createElement("span"));
						displayedValue.classList.add("CodeMirror-hint-arg");
						displayedValue.textContent = hint.displayed.substring(hint.entered[0] + hint.entered[1]);

					} else {
						let displayedValue = element.appendChild(document.createElement("span"));
						displayedValue.classList.add("CodeMirror-hint-arg");
						displayedValue.textContent = hint.displayed;
					}

				},
				hint: (cm, self, data) => {
					const tokens: Array<Token> = cm.getLineTokens(cur.line, true);
					let nextToken = undefined;
					let targetToken = undefined;
					let priorToken = undefined;
					let i = 0;
					for (i = 0; i < tokens.length; i++) {
						if (tokens[i].start <= cur.ch && cur.ch <= tokens[i].end) {
							targetToken = tokens[i];
							if (i - 1 >= 0) {
								priorToken = tokens[i - 1];
							}
							if (i + 1 < tokens.length) {
								nextToken = tokens[i + 1];
							}
							break;
						}
					}
					//console.log("apply hint", cur, data, hint.type, priorToken, targetToken, nextToken, tokens);
					let replacement = data.text;
					const from = self.from || data.from;
					const to = self.to || data.to;
					if (targetToken) {
						if ("method" == hint.type || "constructor" == hint.type) {
							if ("." == targetToken.string) {
								replacement = "." + replacement;
							} else if ("(" == targetToken.string && priorToken && priorToken.string != "" && ["variable", "property"].includes(priorToken.type ? priorToken.type : "")) {
								from.ch = priorToken.start;
								if (nextToken && ")" == nextToken.string) {
									to.ch = nextToken.end;
								}
							} else if ([")", ","].includes(targetToken.string) && priorToken) {
								for (let j = i - 1; j >= 0; j--) {
									let previousToken = tokens[j];
									if ("." == previousToken.string) {
										from.ch = previousToken.end;
										break;
									}
								}
							}
						} else if ("property" == hint.type) {
							if ("." == targetToken.string) {
								replacement = "." + replacement;
							}
						} else if ("import-package" == hint.type) {
							if ("." == targetToken.string) {
								replacement = "." + replacement + ".";
							}
						} else if ("import-class" == hint.type) {
							if ("." == targetToken.string) {
								replacement = "." + replacement;
							}
						} else if ("import-method" == hint.type) {
							if ("." == targetToken.string) {
								replacement = "." + replacement;
							}
						}
					}

					cm.replaceRange(replacement, from, to, "complete");
				},
			});
		}


		return hints;

	}



	async groovyLint(content: string, options: Object, codeMirror: CodeMirror.Editor): Promise<Annotation[]> {
		let found = <Annotation[]>[];

		if (content === "") {
			return found;
		}

		const validateRequest = <ValidateRequest>{
			name: this.scriptName,
			script: content
		};

		const response = await fetch(this.validatePath, {
			method: 'POST',
			headers: {
				'Accept': 'application/json',
				'Content-Type': 'application/json'
			},
			body: JSON.stringify(validateRequest)
		});

		const validateResult: ValidateResponse = await response.json();
		if (!response.ok) {
			throw Error(response.statusText);
		}
		if (validateResult.errors) {
			for (let error of validateResult.errors) {
				const annotation = {
					from: CodeMirror.Pos((error.sline ? error.sline : 1) - 1, (error.scolumn ? error.scolumn : 1) - 1),
					to: CodeMirror.Pos((error.eline ? error.eline : 1) - 1, (error.ecolumn ? error.ecolumn : 1) - 1),
					severity: error.severity ? error.severity : undefined,
					message: error.message
				}
				found.push(annotation);
			}
			this.hasErrors = true;
		} else {
			this.hasErrors = false;
		}

		this.dispatchEvent(new CustomEvent(`editor-lint`, { composed: true, detail: { errors: validateResult.errors ? validateResult.errors : [] } }))

		return found;
	}

	fullscreen(cm: Editor) {
		cm.setOption("fullScreen", !cm.getOption("fullScreen"));
	}

	exitFullscreen(cm: Editor) {
		if (cm.getOption("fullScreen")) {
			cm.setOption("fullScreen", false)
		}
	}

	save(cm: Editor) {
		this.dispatchEvent(new CustomEvent(`editor-save`, { composed: true, detail: {} }))
	}


}

export interface HintRequest {
	line: number;
	ch: number;
	sticky: string;
	name: string;
	script: string;
}

export interface HintResponse {
	hints: GCEHint[];

}

export interface GCEHint {
	entered: number[];
	displayed: string;
	value: string;
	type: string;
}

export interface ValidateRequest {
	name: string;
	script: string;
}

export interface ValidateResponse {
	errors?: LintError[];

}

export interface LintError {
	sline?: number;
	eline?: number;
	scolumn?: number;
	ecolumn?: number;
	severity?: string;
	message: string;
}


