import {LitElement, CSSResult, html, css} from 'lit';
import {property, customElement} from 'lit/decorators.js';

import { ViewElement } from '../../components/view';

import { GCEStore } from '../../app/store';
import { Script, fetchScripts, newScript, editScript, deleteScript, runScript, resetScripts } from './editor-actions';



@customElement('groovy-scripts-page')
export class GroovyScriptsElement extends ViewElement {

	@property({ type: String })
	pageTitle = 'Scripts';

	@property({ type: Array })
	scripts: Array<Script> = [];
	
	@property({ type: Number })
	selected?: number = -1;



	firstUpdated() {
		this.dispatch(fetchScripts());		
	}


	render() {

		return html`

			${this.pageTitleTemplate}
			${this.loadingTemplate}
			${this.errorTemplate}
			<div class="container">
				<table class="table">
				   <thead class="thead-dark">
				    <tr>
				      <th scope="col">#</th>
				      <th scope="col">Name</th>
				      <th scope="col">Last Modified</th>
				    </tr>
				  </thead>
				  <tbody>
					${this.scripts.map((s, i)=> 
						html ` <tr class=${ this.selected == i ? "table-active": undefined } @click=${(e: MouseEvent)=> this.selected = (this.selected == i? -1: i)}>
							     <th scope="row">${i + 1}</th>
							     <td>${s.name}</td>
							     <td>${new Date(s.lastModified).toLocaleString()}</td>
							   </tr>`
					)}
				   				   
				  </tbody>
				</table>
				
				<div class="btn-group" role="group" aria-label="Scripts">			  		
					<button type="button" ?disabled=${this.selected != -1} class="btn btn-primary mr-2" @click=${(e: MouseEvent)=> this.dispatch(newScript())}>New</button>
					<button type="button" ?disabled=${this.selected == -1} class="btn btn-secondary mr-2" @click=${(e: MouseEvent)=> this.dispatch(editScript(this.selected))}>Edit</button>
					<button type="button" ?disabled=${this.selected == -1} class="btn btn-secondary mr-2" @click=${(e: MouseEvent)=> this.dispatch(runScript(this.selected))}>Run</button>
					<button type="button" ?disabled=${this.selected == -1} class="btn btn-danger mr-4" @click=${(e: MouseEvent)=> this.dispatch(deleteScript(this.selected))}>Delete</button>
					
					<button type="button" class="btn btn-warning" @click=${(e: MouseEvent)=> this.dispatch(resetScripts())}>Reset</button>
				</div>
			</div>
			
			

    `;
	}


	
	stateChanged(state: GCEStore) {
		if (state.editor) {
			console.log(state.editor);
			this.scripts = state.editor.scripts;
			this.loading = state.editor.loading;
			this.errorMessage = state.editor.errorMessage;
		}

	}




}


export default GroovyScriptsElement;

