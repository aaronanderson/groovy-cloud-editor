const httpProxy = require('http-proxy');
const proxy = httpProxy.createServer({"target": "http://localhost:5000","proxyTimeout" : 600000});
const proxyDest = (req, res) => proxy.web(req, res);
/** @type {import("snowpack").SnowpackUserConfig } */
module.exports = {

  "mount": {
    "src/main/web": "/"
  },
  "routes": [
    {
      src: '/api/.*',
      dest: proxyDest,
    },
    
   {"match": "routes", "src": ".*", "dest": "/index.html"}
  ],
  "buildOptions": {
    "sourcemap": true,
    "out": "./target/web-build"
  },
  "plugins": [         	
      "@snowpack/plugin-typescript",
      //"./lit-scss-plugin.js",
      "./lit-cssx-plugin.js",		    
  ]
};

