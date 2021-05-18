import { Router, RouterLocation, EmptyCommands } from '@vaadin/router';

import {LitElement, CSSResult, html, css} from 'lit';
import {property, customElement} from 'lit/decorators.js';

import { ViewElement } from '../../components/view';

import { GCEStore } from '../../app/store';
import { RunExecution, back } from './editor-actions';



@customElement('groovy-run-page')
export class GroovyRunPageElement extends ViewElement {

	@property({ type: String })
	pageTitle = 'Run';

	@property({ type: Object })
	execution?: RunExecution;
	
	@property({ type: Object })
	targetScript: any = {};


	
	static get styles() {
		return [super.styles, css` 
					
					`];
	}
	
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
				
				${this.execution? html `
					${this.execution.out? html `		
					 <div class="form-group">
	    				<label>Execution Output:</label>
	    				<pre><code>${this.execution.out}</code></pre>
	  				</div>`: undefined}
					${this.execution.result? html `		
					 <div class="form-group">
	    				<label>Execution Result:</label>
	    				<pre><code>${this.execution.result instanceof String? this.execution.result : JSON.stringify(this.execution.result,  null, 2)}</code></pre>
	  				</div>`: undefined}					
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
			this.execution = state.editor.execution; 
		}

	}




}


export default GroovyRunPageElement;
