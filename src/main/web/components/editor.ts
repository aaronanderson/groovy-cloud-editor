import { html, css, LitElement } from 'lit';
import { customElement, property, query } from 'lit/decorators.js';
import { ifDefined } from 'lit/directives/if-defined.js';

import { connect, store, GCEStore } from '../app/store';

import CodeMirror, {
	Hints,
	Hint,
	Editor,
	Position,
	EditorConfiguration,
} from 'codemirror';

import {
	Annotation,
	SyncLintStateOptions,
} from 'codemirror/addon/lint/lint';

import 'codemirror/mode/groovy/groovy.js';
import 'codemirror/addon/hint/show-hint.js';
import 'codemirror/addon/lint/lint.js';

const editorCSS = css`'!cssx|codemirror/lib/codemirror.css'`;
const hintCSS = css`'!cssx|codemirror/addon/hint/show-hint.css'`;
const lintCSS = css`'!cssx|codemirror/addon/lint/lint.css'`;


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

	_script: string = "";

	@property({ type: String, attribute: false })
	set script(script: string) {
		console.log("set script", script);
		let oldVal = undefined;
		if (script &&this.editor) {
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
		return [editorCSS, hintCSS, lintCSS, css` 
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
		const config: EditorConfiguration = {
			value: this._script,
			tabSize: 3,
			lineNumbers: true,
			mode: "groovy",
			gutters: ['CodeMirror-lint-markers'],
			extraKeys: { "Ctrl-Space": "autocomplete" },
			hintOptions: { hint: this.groovyHint.bind(this), container: this.hintElement },
			lint: <SyncLintStateOptions<Object>>{ /*lintOnChange: false*/ delay: 2500, selfContain: true, tooltips: true, getAnnotations: this.groovyLint.bind(this) }
		};
		this.editor = CodeMirror(this.editorElement, config);
		this.editor.on("changes", (e: Editor) => { this.dispatchEvent(new CustomEvent(`editor-update`, { composed: true, detail: {} })) });
	}

	/*
		updated(changedProperties: Map<string, unknown>) {
			if (this.editor) {
				if (changedProperties.has("script") && this.script && this.script !== "") {
					this.editor.setValue(this.script);
				}
				this.editor.refresh();
			}
		}
	*/

	render() {
		return html`${this.editorElement}${this.hintElement}`;
	}

	validate() {
		if (this.editor) {
			this.editor.performLint();
			return !this.hasErrors;
		}
		return false;
	}

	//Codemirror accepts PromiseLike return type so no need to use a formal AsyncHintFunction 
	async groovyHint(cm: Editor) {
		let cur = cm.getCursor();
		let token = cm.getTokenAt(cur);

		let start = token.start;
		let end = cur.ch;


		let word = token.string.slice(0, end - start);
		const hints: Hints = {
			list: [],
			from: CodeMirror.Pos(cur.line, start),
			to: CodeMirror.Pos(cur.line, end),
		};
		console.log("hint requested", cur, word, token, start, end);

		//at least two options are needed to prompt otherwise a single value is autocompleted.
		hints.list.push({
			displayText: "displayText",
			text: "text",
			render: (element, data, cur) => {
				let entered = element.appendChild(document.createElement("span"));
				entered.classList.add("CodeMirror-hint-entered");
				entered.textContent = word;

				element.appendChild(
					document.createElement("span")
				).innerHTML = `<span class="CodeMirror-hint-arg">${cur.displayText as string}</span>`;
			},
			//hint: (cm, self, data) => {console.log("apply hint", cm, self, data);},
		});

		hints.list.push({
			displayText: "displayText2",
			text: "text2",
			render: (element, data, cur) => {
				let entered = element.appendChild(document.createElement("span"));
				entered.classList.add("CodeMirror-hint-entered");
				entered.textContent = word;

				element.appendChild(
					document.createElement("span")
				).innerHTML = `<span class="CodeMirror-hint-arg">${cur.displayText as string}</span>`;
			},
			//hint: (cm, self, data) => {console.log("apply hint", cm, self, data);},
		});


		/*
		if (word === "\\\\") {
			cm.state.completionActive.close();
			return {
				list: [],
				from: CodeMirror.Pos(cur.line, start),
				to: CodeMirror.Pos(cur.line, end),
			};
		}
		if (/[^\w\\]/.test(word)) {
			word = "";
			start = end = cur.ch;
		}

		

		if (token.type == "tag") {
			for (const macro of macros) {
				if (!word || macro.text.lastIndexOf(word, 0) === 0) {
					hints.list.push({
						displayText: macro.text,
						text: macro.snippet,
						render: (element, data, cur) => {
							let entered = element.appendChild(document.createElement("span"));
							entered.classList.add("CodeMirror-hint-entered");
							entered.textContent = word;

							element.appendChild(
								document.createElement("span")
							).innerHTML = (cur.displayText as string)
								.slice(word.length)
								.replace(/(\#[1-9])/g, (_, arg) => `<span class="CodeMirror-hint-arg">${arg}</span>`);
						},
						hint: intelliSense,
					});
				}
			}
		}*/

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
		const hintResult: HintResponse = await response.json();
		if (!response.ok) {
			throw Error(response.statusText);
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


}

export interface HintRequest {
	line: number;
	ch: number;
	sticky: string;
	name: string;
	script: string;
}

export interface HintResponse {

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


