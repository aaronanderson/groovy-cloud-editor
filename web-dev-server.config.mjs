import { esbuildPlugin } from "@web/dev-server-esbuild";
import proxy from 'koa-proxies';

export default {
   port: 8000,
   nodeResolve: true,
   open: true,
   watch: true,
   appIndex: 'index.html',
   rootDir: 'src/main/web',	
   plugins: [esbuildPlugin({ loaders: { '.ts': 'ts', '.css': 'text' } })],

   middleware: [
	  proxy('/api', {
	    target: 'http://localhost:5000/api',
	  }),
	],
};
