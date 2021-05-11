const httpProxy = require('http-proxy');
const proxy = httpProxy.createServer({"target": "http://localhost:5000","proxyTimeout" : 60000});
const proxyDest = (req, res) => proxy.web(req, res);
/** @type {import("snowpack").SnowpackUserConfig } */
module.exports = {

  "mount": {
    "src/main/web": "/"
  },
  "routes": [
    {
      src: '/etlu-modules.js',
      dest: proxyDest,
    },    
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
    [
      "./lit-scss-plugin.js",
      {}
    ]
  ]
};
