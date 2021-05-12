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
    name: 'lit-css-plugin',
    transform: async ({id, fileExt, contents}) => {
    	if (fileExt === '.scss'){
      		console.log('scss transform', id, fileExt, contents);
      	} else if (fileExt === '.css'){
      		console.log('css transform', id, fileExt, contents);
      	}
    },
   

  };
}
