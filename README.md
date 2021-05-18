
Inspired from [codemirror-latex-hint](https://github.com/jun-sheaf/codemirror-latex-hint)

TODO: Update to CodeMirror 6.0. However this will require writing a [custom grammer](https://lezer.codemirror.net/docs/guide/#building-a-grammar) for groovy using [Lezer](https://lezer.codemirror.net/docs/guide/#writing-a-grammar), similar to the [Java](https://github.com/lezer-parser/java/blob/master/src/java.grammar) one. This is no small effort.

```
import {
	...
	ShowHintOptions,
	AsyncHintFunction
} from 'codemirror';

const hintFunction: any = this.groovyHint;
hintFunction.async = true;

const config: EditorConfiguration = {
	...
	hintOptions: {hint: hintFunction, container: this.hintElement},
	...
}	


async groovyHint(cm: Editor, callback: (hints: Hints | null | undefined) => void, options: any){
...
	console.log(callback);

```
https://stackoverflow.com/questions/41405016/how-to-underline-errors-with-codemirror
https://codemirror.net/demo/lint.html
