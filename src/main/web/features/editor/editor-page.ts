import {LitElement, CSSResult, html, css} from 'lit';
import {property, customElement, query, state} from 'lit/decorators.js';
import {ifDefined} from 'lit/directives/if-defined.js';

import { ViewElement } from '../../components/view';

import { GCEStore } from '../../app/store';
import { Script, back, save, readFile } from './editor-actions';

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
		
	@state()
	scriptName?: string;
	
	@state()
	scriptContents?: string;
	
	@state()
	attachment?: File;
	
			
	@query('#scriptName')
	scriptNameElement?:HTMLInputElement;
	
	@query('#groovyEditor')
	groovyEditor?: GroovyEditorElement;
	
	@query("#fileInput")
	fileInputElement?: HTMLInputElement;
	
	
	
	constructor() {
	    super();
    	this.addEventListener('editor-update', (e: Event) => {
			const codeChange = (e as CustomEvent).detail.changes.find((c: any) => c.origin != "setValue");
			this.modified = codeChange;
		});
		this.addEventListener('editor-lint', (e: Event) => {this.hasErrors = (e as any).detail.errors.length > 0;});
  	}


	static get styles() {
		return [super.styles, css` 
			.upload-wrapper input[type=file] {
				font-size: 42px;
				position: absolute;
				left: 0;
				top: 0;
				opacity: 0;
				height: 0;
				width: 0;
			}	
			
			.upload-picker-list {
				width: 100%;
				display: flex;
				flex-direction: column;
				align-items: center;
				justify-content: center;
			}
					`];
	}
	



	
	render() {		
		return html`

			${this.pageTitleTemplate}
			${this.loadingTemplate}
			${this.errorTemplate}
			<div class="container">
				 <div class="form-group">
					<label  for="scriptName">Name</label>
    				<input class="form-control" type="text" placeholder="Default input" id="scriptName" .value=${ifDefined(this.scriptName)} @change=${(e: Event) => this.modified = true}></input>
				 </div>
			
				<div class="form-group">
    				<label for="groovyEditor">Script</label>
    				<groovy-editor id="groovyEditor" script-name=${ifDefined(this.scriptName)} .script=${ifDefined(this.scriptContents)}></groovy-editor>					
  				</div>
		
				<div class="upload-wrapper">
					 	<input type="file" id="fileInput" @change=${this.fileChanged}/>
				</div>
				
				${this.attachment? html `
				<div class="form-group">
    				<label>File Attachment</label>
						<div>${this.attachment.name}</div>
  				</div>	
				
				
				`: undefined}
				
								
				<div class="btn-group" role="group" aria-label="Run">			  		
					<button ?disabled=${!this.modified || this.hasErrors } type="button" class="btn btn-primary mr-2" @click=${this.handleSave}>Save</button>
					<button type="button" class="btn btn-primary mr-2" @click=${this.selectFile}>File</button>
					<button type="button" class="btn btn-secondary" @click=${(e: MouseEvent)=> this.dispatch(back())}>Back</button>	
				</div>
			</div>

    `;
	}
	
	
	selectFile(e: MouseEvent) {
		if (this.fileInputElement) {
			console.log("selectFile", e.target);
			this.modified = true;
			this.fileInputElement.click();
		}
	}
	
	fileChanged(e: Event){
		console.log("fileChange", e.target);
		 let fileInput: HTMLInputElement =  e.target as any;
		if (fileInput && fileInput.files && fileInput.files?.length>0){
			this.attachment = fileInput.files[0];
		}
		 this.requestUpdate();
	}
	
	
	handleSave(e: MouseEvent){
		if (this.groovyEditor && this.groovyEditor.validate()){
			let name= this.scriptNameElement && this.scriptNameElement.value? this.scriptNameElement.value: "undefined.groovy";
			let contents = new File([this.groovyEditor.script], name,{type:"text/plain", lastModified:new Date().getTime()});
			let updateScript = <Script>{
				scriptId: this.targetScript?.scriptId,				
				contents: contents,
				attachment: this.attachment
			};
			console.log("save", this.targetScript?.scriptId, updateScript);
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
				this.pageTitle = 'Editor - ' + (this.targetScript.contents ? 'Update': 'New');
				this.scriptName = this.targetScript.contents.name;
				 const reader = new FileReader();				
		  		 reader.readAsText(this.targetScript.contents);
				 reader.onload = ()=> {				
          			this.scriptContents = reader.result as string;
        		};
				if (this.targetScript.attachment){
					 //let container = new DataTransfer();
 					 //container.items.add(this.targetScript.attachment);
					 //this.fileInput.files = container.files;
					this.attachment = this.targetScript.attachment;
				}
			}else {
				this.pageTitle = 'Editor';
			}
		}

	}





}


export default GroovyEditorPageElement;
