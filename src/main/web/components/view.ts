import {html, css, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';


import { connect, store, GCEStore } from '../app/store';

import { Router } from '@vaadin/router';

//@ts-ignore
import {bootstrapStyles} from '@granite-elements/granite-lit-bootstrap/granite-lit-bootstrap.js';

export class ViewElement extends connect<GCEStore>(store)(LitElement) {

	@property({ type: String, attribute: 'page-title', reflect: true })
	pageTitle?: string;

	@property({ type: String, attribute: 'page-sub-title', reflect: true })
	subPageTitle?: string;

	@property({ type: Boolean })
	loading?: boolean = false;

	@property({ type: String })
	loadingMessage = "";

	@property({ type: String })
	errorMessage?: string;

	location?: Router.Location;

	//lwdc-section-row

	static get styles() {
	  return [bootstrapStyles];
	}

	get pageTitleTemplate() {
		return html`<div class="container">
  						<div class="page-header">
    						<h2>${this.pageTitle}</h2>      
  						</div>
					</div>
					<br/>
					`;
	}

	get errorTemplate(){
		if (this.errorMessage) {
			return html `<div class="container">
							<div class="alert alert-danger" role="alert">${this.errorMessage}</div>
						</div>`;
		}
	}

	get subPageTitleTemplate() {
		if (this.subPageTitle) {
			return html`<div class="wdc-page-sub-header-title">${this.subPageTitle}</div>`;
		}
	}

	get loadingTemplate() {
		if (this.loading) {
			return html`<div class="container">
							<div class="spinner-border" role="status">
  								<span class="sr-only">Loading...</span>
							</div>
						</div>`;
		}
	}
	


}




