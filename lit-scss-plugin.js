//const fs = require("fs").promises;
const sass = require('sass')


 
        
const cssResultModule = (cssText) => {
	const json = JSON.stringify(cssText).replace(/\u2028/g, "\\u2028").replace(/\u2029/g, "\\u2029"); 
	return `\
import {css} from "lit";
export default css\`
${json}\`;
`;
}

module.exports = function (snowpackConfig, pluginOptions) {
  return {
    name: 'lit-scss-plugin',   
    resolve: {
      input: ['.scss'],
      output: ['.js'],
    },
    async load({ filePath }) {
      console.log("lit-scss-plugin", filePath);
      //const fileContents = await fs.readFile(filePath, 'utf-8');
      const result = sass.renderSync({
        file: filePath,
          includePaths: ["node_modules"]
        });
      return cssResultModule(result.css.toString());
    }

  };
}
