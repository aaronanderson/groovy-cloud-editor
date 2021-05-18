const fs = require("fs").promises;

module.exports = function(snowpackConfig, pluginOptions) {
	return {
		name: 'lit-cssx-plugin',
		transform: async ({ id, fileExt, contents }) => {
			if (fileExt === '.js') {
				let cssxPaths = [...contents.matchAll(/(('|")!cssx\|(.+)('|"))/g)].map(r => [r[1], r[3]]);
				for (let cssxPath of cssxPaths) {
					//console.log('cssx load', id, cssxPath[1]);
					let filePath = cssxPath[1];
					if (!filePath.startsWith('.')) {
						filePath = 'node_modules/' + filePath;
					}
					const rawContents = await fs.readFile(filePath, 'utf-8');
					const cssContents = rawContents.replace(/\u2028/g, "\\u2028").replace(/\u2029/g, "\\u2029");
					contents = contents.replace(cssxPath[0], cssContents);
				}
				return contents;
			}
		},
	};
}
