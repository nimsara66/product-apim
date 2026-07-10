/*
 *  Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

// A real WSDL-backed SOAP service built on the official node-soap library. Unlike the fixed-envelope soap-stub,
// this exposes a proper WSDL (GET /service?wsdl) so APIM can IMPORT an API from the WSDL, generate SOAP-to-REST
// resources from it, and export/import a WSDL-backed SOAP API. The single sayHello(name) operation returns a
// greeting, so a proxied SOAP call can be verified end-to-end.
const soap = require('soap');
const http = require('http');
const fs = require('fs');
const path = require('path');

const port = process.env.PORT || 3021;
const wsdl = fs.readFileSync(path.join(__dirname, 'hello.wsdl'), 'utf8');

const service = {
  HelloService: {
    HelloPort: {
      sayHello: function (args) {
        const name = args && args.name ? args.name : 'World';
        return { greeting: 'Hello ' + name };
      }
    }
  }
};

// Plain GET (other than ?wsdl) is a simple health check; node-soap handles POST + GET ?wsdl on /service.
const server = http.createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    res.statusCode = 200;
    res.end('OK');
    return;
  }
  res.statusCode = 404;
  res.end('404: Not Found (SOAP service at /service, WSDL at /service?wsdl)');
});

server.listen(port, () => {
  soap.listen(server, '/service', service, wsdl);
  console.log('node-soap HelloService running at http://nodebackend:' + port + '/service?wsdl');
});
