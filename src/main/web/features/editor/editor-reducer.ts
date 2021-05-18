import {
	EditorState,
	FETCH_SCRIPTS, FETCH_SCRIPTS_SUCCESS, FETCH_SCRIPTS_ERROR,
	NEW_SCRIPT,
	EDIT_SCRIPT,
	DELETE_SCRIPT, DELETE_SCRIPT_SUCCESS, DELETE_SCRIPT_ERROR,
	RUN_SCRIPT, RUN_SCRIPT_SUCCESS, RUN_SCRIPT_ERROR,
	SAVE_SCRIPT, SAVE_SCRIPT_SUCCESS, SAVE_SCRIPT_ERROR,
	RESET_SCRIPTS, RESET_SCRIPTS_SUCCESS, RESET_SCRIPTS_ERROR,
} from './editor-actions'

import produce from 'immer'


const initialState: EditorState = {
	loading: false,
	scripts: [],

}

const editor = (state: EditorState = initialState, action: any) => {
	return produce(state, (draft: EditorState) => {

		switch (action.type) {
			case FETCH_SCRIPTS: {
				draft.errorMessage = undefined;
				draft.loading = true;
				break
			}
			case FETCH_SCRIPTS_SUCCESS: {
				draft.errorMessage = undefined;
				draft.scripts = action.payload.scripts;
				draft.loading = false;
				break
			}
			case FETCH_SCRIPTS_ERROR: {
				draft.errorMessage = action.payload.error;
				draft.scripts = [];
				draft.loading = false;
				break
			}

			case NEW_SCRIPT: {
				draft.targetScript = action.payload.targetScript;
				break
			}

			case EDIT_SCRIPT: {
				draft.targetScript = action.payload.targetScript;
				break
			}

			case DELETE_SCRIPT: {
				draft.errorMessage = undefined;
				draft.scripts.splice(action.payload.index, 1);
				break
			}

			case RUN_SCRIPT: {
				draft.errorMessage = undefined;
				draft.targetScript = action.payload.targetScript;
				draft.loading = true;
				break
			}
			case RUN_SCRIPT_SUCCESS: {
				draft.errorMessage = undefined;
				draft.execution = action.payload.execution;
				draft.loading = false;
				break
			}
			case RUN_SCRIPT_ERROR: {
				draft.errorMessage = action.payload.error;
				draft.execution = undefined;
				draft.loading = false;
				break
			}

			case SAVE_SCRIPT: {
				draft.errorMessage = undefined;
				draft.targetScript = undefined;
				draft.loading = true;
				break
			}
			case SAVE_SCRIPT_SUCCESS: {
				draft.errorMessage = undefined;
				draft.targetScript = action.payload.targetScript;
				draft.loading = false;
				break
			}
			case SAVE_SCRIPT_ERROR: {
				draft.errorMessage = action.payload.error;
				draft.targetScript = undefined;
				draft.loading = false;
				break
			}

			case RESET_SCRIPTS: {
				draft.errorMessage = undefined;
				draft.loading = true;
				break
			}
			case RESET_SCRIPTS_SUCCESS: {
				draft.errorMessage = undefined;
				draft.scripts = action.payload.scripts;
				draft.loading = false;
				break
			}
			case RESET_SCRIPTS_ERROR: {
				draft.errorMessage = action.payload.error;
				draft.scripts = [];
				draft.loading = false;
				break
			}



			default:
				return draft
		}
	});
}



export default editor