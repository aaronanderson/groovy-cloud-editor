import { html, css, LitElement } from 'lit';
import { customElement, property, query } from 'lit/decorators.js';
import { ifDefined } from 'lit/directives/if-defined.js';

import { connect, store, GCEStore } from '../app/store';

//import './codemirror.css';
// @ts-ignore
//mport * as CodeMirror from 'codemirror';
import CodeMirror from 'codemirror/lib/codemirror.js';
import 'codemirror/mode/groovy/groovy.js';



//import codemirrorStyles from 'codemirror/lib/codemirror.css';
//import * as codemirrorStyles from 'codemirror/lib/codemirror.css';
//console.log(codemirrorStyles);
// @ts-ignore
//import blackboardStyles from 'codemirror/theme/blackboard.css';



//const codemirrorStyle = css(<any>[codemirrorStyles]);

const config: EditorConfiguration = {
	tabSize: 3,
	lineNumbers: true,
	mode: 'xml',
	theme: 'blackboard'
};

@customElement('groovy-editor')
export class GroovyEditorElement extends connect<GCEStore>(store)(LitElement) {

	@property({ type: String })
	script?: string;

	@query("#editor")
	editorElement?: HTMLSpanElement;


	static get styles() {
		return [ css` textarea {
						width: 100%;		
					}
						 
					`];
	}

	firstUpdated() {
		const editor = CodeMirror(this.editorElement, config);
		console.log(editor);
	}

	render() {
		//.value=${ifDefined(this.script)
		return html`<span id="editor"></span>`;
	}



}




