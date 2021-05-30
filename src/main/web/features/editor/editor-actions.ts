import { Router } from '@vaadin/router';

import { gceDB, cyrb53 } from '../../app/store';

export const FETCH_SCRIPTS = 'FETCH_SCRIPTS'
export const FETCH_SCRIPTS_SUCCESS = 'FETCH_SCRIPTS_SUCCESS'
export const FETCH_SCRIPTS_ERROR = 'FETCH_SCRIPTS_ERROR'

export const NEW_SCRIPT = 'NEW_SCRIPT'

export const EDIT_SCRIPT = 'EDIT_SCRIPT'

export const VALIDATE_SCRIPT = 'VALIDATE_SCRIPT'

export const DELETE_SCRIPT = 'DELETE_SCRIPT'
export const DELETE_SCRIPT_SUCCESS = 'DELETE_SCRIPT_SUCCESS'
export const DELETE_SCRIPT_ERROR = 'DELETE_SCRIPT_ERROR'

export const RUN_SCRIPT = 'RUN_SCRIPT'
export const RUN_SCRIPT_SUCCESS = 'RUN_SCRIPT_SUCCESS'
export const RUN_SCRIPT_ERROR = 'RUN_SCRIPT_ERROR'

export const RESET_SCRIPTS = 'RESET_SCRIPTS'
export const RESET_SCRIPTS_SUCCESS = 'RESET_SCRIPTS_SUCCESS'
export const RESET_SCRIPTS_ERROR = 'RESET_SCRIPTS_ERROR'

export const BACK_TO_HOME = 'BACK_TO_HOME'
export const BACK_TO_HOME_SUCCESS = 'BACK_TO_HOME_SUCCESS'
export const BACK_TO_HOME_ERROR = 'BACK_TO_HOME_ERROR'

export const SAVE_SCRIPT = 'SAVE_SCRIPT'
export const SAVE_SCRIPT_SUCCESS = 'SAVE_SCRIPT_SUCCESS'
export const SAVE_SCRIPT_ERROR = 'SAVE_SCRIPT_ERROR'


export const UPDATE_USER = 'UPDATE_USER'
export const DELETE_USER = 'DELETE_USER'


export interface EditorState {
	loading: boolean;
	errorMessage?: string;
	action?: string;

	scripts: Script[];
	targetScript?: Script;
	execution?: RunExecution;
}


export const fetchScripts: any = () => async (dispatch: any) => {
	dispatch({ type: FETCH_SCRIPTS });

	let db = await gceDB();
	try {
		let scripts: Array<Script> = [];
		let cursor = await db.transaction('scripts').store.openCursor();

		while (cursor) {
			scripts.push(<Script>{ ...cursor.value });
			cursor = await cursor.continue();
		}

		if (scripts.length > 0) {
			dispatch({ type: FETCH_SCRIPTS_SUCCESS, payload: { scripts: scripts } });
		} else {
			try {
				scripts = await loadScripts(db);
				dispatch({ type: FETCH_SCRIPTS_SUCCESS, payload: { scripts: scripts } })
			} catch (error) {
				console.error('Error:', error);
				dispatch({ type: FETCH_SCRIPTS_ERROR, payload: { error: error } })
			}
		}
	} finally {
		db.close();
	}
}


const loadScripts = async (db: any): Promise<Array<Script>> => {
	const response = await fetch("/api/gce/scripts", {
		method: 'GET'
	});
	const scriptsResult: ScriptsResult = await response.json();
	if (!response.ok) {
		throw Error(response.statusText);
	}

	//base64 encoded files in a single fetch is faster for small file sizes. Larger sizes may require formdata binary encoding.
	let sourceScripts: Array<SourceScript> = scriptsResult.scripts;
	let scripts: Array<Script> = [];
	for (let s of sourceScripts) {
		let script = <Script>{}
		script.scriptId = Number(s.scriptId);
		let contents = await fetch(`data:${s.contents.content_type};base64,${s.contents.text}`);

		script.contents = new File([await contents.blob()], s.contents.name, { type: s.contents.content_type, lastModified: new Date(s.contents.lastModified).getTime() });
		if (s.attachment) {
			contents = await fetch(`data:${s.attachment.content_type};base64,${s.attachment.text}`);
			script.attachment = new File([await contents.blob()], s.attachment.name, { type: s.attachment.content_type, lastModified: new Date(s.attachment.lastModified).getTime() });
		}
		scripts.push(script);
	};

	//	let formData = await fetchFiles(s.id);		

	const tx = db.transaction('scripts', 'readwrite');
	const store = tx.objectStore('scripts');
	for (let i = 0; i < scripts.length; i++) {
		let s: Script = scripts[i];
		let entry = { ...s };
		await store.put(entry);
	}
	return scripts;
}


//not used
const fetchFiles = async (id: string): Promise<FormData> => {
	const response = await fetch(`api/gce/script-file/${id}`, {
		method: 'GET'
	});
	const scriptFiles: FormData = await response.formData();
	if (!response.ok) {
		throw Error(response.statusText);
	}
	return scriptFiles;
}


export const newScript: any = () => async (dispatch: any, getState: any) => {
	let contents = new File([], "undefined.groovy", { type: "text/plain", lastModified: new Date().getTime() });
	await dispatch({ type: NEW_SCRIPT, payload: { targetScript: { name: 'new.groovy', contents: contents } } });
	Router.go('/edit');

}


export const editScript: any = (index: number) => async (dispatch: any, getState: any) => {
	const { scripts } = getState().editor;
	let s = scripts[index];
	await dispatch({ type: EDIT_SCRIPT, payload: { targetScript: s } })
	Router.go('/edit');

}


export const validateScript: any = (validating: boolean) => async (dispatch: any, getState: any) => {
	await dispatch({ type: VALIDATE_SCRIPT, payload: { validating: validating } })
}


export const deleteScript: any = (index: number) => async (dispatch: any, getState: any) => {
	let db = await gceDB();
	try {
		const { scripts } = getState().editor;
		let s = scripts[index];
		dispatch({ type: DELETE_SCRIPT, payload: { index: index } });
		const tx = db.transaction('scripts', 'readwrite');
		const store = tx.objectStore('scripts');
		let val = await store.delete(s.scriptId);
		//await store.clear();
	} finally {
		db.close();
	}
}


export const runScript: any = (index: number) => async (dispatch: any, getState: any) => {
	const { scripts } = getState().editor;
	let s = scripts[index];
	await dispatch({ type: RUN_SCRIPT, payload: { targetScript: s } });
	Router.go('/run');

	try {
		const { scripts } = getState().editor;
		let s = scripts[index];

		const formData = new FormData();
		formData.append('contents', s.contents);
		if (s.attachment) {
			formData.append('attachment', s.attachment);
		}


		const response = await fetch("/api/gce/run", {
			method: 'POST',
			headers: {
				'Accept': 'application/json',
				//'Content-Type': 'multipart/form-data'
			},
			body: formData
		});
		const runResult: RunResult = await response.json();
		if (runResult.status == "error") {
			throw Error(runResult.message);
		}
		dispatch({ type: RUN_SCRIPT_SUCCESS, payload: { execution: <RunExecution>{ result: runResult.result, out: runResult.out } } })
	} catch (error) {
		console.error('Error:', error);
		dispatch({ type: RUN_SCRIPT_ERROR, payload: { error: error } })
	}
}


export const resetScripts: any = () => async (dispatch: any) => {
	dispatch({ type: RESET_SCRIPTS });
	let db = await gceDB();
	try {
		let cursor = await db.transaction('scripts', 'readwrite').store.openCursor();
		while (cursor) {
			if (cursor && cursor.delete) {
				cursor.delete();
			}
			cursor = await cursor.continue();
		}
		let scripts: Array<Script> = await loadScripts(db);
		dispatch({ type: RESET_SCRIPTS_SUCCESS, payload: { scripts: scripts } })
	} catch (error) {
		console.error('Error:', error); dispatch({ type: RESET_SCRIPTS_ERROR, payload: { error: error } })

	} finally {
		db.close();
	}
}


export const back: any = () => async (dispatch: any) => {
	dispatch({ type: BACK_TO_HOME });
	try {

		dispatch({ type: BACK_TO_HOME_SUCCESS, payload: {} })
	} catch (error) {
		console.error('Error:', error);
		dispatch({ type: BACK_TO_HOME_ERROR, payload: { error: error } })
	}
	Router.go('/');

}


export const save: any = (updateScript: Script) => async (dispatch: any) => {
	dispatch({ type: SAVE_SCRIPT });
	let db = await gceDB();
	try {
		const tx = db.transaction('scripts', 'readwrite');
		const store = tx.objectStore('scripts');
		await store.put(updateScript);
		dispatch({ type: SAVE_SCRIPT_SUCCESS, payload: { targetScript: updateScript } })
		Router.go('/');
	} catch (error) {
		console.error('Error:', error);
		dispatch({ type: SAVE_SCRIPT_ERROR, payload: { error: error } })
	} finally {
		db.close();
	}
}


export const readFile = (file: File): Promise<string> => {
	const fileReader = new FileReader();

	return new Promise((resolve, reject) => {
		fileReader.onerror = () => {
			fileReader.abort();
			reject(new DOMException("Problem parsing input file."));
		};

		fileReader.onload = () => {
			resolve(fileReader.result as string);
		};
		fileReader.readAsText(file);
	});
};


export interface ScriptsResult {
	scripts: Array<SourceScript>;
}


export interface SourceScript {
	scriptId: number;
	contents: SourceAttachment;
	attachment?: SourceAttachment;
}


export interface SourceAttachment {
	name: string;
	lastModified: string;
	text: string;
	content_type?: string;
}


export interface SourceAttachment {

}


export interface RunResult {
	result?: Object;
	out?: string;
	status: string;
	message?: string;
}


export interface RunExecution {
	result?: Object;
	out?: string;
}


export interface Script {
	scriptId: number;
	contents: File;
	attachment?: File;
}