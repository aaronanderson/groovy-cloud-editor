import { Router } from '@vaadin/router';

import {html, css, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';


import '../features/editor/scripts-page';
import '../features/editor/editor-page';
import '../features/editor/run-page';

import {bootstrapStyles} from '@granite-elements/granite-lit-bootstrap/granite-lit-bootstrap.js';


@customElement('groovy-cloud-editor-app')
export class AppElement extends LitElement {


  firstUpdated() {
	
    if (this.shadowRoot) {

      let mainContent: HTMLElement = this.shadowRoot.getElementById('main-content') as HTMLElement;
      let router = new Router(mainContent);
      router.setRoutes([
        { path: '/', component: 'groovy-scripts-page' },
		{ path: '/edit', component: 'groovy-editor-page' },
		{ path: '/run', component: 'groovy-run-page' },
      

      ]);


    }

  }


  static get styles() {
    return [bootstrapStyles];
  }


  render() {
    return html`
	  <div class="jumbotron">
	    <h1>Groovy Cloud Editor Demo</h1>      
	  </div>
      <main>
        <section class="main-content" id="main-content"></section>
      </main>



    `;
  }




 


}



export default AppElement;


