import {LitElement, CSSResult, html, css} from 'lit';
import {property, customElement, query} from 'lit/decorators.js';
import {ifDefined} from 'lit/directives/if-defined.js';

import { ViewElement } from '../../components/view';

import { GCEStore } from '../../app/store';
import { Script, back, save } from './editor-actions';

import '../../components/editor';
import { GroovyEditorElement } from '../../components/editor';


@customElement('groovy-editor-page')
export class GroovyEditorPageElement extends ViewElement {

	@property({ type: String })
	pageTitle = 'Editor';

	@property({ type: Object })
	targetScript?: Script;

	@property({ type: Boolean })
	modified= false;
	
	@property({ type: Boolean })
	hasErrors= false;


	@query('groovy-editor')
	groovyEditor?: GroovyEditorElement;
	
	
	constructor() {
	    super();
    	this.addEventListener('editor-update', (e: Event) => {this.modified = true; });
		this.addEventListener('editor-lint', (e: Event) => {this.hasErrors = (e as any).detail.errors.length > 0;});
  	}

	
	render() {		
		const contents = this.targetScript && this.targetScript.contents ? atob (this.targetScript.contents): undefined;
		const name = this.targetScript && this.targetScript.name ? this.targetScript.name: undefined;
		return html`

			${this.pageTitleTemplate}
			${this.loadingTemplate}
			${this.errorTemplate}
			<div class="container">
				 <div class="form-group">
					<label  for="scriptName">Name</label>
    				<input class="form-control" type="text" placeholder="Default input" id="scriptName" .value=${ifDefined(this.targetScript?.name)}></input>
				 </div>
			
				<div class="form-group">
    				<label for="scriptContent">Script</label>
    				<groovy-editor script-name=${ifDefined(name)} .script=${ifDefined(contents)}></groovy-editor>					
  				</div>	
								
				<div class="btn-group" role="group" aria-label="Run">			  		
					<button ?disabled=${!this.modified || this.hasErrors } type="button" class="btn btn-primary mr-2" @click=${this.handleSave}>Save</button>
					<button type="button" class="btn btn-secondary" @click=${(e: MouseEvent)=> this.dispatch(back())}>Back</button>	
				</div>
			</div>

    `;
	}
	
	handleSave(e: MouseEvent){
		if (this.groovyEditor && this.groovyEditor.validate()){
			let updateScript = <Script>{
				...this.targetScript,
				lastModified: new Date().toISOString (),
				contents: btoa(this.groovyEditor.script)
			};
			this.dispatch(save(updateScript));
			this.modified = false;
		}
	}


	
	stateChanged(state: GCEStore) {
		if (state.editor) {
			//console.log(state.editor);
			this.loading = state.editor.loading;
			this.errorMessage = state.editor.errorMessage;
			this.targetScript = state.editor.targetScript; 
			if (this.targetScript){
				this.pageTitle = 'Editor - ' + (this.targetScript.lastModified ? 'Update': 'New');
			}else {
				this.pageTitle = 'Editor';
			}
		}

	}





}


export default GroovyEditorPageElement;
