import {LitElement, CSSResult, html, css} from 'lit';
import {property, customElement, query} from 'lit/decorators.js';
import {ifDefined} from 'lit/directives/if-defined.js';

import { ViewElement } from '../../components/view';

import { GCEStore } from '../../app/store';
import { back } from './editor-actions';

import '../../components/editor';
import { GroovyEditorElement } from '../../components/editor';


@customElement('groovy-editor-page')
export class GroovyEditorPageElement extends ViewElement {

	@property({ type: String })
	pageTitle = 'Editor';

	@property({ type: Object })
	targetScript: any = {};

	@property({ type: Boolean })
	modified= false;


	@query('groovy-editor')
	groovyEditor?: GroovyEditorElement;
	
	
	constructor() {
	    super();
    	this.addEventListener('editor-update', (e: Event) => {this.modified = true; console.log(e)});
  	}

	
	render() {		
		const contents = ifDefined(this.targetScript && this.targetScript.contents ? atob (this.targetScript.contents): undefined);
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
    				<groovy-editor .script=${contents} hintPath="/api/gce/hint"></groovy-editor>					
  				</div>	
								
				<div class="btn-group" role="group" aria-label="Run">			  		
					<button ?disabled=${!this.modified} type="button" class="btn btn-primary mr-2" @click=${(e: MouseEvent)=> this.dispatch(back())}>Save</button>
					<button ?disabled=${!!this.modified} type="button" class="btn btn-secondary mr-2" @click=${(e: MouseEvent)=> this.dispatch(back())}>Run</button>
					<button type="button" class="btn btn-secondary" @click=${(e: MouseEvent)=> this.dispatch(back())}>Back</button>	
				</div>
			</div>

    `;
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
