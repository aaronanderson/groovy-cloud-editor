import { html, css, LitElement } from 'lit';
import { customElement, property, query } from 'lit/decorators.js';
import { ifDefined } from 'lit/directives/if-defined.js';

import { connect, store, GCEStore } from '../app/store';

//import './codemirror.css';
// @ts-ignore
//mport * as CodeMirror from 'codemirror';
import CodeMirror, {
	Hints,
	Hint,
	Editor,
	Position,
	EditorConfiguration
} from 'codemirror';
import 'codemirror/mode/groovy/groovy.js';
import 'codemirror/addon/hint/show-hint.js';

const editorCSS = css `'!cssx|codemirror/lib/codemirror.css'`;
const hintCSS = css `'!cssx|codemirror/addon/hint/show-hint.css'`;


@customElement('groovy-editor')
export class GroovyEditorElement extends LitElement {

	editorElement: HTMLDivElement = document.createElement("div");
	hintElement: HTMLDivElement = document.createElement("div");
	editor: Editor;

	@property({ type: String })
	hintPath?: string;
		
	@property({ type: String })
	set script(script: string) {
		let oldVal = this.editor.getValue();
		this.editor.setValue(script);
		this.requestUpdate('script', oldVal)
	}

	get script() {
		return this.editor.getValue();
	}

	constructor() {
		super();
		const config: EditorConfiguration = {
			tabSize: 3,
			lineNumbers: true,
			//matchBrackets: true,
			//foldGutter: true,
			mode: "groovy",
			extraKeys: { "Ctrl-Space": "autocomplete" },
			hintOptions: {hint: this.groovyHint, container: this.hintElement},
			//gutters: ["CodeMirror-linenumbers", "CodeMirror-foldgutter"]
		};
		this.editor = CodeMirror(this.editorElement, config);
		this.editor.on("changes", (e: any) => { this.dispatchEvent(new CustomEvent(`editor-update`, { composed: true, detail: {} })) });		
		//CodeMirror.registerHelper("hint", "groovy", this.groovyHint);
	}

	


	static get styles() {
		return [editorCSS, hintCSS, css` 
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

	updated() {
		this.editor.refresh();
	}

	render() {
		//.value=${ifDefined(this.script)
		//return html`<span id="editor"></span>`;
		return html`${this.editorElement}${this.hintElement}`;
	}

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
		return hints;

	}
	
	/*
	async fetchHints(): Hint[]{
	const response = await fetch("/api/gce/scripts", {
		method: 'GET'
	});
	const scriptsResult: ScriptsResult = await response.json();
	if (!response.ok) {
		throw Error(response.statusText);
	}
	let scripts: Array<Script> = scriptsResult.scripts;
	await saveScripts(scripts, db);
	return scripts;
	
}
*/

}





