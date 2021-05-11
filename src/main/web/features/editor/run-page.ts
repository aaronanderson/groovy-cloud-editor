import { Router, RouterLocation, EmptyCommands } from '@vaadin/router';

import {LitElement, CSSResult, html, css} from 'lit';
import {property, customElement} from 'lit/decorators.js';

import { ViewElement } from '../../components/view';

import { GCEStore } from '../../app/store';
import { back } from './editor-actions';



@customElement('groovy-run-page')
export class GroovyRunElement extends ViewElement {

	@property({ type: String })
	pageTitle = 'Run';

	@property({ type: Object })
	runResult: any = {};
	
	@property({ type: Object })
	targetScript: any = {};

	
	render() {

		return html`

			${this.pageTitleTemplate}
			${this.loadingTemplate}
			${this.errorTemplate}
			<div class="container">
				 <div class="form-group">
					<label>Script:</label>
    				<span>${this.targetScript?.name}</span>
				 </div>
				
				${this.runResult? html `
				 <div class="form-group">
    				<label>Execution Result:</label>
    				<span>${this.runResult instanceof String? this.runResult : JSON.stringify(this.runResult)}</span>
  				</div>
				`: undefined}
				<div class="btn-group" role="group" aria-label="Run">			  		
					<button type="button" class="btn btn-secondary" @click=${(e: MouseEvent)=> this.dispatch(back())}>Back</button>					
				</div>
			</div>

    `;
	}


	
	stateChanged(state: GCEStore) {
		if (state.editor) {
			console.log(state.editor);
			this.loading = state.editor.loading;
			this.errorMessage = state.editor.errorMessage;
			this.targetScript = state.editor.targetScript;
			this.runResult = state.editor.runResult; 
		}

	}




}


export default GroovyRunElement;
