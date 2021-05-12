import { Router } from '@vaadin/router';

import { gceuDB, cyrb53 } from '../../app/store';

export const FETCH_SCRIPTS = 'FETCH_SCRIPTS'
export const FETCH_SCRIPTS_SUCCESS = 'FETCH_SCRIPTS_SUCCESS'
export const FETCH_SCRIPTS_ERROR = 'FETCH_SCRIPTS_ERROR'

export const NEW_SCRIPT = 'NEW_SCRIPT'

export const EDIT_SCRIPT = 'EDIT_SCRIPT'

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


export const UPDATE_USER = 'UPDATE_USER'
export const DELETE_USER = 'DELETE_USER'


export interface EditorState {
	loading: boolean;
	errorMessage?: string;
	action?: string;

	scripts: Script[];
	targetScript?: Script;
	runResult?: Object;
}


export const fetchScripts: any = () => async (dispatch: any) => {
	dispatch({ type: FETCH_SCRIPTS });


	let db = await gceuDB();
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
	let scripts: Array<Script> = scriptsResult.scripts;
	await saveScripts(scripts, db);
	return scripts;
}

const saveScripts = async (scripts: Array<Script>, db: any): Promise<void> => {
	const tx = db.transaction('scripts', 'readwrite');
	const store = tx.objectStore('scripts');
	for (let s of scripts) {
		let entry = { ...s, scriptId: s.name };
		await store.put(entry);
	}
}

export const newScript: any = () => async (dispatch: any) => {
	await dispatch({ type: NEW_SCRIPT, payload: { targetScript: {} } });
	Router.go('/edit');

}

export const editScript: any = (index: number) => async (dispatch: any, getState: any) => {
	const { scripts } = getState().editor;
	let s = scripts[index];
	await dispatch({ type: EDIT_SCRIPT, payload: { targetScript: s } })
	Router.go('/edit');
	
}

export const deleteScript: any = (index: number) => async (dispatch: any, getState: any) => {
	let db = await gceuDB();
	try {
		const { scripts } = getState().editor;
		let s = scripts[index];
		dispatch({ type: DELETE_SCRIPT, payload: { index: index } });
		const tx = db.transaction('scripts', 'readwrite');
		const store = tx.objectStore('scripts');
		let val = await store.delete([s.name]);
		console.log(s.name, val);
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
		const response = await fetch("/api/gce/run", {
			method: 'POST',
			headers: {
				'Accept': 'application/json',
				'Content-Type': 'application/json'
			},
			body: JSON.stringify(s)
		});
		const runResult: RunResult = await response.json();
		if (runResult.status == "error") {
			throw Error(runResult.message);
		}
		dispatch({ type: RUN_SCRIPT_SUCCESS, payload: { runResult: runResult.result } })
	} catch (error) {
		console.error('Error:', error);
		dispatch({ type: RUN_SCRIPT_ERROR, payload: { error: error } })
	}


}

export const resetScripts: any = () => async (dispatch: any) => {
	dispatch({ type: RESET_SCRIPTS });
	let db = await gceuDB();
	try {
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

export interface ScriptsResult {
	scripts: Array<Script>;
}

export interface RunResult {
	result?: Object;
	status: string;
	message?: string;
}


export interface Script {
	id: string;
	name: string;
	contents: string;
	lastModified: string;
}


