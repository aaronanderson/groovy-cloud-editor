const esbuild = require("esbuild");
const path = require("path");
const sassPlugin = require("esbuild-sass-plugin").sassPlugin;
const postcss = require("postcss");
const fs = require('fs');

const targetDir = 'target/web-build';

if (!fs.existsSync(targetDir)){
    fs.mkdirSync(targetDir);
}

fs.writeFileSync(`${targetDir}/index.html`, fs.readFileSync('src/main/web/index.html'));

esbuild.build({
    entryPoints: {
      'index': 'index.ts',      
    },
    format: 'esm',
    outdir: 'target/web-build',
    bundle: true,
    splitting: true,
    sourcemap: 'external',
    plugins: [
      sassPlugin({
       implementation: 'node-sass',
       //wait for update to lit
       //type: 'lit-css',
       type: "css-text",
       includePaths: [
         path.resolve('node_modules'),
       ],
       async transform(source, resolveDir) {
         const {css} = await postcss([require('postcss-inline-svg')]).process(source);
         return css;
       }

     }),
   ],
    watch: {
	    onRebuild(error, result) {
	      if (error) console.error('watch build failed:', error)
	      else console.log('watch build succeeded:', result)
	    },
  	},
  }).catch((e) => console.error(e.message))
  //.then((result)=>  result.stop());
  
